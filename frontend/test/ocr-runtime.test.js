const test = require("node:test");
const assert = require("node:assert/strict");

const { createOcrRuntime } = require("../src/runtime/ocr-runtime.js");

function createBaseDeps() {
  const state = {
    username: "admin",
    token: "token",
    ws: null,
    wsConnected: false,
    ocrTasks: new Map(),
    entries: new Map()
  };

  const calls = {
    logs: [],
    fetchAssets: [],
    renderAll: 0,
    saveWorkspace: 0
  };

  return {
    state,
    calls,
    deps: {
      state,
      log: (msg) => calls.logs.push(msg),
      handleAiResult: () => {},
      toAnswerXmlPayload: (text) => `<answer version="1"><p>${text}</p></answer>`,
      saveWorkspace: () => {
        calls.saveWorkspace += 1;
      },
      renderAll: () => {
        calls.renderAll += 1;
      },
      fetchAssets: (qid) => calls.fetchAssets.push(qid),
      wsUrl: () => "ws://localhost/ws/questions?x=1"
    }
  };
}

test("upsertWs does not overwrite existing answer draft", () => {
  const { state, deps } = createBaseDeps();
  state.entries.set("q-1", {
    questionUuid: "q-1",
    answerDraft: "<answer version=\"1\"><p>manually edited</p></answer>",
    stemDraft: "",
    lastAnswerOcrTaskUuid: "",
    lastAnswerOcrStatus: "",
    updatedAt: 0
  });

  const runtime = createOcrRuntime(deps);
  runtime.upsertWs({
    event: "ocr.task.succeeded",
    payload: {
      taskUuid: "t-1",
      bizType: "ANSWER_CONTENT",
      bizId: "q-1",
      recognizedText: "ocr answer"
    }
  });

  assert.equal(
    state.entries.get("q-1").answerDraft,
    "<answer version=\"1\"><p>manually edited</p></answer>"
  );
});

test("upsertWs fills empty stem draft for question stem", () => {
  const { state, deps } = createBaseDeps();
  state.entries.set("q-1", {
    questionUuid: "q-1",
    answerDraft: "",
    stemDraft: "",
    lastOcrTaskUuid: "",
    lastOcrStatus: "",
    updatedAt: 0
  });

  const runtime = createOcrRuntime(deps);
  runtime.upsertWs({
    event: "ocr.task.succeeded",
    payload: {
      taskUuid: "t-2",
      bizType: "QUESTION_STEM",
      bizId: "q-1",
      recognizedText: "<stem version=\"1\"><p>x</p></stem>"
    }
  });

  assert.equal(state.entries.get("q-1").stemDraft, "<stem version=\"1\"><p>x</p></stem>");
});

test("connectWs triggers fetchAssets on OCR success", () => {
  class FakeWebSocket {
    static instances = [];
    constructor(url) {
      this.url = url;
      this.onopen = null;
      this.onclose = null;
      this.onerror = null;
      this.onmessage = null;
      FakeWebSocket.instances.push(this);
    }
    close() {}
  }

  const { state, calls, deps } = createBaseDeps();
  state.entries.set("q-1", { questionUuid: "q-1", assetsLoaded: true, answerDraft: "", stemDraft: "", updatedAt: 0 });

  const runtime = createOcrRuntime({ ...deps, WebSocketImpl: FakeWebSocket });
  runtime.connectWs();

  const ws = FakeWebSocket.instances[0];
  ws.onmessage({
    data: JSON.stringify({
      event: "ocr.task.succeeded",
      payload: {
        taskUuid: "t-3",
        bizType: "ANSWER_CONTENT",
        bizId: "q-1",
        recognizedText: "ocr answer"
      }
    })
  });

  assert.deepEqual(calls.fetchAssets, ["q-1"]);
  assert.equal(state.entries.get("q-1").assetsLoaded, false);
});
