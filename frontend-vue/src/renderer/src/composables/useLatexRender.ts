/**
 * composables/useLatexRender.ts — KaTeX rendering composable.
 *
 * Parses XML (stem/answer) into DOM and applies KaTeX math rendering.
 * Eliminates 6× duplication of renderLatexNode / renderAnswerLatex.
 */

import { parseXmlDocument, textOfNode, toXmlPayload } from '@/lib/stemXml'
import type { RootTag } from '@/lib/stemXml'

// KaTeX auto-render delimiters
const KATEX_DELIMITERS = [
  { left: '$$', right: '$$', display: true },
  { left: '$', right: '$', display: false },
  { left: '\\(', right: '\\)', display: false },
  { left: '\\[', right: '\\]', display: true }
]

export interface RenderOptions {
  /** Function to resolve image ref → base64 data URL. */
  imageResolver?: (ref: string) => string
  /** Root tag for XML parsing ('stem' or 'answer'). Auto-detected if omitted. */
  mode?: RootTag
}

/**
 * Convert image base64 to data URL.
 */
function imageDataUrl(base64: string): string {
  if (!base64) return ''
  if (base64.startsWith('data:')) return base64
  // Try to detect MIME from first bytes
  if (base64.startsWith('/9j/')) return `data:image/jpeg;base64,${base64}`
  if (base64.startsWith('iVBOR')) return `data:image/png;base64,${base64}`
  return `data:image/png;base64,${base64}`
}

/**
 * Invoke KaTeX renderMathInElement on a container.
 * Safe to call even if KaTeX is not loaded.
 */
function applyKatex(el: HTMLElement): void {
  const renderMathInElement = (window as any).renderMathInElement
  if (typeof renderMathInElement === 'function') {
    renderMathInElement(el, {
      delimiters: KATEX_DELIMITERS,
      throwOnError: false,
      strict: 'ignore'
    })
  }
}

/**
 * Recursively process an XML node and append rendered DOM to parent.
 */
function processNode(
  xmlNode: Node,
  parentEl: HTMLElement,
  resolver: (ref: string) => string
): void {
  if (xmlNode.nodeType === Node.TEXT_NODE) {
    parentEl.appendChild(document.createTextNode(xmlNode.nodeValue || ''))
    return
  }

  if (xmlNode.nodeType !== Node.ELEMENT_NODE) return
  const tag = (xmlNode as Element).tagName?.toLowerCase()

  switch (tag) {
    case 'stem':
    case 'answer': {
      for (const child of Array.from(xmlNode.childNodes)) {
        processNode(child, parentEl, resolver)
      }
      break
    }

    case 'p': {
      const p = document.createElement('p')
      p.className = 'stem-p'
      // Recursively process children so inline images / nested elements are preserved
      for (const child of Array.from(xmlNode.childNodes)) {
        processNode(child, p, resolver)
      }
      parentEl.appendChild(p)
      break
    }

    case 'image': {
      const ref = (xmlNode as Element).getAttribute('ref') || ''
      const src = resolver(ref)
      if (src) {
        const img = document.createElement('img')
        img.className = 'stem-image'
        img.src = imageDataUrl(src)
        img.alt = ref
        parentEl.appendChild(img)
      } else {
        const span = document.createElement('span')
        span.className = 'empty-note'
        span.textContent = `⚠ 图片未识别: ${ref}`
        parentEl.appendChild(span)
      }
      break
    }

    case 'choices': {
      const mode = (xmlNode as Element).getAttribute('mode') || 'single'
      const container = document.createElement('div')
      container.className = `choices-container ${mode}`
      for (const ch of Array.from((xmlNode as Element).children)) {
        if (ch.tagName?.toLowerCase() === 'choice') {
          processChoiceNode(ch, container, resolver)
        }
      }
      parentEl.appendChild(container)
      break
    }

    case 'blanks': {
      const container = document.createElement('div')
      container.className = 'blanks-container'
      let idx = 1
      for (const bl of Array.from((xmlNode as Element).children)) {
        if (bl.tagName?.toLowerCase() === 'blank') {
          const span = document.createElement('span')
          span.className = 'blank-item'
          const id = bl.getAttribute('id') || String(idx)
          span.title = `blank-${id}`
          span.textContent = `____`
          container.appendChild(span)
          idx++
        }
      }
      parentEl.appendChild(container)
      break
    }

    case 'answer-area': {
      const lines = Number((xmlNode as Element).getAttribute('lines') || 4)
      const area = document.createElement('div')
      area.className = 'answer-area'
      const label = document.createElement('div')
      label.className = 'answer-area-label'
      label.textContent = '解答区'
      area.appendChild(label)
      const linesContainer = document.createElement('div')
      linesContainer.className = 'answer-area-lines'
      for (let i = 0; i < lines; i++) {
        const line = document.createElement('div')
        line.className = 'answer-area-line'
        linesContainer.appendChild(line)
      }
      area.appendChild(linesContainer)
      parentEl.appendChild(area)
      break
    }

    default: {
      // Unknown tag — render as text
      parentEl.appendChild(
        document.createTextNode((xmlNode as Element).textContent || '')
      )
    }
  }
}

