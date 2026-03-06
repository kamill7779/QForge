/**
 * QForge 试卷自动解析 Runtime
 * 管理 exam-parse 会话状态、API 调用、WS 事件、渲染逻辑。
 *
 * IIFE 模式，挂载 window.QForgeExamParseRuntime
 */
(function (global) {
  "use strict";

  /* ================================================================
   *  会话状态
   * ================================================================ */

  /** @type {{ tasks: Map<string, object>, activeTaskUuid: string, questions: Map<string, object[]> }} */
  const epState = {
    /** taskUuid → task meta */
    tasks: new Map(),
    /** 当前选中的任务 UUID */
    activeTaskUuid: "",
    /** taskUuid → ExamParseQuestion[] */
    questions: new Map(),
    /** 信息日志队列（最新在前） */
    logs: [],
  };

  /* ================================================================
   *  依赖占位 —— 由 init() 注入
   * ================================================================ */
  let _api = null;       // function(path, method, body)
  let _log = null;       // function(msg)
  let _render = null;    // function() – 触发全局 renderAll
  let _state = null;     // 全局 state 对象（取 token / username）
  let _saveWs = null;    // saveWorkspace

  /* ================================================================
   *  初始化
   * ================================================================ */

  function init(deps) {
    _api = deps.api;
    _log = deps.log || console.log;
    _render = deps.render;
    _state = deps.state;
    _saveWs = deps.saveWorkspace;
    // 从 localStorage 恢复
    loadLocalState();
  }

  /* ================================================================
   *  持久化存储
   * ================================================================ */

  function storageKey() {
    return `qforge.examparse.${_state ? _state.username : "anon"}`;
  }

  function saveLocalState() {
    try {
      const obj = {
        tasks: [...epState.tasks.values()],
        activeTaskUuid: epState.activeTaskUuid,
        questions: {},
        logs: epState.logs.slice(0, 200),
      };
      for (const [k, v] of epState.questions) obj.questions[k] = v;
      localStorage.setItem(storageKey(), JSON.stringify(obj));
    } catch { /* ignore */ }
  }

  function loadLocalState() {
    try {
      const raw = localStorage.getItem(storageKey());
      if (!raw) return;
      const obj = JSON.parse(raw);
      epState.tasks.clear();
      epState.questions.clear();
      for (const t of (obj.tasks || [])) epState.tasks.set(t.taskUuid, t);
      epState.activeTaskUuid = obj.activeTaskUuid || "";
      for (const [k, v] of Object.entries(obj.questions || {})) {
        epState.questions.set(k, v);
      }
      epState.logs = Array.isArray(obj.logs) ? obj.logs.slice(0, 200) : [];
    } catch { /* ignore */ }
  }

  /* ================================================================
   *  日志
   * ================================================================ */

  function epLog(msg) {
    const ts = new Date().toLocaleTimeString();
    epState.logs.unshift({ ts, msg });
    if (epState.logs.length > 200) epState.logs.length = 200;
    if (_log) _log("[试卷解析] " + msg);
  }

  /* ================================================================
   *  API
   * ================================================================ */

  /** 创建解析任务 */
  async function createTask(files, hasAnswerHint) {
    if (!files || !files.length) throw new Error("请至少选择 1 个文件");

    // 在 Electron 中 File 对象有 .path 属性
    const filePaths = Array.from(files).map(f => f.path).filter(Boolean);
    if (!filePaths.length) throw new Error("无法获取文件路径，请重新选择");

    // 通过主进程代理 multipart 上传
    const r = await window.qforge.api.uploadMultipart(
      "/api/exam-parse/tasks",
      _state.token,
      filePaths,
      { hasAnswerHint: String(!!hasAnswerHint) }
    );
    const task = r.data || r;
    epState.tasks.set(task.taskUuid, task);
    epState.activeTaskUuid = task.taskUuid;
    saveLocalState();
    epLog(`任务已创建: ${task.taskUuid.slice(0, 8)}…`);
    return task;
  }

  /** 拉取当前用户任务列表 */
  async function refreshTaskList() {
    const r = await _api("/api/exam-parse/tasks", "GET");
    const list = Array.isArray(r.data) ? r.data : (Array.isArray(r) ? r : []);
    const old = new Map(epState.tasks);
    epState.tasks.clear();
    for (const t of list) {
      const prev = old.get(t.taskUuid);
      epState.tasks.set(t.taskUuid, Object.assign({}, prev || {}, t));
    }
    saveLocalState();
  }

  /** 拉取某任务详情（含题目列表） */
  async function refreshQuestions(taskUuid) {
    const r = await _api(`/api/exam-parse/tasks/${taskUuid}`, "GET");
    const d = r.data || r;
    // 后端返回 { task: {...}, questions: [...] }
    const list = Array.isArray(d.questions) ? d.questions : [];
    epState.questions.set(taskUuid, list);
    // 顺便更新 task 元信息
    if (d.task) {
      const prev = epState.tasks.get(taskUuid);
      epState.tasks.set(taskUuid, Object.assign({}, prev || {}, d.task));
    }
    saveLocalState();
  }

  /** 更新单题 */
  async function updateQuestion(taskUuid, seqNo, updates) {
    await _api(`/api/exam-parse/tasks/${taskUuid}/questions/${seqNo}`, "PUT", updates);
    await refreshQuestions(taskUuid);
  }

  /** 批量确认入库 */
  async function confirmTask(taskUuid) {
    const r = await _api(`/api/exam-parse/tasks/${taskUuid}/confirm`, "POST");
    const d = r.data || r;
    const count = d.confirmedCount || 0;
    epLog(`确认入库完成，共 ${count} 题`);
    await refreshTaskList();
    await refreshQuestions(taskUuid);
    return count;
  }

  /** 删除任务 */
  async function deleteTask(taskUuid) {
    await _api(`/api/exam-parse/tasks/${taskUuid}`, "DELETE");
    epState.tasks.delete(taskUuid);
    epState.questions.delete(taskUuid);
    if (epState.activeTaskUuid === taskUuid) {
      epState.activeTaskUuid = "";
    }
    saveLocalState();
    epLog(`任务已删除: ${taskUuid.slice(0, 8)}…`);
  }

  /* ================================================================
   *  WebSocket 事件处理
   * ================================================================ */

  /**
   * 处理来自 WS 的消息，返回 true 表示已消费。
   */
  function handleWsMessage(msg) {
    const evt = msg.event || "";
    const p = msg.payload || {};

    if (evt === "exam.parse.question.result") {
      const taskUuid = p.taskUuid;
      if (!taskUuid) return false;
      epLog(`收到第 ${p.seqNo} 题结果 (${taskUuid.slice(0, 8)}…)`);
      // 拉取最新题目列表
      refreshQuestions(taskUuid).then(() => {
        saveLocalState();
        if (_render) _render();
      }).catch(() => {});
      return true;
    }

    if (evt === "exam.parse.completed") {
      const taskUuid = p.taskUuid;
      if (!taskUuid) return false;
      const t = epState.tasks.get(taskUuid);
      const expectedCount = p.questionCount || 0;
      if (t) {
        t.status = p.status || "SUCCESS";
        t.progress = 100;
        t.questionCount = expectedCount || t.questionCount || 0;
        if (p.errorMsg) t.errorMsg = p.errorMsg;
      }
      epLog(`解析完成: ${taskUuid.slice(0, 8)}… 状态=${p.status} 题数=${expectedCount}`);

      // completed 事件可能先于最后几道题的 result 事件被消费（不同 MQ 队列），
      // 因此 refreshQuestions 拿到的题目数可能不足，需要重试。
      const refreshWithRetry = async (retries = 3, delay = 800) => {
        for (let i = 0; i <= retries; i++) {
          await refreshQuestions(taskUuid);
          const qs = epState.questions.get(taskUuid) || [];
          if (qs.length >= expectedCount || expectedCount === 0) break;
          if (i < retries) {
            epLog(`题目数不足 (${qs.length}/${expectedCount})，${delay}ms 后重试…`);
            await new Promise(r => setTimeout(r, delay));
          }
        }
        saveLocalState();
        if (_render) _render();
      };
      refreshWithRetry().catch(() => {});

      saveLocalState();
      if (_render) _render();
      return true;
    }

    return false;
  }

  /* ================================================================
   *  查询 helpers
   * ================================================================ */

  function getActiveTask() {
    return epState.tasks.get(epState.activeTaskUuid) || null;
  }

  function getActiveQuestions() {
    return epState.questions.get(epState.activeTaskUuid) || [];
  }

  function selectTask(taskUuid) {
    epState.activeTaskUuid = taskUuid;
    saveLocalState();
  }

  function getState() {
    return epState;
  }

  /* ================================================================
   *  渲染辅助
   * ================================================================ */

  function statusLabel(s) {
    const map = {
      PENDING: "等待中",
      OCR_PROCESSING: "OCR 识别中",
      SPLITTING: "LLM 拆题中",
      GENERATING: "题目生成中",
      SUCCESS: "解析成功",
      PARTIAL_FAILED: "部分失败",
      FAILED: "解析失败",
    };
    return map[s] || s || "-";
  }

  function statusCls(s) {
    if (s === "SUCCESS") return "success";
    if (s === "FAILED") return "fail";
    if (s === "PARTIAL_FAILED") return "warn";
    if (["OCR_PROCESSING", "SPLITTING", "GENERATING", "PENDING"].includes(s)) return "warn";
    return "neutral";
  }

  function confirmStatusLabel(s) {
    if (s === "CONFIRMED") return "已入库";
    if (s === "SKIPPED") return "跳过";
    return "待确认";
  }

  function confirmStatusCls(s) {
    if (s === "CONFIRMED") return "success";
    if (s === "SKIPPED") return "fail";
    return "neutral";
  }

  /* ================================================================
   *  DOM 渲染（自包含）
   * ================================================================ */

  const $ = (id) => document.getElementById(id);

  function renderExamParse() {
    renderTaskList();
    renderActiveSession();
    renderSessionLogs();
  }

  /* ---------- 左侧：任务列表 ---------- */

  function renderTaskList() {
    const list = $("ep-task-list");
    if (!list) return;
    list.innerHTML = "";
    const tasks = [...epState.tasks.values()].sort(
      (a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0)
    );
    if (!tasks.length) {
      list.innerHTML = '<div class="empty-note">暂无解析任务，请上传试卷</div>';
      return;
    }
    for (const t of tasks) {
      const active = t.taskUuid === epState.activeTaskUuid;
      const item = document.createElement("div");
      item.className = `entry-item ${active ? "active" : ""}`;
      const statusTag = `<span class="tag ${statusCls(t.status)}">${statusLabel(t.status)}</span>`;
      const progress = t.progress != null && t.progress < 100
        ? `<span class="ep-progress">${t.progress}%</span>` : "";
      item.innerHTML = `
        <div class="entry-title">${t.taskUuid.slice(0, 12)}…</div>
        <div class="entry-sub">
          <span>文件 ${t.fileCount || 0}</span>
          <span>题 ${t.questionCount || 0}</span>
          ${progress}${statusTag}
        </div>`;
      item.addEventListener("click", () => {
        selectTask(t.taskUuid);
        refreshQuestions(t.taskUuid).catch(() => {});
        if (_render) _render();
      });
      list.appendChild(item);
    }
  }

  /* ---------- 右侧：会话详情 ---------- */

  function renderActiveSession() {
    const panel = $("ep-session-panel");
    if (!panel) return;

    const task = getActiveTask();
    if (!task) {
      panel.innerHTML = `
        <div class="ep-empty-session">
          <div class="ep-empty-icon">📋</div>
          <div class="ep-empty-title">上传试卷开始解析</div>
          <div class="ep-empty-desc">支持 PDF / 图片，可选多个文件一起上传</div>
        </div>`;
      return;
    }

    const questions = getActiveQuestions();
    const isRunning = ["PENDING", "OCR_PROCESSING", "SPLITTING", "GENERATING"].includes(task.status);
    const isFinished = ["SUCCESS", "PARTIAL_FAILED", "FAILED"].includes(task.status);

    let html = "";

    // ---- 任务卡片 ----
    html += `<div class="ep-session-task card">
      <div class="ep-task-header">
        <span class="ep-task-id">任务 ${task.taskUuid.slice(0, 12)}…</span>
        <span class="tag ${statusCls(task.status)}">${statusLabel(task.status)}</span>
      </div>
      <div class="ep-task-meta">
        <span>文件数: ${task.fileCount || 0}</span>
        <span>总页数: ${task.totalPages || "-"}</span>
        <span>题目数: ${task.questionCount || 0}</span>
        ${task.hasAnswerHint ? '<span class="tag neutral">含答案</span>' : ""}
      </div>`;

    if (isRunning) {
      const pct = task.progress || 0;
      html += `<div class="ep-progress-bar">
        <div class="ep-progress-fill" style="width:${pct}%"></div>
      </div>
      <div class="ep-progress-text">${statusLabel(task.status)} ${pct}%</div>`;
    }

    if (task.errorMsg) {
      html += `<div class="ep-error-msg">${escHtml(task.errorMsg)}</div>`;
    }

    html += `<div class="ep-task-actions">`;
    if (isFinished && questions.some(q => q.confirmStatus === "PENDING")) {
      html += `<button id="ep-confirm-btn" class="btn btn-primary">全部确认入库</button>`;
    }
    html += `<button id="ep-delete-task-btn" class="btn btn-danger btn-mini">删除任务</button>`;
    html += `</div></div>`;

    // ---- 题目卡片列表（会话气泡风格）----
    if (questions.length) {
      html += `<div class="ep-question-list">`;
      for (const q of questions) {
        const hasError = q.parseError || q.errorMsg;
        html += `<div class="ep-question-card card ${hasError ? "ep-q-error" : ""}" data-seq="${q.seqNo}">
          <div class="ep-q-header">
            <span class="ep-q-seq">第 ${q.seqNo} 题</span>
            <span class="ep-q-type">${q.questionType || "未知"}</span>
            <span class="tag ${confirmStatusCls(q.confirmStatus)}">${confirmStatusLabel(q.confirmStatus)}</span>
          </div>`;

        // 题干预览（完整渲染，含图片+KaTeX）
        if (q.stemXml || q.rawStemText) {
          html += `<div class="ep-q-section">
            <div class="ep-q-label">题干</div>
            <div class="ep-q-content ep-q-stem" data-task="${task.taskUuid}" data-seq="${q.seqNo}" data-field="stem"></div>
          </div>`;
        }

        // 答案预览（完整渲染，含图片+KaTeX）
        if (q.answerXml || q.rawAnswerText) {
          html += `<div class="ep-q-section">
            <div class="ep-q-label">答案</div>
            <div class="ep-q-content ep-q-answer" data-task="${task.taskUuid}" data-seq="${q.seqNo}" data-field="answer"></div>
          </div>`;
        }

        // 错误信息
        if (q.errorMsg) {
          html += `<div class="ep-q-error-msg">${escHtml(q.errorMsg)}</div>`;
        }

        html += `</div>`;
      }
      html += `</div>`;
    } else if (isRunning) {
      html += `<div class="ep-waiting">
        <div class="ep-spinner"></div>
        <div>正在解析试卷，请稍候…</div>
      </div>`;
    }

    panel.innerHTML = html;

    // ---- 后渲染：用 KaTeX + inline 图片填充题干/答案内容 ----
    postRenderQuestionContent(panel, questions);

    // 绑定事件
    const confirmBtn = $("ep-confirm-btn");
    if (confirmBtn) {
      confirmBtn.addEventListener("click", async () => {
        confirmBtn.disabled = true;
        confirmBtn.textContent = "入库中…";
        try {
          const count = await confirmTask(task.taskUuid);
          epLog(`已确认入库 ${count} 题`);
          // 刷新主题目列表，让已确认的题目出现在录题/题库区域
          if (typeof window.__qforgeSyncQuestions === "function") {
            window.__qforgeSyncQuestions().catch(() => {});
          }
          if (_render) _render();
        } catch (err) {
          epLog(`确认入库失败: ${err.message}`);
          confirmBtn.disabled = false;
          confirmBtn.textContent = "全部确认入库";
        }
      });
    }

    const delBtn = $("ep-delete-task-btn");
    if (delBtn) {
      delBtn.addEventListener("click", async () => {
        if (!confirm("确认删除该解析任务？")) return;
        try {
          await deleteTask(task.taskUuid);
          if (_render) _render();
        } catch (err) {
          epLog(`删除失败: ${err.message}`);
        }
      });
    }

    // 渲染 KaTeX（如果可用）
    requestAnimationFrame(() => {
      if (typeof window.renderMathInElement === "function") {
        const container = $("ep-session-panel");
        if (container) {
          try {
            window.renderMathInElement(container, {
              delimiters: [
                { left: "$$", right: "$$", display: true },
                { left: "$", right: "$", display: false },
                { left: "\\(", right: "\\)", display: false },
                { left: "\\[", right: "\\]", display: true },
              ],
              throwOnError: false,
              strict: "ignore",
            });
          } catch { /* ignore */ }
        }
      }
    });
  }

  /* ---------- 日志面板 ---------- */

  function renderSessionLogs() {
    const box = $("ep-log-list");
    if (!box) return;
    box.innerHTML = "";
    for (const entry of epState.logs.slice(0, 50)) {
      const li = document.createElement("li");
      li.textContent = `${entry.ts}  ${entry.msg}`;
      box.appendChild(li);
    }
  }

  /* ---------- 工具函数 ---------- */

  const KATEX_DELIMITERS = [
    { left: "$$", right: "$$", display: true },
    { left: "$", right: "$", display: false },
    { left: "\\(", right: "\\)", display: false },
    { left: "\\[", right: "\\]", display: true },
  ];

  /**
   * 构建图片 refKey → base64 data URL 映射。
   * imagesJson 是类似 [{"refKey":"img-1","imageBase64":"...","mimeType":"image/png"}] 的 JSON 字符串。
   */
  function buildImageMap(imagesJson) {
    if (!imagesJson) return {};
    try {
      const arr = typeof imagesJson === "string" ? JSON.parse(imagesJson) : imagesJson;
      if (!Array.isArray(arr)) return {};
      const map = {};
      for (const img of arr) {
        if (img.refKey && img.imageBase64) {
          const mime = img.mimeType || "image/png";
          const prefix = img.imageBase64.startsWith("data:") ? "" : `data:${mime};base64,`;
          map[img.refKey] = prefix + img.imageBase64;
        }
      }
      return map;
    } catch { return {}; }
  }

  /**
   * 将原始文本渲染为富文本 DOM（KaTeX + 内联图片），插入到 node 中。
   * @param {HTMLElement} node 目标节点
   * @param {string} text 原始文本（rawStemText / rawAnswerText / stemXml / answerXml）
   * @param {Object} imageMap refKey → data URL 映射
   */
  function renderRichContent(node, text, imageMap) {
    if (!text || !text.trim()) {
      node.innerHTML = '<span class="empty-note">（空）</span>';
      return;
    }

    // 如果是 stem/answer XML，尝试委托给 global renderLatexNode（renderer.js 的富文本渲染器）
    if (text.trim().startsWith("<stem") || text.trim().startsWith("<answer")) {
      try {
        // 利用 renderer.js 已有的 XML 渲染管道
        const parser = new DOMParser();
        const xmlDoc = parser.parseFromString(text, "text/xml");
        if (!xmlDoc.querySelector("parsererror")) {
          node.innerHTML = "";
          renderXmlNode(xmlDoc.documentElement, node, imageMap);
          // KaTeX 后处理
          if (typeof window.renderMathInElement === "function") {
            try { window.renderMathInElement(node, { delimiters: KATEX_DELIMITERS, throwOnError: false, strict: "ignore" }); } catch {}
          }
          return;
        }
      } catch {}
    }

    // 纯文本模式：将 <image ref="xxx" /> 替换为真实图片，其余用 textContent + KaTeX
    node.innerHTML = "";
    // 按 <image ref="..."/> 拆分
    const imgTagRegex = /<image\s+ref="([^"]+)"[^/]*\/>/g;
    let lastIdx = 0;
    let match;
    while ((match = imgTagRegex.exec(text)) !== null) {
      // 前面的文本段
      if (match.index > lastIdx) {
        const span = document.createElement("span");
        span.textContent = text.substring(lastIdx, match.index);
        node.appendChild(span);
      }
      // 图片
      const ref = match[1];
      const src = imageMap[ref];
      if (src) {
        const img = document.createElement("img");
        img.className = "ep-inline-image";
        img.src = src;
        img.alt = ref;
        node.appendChild(img);
      } else {
        const placeholder = document.createElement("span");
        placeholder.className = "ep-image-placeholder";
        placeholder.textContent = `[配图: ${ref}]`;
        node.appendChild(placeholder);
      }
      lastIdx = match.index + match[0].length;
    }
    // 剩余文本
    if (lastIdx < text.length) {
      const span = document.createElement("span");
      span.textContent = text.substring(lastIdx);
      node.appendChild(span);
    }
    // [配图] 文本标记也显示为提示
    const placeholderMarkers = node.querySelectorAll ? [] : [];
    // KaTeX 后处理
    if (typeof window.renderMathInElement === "function") {
      try { window.renderMathInElement(node, { delimiters: KATEX_DELIMITERS, throwOnError: false, strict: "ignore" }); } catch {}
    }
  }

  /**
   * 简易 XML 节点递归渲染器（处理 stem/answer XML）
   */
  function renderXmlNode(xmlNode, parentEl, imageMap) {
    if (xmlNode.nodeType === Node.TEXT_NODE) {
      parentEl.appendChild(document.createTextNode(xmlNode.nodeValue || ""));
      return;
    }
    if (xmlNode.nodeType !== Node.ELEMENT_NODE) return;
    const tag = String(xmlNode.tagName || "").toLowerCase();

    if (tag === "stem" || tag === "answer") {
      Array.from(xmlNode.childNodes).forEach(n => renderXmlNode(n, parentEl, imageMap));
      return;
    }
    if (tag === "p") {
      const p = document.createElement("p");
      p.className = "stem-p";
      Array.from(xmlNode.childNodes).forEach(n => renderXmlNode(n, p, imageMap));
      parentEl.appendChild(p);
      return;
    }
    if (tag === "image") {
      const ref = (xmlNode.getAttribute("ref") || "").trim();
      const src = imageMap[ref];
      if (src) {
        const img = document.createElement("img");
        img.className = "ep-inline-image";
        img.src = src;
        img.alt = ref || "image";
        parentEl.appendChild(img);
      } else {
        const note = document.createElement("span");
        note.className = "ep-image-placeholder";
        note.textContent = ref ? `[配图: ${ref}]` : "[配图]";
        parentEl.appendChild(note);
      }
      return;
    }
    if (tag === "choices") {
      const div = document.createElement("div");
      div.className = "choices-container";
      Array.from(xmlNode.childNodes).forEach(n => renderXmlNode(n, div, imageMap));
      parentEl.appendChild(div);
      return;
    }
    if (tag === "choice") {
      const key = (xmlNode.getAttribute("key") || "").trim();
      const item = document.createElement("div");
      item.className = "choice-item";
      const k = document.createElement("span");
      k.className = "choice-key";
      k.textContent = key ? `${key}.` : "•";
      const cont = document.createElement("div");
      cont.className = "choice-content";
      Array.from(xmlNode.childNodes).forEach(n => renderXmlNode(n, cont, imageMap));
      item.appendChild(k);
      item.appendChild(cont);
      parentEl.appendChild(item);
      return;
    }
    // fallback
    Array.from(xmlNode.childNodes).forEach(n => renderXmlNode(n, parentEl, imageMap));
  }

  /**
   * 在 innerHTML 设置后，对题目卡片内的题干/答案区域执行富文本渲染。
   */
  function postRenderQuestionContent(panel, questions) {
    for (const q of questions) {
      const stemImageMap = buildImageMap(q.stemImagesJson);
      const answerImageMap = buildImageMap(q.answerImagesJson);

      // 题干
      const stemNode = panel.querySelector(`.ep-q-stem[data-seq="${q.seqNo}"]`);
      if (stemNode) {
        renderRichContent(stemNode, q.stemXml || q.rawStemText || "", stemImageMap);
      }

      // 答案
      const answerNode = panel.querySelector(`.ep-q-answer[data-seq="${q.seqNo}"]`);
      if (answerNode) {
        renderRichContent(answerNode, q.answerXml || q.rawAnswerText || "", answerImageMap);
      }
    }
  }

  function escHtml(s) {
    return String(s || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function truncate(s, n) {
    if (!s) return "";
    return s.length > n ? s.slice(0, n) + "…" : s;
  }

  /* ================================================================
   *  导出
   * ================================================================ */

  const api = {
    init,
    getState,
    getActiveTask,
    getActiveQuestions,
    selectTask,
    createTask,
    refreshTaskList,
    refreshQuestions,
    updateQuestion,
    confirmTask,
    deleteTask,
    handleWsMessage,
    renderExamParse,
    statusLabel,
    statusCls,
    saveLocalState,
    loadLocalState,
    epLog,
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  global.QForgeExamParseRuntime = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
