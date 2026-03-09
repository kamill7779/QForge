export type RootTag = 'stem' | 'answer'

export function escapeXml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

export function toXmlPayload(input: string, rootTag: RootTag): string {
  const trimmed = String(input || '').trim()
  if (!trimmed) {
    return `<${rootTag} version="1"><p></p></${rootTag}>`
  }
  if (trimmed.startsWith(`<${rootTag}`)) {
    return trimmed
  }
  const paragraphs = trimmed
    .split(/\n+/)
    .map((line) => `<p>${escapeXml(line)}</p>`)
    .join('')
  return `<${rootTag} version="1">${paragraphs}</${rootTag}>`
}

export function parseXmlDocument(input: string, rootTag: RootTag): Document | null {
  const text = String(input || '').trim()
  if (!text.startsWith(`<${rootTag}`)) return null
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(text, 'text/xml')
    if (doc.querySelector('parsererror')) return null
    return doc
  } catch {
    return null
  }
}