(function (global) {
  const ALLOWED_TAG_NAMES = [
    "stem",
    "p",
    "image",
    "choices",
    "choice",
    "blanks",
    "blank",
    "answer-area"
  ];

  const ALLOWED_TAG_TOKEN_RE = new RegExp(
    `<\\/?(?:${ALLOWED_TAG_NAMES.join("|")})\\b[^>]*\\/?>`,
    "gi"
  );

  function escapeXml(text) {
    return String(text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&apos;");
  }

  function escapeXmlTextPreserveEntities(text) {
    return String(text)
      .replace(/&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9A-Fa-f]+);)/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
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

  function normalizeStemXmlForDom(input) {
    const raw = String(input || "");
    if (!raw) return raw;

    const tokens = [...raw.matchAll(ALLOWED_TAG_TOKEN_RE)];
    if (!tokens.length) return raw;

    let result = "";
    let cursor = 0;
    for (const match of tokens) {
      const token = match[0];
      const index = match.index || 0;
      const between = raw.slice(cursor, index);
      result += escapeXmlTextPreserveEntities(between);
      result += token;
      cursor = index + token.length;
    }
    result += escapeXmlTextPreserveEntities(raw.slice(cursor));
    return result.trim();
  }

  function parseStemXmlDocument(input) {
    const raw = String(input || "").trim();
    if (!raw) return null;
    if (typeof DOMParser === "undefined") return null;
    if (!raw.startsWith("<stem")) return null;

    const normalized = normalizeStemXmlForDom(raw);
    try {
      const doc = new DOMParser().parseFromString(normalized, "application/xml");
      if (doc.querySelector("parsererror")) return null;
      if (!doc.documentElement || doc.documentElement.tagName !== "stem") return null;
      return doc;
    } catch {
      return null;
    }
  }

  function isValidStemXml(xml) {
    const raw = String(xml || "").trim();
    if (!raw) return false;

    if (typeof DOMParser !== "undefined") {
      const doc = parseStemXmlDocument(raw);
      return Boolean(doc);
    }

    const parsed = parseTagSequence(raw);
    return parsed.ok && parsed.root === "stem";
  }

  function toStemXmlPayload(input) {
    const raw = String(input || "").trim();
    if (!raw) return "";
    if (isValidStemXml(raw)) return normalizeStemXmlForDom(raw);

    if (raw.startsWith("<stem")) {
      const normalized = normalizeStemXmlForDom(raw);
      if (isValidStemXml(normalized)) return normalized;
    }

    const normalized = raw.replace(/\r\n/g, "\n");
    const lines = normalized.split(/\n+/).map((x) => x.trim()).filter(Boolean);
    const paragraphs = lines.length ? lines : [normalized.trim()];
    const body = paragraphs.map((line) => `<p>${escapeXml(line)}</p>`).join("");
    return `<stem version=\"1\">${body}</stem>`;
  }

  function textOfNode(node) {
    if (!node) return "";
    if (node.nodeType === 3) return node.nodeValue || "";
    if (node.nodeType !== 1) return "";

    const name = String(node.tagName || "").toLowerCase();
    if (name === "blank") return "____";
    if (name === "image") return "[image]";
    if (name === "answer-area") return "[answer-area]";

    let out = "";
    for (const child of Array.from(node.childNodes || [])) {
      out += textOfNode(child);
    }
    return out;
  }

  function toEditorStemText(input) {
    const raw = String(input || "").trim();
    if (!raw) return "";

    const doc = parseStemXmlDocument(raw);
    if (doc) {
      const root = doc.documentElement;
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
    }

    const plain = raw
      .replace(/<blank\b[^>]*\/>/gi, "____")
      .replace(/<answer-area\b[^>]*\/>/gi, "[answer-area]")
      .replace(/<image\b[^>]*\/>/gi, "[image]")
      .replace(/<choice\b[^>]*\bkey=['\"]?([^'\">\s]+)['\"]?[^>]*>/gi, "$1. ")
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
    toEditorStemText,
    parseStemXmlDocument,
    normalizeStemXmlForDom
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  global.StemXml = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
