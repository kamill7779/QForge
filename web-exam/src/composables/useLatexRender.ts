/**
 * composables/useLatexRender.ts — KaTeX rendering composable.
 */

import { parseXmlDocument, textOfNode, toXmlPayload } from '@/lib/stemXml'
import type { RootTag } from '@/lib/stemXml'

const KATEX_DELIMITERS = [
  { left: '$$', right: '$$', display: true },
  { left: '$', right: '$', display: false },
  { left: '\\(', right: '\\)', display: false },
  { left: '\\[', right: '\\]', display: true }
]

export interface RenderOptions {
  imageResolver?: (ref: string) => string
  mode?: RootTag
}

function imageDataUrl(base64: string): string {
  if (!base64) return ''
  if (base64.startsWith('data:')) return base64
  if (base64.startsWith('/9j/')) return `data:image/jpeg;base64,${base64}`
  if (base64.startsWith('iVBOR')) return `data:image/png;base64,${base64}`
  return `data:image/png;base64,${base64}`
}

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

function processNode(xmlNode: Node, parentEl: HTMLElement, resolver: (ref: string) => string): void {
  if (xmlNode.nodeType === Node.TEXT_NODE) {
    parentEl.appendChild(document.createTextNode(xmlNode.nodeValue || ''))
    return
  }
  if (xmlNode.nodeType !== Node.ELEMENT_NODE) return
  const tag = (xmlNode as Element).tagName?.toLowerCase()

  switch (tag) {
    case 'stem':
    case 'answer':
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, parentEl, resolver)
      break
    case 'p': {
      const p = document.createElement('p')
      p.className = 'stem-p'
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, p, resolver)
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
        img.loading = 'lazy'
        parentEl.appendChild(img)
      } else {
        const span = document.createElement('span')
        span.className = 'image-placeholder'
        span.title = `图片: ${ref}`
        span.textContent = `[图 ${ref}]`
        parentEl.appendChild(span)
      }
      break
    }
    case 'choices': {
      const mode = (xmlNode as Element).getAttribute('mode') || 'single'
      const container = document.createElement('div')
      container.className = `choices-container ${mode}`
      for (const ch of Array.from((xmlNode as Element).children)) {
        if (ch.tagName?.toLowerCase() === 'choice') processChoiceNode(ch, container, resolver)
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
          span.textContent = '____'
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
      parentEl.appendChild(area)
      break
    }
    case 'table': {
      const table = document.createElement('table')
      table.className = 'stem-table'
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, table, resolver)
      parentEl.appendChild(table)
      break
    }
    case 'thead': {
      const thead = document.createElement('thead')
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, thead, resolver)
      parentEl.appendChild(thead)
      break
    }
    case 'tbody': {
      const tbody = document.createElement('tbody')
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, tbody, resolver)
      parentEl.appendChild(tbody)
      break
    }
    case 'tr': {
      const tr = document.createElement('tr')
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, tr, resolver)
      parentEl.appendChild(tr)
      break
    }
    case 'th': {
      const th = document.createElement('th')
      th.className = 'stem-th'
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, th, resolver)
      parentEl.appendChild(th)
      break
    }
    case 'td': {
      const td = document.createElement('td')
      td.className = 'stem-td'
      for (const child of Array.from(xmlNode.childNodes)) processNode(child, td, resolver)
      parentEl.appendChild(td)
      break
    }
    default:
      parentEl.appendChild(document.createTextNode((xmlNode as Element).textContent || ''))
  }
}

function processChoiceNode(choiceEl: Element, parentEl: HTMLElement, resolver: (ref: string) => string): void {
  const key = choiceEl.getAttribute('key') || ''
  const item = document.createElement('div')
  item.className = 'choice-item'
  const keySpan = document.createElement('span')
  keySpan.className = 'choice-key'
  keySpan.textContent = `${key}.`
  item.appendChild(keySpan)
  const content = document.createElement('div')
  content.className = 'choice-content'
  for (const child of Array.from(choiceEl.childNodes)) processNode(child, content, resolver)
  item.appendChild(content)
  parentEl.appendChild(item)
}

function stripXmlTags(text: string): string {
  return text
    .replace(/<\/?(?:stem|answer|p|choices|choice|image|blanks|blank|answer-area|table|thead|tbody|tr|th|td)[^>]*>/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

export function useLatexRender() {
  function render(container: HTMLElement, xml: string, options?: RenderOptions): void {
    container.innerHTML = ''
    const text = String(xml || '').trim()
    if (!text) return

    const resolver = options?.imageResolver ?? (() => '')
    let mode: RootTag = options?.mode ?? 'stem'
    if (text.startsWith('<answer')) mode = 'answer'

    if (!text.startsWith('<stem') && !text.startsWith('<answer')) {
      container.textContent = text
      applyKatex(container)
      return
    }

    let doc = parseXmlDocument(text, mode)
    if (!doc) {
      const sanitized = toXmlPayload(text, mode)
      doc = parseXmlDocument(sanitized, mode)
    }

    if (doc) {
      const firstP = doc.documentElement.getElementsByTagName('p')[0]
      if (firstP) {
        const pText = (firstP.textContent || '').trim()
        if (pText.startsWith('<stem') || pText.startsWith('<answer')) {
          let innerDoc = parseXmlDocument(pText, mode)
          if (!innerDoc) {
            const innerSanitized = toXmlPayload(pText, mode)
            innerDoc = parseXmlDocument(innerSanitized, mode)
          }
          if (innerDoc) doc = innerDoc
        }
      }
    }

    if (!doc) {
      container.textContent = stripXmlTags(text)
      applyKatex(container)
      return
    }

    processNode(doc.documentElement, container, resolver)
    applyKatex(container)
  }

  return { render }
}
