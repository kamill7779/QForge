/**
 * stemXml.ts — Parameterized XML parsing/serialization for <stem> and <answer> documents.
 *
 * Eliminates the 4 pairs of duplicated stem/answer functions from the original stem-xml.js
 * by parameterizing on rootTag ('stem' | 'answer').
 */

export type RootTag = 'stem' | 'answer'

// ──────────────────── XML Escaping ────────────────────

/** Escape all XML-special characters. */
export function escapeXml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

/** Escape < and > but preserve existing XML entities like &amp; &lt; &#123; etc. */
export function escapeXmlPreserveEntities(text: string): string {
  return text
    .replace(/&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[\da-fA-F]+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** Decode common XML entities back to characters. */
export function decodeXml(text: string): string {
  return text
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
}

// ──────────────────── Internal Helpers ────────────────────

/**
 * Escape bare `<`, `>`, `&` that appear inside LaTeX math delimiters
 * (`$...$`, `$$...$$`, `\(...\)`, `\[...\]`) so the XML parser won't choke.
 *
 * Strategy: split the text on XML tag boundaries, then within non-tag segments
 * escape any bare `<` / `>` / `&` that are NOT part of XML tags or entities.
 */
function escapeLatexInXml(xml: string): string {
  // Escape & and < > that are not part of XML tags or existing entities.
  // We walk through the string preserving XML tags (<tag>, </tag>, <tag />)
  // and XML entities (&amp; etc.) while escaping anything else.
  const TAG_RE = /<\/?[a-zA-Z][^>]*\/?>/g
  const parts: string[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = TAG_RE.exec(xml)) !== null) {
    // Text segment before this tag
    if (match.index > lastIndex) {
      parts.push(escapeNonTagText(xml.slice(lastIndex, match.index)))
    }
    // The tag itself — keep as-is
    parts.push(match[0])
    lastIndex = match.index + match[0].length
  }
  // Remaining text after last tag
  if (lastIndex < xml.length) {
    parts.push(escapeNonTagText(xml.slice(lastIndex)))
  }
  return parts.join('')
}

/**
 * Escape `&`, `<`, `>` in a text segment that is NOT an XML tag.
 * Preserves existing XML entities like `&amp;` `&lt;` `&#123;`.
 */
function escapeNonTagText(text: string): string {
  return text
    .replace(/&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[\da-fA-F]+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/**
 * Ensure the XML string has a version attribute on its root tag.
 * If missing, strip existing root tags and re-wrap with version="1".
 */
function normalizeXmlForDom(xml: string, rootTag: RootTag): string {
  const trimmed = xml.trim()
  const versionRe = new RegExp(`^<${rootTag}\\s[^>]*version=`)
  if (versionRe.test(trimmed)) return trimmed

  const openRe = new RegExp(`^<${rootTag}[^>]*>`)
  const closeRe = new RegExp(`</${rootTag}>$`)
  const inner = trimmed.replace(openRe, '').replace(closeRe, '')
  return `<${rootTag} version="1">${inner}</${rootTag}>`
}

/** Extract text content from a DOM node, converting <img ref="..."> to [img:ref]. */
export function textOfNode(node: Node): string {
  let t = ''
  for (let i = 0; i < node.childNodes.length; i++) {
    const child = node.childNodes[i]
    if (child.nodeType === 3) {
      // Text node
      t += child.textContent
    } else if (child.nodeType === 1) {
      // Element node
      const el = child as Element
      if (el.tagName === 'img') {
        const ref = el.getAttribute('ref') || ''
        if (ref) t += `[img:${ref}]`
      } else {
        t += textOfNode(child)
      }
    }
  }
  return t
}

// ──────────────────── Core Functions ────────────────────

/**
 * Parse an XML string into a Document.
 * Returns null if the input is empty, doesn't start with the expected root tag,
 * or contains parse errors.
 */
export function parseXmlDocument(input: string, rootTag: RootTag): Document | null {
  if (!input || typeof input !== 'string') return null
  const trimmed = input.trim()
  if (!trimmed.startsWith(`<${rootTag}`)) return null

  const normalized = normalizeXmlForDom(trimmed, rootTag)
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(normalized, 'text/xml')
    if (doc.querySelector('parsererror')) return null
    const root = doc.documentElement
    if (!root || root.tagName !== rootTag) return null
    return doc
  } catch {
    return null
  }
}

/** Check whether the given XML string is valid for the specified root tag. */
export function isValidXml(xml: string, rootTag: RootTag): boolean {
  return parseXmlDocument(xml, rootTag) !== null
}

/**
 * Convert user input (plain text or XML) into a well-formed XML payload.
 *
 * - Empty input → `<rootTag version="1"><p></p></rootTag>`
 * - Input starting with `<rootTag` that is valid XML → returned as-is
 * - Input starting with `<rootTag` that has LaTeX issues → sanitize & retry
 * - Input starting with `<rootTag` that is truly malformed → escaped and wrapped
 * - Plain text → split by newlines into `<p>` paragraphs
 */
export function toXmlPayload(input: string, rootTag: RootTag): string {
  if (!input || !input.trim()) return `<${rootTag} version="1"><p></p></${rootTag}>`

  const trimmed = input.trim()
  if (trimmed.startsWith(`<${rootTag}`)) {
    if (isValidXml(trimmed, rootTag)) return trimmed
    // Try sanitizing LaTeX math (< > & inside math delimiters)
    const sanitized = escapeLatexInXml(trimmed)
    if (isValidXml(sanitized, rootTag)) return sanitized
    // Truly malformed — escape everything
    return `<${rootTag} version="1"><p>${escapeXml(trimmed)}</p></${rootTag}>`
  }

  // Plain text — each line becomes a <p>
  const lines = trimmed.split(/\n/)
  const paragraphs = lines.map((l) => `<p>${escapeXmlPreserveEntities(l)}</p>`).join('')
  return `<${rootTag} version="1">${paragraphs}</${rootTag}>`
}

/**
 * Convert XML back to editor-friendly plain text.
 * Each `<p>` becomes a line; `<img ref="...">` becomes `[img:ref]`.
 */
export function toEditorText(xml: string, rootTag: RootTag): string {
  const doc = parseXmlDocument(xml, rootTag)
  if (!doc) return ''

  const root = doc.documentElement
  const pNodes = root.getElementsByTagName('p')
  if (!pNodes.length) return ''

  const lines: string[] = []
  for (let i = 0; i < pNodes.length; i++) {
    lines.push(textOfNode(pNodes[i]))
  }
  return lines.join('\n')
}

// ──────────────────── Convenience Aliases ────────────────────

export const parseStemXmlDocument = (input: string) => parseXmlDocument(input, 'stem')
export const parseAnswerXmlDocument = (input: string) => parseXmlDocument(input, 'answer')

export const isValidStemXml = (xml: string) => isValidXml(xml, 'stem')
export const isValidAnswerXml = (xml: string) => isValidXml(xml, 'answer')

/**
 * Check if an XML payload has no meaningful text or image content.
 * Returns true for empty wrappers like `<answer version="1"><p></p></answer>`.
 */
export function isEmptyXmlContent(xml: string, rootTag: RootTag): boolean {
  const doc = parseXmlDocument(xml, rootTag)
  if (!doc) return true
  const root = doc.documentElement
  // Check if there's any non-whitespace text or <image>/<img> elements
  const hasText = root.textContent?.trim()
  const hasImage = root.querySelector('image') || root.querySelector('img')
  return !hasText && !hasImage
}

export const toStemXmlPayload = (input: string) => toXmlPayload(input, 'stem')
export const toAnswerXmlPayload = (input: string) => toXmlPayload(input, 'answer')

export const toEditorStemText = (xml: string) => toEditorText(xml, 'stem')
export const toEditorAnswerText = (xml: string) => toEditorText(xml, 'answer')
