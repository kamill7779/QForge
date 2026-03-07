/**
 * QForge 试卷自动解析 Runtime v2
 * 气泡导航 + 单题焦点 + 结构化编辑 + 标签/难度
 *
 * IIFE 模式，挂载 window.QForgeExamParseRuntime
 */
(function (global) {
  "use strict";

  /* ================================================================
   *  会话状态
   * ================================================================ */
  const epState = {
    tasks: new Map(),
    activeTaskUuid: "",
    questions: new Map(),
    logs: [],
    /** 当前聚焦的题目 seqNo */
    activeSeqNo: 0,
    /** 前端焦点状态 "taskUuid:seqNo" -> { focusStage, mainTags, secondaryTags, difficulty } */
    questionFocus: {},
  };

  /* ================================================================
   *  依赖占位
   * ================================================================ */
  let _api = null;
  let _log = null;
  let _render = null;
  let _state = null;
  let _saveWs = null;

  /* ================================================================
   *  初始化
   * ================================================================ */
  function init(deps) {
    _api = deps.api;
    _log = deps.log || console.log;
    _render = deps.render;
    _state = deps.state;
    _saveWs = deps.saveWorkspace;
    loadLocalState();
    setupKeyboard();
  }

  /* ================================================================
   *  持久化
   * ================================================================ */
  function storageKey() {
    return "qforge.examparse." + (_state ? _state.username : "anon");
  }

  function saveLocalState() {
    try {
      const obj = {
        tasks: [...epState.tasks.values()],
        activeTaskUuid: epState.activeTaskUuid,
        questions: {},
        logs: epState.logs.slice(0, 200),
        activeSeqNo: epState.activeSeqNo,
        questionFocus: epState.questionFocus,
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
      epState.activeSeqNo = obj.activeSeqNo || 0;
      epState.questionFocus = obj.questionFocus || {};
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
  async function createTask(files, hasAnswerHint) {
    if (!files || !files.length) throw new Error("请至少选择 1 个文件");
    const filePaths = Array.from(files).map(f => f.path).filter(Boolean);
    if (!filePaths.length) throw new Error("无法获取文件路径，请重新选择");
    const r = await window.qforge.api.uploadMultipart(
      "/api/exam-parse/tasks", _state.token, filePaths,
      { hasAnswerHint: String(!!hasAnswerHint) }
    );
    const task = r.data || r;
    epState.tasks.set(task.taskUuid, task);
    epState.activeTaskUuid = task.taskUuid;
    epState.activeSeqNo = 0;
    saveLocalState();
    epLog("任务已创建: " + task.taskUuid.slice(0, 8) + "…");
    return task;
  }

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

  async function refreshQuestions(taskUuid) {
    const r = await _api("/api/exam-parse/tasks/" + taskUuid, "GET");
    const d = r.data || r;
    const list = Array.isArray(d.questions) ? d.questions : [];
    epState.questions.set(taskUuid, list);
    if (d.task) {
      const prev = epState.tasks.get(taskUuid);
      epState.tasks.set(taskUuid, Object.assign({}, prev || {}, d.task));
    }
    saveLocalState();
  }

  async function updateQuestion(taskUuid, seqNo, updates) {
    await _api("/api/exam-parse/tasks/" + taskUuid + "/questions/" + seqNo, "PUT", updates);
    await refreshQuestions(taskUuid);
  }

  async function confirmTask(taskUuid) {
    const r = await _api("/api/exam-parse/tasks/" + taskUuid + "/confirm", "POST");
    const d = r.data || r;
    const count = d.confirmedCount || 0;
    epLog("确认入库完成，共 " + count + " 题");
    await refreshTaskList();
    await refreshQuestions(taskUuid);
    return count;
  }

  async function deleteTask(taskUuid) {
    await _api("/api/exam-parse/tasks/" + taskUuid, "DELETE");
    epState.tasks.delete(taskUuid);
    epState.questions.delete(taskUuid);
    if (epState.activeTaskUuid === taskUuid) {
      epState.activeTaskUuid = "";
      epState.activeSeqNo = 0;
    }
    for (var key of Object.keys(epState.questionFocus)) {
      if (key.startsWith(taskUuid + ":")) delete epState.questionFocus[key];
    }
    saveLocalState();
    epLog("任务已删除: " + taskUuid.slice(0, 8) + "…");
  }

  /* ================================================================
   *  WebSocket 事件处理
   * ================================================================ */
  function handleWsMessage(msg) {
    var evt = msg.event || "";
    var p = msg.payload || {};
    if (evt === "exam.parse.question.result") {
      var taskUuid = p.taskUuid;
      if (!taskUuid) return false;
      epLog("收到第 " + p.seqNo + " 题结果 (" + taskUuid.slice(0, 8) + "…)");
      refreshQuestions(taskUuid).then(function () {
        saveLocalState();
        if (_render) _render();
      }).catch(function () {});
      return true;
    }
    if (evt === "exam.parse.completed") {
      var taskUuid2 = p.taskUuid;
      if (!taskUuid2) return false;
      var t = epState.tasks.get(taskUuid2);
      var expectedCount = p.questionCount || 0;
      if (t) {
        t.status = p.status || "SUCCESS";
        t.progress = 100;
        t.questionCount = expectedCount || t.questionCount || 0;
        if (p.errorMsg) t.errorMsg = p.errorMsg;
      }
      epLog("解析完成: " + taskUuid2.slice(0, 8) + "… 状态=" + p.status + " 题数=" + expectedCount);
      var refreshWithRetry = async function (retries, delay) {
        retries = retries || 3; delay = delay || 800;
        for (var i = 0; i <= retries; i++) {
          await refreshQuestions(taskUuid2);
          var qs = epState.questions.get(taskUuid2) || [];
          if (qs.length >= expectedCount || expectedCount === 0) break;
          if (i < retries) {
            epLog("题目数不足 (" + qs.length + "/" + expectedCount + ")，" + delay + "ms 后重试…");
            await new Promise(function (r) { setTimeout(r, delay); });
          }
        }
        saveLocalState();
        if (_render) _render();
      };
      refreshWithRetry().catch(function () {});
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
    epState.activeSeqNo = 0;
    saveLocalState();
  }
  function getState() { return epState; }

  /* ================================================================
   *  焦点状态管理
   * ================================================================ */
  function focusKey(taskUuid, seqNo) { return taskUuid + ":" + seqNo; }

  function getFocusData(q) {
    if (!q) return null;
    return epState.questionFocus[focusKey(epState.activeTaskUuid, q.seqNo)] || null;
  }
  function ensureFocusData(q) {
    var fk = focusKey(epState.activeTaskUuid, q.seqNo);
    if (!epState.questionFocus[fk]) epState.questionFocus[fk] = { focusStage: "PREVIEW" };
    return epState.questionFocus[fk];
  }
  function getFocusStage(q) {
    if (!q) return "PREVIEW";
    if (q.confirmStatus === "CONFIRMED") return "CONFIRMED";
    if (q.confirmStatus === "SKIPPED") return "SKIPPED";
    if (q.parseError || q.errorMsg) return "ERROR";
    var fd = getFocusData(q);
    return (fd && fd.focusStage) || "PREVIEW";
  }
  function setFocusStage(q, stage) {
    ensureFocusData(q).focusStage = stage;
    saveLocalState();
  }
  function selectQuestion(seqNo) {
    epState.activeSeqNo = seqNo;
    saveLocalState();
    if (_render) _render();
  }
  function getCurrentQuestion(questions) {
    if (!questions || !questions.length) return null;
    var q = questions.find(function (x) { return x.seqNo === epState.activeSeqNo; });
    if (q) return q;
    epState.activeSeqNo = questions[0].seqNo;
    return questions[0];
  }

  /* ================================================================
   *  渲染辅助
   * ================================================================ */
  function statusLabel(s) {
    var map = {
      PENDING: "等待中", OCR_PROCESSING: "OCR 识别中", SPLITTING: "LLM 拆题中",
      GENERATING: "题目生成中", SUCCESS: "解析成功", PARTIAL_FAILED: "部分失败", FAILED: "解析失败",
    };
    return map[s] || s || "-";
  }
  function statusCls(s) {
    if (s === "SUCCESS") return "success";
    if (s === "FAILED") return "fail";
    if (s === "PARTIAL_FAILED") return "warn";
    if (["OCR_PROCESSING", "SPLITTING", "GENERATING", "PENDING"].indexOf(s) >= 0) return "warn";
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
  function bubbleCls(q) {
    var fs = getFocusStage(q);
    if (fs === "CONFIRMED") return "confirmed";
    if (fs === "SKIPPED") return "skipped";
    if (fs === "ERROR") return "error";
    if (fs === "EDITING_STEM") return "editing-stem";
    if (fs === "EDITING_ANSWER") return "editing-answer";
    if (fs === "READY") return "ready";
    return "preview";
  }
  function focusStageLabel(fs) {
    var map = {
      PREVIEW: "预览", EDITING_STEM: "编辑题干", EDITING_ANSWER: "编辑答案",
      READY: "就绪", CONFIRMED: "已入库", SKIPPED: "已跳过", ERROR: "错误",
    };
    return map[fs] || fs;
  }
  function focusStageCls(fs) {
    if (fs === "EDITING_STEM" || fs === "EDITING_ANSWER") return "warn";
    if (fs === "READY") return "success";
    if (fs === "CONFIRMED") return "success";
    if (fs === "ERROR") return "fail";
    return "neutral";
  }

  /* ================================================================
   *  DOM 渲染
   * ================================================================ */
  var $ = function (id) { return document.getElementById(id); };

  function renderExamParse() {
    renderTaskList();
    renderActiveSession();
    renderSessionLogs();
  }

  /* ---------- 左侧：任务列表 ---------- */
  function renderTaskList() {
    var list = $("ep-task-list");
    if (!list) return;
    list.innerHTML = "";
    var tasks = [].concat(Array.from(epState.tasks.values())).sort(function (a, b) {
      return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
    });
    if (!tasks.length) {
      list.innerHTML = '<div class="empty-note">暂无解析任务，请上传试卷</div>';
      return;
    }
    for (var i = 0; i < tasks.length; i++) {
      var t = tasks[i];
      var active = t.taskUuid === epState.activeTaskUuid;
      var item = document.createElement("div");
      item.className = "entry-item" + (active ? " active" : "");
      var statusTag = '<span class="tag ' + statusCls(t.status) + '">' + statusLabel(t.status) + '</span>';
      var progress = t.progress != null && t.progress < 100
        ? '<span class="ep-progress">' + t.progress + '%</span>' : "";
      item.innerHTML =
        '<div class="entry-title">' + t.taskUuid.slice(0, 12) + '…</div>' +
        '<div class="entry-sub"><span>文件 ' + (t.fileCount || 0) + '</span>' +
        '<span>题 ' + (t.questionCount || 0) + '</span>' + progress + statusTag + '</div>';
      (function (uuid) {
        item.addEventListener("click", function () {
          selectTask(uuid);
          refreshQuestions(uuid).catch(function () {});
          if (_render) _render();
        });
      })(t.taskUuid);
      list.appendChild(item);
    }
  }

  /* ---------- 右侧：会话详情 (v2 — 气泡导航 + 焦点视图) ---------- */
  function renderActiveSession() {
    var panel = $("ep-session-panel");
    if (!panel) return;
    var task = getActiveTask();
    if (!task) {
      panel.innerHTML =
        '<div class="ep-empty-session">' +
        '<div class="ep-empty-icon">���</div>' +
        '<div class="ep-empty-title">上传试卷开始解析</div>' +
        '<div class="ep-empty-desc">支持 PDF / 图片，可选多个文件一起上传</div></div>';
      return;
    }
    var questions = getActiveQuestions();
    var isRunning = ["PENDING", "OCR_PROCESSING", "SPLITTING", "GENERATING"].indexOf(task.status) >= 0;
    var isFinished = ["SUCCESS", "PARTIAL_FAILED", "FAILED"].indexOf(task.status) >= 0;
    var html = "";

    // ---- 任务头 ----
    html += '<div class="ep-session-task card">' +
      '<div class="ep-task-header">' +
      '<span class="ep-task-id">任务 ' + task.taskUuid.slice(0, 12) + '…</span>' +
      '<span class="tag ' + statusCls(task.status) + '">' + statusLabel(task.status) + '</span></div>' +
      '<div class="ep-task-meta">' +
      '<span>文件数: ' + (task.fileCount || 0) + '</span>' +
      '<span>总页数: ' + (task.totalPages || "-") + '</span>' +
      '<span>题目数: ' + (task.questionCount || 0) + '</span>' +
      (task.hasAnswerHint ? '<span class="tag neutral">含答案</span>' : '') + '</div>';
    if (isRunning) {
      var pct = task.progress || 0;
      html += '<div class="ep-progress-bar"><div class="ep-progress-fill" style="width:' + pct + '%"></div></div>' +
        '<div class="ep-progress-text">' + statusLabel(task.status) + ' ' + pct + '%</div>';
    }
    if (task.errorMsg) {
      html += '<div class="ep-error-msg">' + escHtml(task.errorMsg) + '</div>';
    }
    html += '<div class="ep-task-actions">';
    if (isFinished && questions.some(function (q) { return q.confirmStatus === "PENDING"; })) {
      html += '<button id="ep-confirm-btn" class="btn btn-primary">全部确认入库</button>';
    }
    html += '<button id="ep-delete-task-btn" class="btn btn-danger btn-mini">删除任务</button>';
    html += '</div></div>';

    // ---- 气泡导航 ----
    if (questions.length) {
      html += '<div class="ep-bubble-nav" id="ep-bubble-nav">';
      for (var i = 0; i < questions.length; i++) {
        var bq = questions[i];
        var cls = bubbleCls(bq);
        var act = bq.seqNo === epState.activeSeqNo ? " active" : "";
        var ttl = "第" + bq.seqNo + "题 - " + (bq.questionType || "未知题型") + " - " + focusStageLabel(getFocusStage(bq));
        html += '<button class="ep-bubble ' + cls + act + '" data-seq="' + bq.seqNo + '" title="' + escHtml(ttl) + '">' + bq.seqNo + '</button>';
      }
      html += '</div>';
    }

    // ---- 焦点视图 ----
    var q = getCurrentQuestion(questions);
    if (q) {
      var stage = getFocusStage(q);
      html += '<div class="ep-question-focus" id="ep-question-focus">';
      // 状态条
      html += '<div class="ep-focus-status-bar">' +
        '<span class="ep-q-seq">第 ' + q.seqNo + ' 题</span>' +
        '<span class="ep-q-type">' + (q.questionType || "未知题型") + '</span>' +
        '<span class="tag ' + confirmStatusCls(q.confirmStatus) + '">' + confirmStatusLabel(q.confirmStatus) + '</span>' +
        '<span class="tag ' + focusStageCls(stage) + '">' + focusStageLabel(stage) + '</span>' +
        '<div class="ep-focus-actions">';
      if (stage === "PREVIEW" || stage === "ERROR") {
        html += '<button class="btn btn-mini ep-edit-stem-btn">编辑题干</button>';
        if (q.answerXml || q.rawAnswerText) {
          html += '<button class="btn btn-mini ep-edit-answer-btn">编辑答案</button>';
        }
        html += '<button class="btn btn-mini btn-ghost ep-skip-btn">跳过</button>';
      } else if (stage === "EDITING_STEM") {
        html += '<button class="btn btn-primary btn-mini ep-save-stem-btn">保存题干</button>';
        html += '<button class="btn btn-mini btn-ghost ep-cancel-edit-btn">取消</button>';
      } else if (stage === "EDITING_ANSWER") {
        html += '<button class="btn btn-primary btn-mini ep-save-answer-btn">保存答案</button>';
        html += '<button class="btn btn-mini btn-ghost ep-cancel-edit-btn">取消</button>';
      } else if (stage === "READY") {
        html += '<button class="btn btn-mini ep-edit-stem-btn">编辑题干</button>';
        html += '<button class="btn btn-mini ep-edit-answer-btn">编辑答案</button>';
      }
      html += '</div></div>';

      // 焦点内容
      html += '<div class="ep-focus-content" id="ep-focus-content">';
      if (stage === "EDITING_STEM") {
        html += buildStemEditorHtml();
      } else if (stage === "EDITING_ANSWER") {
        html += buildAnswerEditorHtml();
      } else {
        html += buildPreviewHtml(q);
        if (q.errorMsg) html += '<div class="ep-q-error-msg">' + escHtml(q.errorMsg) + '</div>';
      }
      html += '</div>'; // ep-focus-content

      // 标签 & 难度 (非编辑模式下显示)
      if (stage !== "EDITING_STEM" && stage !== "EDITING_ANSWER" && stage !== "CONFIRMED") {
        html += buildTagsDifficultyHtml(q);
      }
      html += '</div>'; // ep-question-focus
    } else if (isRunning) {
      html += '<div class="ep-waiting"><div class="ep-spinner"></div><div>正在解析试卷，请稍候…</div></div>';
    }

    panel.innerHTML = html;

    // ---- 后渲染 ----
    if (q) {
      var stg = getFocusStage(q);
      if (stg === "EDITING_STEM") postRenderStemEditor(q);
      else if (stg === "EDITING_ANSWER") postRenderAnswerEditor(q);
      else postRenderPreview(q);
      if (stg !== "EDITING_STEM" && stg !== "EDITING_ANSWER" && stg !== "CONFIRMED") {
        postRenderTagsDifficulty(q);
      }
    }
    // 气泡滚动到 active
    var activeBubble = panel.querySelector(".ep-bubble.active");
    if (activeBubble) activeBubble.scrollIntoView({ block: "nearest", inline: "nearest" });
    // ---- 绑定事件 ----
    bindSessionEvents(task, questions, q);
  }

  /* ---------- 预览 HTML ---------- */
  function buildPreviewHtml(q) {
    var html = '';
    if (q.stemXml || q.rawStemText) {
      html += '<div class="ep-preview-section">' +
        '<div class="ep-preview-label">题干</div>' +
        '<div class="ep-preview-display" id="ep-stem-display"></div></div>';
    }
    if (q.answerXml || q.rawAnswerText) {
      html += '<div class="ep-preview-section">' +
        '<div class="ep-preview-label">答案</div>' +
        '<div class="ep-preview-display" id="ep-answer-display"></div></div>';
    }
    if (!q.stemXml && !q.rawStemText && !q.answerXml && !q.rawAnswerText) {
      html += '<div class="empty-note" style="padding:20px;text-align:center;">该题数据为空</div>';
    }
    return html;
  }

  /* ---------- 标签 & 难度 HTML ---------- */
  function buildTagsDifficultyHtml(q) {
    var fd = ensureFocusData(q);
    var html = '<div class="ep-tags-difficulty" id="ep-tags-difficulty">';
    // 题型 select
    html += '<div class="ep-td-row"><label class="ep-td-label">题型</label>' +
      '<select id="ep-qtype-select" class="ep-td-input">' +
      '<option value=""' + (!q.questionType ? ' selected' : '') + '>自动</option>';
    var types = ["单选题", "多选题", "填空题", "判断题", "解答题", "计算题", "证明题", "作图题", "简答题", "综合题", "其他"];
    for (var ti = 0; ti < types.length; ti++) {
      var sel = q.questionType === types[ti] ? " selected" : "";
      html += '<option value="' + types[ti] + '"' + sel + '>' + types[ti] + '</option>';
    }
    html += '</select></div>';
    // 主标签 (渲染 renderMainTagSelectorsTo)
    html += '<div class="ep-td-row"><label class="ep-td-label">主标签</label>' +
      '<div id="ep-main-tags-area" class="ep-td-wide"></div></div>';
    // 副标签 (capsule输入)
    var stags = (fd.secondaryTags || []);
    html += '<div class="ep-td-row"><label class="ep-td-label">副标签</label>';
    html += '<div class="ep-secondary-tags" id="ep-secondary-tags">';
    for (var si = 0; si < stags.length; si++) {
      html += '<span class="ep-tag-capsule">' + escHtml(stags[si]) + '<button class="ep-tag-rm" data-tag="' + escHtml(stags[si]) + '">×</button></span>';
    }
    html += '<input type="text" id="ep-stag-input" class="ep-td-input ep-stag-input" placeholder="输入标签 回车添加" />';
    html += '</div></div>';
    // 难度 slider
    var diff = fd.difficulty != null ? Number(fd.difficulty) : null;
    var diffLabel = diff != null ? dl(diff).label : "未设置";
    var diffVal = diff != null ? Math.round(diff * 100) : 50;
    var diffCls = diff != null ? dl(diff).cls : "d-unset";
    html += '<div class="ep-td-row"><label class="ep-td-label">难度</label>' +
      '<input type="range" id="ep-diff-slider" min="0" max="100" value="' + diffVal + '" class="ep-diff-slider' + (diff == null ? ' unset' : '') + '" />' +
      '<span id="ep-diff-val" class="ep-diff-val">' + (diff != null ? diff.toFixed(2) : '-') + '</span>' +
      '<span id="ep-diff-badge" class="difficulty-badge ' + diffCls + '">' + diffLabel + '</span></div>';
    html += '</div>';
    return html;
  }

  function dl(v) {
    if (v <= 0.3) return { label: "困难", cls: "d-hard" };
    if (v <= 0.7) return { label: "中等", cls: "d-medium" };
    return { label: "简单", cls: "d-easy" };
  }

  function renderEpMainTags(container, categories, mainTags, onChanged) {
    container.innerHTML = "";
    if (!categories || !categories.length) {
      container.innerHTML = '<div class="empty-note">暂无主标签字典</div>';
      return;
    }
    var map = {};
    for (var i = 0; i < mainTags.length; i++) map[mainTags[i].categoryCode] = mainTags[i];
    for (var ci = 0; ci < categories.length; ci++) {
      var c = categories[ci];
      var row = document.createElement("div");
      row.className = "row";
      var lab = document.createElement("label");
      lab.className = "tag-edit-label";
      lab.textContent = c.categoryName;
      var sel = document.createElement("select");
      for (var oi = 0; oi < c.options.length; oi++) {
        var op = document.createElement("option");
        op.value = c.options[oi].tagCode;
        op.textContent = c.options[oi].tagName || c.options[oi].tagCode;
        sel.appendChild(op);
      }
      var cur = map[c.categoryCode];
      sel.value = cur ? cur.tagCode : (c.options[0] ? c.options[0].tagCode : "UNCATEGORIZED");
      (function (cat) {
        sel.addEventListener("change", function () {
          var opt = null;
          for (var x = 0; x < cat.options.length; x++) { if (cat.options[x].tagCode === sel.value) { opt = cat.options[x]; break; } }
          var tags = mainTags.slice();
          var fi = -1;
          for (var j = 0; j < tags.length; j++) { if (tags[j].categoryCode === cat.categoryCode) { fi = j; break; } }
          var next = { categoryCode: cat.categoryCode, categoryName: cat.categoryName, tagCode: sel.value, tagName: opt ? opt.tagName : sel.value };
          if (fi >= 0) tags[fi] = next; else tags.push(next);
          mainTags.length = 0;
          for (var k = 0; k < tags.length; k++) mainTags.push(tags[k]);
          if (onChanged) onChanged(mainTags);
        });
      })(c);
      row.appendChild(lab);
      row.appendChild(sel);
      container.appendChild(row);
    }
  }

  /* ---------- 后渲染: 标签 & 难度 ---------- */
  function postRenderTagsDifficulty(q) {
    var fd = ensureFocusData(q);
    // 主标签 (EP 自己的实现，不依赖 global renderMainTagSelectorsTo)
    var mainArea = $("ep-main-tags-area");
    if (mainArea && _state && _state.tagCatalog && _state.tagCatalog.mainCategories) {
      renderEpMainTags(mainArea, _state.tagCatalog.mainCategories, fd.mainTags || [], function (tags) {
        fd.mainTags = tags;
        saveLocalState();
      });
    }
    // 副标签 input
    var stagInput = $("ep-stag-input");
    if (stagInput) {
      stagInput.addEventListener("keydown", function (ev) {
        if (ev.key === "Enter") {
          ev.preventDefault();
          var v = stagInput.value.trim();
          if (!v) return;
          if (!fd.secondaryTags) fd.secondaryTags = [];
          if (fd.secondaryTags.indexOf(v) < 0) fd.secondaryTags.push(v);
          stagInput.value = "";
          saveLocalState();
          if (_render) _render();
        }
      });
    }
    var stagBox = $("ep-secondary-tags");
    if (stagBox) {
      stagBox.addEventListener("click", function (ev) {
        var rm = ev.target.closest(".ep-tag-rm");
        if (!rm) return;
        var tag = rm.dataset.tag;
        if (fd.secondaryTags) {
          fd.secondaryTags = fd.secondaryTags.filter(function (t) { return t !== tag; });
          saveLocalState();
          if (_render) _render();
        }
      });
    }
    // 难度 slider
    var slider = $("ep-diff-slider");
    if (slider) {
      slider.addEventListener("input", function () {
        var v = Number(slider.value) / 100;
        fd.difficulty = v;
        var valSpan = $("ep-diff-val");
        var badge = $("ep-diff-badge");
        if (valSpan) valSpan.textContent = v.toFixed(2);
        if (badge) { var d = dl(v); badge.textContent = d.label; badge.className = "difficulty-badge " + d.cls; }
        slider.classList.remove("unset");
        saveLocalState();
      });
    }
    // 题型 select
    var qsel = $("ep-qtype-select");
    if (qsel) {
      qsel.addEventListener("change", function () {
        var val = qsel.value;
        var task = getActiveTask();
        if (!task) return;
        updateQuestion(task.taskUuid, q.seqNo, { questionType: val }).catch(function (err) {
          epLog("更新题型失败: " + err.message);
        });
      });
    }
  }

  /* ---------- 题干编辑器 HTML ---------- */
  function buildStemEditorHtml() {
    return '<div class="ep-editor-wrap">' +
      '<div class="ep-editor-grid">' +
      '<div class="ep-editor-col">' +
      '<div class="stem-structured-editor" id="ep-stem-editor"></div>' +
      '<div class="stem-editor-add-bar">' +
      '<button class="btn btn-mini" id="ep-add-p-btn">+ 段落</button>' +
      '<button class="btn btn-mini" id="ep-add-choices-btn">+ 选项</button>' +
      '<button class="btn btn-mini" id="ep-add-blanks-btn">+ 填空</button>' +
      '<button class="btn btn-mini" id="ep-add-image-btn">+ 配图</button>' +
      '<button class="btn btn-mini" id="ep-add-answer-area-btn">+ 答题区</button>' +
      '</div></div>' +
      '<div class="ep-preview-col">' +
      '<div class="latex-preview" id="ep-stem-edit-preview">题干预览</div>' +
      '</div></div></div>';
  }

  /* ---------- 答案编辑器 HTML ---------- */
  function buildAnswerEditorHtml() {
    return '<div class="ep-editor-wrap">' +
      '<div class="ep-stem-readonly" id="ep-answer-stem-ro"></div>' +
      '<div class="ep-editor-grid">' +
      '<div class="ep-editor-col">' +
      '<div class="answer-structured-editor" id="ep-answer-editor"></div>' +
      '<div class="answer-editor-toolbar">' +
      '<button class="btn btn-mini" id="ep-ans-add-p-btn">+ 段落</button>' +
      '<button class="btn btn-mini" id="ep-ans-add-img-btn">+ 配图</button>' +
      '</div></div>' +
      '<div class="ep-preview-col">' +
      '<div class="latex-preview" id="ep-answer-edit-preview">答案预览</div>' +
      '</div></div></div>';
  }

  /* ---------- 后渲染：预览 ---------- */
  function postRenderPreview(q) {
    var stemMap = buildImageMap(q.stemImagesJson);
    var ansMap = buildImageMap(q.answerImagesJson);
    var stemEl = $("ep-stem-display");
    if (stemEl) {
      var txt = q.stemXml || q.rawStemText || "";
      if (typeof renderLatexNode === "function" && (txt.trim().startsWith("<stem") || txt.trim().startsWith("<answer"))) {
        renderLatexNode(stemEl, txt, "（无题干）", { imageResolver: makeImageResolver(stemMap) });
      } else {
        renderRichContent(stemEl, txt, stemMap);
      }
    }
    var ansEl = $("ep-answer-display");
    if (ansEl) {
      var atxt = q.answerXml || q.rawAnswerText || "";
      if (typeof renderLatexNode === "function" && (atxt.trim().startsWith("<stem") || atxt.trim().startsWith("<answer"))) {
        renderLatexNode(ansEl, atxt, "（无答案）", { imageResolver: makeImageResolver(ansMap) });
      } else {
        renderRichContent(ansEl, atxt, ansMap);
      }
    }
  }

  /* ---------- 后渲染：题干编辑器 ---------- */
  function postRenderStemEditor(q) {
    var editor = $("ep-stem-editor");
    if (!editor) return;
    var epEntry = makeEpEntry(q);
    var source = q.stemXml || q.rawStemText || "";
    var blocks = (typeof xmlToBlocks === "function") ? xmlToBlocks(source) : [{ type: "p", text: source }];
    editor.innerHTML = "";
    for (var i = 0; i < blocks.length; i++) {
      editor.appendChild((typeof createBlockEl === "function") ? createBlockEl(blocks[i], epEntry) : buildFallbackBlock(blocks[i]));
    }
    if (typeof initBlockDrag === "function") initBlockDrag(editor, epStemEditorChanged);
    epStemEditorChanged();
    // Bind add-block
    bindClick("ep-add-p-btn", function () { epAddStemBlock("p"); });
    bindClick("ep-add-choices-btn", function () { epAddStemBlock("choices"); });
    bindClick("ep-add-blanks-btn", function () { epAddStemBlock("blanks"); });
    bindClick("ep-add-image-btn", function () { epAddStemBlock("image"); });
    bindClick("ep-add-answer-area-btn", function () { epAddStemBlock("answer-area"); });
    editor.addEventListener("input", function (ev) {
      if (ev.target.matches(".block-text-input,.choice-text-input,.answer-area-lines-input")) epStemEditorChanged();
    });
    editor.addEventListener("click", function (ev) {
      var rmb = ev.target.closest(".block-remove-btn");
      if (rmb) { var bl = rmb.closest(".stem-block"); if (bl) { bl.remove(); epStemEditorChanged(); } }
    });
  }

  /* ---------- 后渲染：答案编辑器 ---------- */
  function postRenderAnswerEditor(q) {
    var editor = $("ep-answer-editor");
    if (!editor) return;
    var epEntry = makeEpEntry(q);
    var source = q.answerXml || q.rawAnswerText || "";
    var blocks = (typeof answerXmlToBlocks === "function") ? answerXmlToBlocks(source) : [{ type: "p", text: source }];
    if (typeof renderAnswerStructuredEditor === "function") {
      renderAnswerStructuredEditor("ep-answer-editor", blocks, epEntry);
    }
    if (typeof initBlockDrag === "function") initBlockDrag(editor, function () { epAnswerEditorChanged(q); });
    // Stem read-only
    var stemRo = $("ep-answer-stem-ro");
    if (stemRo) {
      var stxt = q.stemXml || q.rawStemText || "";
      var smap = buildImageMap(q.stemImagesJson);
      if (typeof renderLatexNode === "function" && stxt.trim().startsWith("<stem")) {
        renderLatexNode(stemRo, stxt, "（无题干）", { imageResolver: makeImageResolver(smap) });
      } else {
        renderRichContent(stemRo, stxt, smap);
      }
    }
    bindClick("ep-ans-add-p-btn", function () { epAddAnswerBlock("p"); });
    bindClick("ep-ans-add-img-btn", function () { epAddAnswerBlock("image"); });
    editor.addEventListener("input", function (ev) {
      if (ev.target.matches(".block-text-input")) epAnswerEditorChanged(q);
    });
    editor.addEventListener("click", function (ev) {
      var rmb = ev.target.closest(".block-remove-btn");
      if (rmb) { var bl = rmb.closest(".stem-block"); if (bl) { bl.remove(); epAnswerEditorChanged(q); } }
    });
    epAnswerEditorChanged(q);
  }

  /* ---------- 编辑器变更 ---------- */
  function epStemEditorChanged() {
    var blocks = collectBlocksFromEpEditor("ep-stem-editor");
    var xml = (typeof blocksToXml === "function") ? blocksToXml(blocks) : "";
    var preview = $("ep-stem-edit-preview");
    if (preview && typeof renderLatexNode === "function") {
      var q = getCurrentQuestion(getActiveQuestions());
      var imgMap = q ? buildImageMap(q.stemImagesJson) : {};
      renderLatexNode(preview, xml, "题干预览", { imageResolver: makeImageResolver(imgMap) });
    }
  }

  function epAnswerEditorChanged(q) {
    var blocks = (typeof collectAnswerBlocksFromEditor === "function")
      ? collectAnswerBlocksFromEditor("ep-answer-editor") : [];
    var xml = (typeof blocksToAnswerXml === "function") ? blocksToAnswerXml(blocks) : "";
    var preview = $("ep-answer-edit-preview");
    if (preview && typeof renderAnswerLatex === "function") {
      renderAnswerLatex(preview, xml, "答案预览", makeEpEntry(q));
    }
  }

  function collectBlocksFromEpEditor(editorId) {
    var editor = $(editorId);
    if (!editor) return [];
    var blocks = [];
    var els = editor.querySelectorAll(".stem-block");
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var type = el.dataset.type;
      if (type === "p") {
        var inp = el.querySelector(".block-text-input");
        blocks.push({ type: "p", text: inp ? inp.value : "" });
      } else if (type === "choices") {
        var items = [];
        var rows = el.querySelectorAll(".choice-edit-row");
        for (var j = 0; j < rows.length; j++) {
          var cinp = rows[j].querySelector(".choice-text-input");
          var cprev = rows[j].querySelector(".choice-image-preview");
          items.push({ key: cinp ? cinp.dataset.key || "" : "", text: cinp ? cinp.value : "", imageRef: cprev ? cprev.dataset.ref || "" : "" });
        }
        var lbl = el.querySelector(".block-type-label");
        var mode = (lbl && lbl.textContent.indexOf("多选") >= 0) ? "multi" : "single";
        blocks.push({ type: "choices", mode: mode, items: items });
      } else if (type === "image") {
        blocks.push({ type: "image", ref: el.dataset.ref || "stem-local" });
      } else if (type === "blanks") {
        var bitems = [];
        var chips = el.querySelectorAll(".blank-edit-chip");
        for (var k = 0; k < chips.length; k++) {
          bitems.push({ id: chips[k].textContent.replace(/^空/, "") });
        }
        blocks.push({ type: "blanks", items: bitems });
      } else if (type === "answer-area") {
        var aInp = el.querySelector(".answer-area-lines-input");
        blocks.push({ type: "answer-area", lines: Number(aInp ? aInp.value : 4) });
      }
    }
    if (typeof assignInlineBlankIds === "function") assignInlineBlankIds(blocks);
    return blocks;
  }

  function epAddStemBlock(type) {
    var editor = $("ep-stem-editor");
    if (!editor) return;
    var blocks = collectBlocksFromEpEditor("ep-stem-editor");
    if (type === "p") blocks.push({ type: "p", text: "" });
    else if (type === "choices") blocks.push({ type: "choices", mode: "single", items: [{ key: "A", text: "" }, { key: "B", text: "" }, { key: "C", text: "" }, { key: "D", text: "" }] });
    else if (type === "blanks") {
      var mx = 0;
      for (var b = 0; b < blocks.length; b++) {
        if (blocks[b].type === "blanks") for (var it = 0; it < (blocks[b].items || []).length; it++) mx = Math.max(mx, Number(blocks[b].items[it].id) || 0);
      }
      blocks.push({ type: "blanks", items: [{ id: String(mx + 1) }] });
    }
    else if (type === "image") blocks.push({ type: "image", ref: "stem-local" });
    else if (type === "answer-area") blocks.push({ type: "answer-area", lines: 4 });
    var xml = (typeof blocksToXml === "function") ? blocksToXml(blocks) : "";
    var q = getCurrentQuestion(getActiveQuestions());
    var epEntry = makeEpEntry(q);
    var newBlocks = (typeof xmlToBlocks === "function") ? xmlToBlocks(xml) : blocks;
    editor.innerHTML = "";
    for (var i = 0; i < newBlocks.length; i++) {
      editor.appendChild((typeof createBlockEl === "function") ? createBlockEl(newBlocks[i], epEntry) : buildFallbackBlock(newBlocks[i]));
    }
    if (typeof initBlockDrag === "function") initBlockDrag(editor, epStemEditorChanged);
    epStemEditorChanged();
  }

  function epAddAnswerBlock(type) {
    var editor = $("ep-answer-editor");
    if (!editor) return;
    var q = getCurrentQuestion(getActiveQuestions());
    var epEntry = makeEpEntry(q);
    var blocks = (typeof collectAnswerBlocksFromEditor === "function")
      ? collectAnswerBlocksFromEditor("ep-answer-editor") : [];
    if (type === "p") blocks.push({ type: "p", text: "" });
    else if (type === "image") blocks.push({ type: "image", ref: "fig-1" });
    if (typeof renderAnswerStructuredEditor === "function") {
      renderAnswerStructuredEditor("ep-answer-editor", blocks, epEntry);
    }
    epAnswerEditorChanged(q);
  }

  /* ---------- EP Entry 适配器 ---------- */
  function makeEpEntry(q) {
    if (!q) return { inlineImages: {}, answerImages: {}, stemImageBase64: "" };
    return {
      questionUuid: (q.taskUuid || "") + ":" + q.seqNo,
      inlineImages: buildImageMap(q.stemImagesJson),
      answerImages: buildImageMap(q.answerImagesJson),
      stemImageBase64: "",
    };
  }

  /* ---------- 事件绑定 ---------- */
  function bindSessionEvents(task, questions, q) {
    // Confirm all
    bindClick("ep-confirm-btn", async function () {
      var btn = $("ep-confirm-btn");
      if (btn) { btn.disabled = true; btn.textContent = "入库中…"; }
      try {
        var count = await confirmTask(task.taskUuid);
        epLog("已确认入库 " + count + " 题");
        if (typeof window.__qforgeSyncQuestions === "function") {
          window.__qforgeSyncQuestions().catch(function () {});
        }
        if (_render) _render();
      } catch (err) {
        epLog("确认入库失败: " + err.message);
        if (btn) { btn.disabled = false; btn.textContent = "全部确认入库"; }
      }
    });
    // Delete task
    bindClick("ep-delete-task-btn", async function () {
      if (!confirm("确认删除该解析任务？")) return;
      try { await deleteTask(task.taskUuid); if (_render) _render(); }
      catch (err) { epLog("删除失败: " + err.message); }
    });
    // Bubble nav
    var bubbleNav = $("ep-bubble-nav");
    if (bubbleNav) {
      bubbleNav.addEventListener("click", function (ev) {
        var btn = ev.target.closest(".ep-bubble");
        if (!btn) return;
        var seq = Number(btn.dataset.seq);
        if (Number.isFinite(seq)) selectQuestion(seq);
      });
    }
    // Focus view buttons
    if (q) {
      bindAll(".ep-edit-stem-btn", function () { setFocusStage(q, "EDITING_STEM"); if (_render) _render(); });
      bindAll(".ep-edit-answer-btn", function () { setFocusStage(q, "EDITING_ANSWER"); if (_render) _render(); });
      bindAll(".ep-skip-btn", function () {
        setFocusStage(q, "SKIPPED");
        var idx = questions.findIndex(function (x) { return x.seqNo === q.seqNo; });
        if (idx < questions.length - 1) selectQuestion(questions[idx + 1].seqNo);
        else if (_render) _render();
      });
      bindAll(".ep-cancel-edit-btn", function () { setFocusStage(q, "PREVIEW"); if (_render) _render(); });
      bindAll(".ep-save-stem-btn", async function () {
        try {
          var blocks = collectBlocksFromEpEditor("ep-stem-editor");
          var hasContent = blocks.some(function (b) { return (b.type === "p" && b.text.trim()) || b.type !== "p"; });
          if (!hasContent) { epLog("题干不能为空"); return; }
          var xml = (typeof blocksToXml === "function") ? blocksToXml(blocks) : "";
          await updateQuestion(task.taskUuid, q.seqNo, { stemXml: xml });
          setFocusStage(q, (q.answerXml || q.rawAnswerText) ? "READY" : "EDITING_ANSWER");
          epLog("第" + q.seqNo + "题 题干已保存");
          if (_render) _render();
        } catch (err) { epLog("保存题干失败: " + err.message); }
      });
      bindAll(".ep-save-answer-btn", async function () {
        try {
          var blocks = (typeof collectAnswerBlocksFromEditor === "function")
            ? collectAnswerBlocksFromEditor("ep-answer-editor") : [];
          var hasContent = blocks.some(function (b) { return (b.type === "p" && b.text.trim()) || b.type !== "p"; });
          if (!hasContent) { epLog("答案不能为空"); return; }
          var xml = (typeof blocksToAnswerXml === "function") ? blocksToAnswerXml(blocks) : "";
          await updateQuestion(task.taskUuid, q.seqNo, { answerXml: xml });
          setFocusStage(q, "READY");
          epLog("第" + q.seqNo + "题 答案已保存");
          if (_render) _render();
        } catch (err) { epLog("保存答案失败: " + err.message); }
      });
    }
  }

  function bindClick(id, fn) { var el = $(id); if (el) el.addEventListener("click", fn); }
  function bindAll(sel, fn) { document.querySelectorAll(sel).forEach(function (el) { el.addEventListener("click", fn); }); }

  /* ---------- 日志面板 ---------- */
  function renderSessionLogs() {
    var box = $("ep-log-list");
    if (!box) return;
    box.innerHTML = "";
    var items = epState.logs.slice(0, 50);
    for (var i = 0; i < items.length; i++) {
      var li = document.createElement("li");
      li.textContent = items[i].ts + "  " + items[i].msg;
      box.appendChild(li);
    }
  }

  /* ================================================================
   *  键盘导航
   * ================================================================ */
  function setupKeyboard() {
    document.addEventListener("keydown", function (ev) {
      if (!_state || _state.activeTab !== "exam-parse") return;
      var questions = getActiveQuestions();
      if (!questions.length) return;
      if (ev.altKey && (ev.key === "ArrowLeft" || ev.key === "ArrowRight")) {
        ev.preventDefault();
        var delta = ev.key === "ArrowLeft" ? -1 : 1;
        var idx = questions.findIndex(function (x) { return x.seqNo === epState.activeSeqNo; });
        var newIdx = Math.max(0, Math.min(questions.length - 1, idx + delta));
        if (newIdx !== idx) selectQuestion(questions[newIdx].seqNo);
        return;
      }
      if (ev.altKey && (ev.key === "e" || ev.key === "E")) {
        ev.preventDefault();
        var q = getCurrentQuestion(questions);
        if (q && getFocusStage(q) === "PREVIEW") { setFocusStage(q, "EDITING_STEM"); if (_render) _render(); }
        return;
      }
      if (ev.key === "Escape") {
        var q2 = getCurrentQuestion(questions);
        if (q2) {
          var s = getFocusStage(q2);
          if (s === "EDITING_STEM" || s === "EDITING_ANSWER") { setFocusStage(q2, "PREVIEW"); if (_render) _render(); }
        }
      }
    });
  }

  /* ================================================================
   *  截图处理
   * ================================================================ */
  function handleScreenshot(params) {
    var questions = getActiveQuestions();
    var q = getCurrentQuestion(questions);
    if (!q) return false;
    var stage = getFocusStage(q);
    if (params.intent === "insert-image") {
      if (stage === "EDITING_STEM" || stage === "EDITING_ANSWER") {
        epLog("截图插图功能将在后续版本完善");
        return true;
      }
    }
    return false;
  }

  /* ================================================================
   *  工具函数
   * ================================================================ */
  var KATEX_DELIMITERS = [
    { left: "$$", right: "$$", display: true },
    { left: "$", right: "$", display: false },
    { left: "\\(", right: "\\)", display: false },
    { left: "\\[", right: "\\]", display: true },
  ];

  function buildImageMap(imagesJson) {
    if (!imagesJson) return {};
    try {
      var arr = typeof imagesJson === "string" ? JSON.parse(imagesJson) : imagesJson;
      if (!Array.isArray(arr)) return {};
      var map = {};
      for (var i = 0; i < arr.length; i++) {
        var img = arr[i];
        if (img.refKey && img.imageBase64) {
          var mime = img.mimeType || "image/png";
          var prefix = img.imageBase64.indexOf("data:") === 0 ? "" : "data:" + mime + ";base64,";
          map[img.refKey] = prefix + img.imageBase64;
        }
      }
      return map;
    } catch (e) { return {}; }
  }

  function makeImageResolver(imageMap) {
    return function (ref) { return imageMap[ref] || ""; };
  }

  function renderRichContent(node, text, imageMap) {
    if (!text || !text.trim()) { node.innerHTML = '<span class="empty-note">（空）</span>'; return; }
    if (typeof renderLatexNode === "function") {
      if (text.trim().indexOf("<stem") === 0 || text.trim().indexOf("<answer") === 0) {
        renderLatexNode(node, text, "（空）", { imageResolver: makeImageResolver(imageMap) });
        return;
      }
    }
    node.innerHTML = "";
    var imgTagRegex = /<image\s+ref="([^"]+)"[^/]*\/>/g;
    var lastIdx = 0, match;
    while ((match = imgTagRegex.exec(text)) !== null) {
      if (match.index > lastIdx) {
        var span = document.createElement("span");
        span.textContent = text.substring(lastIdx, match.index);
        node.appendChild(span);
      }
      var ref = match[1], src = imageMap[ref];
      if (src) {
        var img = document.createElement("img");
        img.className = "ep-inline-image"; img.src = src; img.alt = ref;
        node.appendChild(img);
      } else {
        var ph = document.createElement("span");
        ph.className = "ep-image-placeholder"; ph.textContent = "[配图: " + ref + "]";
        node.appendChild(ph);
      }
      lastIdx = match.index + match[0].length;
    }
    if (lastIdx < text.length) {
      var sp = document.createElement("span"); sp.textContent = text.substring(lastIdx); node.appendChild(sp);
    }
    var usedRefs = new Set();
    node.querySelectorAll(".ep-inline-image").forEach(function (el) { usedRefs.add(el.alt); });
    for (var r in imageMap) {
      if (!usedRefs.has(r)) {
        var oi = document.createElement("img");
        oi.className = "ep-inline-image"; oi.src = imageMap[r]; oi.alt = r;
        node.appendChild(oi);
      }
    }
    if (typeof window.renderMathInElement === "function") {
      try { window.renderMathInElement(node, { delimiters: KATEX_DELIMITERS, throwOnError: false, strict: "ignore" }); } catch (e) {}
    }
  }

  function buildFallbackBlock(block) {
    var div = document.createElement("div");
    div.className = "stem-block"; div.dataset.type = block.type;
    div.textContent = block.text || "[" + block.type + "]";
    return div;
  }

  function escHtml(s) {
    return String(s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function truncate(s, n) { if (!s) return ""; return s.length > n ? s.slice(0, n) + "…" : s; }

  /* ================================================================
   *  导出
   * ================================================================ */
  var exports = {
    init: init,
    getState: getState,
    getActiveTask: getActiveTask,
    getActiveQuestions: getActiveQuestions,
    selectTask: selectTask,
    createTask: createTask,
    refreshTaskList: refreshTaskList,
    refreshQuestions: refreshQuestions,
    updateQuestion: updateQuestion,
    confirmTask: confirmTask,
    deleteTask: deleteTask,
    handleWsMessage: handleWsMessage,
    handleScreenshot: handleScreenshot,
    renderExamParse: renderExamParse,
    statusLabel: statusLabel,
    statusCls: statusCls,
    saveLocalState: saveLocalState,
    loadLocalState: loadLocalState,
    epLog: epLog,
    selectQuestion: selectQuestion,
    getCurrentQuestion: getCurrentQuestion,
    getFocusStage: getFocusStage,
    setFocusStage: setFocusStage,
    buildImageMap: buildImageMap,
  };

  if (typeof module !== "undefined" && module.exports) module.exports = exports;
  global.QForgeExamParseRuntime = exports;
})(typeof globalThis !== "undefined" ? globalThis : this);
