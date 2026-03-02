(function (global) {
  function escapeXml(text) {
    return String(text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&apos;");
  }

  function decodeXml(text) {
    return String(text)
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&quot;/g, "\"")
      .replace(/&apos;/g, "'")
      .replace(/&amp;/g, "&");
  }

  function parseTagSequence(xml) {
    const input = String(xml || "").trim();
    if (!input) return { ok: false, root: "" };

    const tokenRegex = /<[^>]+>/g;
    const stack = [];
    let root = "";
    let lastIndex = 0;
    let tokenCount = 0;
    let rootClosed = false;

    while (true) {
      const match = tokenRegex.exec(input);
      if (!match) break;

      const token = match[0];
      const textBetween = input.slice(lastIndex, match.index);
      if (!tokenCount && textBetween.trim().length > 0) {
        return { ok: false, root: "" };
      }
      if (rootClosed && textBetween.trim().length > 0) {
        return { ok: false, root: "" };
      }

      tokenCount += 1;
      lastIndex = tokenRegex.lastIndex;

      if (/^<\?/.test(token) || /^<!/.test(token)) {
        continue;
      }

      const closeMatch = /^<\/\s*([A-Za-z][A-Za-z0-9._:-]*)\s*>$/.exec(token);
      if (closeMatch) {
        const name = closeMatch[1];
        const top = stack.pop();
        if (top !== name) return { ok: false, root: "" };
        if (stack.length === 0) {
          rootClosed = true;
        }
        continue;
      }

      const openMatch = /^<\s*([A-Za-z][A-Za-z0-9._:-]*)(\s[^>]*)?>$/.exec(token);
      if (openMatch) {
        const name = openMatch[1];
        if (!root) root = name;
        stack.push(name);
        continue;
      }

      const selfCloseMatch = /^<\s*([A-Za-z][A-Za-z0-9._:-]*)(\s[^>]*)?\/>$/.exec(token);
      if (selfCloseMatch) {
        const name = selfCloseMatch[1];
        if (!root) root = name;
        continue;
      }

      return { ok: false, root: "" };
    }

    if (input.slice(lastIndex).trim().length > 0 && rootClosed) {
      return { ok: false, root: "" };
    }
    if (!tokenCount || stack.length > 0 || !root) {
      return { ok: false, root: "" };
    }
    if (!rootClosed && root !== "stem") {
      return { ok: false, root: "" };
    }
    return { ok: true, root };
  }

  function isValidStemXml(xml) {
    const raw = String(xml || "").trim();
    if (!raw) return false;

    if (typeof DOMParser !== "undefined") {
      try {
        const doc = new DOMParser().parseFromString(raw, "application/xml");
        if (doc.querySelector("parsererror")) return false;
        return Boolean(doc.documentElement) && doc.documentElement.tagName === "stem";
      } catch {
        return false;
      }
    }

    const parsed = parseTagSequence(raw);
    return parsed.ok && parsed.root === "stem";
  }

  function toStemXmlPayload(input) {
    const raw = String(input || "").trim();
    if (!raw) return "";
    if (isValidStemXml(raw)) return raw;

    const normalized = raw.replace(/\r\n/g, "\n");
    const lines = normalized.split(/\n+/).map((x) => x.trim()).filter(Boolean);
    const paragraphs = lines.length ? lines : [normalized.trim()];
    const body = paragraphs.map((line) => `<p>${escapeXml(line)}</p>`).join("");
    return `<stem version="1">${body}</stem>`;
  }

  function textOfNode(node) {
    if (!node) return "";
    if (node.nodeType === 3) return node.nodeValue || "";
    if (node.nodeType !== 1) return "";

    const name = String(node.tagName || "").toLowerCase();
    if (name === "blank") return "____";
    if (name === "image") return "[图片]";
    if (name === "answer-area") return "[解答区]";

    let out = "";
    for (const child of Array.from(node.childNodes || [])) {
      out += textOfNode(child);
    }
    return out;
  }

  function toEditorStemText(input) {
    const raw = String(input || "").trim();
    if (!raw) return "";
    if (!isValidStemXml(raw)) return raw;

    if (typeof DOMParser !== "undefined") {
      try {
        const doc = new DOMParser().parseFromString(raw, "application/xml");
        const root = doc.documentElement;
        if (!root || root.tagName !== "stem") return raw;
        const lines = [];
        for (const child of Array.from(root.childNodes || [])) {
          if (child.nodeType !== 1) continue;
          const name = String(child.tagName || "").toLowerCase();
          if (name === "p") {
            const text = textOfNode(child).trim();
            if (text) lines.push(text);
            continue;
          }
          if (name === "choices") {
            for (const option of Array.from(child.childNodes || [])) {
              if (option.nodeType !== 1 || String(option.tagName || "").toLowerCase() !== "choice") continue;
              const key = option.getAttribute("key") || "";
              const text = textOfNode(option).trim();
              lines.push(key ? `${key}. ${text}` : text);
            }
            continue;
          }
          const fallback = textOfNode(child).trim();
          if (fallback) lines.push(fallback);
        }
        const merged = lines.filter(Boolean).join("\n").trim();
        return merged || raw;
      } catch {
        return raw;
      }
    }

    const plain = raw
      .replace(/<blank\b[^>]*\/>/gi, "____")
      .replace(/<answer-area\b[^>]*\/>/gi, "[解答区]")
      .replace(/<image\b[^>]*\/>/gi, "[图片]")
      .replace(/<choice\b[^>]*\bkey=['"]?([^'">\s]+)['"]?[^>]*>/gi, "$1. ")
      .replace(/<\/choice>/gi, "\n")
      .replace(/<choices\b[^>]*>/gi, "")
      .replace(/<\/choices>/gi, "\n")
      .replace(/<p\b[^>]*>/gi, "")
      .replace(/<\/p>/gi, "\n")
      .replace(/<[^>]+>/g, "");

    return decodeXml(plain)
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
      .join("\n")
      .trim();
  }

  const api = {
    toStemXmlPayload,
    toEditorStemText
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  global.StemXml = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
