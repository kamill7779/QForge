(function (global) {
  function createOcrRuntime(deps) {
    const {
      state,
      log,
      handleAiResult,
      toAnswerXmlPayload,
      saveWorkspace,
      renderAll,
      fetchAssets,
      wsUrl,
      WebSocketImpl
    } = deps;

    const WsCtor = WebSocketImpl || global.WebSocket;

    function upsertWs(msg) {
      if (msg.event === "ai.analysis.succeeded" || msg.event === "ai.analysis.failed") {
        handleAiResult(msg);
        return;
      }

      // 转发 exam-parse 事件
      const evt = msg.event || "";
      if (evt.startsWith("exam.parse.")) {
        if (typeof deps.handleExamParseWs === "function") {
          deps.handleExamParseWs(msg);
        }
        return;
      }

      const p = msg.payload || {};
      if (!p.taskUuid) return;

      const bizType = p.bizType || "QUESTION_STEM";
      const old = state.ocrTasks.get(p.taskUuid) || {
        taskUuid: p.taskUuid,
        questionUuid: p.bizId || "",
        bizType,
        status: "PENDING",
        recognizedText: "",
        errorMessage: "",
        updatedAt: Date.now()
      };

      old.questionUuid = p.bizId || old.questionUuid;
      old.bizType = bizType;

      if (msg.event === "ocr.task.succeeded") {
        old.status = "SUCCESS";
        old.recognizedText = p.recognizedText || "";
        old.errorMessage = "";
        log(`OCR成功: ${p.taskUuid}`);
      } else if (msg.event === "ocr.task.failed") {
        old.status = "FAILED";
        old.errorMessage = p.errorMessage || "Unknown error";
        log(`OCR失败: ${p.taskUuid}`);
      }

      old.updatedAt = Date.now();
      state.ocrTasks.set(p.taskUuid, old);

      const entry = state.entries.get(p.bizId);
      if (!entry) return;

      if (bizType === "ANSWER_CONTENT") {
        entry.lastAnswerOcrTaskUuid = p.taskUuid;
        entry.lastAnswerOcrStatus = old.status;
        if (!entry.answerDraft && old.recognizedText) {
          entry.answerDraft = toAnswerXmlPayload(old.recognizedText);
        }
      } else {
        entry.lastOcrTaskUuid = p.taskUuid;
        entry.lastOcrStatus = old.status;
        if (!entry.stemDraft && old.recognizedText) {
          entry.stemDraft = old.recognizedText;
        }
      }

      entry.updatedAt = Date.now();
      state.entries.set(entry.questionUuid, entry);
    }

    function connectWs() {
      if (!state.username || !WsCtor) return;
      if (state.ws) {
        state.ws.close();
        state.ws = null;
      }

      const ws = new WsCtor(wsUrl());
      state.ws = ws;

      ws.onopen = () => {
        state.wsConnected = true;
        renderAll();
        log("WS已连接");
      };

      ws.onclose = () => {
        state.wsConnected = false;
        renderAll();
        log("WS已断开");
      };

      ws.onerror = () => {
        state.wsConnected = false;
        renderAll();
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          upsertWs(msg);
          saveWorkspace();
          renderAll();

          if (msg.event === "ocr.task.succeeded" && msg.payload?.bizId) {
            const entry = state.entries.get(msg.payload.bizId);
            if (entry) {
              entry.assetsLoaded = false;
              state.entries.set(entry.questionUuid, entry);
            }
            fetchAssets(msg.payload.bizId);
          }
        } catch {
          log("收到无法解析的WS消息");
        }
      };
    }

    return {
      upsertWs,
      connectWs
    };
  }

  const api = { createOcrRuntime };
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  global.QForgeOcrRuntime = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