function processChoiceNode(
  choiceEl: Element,
  parentEl: HTMLElement,
  resolver: (ref: string) => string
): void {
  const key = choiceEl.getAttribute('key') || ''
  const item = document.createElement('div')
  item.className = 'choice-item'

  const keySpan = document.createElement('span')
  keySpan.className = 'choice-key'
  keySpan.textContent = `${key}.`
  item.appendChild(keySpan)

  const content = document.createElement('div')
  content.className = 'choice-content'

  for (const child of Array.from(choiceEl.childNodes)) {
    processNode(child, content, resolver)
  }

  item.appendChild(content)
  parentEl.appendChild(item)
}

/**
 * Strip known XML wrapper tags so raw fallback doesn't show <answer>/<stem>/<p> etc.
 */
function stripXmlTags(text: string): string {
  return text
    .replace(/<\/?(?:stem|answer|p|choices|choice|image|blanks|blank|answer-area)[^>]*>/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

/**
 * Composable for rendering XML + LaTeX into DOM.
 */
export function useLatexRender() {
  /**
   * Render XML (stem or answer) into a container element.
   * Applies KaTeX math rendering after DOM construction.
   */
  function render(
    container: HTMLElement,
    xml: string,
    options?: RenderOptions
  ): void {
    container.innerHTML = ''
    const text = String(xml || '').trim()

    if (!text) return

    const resolver = options?.imageResolver ?? (() => '')

    // Auto-detect mode
    let mode: RootTag = options?.mode ?? 'stem'
    if (text.startsWith('<answer')) mode = 'answer'

    // Non-XML text: render as plain text with KaTeX
    if (!text.startsWith('<stem') && !text.startsWith('<answer')) {
      container.textContent = text
      applyKatex(container)
      return
    }

    // Parse XML (try direct parse, then sanitized via toXmlPayload)
    let doc = parseXmlDocument(text, mode)
    if (!doc) {
      // toXmlPayload sanitizes LaTeX < > & inside math delimiters and retries
      const sanitized = toXmlPayload(text, mode)
      doc = parseXmlDocument(sanitized, mode)
    }

    // Detect double-encoded XML: if the first <p>'s text starts with <stem or <answer,
    // decode entities and re-parse from the inner text.
    if (doc) {
      const firstP = doc.documentElement.getElementsByTagName('p')[0]
      if (firstP) {
        const pText = (firstP.textContent || '').trim()
        if (pText.startsWith('<stem') || pText.startsWith('<answer')) {
          // The inner text is the original XML — try parsing it
          let innerDoc = parseXmlDocument(pText, mode)
          if (!innerDoc) {
            const innerSanitized = toXmlPayload(pText, mode)
            innerDoc = parseXmlDocument(innerSanitized, mode)
          }
          if (innerDoc) {
            doc = innerDoc
          }
        }
      }
    }

    if (!doc) {
      // XML parse failed even after sanitization. Strip tags and render as text + KaTeX.
      container.textContent = stripXmlTags(text)
      applyKatex(container)
      return
    }

    // Recursively render XML → DOM
    processNode(doc.documentElement, container, resolver)

    // Apply KaTeX to the entire container
    applyKatex(container)
  }

  return { render }
}
