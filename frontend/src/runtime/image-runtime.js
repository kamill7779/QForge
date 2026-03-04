(function (global) {
  function answerTaskSeed(taskUuid) {
    const seedRaw = String(taskUuid || "").replace(/[^0-9a-f]/gi, "").toLowerCase();
    return (seedRaw || "draft0000").slice(0, 8).padEnd(8, "0");
  }

  function buildAnswerTaskRef(taskUuid, index) {
    const n = Number(index || 0);
    if (!Number.isFinite(n) || n <= 0) return "";
    return `a${answerTaskSeed(taskUuid)}-img-${n}`;
  }

  function createImageRuntime(state) {
    function imageDataUrl(value) {
      const x = String(value || "").trim();
      if (!x) return "";
      if (x.startsWith("data:image/")) return x;
      return `data:image/png;base64,${x}`;
    }

    function resolveStemImage(entry, ref) {
      if (!entry) return "";
      const key = String(ref || "").trim();
      if (key && entry.inlineImages && entry.inlineImages[key]) return entry.inlineImages[key];
      if (entry.stemImageBase64 && (!key || key === "original" || key === "stem-local")) {
        return entry.stemImageBase64;
      }
      return "";
    }

    function resolveAnswerImage(entry, ref) {
      if (!entry) return "";
      const key = String(ref || "").trim();
      if (!key) return "";
      if (entry.answerImages && entry.answerImages[key]) return entry.answerImages[key];
      if (entry.inlineImages && entry.inlineImages[key]) return entry.inlineImages[key];

      const fig = key.match(/^fig-(\d+)$/i);
      if (!fig) return "";

      const idx = Number(fig[1] || 0);
      const taskRef = buildAnswerTaskRef(entry.lastAnswerOcrTaskUuid, idx);
      if (taskRef && entry.answerImages && entry.answerImages[taskRef]) {
        return entry.answerImages[taskRef];
      }

      const suffix = `-img-${idx}`;
      if (entry.answerImages) {
        const hit = Object.keys(entry.answerImages).find((k) => k.endsWith(suffix));
        if (hit) return entry.answerImages[hit];
      }
      if (entry.inlineImages) {
        const hit = Object.keys(entry.inlineImages).find((k) => k.endsWith(suffix));
        if (hit) return entry.inlineImages[hit];
      }
      return "";
    }

    function ensureInlineImages(entry) {
      if (!entry.inlineImages || typeof entry.inlineImages !== "object") {
        entry.inlineImages = {};
      }
      return entry.inlineImages;
    }

    function generateImageRef(entry) {
      const images = ensureInlineImages(entry);
      let i = 1;
      while (images[`img-${i}`]) i += 1;
      return `img-${i}`;
    }

    function attachInlineImage(entry, base64) {
      const data = String(base64 || "").trim();
      if (!data) throw new Error("截图数据为空");
      const images = ensureInlineImages(entry);
      const ref = generateImageRef(entry);
      images[ref] = data;
      entry.updatedAt = Date.now();
      state.entries.set(entry.questionUuid, entry);
      return ref;
    }

    function generateAnswerImageRef(entry, answerUuid) {
      if (!entry.answerImages || typeof entry.answerImages !== "object") {
        entry.answerImages = {};
      }
      const seedRaw = String(
        answerUuid || entry?.lastAnswerOcrTaskUuid || entry?.questionUuid || "draft"
      ).replace(/[^0-9a-f]/gi, "").toLowerCase();
      const seed = (seedRaw || "draft0000").slice(0, 8).padEnd(8, "0");
      let i = 1;
      while (entry.answerImages[`a${seed}-img-${i}`]) i += 1;
      return `a${seed}-img-${i}`;
    }

    return {
      imageDataUrl,
      resolveStemImage,
      resolveAnswerImage,
      ensureInlineImages,
      generateImageRef,
      attachInlineImage,
      generateAnswerImageRef
    };
  }

  const api = {
    answerTaskSeed,
    buildAnswerTaskRef,
    createImageRuntime
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  global.QForgeImageRuntime = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
