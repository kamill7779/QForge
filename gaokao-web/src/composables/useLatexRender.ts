import { parseXmlDocument } from '@/lib/stemXml'

const KATEX_DELIMITERS = [
  { left: '$$', right: '$$', display: true },
  { left: '$', right: '$', display: false },
  { left: '\\(', right: '\\)', display: false },
  { left: '\\[', right: '\\]', display: true }
]

function applyKatex(el: HTMLElement) {
  const renderMathInElement = (window as Window & { renderMathInElement?: Function }).renderMathInElement
  if (typeof renderMathInElement === 'function') {
    renderMathInElement(el, {
      delimiters: KATEX_DELIMITERS,
      throwOnError: false,
      strict: 'ignore'
    })
  }
}

export function useLatexRender() {
  function render(container: HTMLElement, xml: string) {
    container.innerHTML = ''
    const text = String(xml || '').trim()
    if (!text) return

    const rootTag = text.startsWith('<answer') ? 'answer' : 'stem'
    const doc = parseXmlDocument(text, rootTag)

    if (!doc) {
      container.textContent = text
      applyKatex(container)
      return
    }

    const paragraphs = Array.from(doc.documentElement.children)
    if (paragraphs.length === 0) {
      container.textContent = doc.documentElement.textContent || ''
      applyKatex(container)
      return
    }

    for (const paragraph of paragraphs) {
      const el = document.createElement('p')
      el.className = 'latex-p'
      el.textContent = paragraph.textContent || ''
      container.appendChild(el)
    }

    applyKatex(container)
  }

  return { render }
}