export type RootTag = 'stem' | 'answer'

export function escapeXml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

export function escapeXmlPreserveEntities(text: string): string {
  return text
    .replace(/&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[\da-fA-F]+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function decodeXml(text: string): string {
  return text
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
}

function escapeLatexInXml(xml: string): string {
  const tagRe = /<\/?(?:stem|answer|p|choices|choice|image|blanks|blank|answer-area|table|thead|tbody|tr|th|td)(?:\s[^>]*)?\/?>/gi
  const parts: string[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = tagRe.exec(xml)) !== null) {
    if (match.index > lastIndex) {
      parts.push(escapeNonTagText(xml.slice(lastIndex, match.index)))
    }
    parts.push(match[0])
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < xml.length) {
    parts.push(escapeNonTagText(xml.slice(lastIndex)))
  }
  return parts.join('')
}

function escapeNonTagText(text: string): string {
  return text
    .replace(/&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[\da-fA-F]+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function normalizeXmlForDom(xml: string, rootTag: RootTag): string {
  const trimmed = xml.trim()
  const versionRe = new RegExp(`^<${rootTag}\\s[^>]*version=`)
  if (versionRe.test(trimmed)) return trimmed
  const openRe = new RegExp(`^<${rootTag}[^>]*>`)
  const closeRe = new RegExp(`</${rootTag}>$`)
  const inner = trimmed.replace(openRe, '').replace(closeRe, '')
  return `<${rootTag} version="1">${inner}</${rootTag}>`
}

export function textOfNode(node: Node): string {
  let text = ''
  for (let i = 0; i < node.childNodes.length; i++) {
    const child = node.childNodes[i]
    if (child.nodeType === 3) {
      text += child.textContent
    } else if (child.nodeType === 1) {
      const el = child as Element
      if (el.tagName === 'img') {
        const ref = el.getAttribute('ref') || ''
        if (ref) text += `[img:${ref}]`
      } else {
        text += textOfNode(child)
      }
    }
  }
  return text
}

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

export function isValidXml(xml: string, rootTag: RootTag): boolean {
  return parseXmlDocument(xml, rootTag) !== null
}

export function toXmlPayload(input: string, rootTag: RootTag): string {
  if (!input || !input.trim()) return `<${rootTag} version="1"><p></p></${rootTag}>`
  const trimmed = input.trim()
  if (trimmed.startsWith(`<${rootTag}`)) {
    if (isValidXml(trimmed, rootTag)) return trimmed
    const sanitized = escapeLatexInXml(trimmed)
    if (isValidXml(sanitized, rootTag)) return sanitized
    return `<${rootTag} version="1"><p>${escapeXml(trimmed)}</p></${rootTag}>`
  }
  const lines = trimmed.split(/\n/)
  const paragraphs = lines.map((line) => `<p>${escapeXmlPreserveEntities(line)}</p>`).join('')
  return `<${rootTag} version="1">${paragraphs}</${rootTag}>`
}

export function toEditorText(xml: string, rootTag: RootTag): string {
  const doc = parseXmlDocument(xml, rootTag)
  if (!doc) return ''
  const root = doc.documentElement
  const paragraphs = root.getElementsByTagName('p')
  if (!paragraphs.length) return ''
  const lines: string[] = []
  for (let i = 0; i < paragraphs.length; i++) {
    lines.push(textOfNode(paragraphs[i]))
  }
  return lines.join('\n')
}

export const parseStemXmlDocument = (input: string) => parseXmlDocument(input, 'stem')
export const parseAnswerXmlDocument = (input: string) => parseXmlDocument(input, 'answer')
export const isValidStemXml = (xml: string) => isValidXml(xml, 'stem')
export const isValidAnswerXml = (xml: string) => isValidXml(xml, 'answer')

export function isEmptyXmlContent(xml: string, rootTag: RootTag): boolean {
  const doc = parseXmlDocument(xml, rootTag)
  if (!doc) return true
  const root = doc.documentElement
  const hasText = root.textContent?.trim()
  const hasImage = root.querySelector('image') || root.querySelector('img')
  return !hasText && !hasImage
}

export const toStemXmlPayload = (input: string) => toXmlPayload(input, 'stem')
export const toAnswerXmlPayload = (input: string) => toXmlPayload(input, 'answer')
export const toEditorStemText = (xml: string) => toEditorText(xml, 'stem')
export const toEditorAnswerText = (xml: string) => toEditorText(xml, 'answer')