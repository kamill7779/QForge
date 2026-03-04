(function (global) {
  function answerRefSeed(source) {
    const seedRaw = String(source || "").replace(/[^0-9a-f]/gi, "").toLowerCase();
    return (seedRaw || "draft0000").slice(0, 8).padEnd(8, "0");
  }

  function buildScopedAnswerRef(source, index) {
    const n = Number(index || 0);
    if (!Number.isFinite(n) || n <= 0) return "";
    return `a${answerRefSeed(source)}-img-${n}`;
  }

  function refIndex(ref) {
    const key = String(ref || "").trim();
    let match = key.match(/^fig-(\d+)$/i);
    if (match) return Number(match[1] || 0);
    match = key.match(/-img-(\d+)$/i);
    if (match) return Number(match[1] || 0);
    match = key.match(/^img-(\d+)$/i);
    if (match) return Number(match[1] || 0);
    return 0;
  }

  function nextFigureRef(refs) {
    let max = 0;
    for (const ref of refs || []) {
      const idx = refIndex(ref);
      if (idx > max) max = idx;
    }
    return `fig-${max + 1}`;
  }

  function toScopedRef(seedSource, ref) {
    const key = String(ref || "").trim();
    if (!key) return "";
    const fig = key.match(/^fig-(\d+)$/i);
    if (fig) return buildScopedAnswerRef(seedSource, Number(fig[1] || 0));
    const img = key.match(/^img-(\d+)$/i);
    if (img) return buildScopedAnswerRef(seedSource, Number(img[1] || 0));
    return key;
  }

  function buildInlineImage(base64) {
    if (!base64 || typeof base64 !== "string") return null;
    const value = base64.trim();
    if (!value) return null;
    return {
      imageData: value,
      mimeType: "image/png"
    };
  }

  function pickImageBase64(entry, originalRef, targetRef, resolveAnswerImage) {
    const fromAnswer = entry?.answerImages || {};
    const fromInline = entry?.inlineImages || {};
    if (fromAnswer[targetRef]) return fromAnswer[targetRef];
    if (fromAnswer[originalRef]) return fromAnswer[originalRef];
    if (fromInline[targetRef]) return fromInline[targetRef];
    if (fromInline[originalRef]) return fromInline[originalRef];
    if (typeof resolveAnswerImage === "function") {
      const resolved = resolveAnswerImage(entry, originalRef);
      if (resolved) return resolved;
      return resolveAnswerImage(entry, targetRef);
    }
    return "";
  }

  function prepareAnswerSubmitData(options) {
    const {
      entry,
      blocks,
      answerUuid,
      resolveAnswerImage
    } = options || {};
    const srcBlocks = Array.isArray(blocks) ? blocks : [];
    const seedSource = answerUuid || entry?.lastAnswerOcrTaskUuid || entry?.questionUuid || "draft";
    const normalizedBlocks = [];
    const inlineImages = {};

    for (const block of srcBlocks) {
      if (!block || block.type !== "image") {
        normalizedBlocks.push(block);
        continue;
      }
      const originalRef = String(block.ref || "").trim();
      const targetRef = toScopedRef(seedSource, originalRef) || originalRef || buildScopedAnswerRef(seedSource, 1);
      normalizedBlocks.push({ ...block, ref: targetRef });
      const base64 = pickImageBase64(entry, originalRef, targetRef, resolveAnswerImage);
      const payload = buildInlineImage(base64);
      if (payload) {
        inlineImages[targetRef] = payload;
      }
    }

    return {
      blocks: normalizedBlocks,
      inlineImages
    };
  }

  const api = {
    answerRefSeed,
    buildScopedAnswerRef,
    nextFigureRef,
    toScopedRef,
    prepareAnswerSubmitData
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  global.QForgeAnswerRuntime = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
