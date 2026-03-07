/**
 * composables/useStemEditor.ts — Block-based structured editor logic.
 *
 * Eliminates 3× duplication of collectBlocksFromEditor / renderStemEditor.
 * Used by StemEditor.vue and AnswerEditor.vue.
 */

import { ref, watch, type Ref } from 'vue'
import { parseXmlDocument, escapeXml, textOfNode } from '@/lib/stemXml'
import type { RootTag } from '@/lib/stemXml'

// ──────────────────── Types ────────────────────

export interface ChoiceItem {
  key: string
  text: string
  imageRef: string
}

export type Block =
  | { type: 'p'; text: string }
  | { type: 'choices'; mode: 'single' | 'multi'; items: ChoiceItem[] }
  | { type: 'image'; ref: string }
  | { type: 'blanks'; items: Array<{ id: string }> }
  | { type: 'answer-area'; lines: number }

export type BlockType = Block['type']

// Default blocks when creating new block types
const DEFAULT_CHOICE_ITEMS: ChoiceItem[] = [
  { key: 'A', text: '', imageRef: '' },
  { key: 'B', text: '', imageRef: '' },
  { key: 'C', text: '', imageRef: '' },
  { key: 'D', text: '', imageRef: '' }
]

// ──────────────────── XML → Block[] ────────────────────

/**
 * Parse an XML string into Block array.
 * Works for both <stem> and <answer> root tags.
 */
export function xmlToBlocks(xmlStr: string, rootTag: RootTag = 'stem'): Block[] {
  const raw = String(xmlStr || '').trim()
  if (!raw) return [{ type: 'p', text: '' }]

  const doc = parseXmlDocument(raw, rootTag)
  if (!doc) return [{ type: 'p', text: raw }]

  const blocks: Block[] = []
  const root = doc.documentElement

  for (const node of Array.from(root.childNodes)) {
    if (node.nodeType !== 1) continue // skip text nodes at root level
    const tag = (node as Element).tagName?.toLowerCase()

    if (tag === 'p') {
      blocks.push({ type: 'p', text: textOfNode(node as Element) })
    } else if (tag === 'image') {
      const defaultRef = rootTag === 'answer' ? 'img-1' : 'stem-local'
      blocks.push({
        type: 'image',
        ref: (node as Element).getAttribute('ref') || defaultRef
      })
    } else if (tag === 'choices') {
      const mode =
        ((node as Element).getAttribute('mode') as 'single' | 'multi') || 'single'
      const items: ChoiceItem[] = []
      for (const ch of Array.from((node as Element).children)) {
        if (ch.tagName?.toLowerCase() === 'choice') {
          const key = ch.getAttribute('key') || ''
          const firstImage = Array.from(ch.children).find(
            (c) => c.tagName?.toLowerCase() === 'image'
          )
          // Collect text from non-image children
          let text = ''
          for (const child of Array.from(ch.childNodes)) {
            if (child.nodeType === 1) {
              const childTag = (child as Element).tagName?.toLowerCase()
              if (childTag === 'p') {
                text += textOfNode(child as Element)
              } else if (childTag !== 'image') {
                text += (child as Element).textContent || ''
              }
            } else if (child.nodeType === 3) {
              text += child.nodeValue || ''
            }
          }
          items.push({
            key,
            text: text.trim(),
            imageRef: firstImage?.getAttribute('ref') || ''
          })
        }
      }
      blocks.push({ type: 'choices', mode, items })
    } else if (tag === 'blanks') {
      const items: Array<{ id: string }> = []
      for (const bl of Array.from((node as Element).children)) {
        if (bl.tagName?.toLowerCase() === 'blank') {
          items.push({ id: bl.getAttribute('id') || String(items.length + 1) })
        }
      }
      blocks.push({ type: 'blanks', items })
    } else if (tag === 'answer-area') {
      blocks.push({
        type: 'answer-area',
        lines: Number((node as Element).getAttribute('lines') || 4)
      })
    }
  }

  if (!blocks.length) blocks.push({ type: 'p', text: '' })
  return blocks
}

// ──────────────────── Block[] → XML ────────────────────

function serializePBlock(b: { type: 'p'; text: string }): string {
  return `<p>${escapeXml(b.text)}</p>`
}

/**
 * Convert Block array into XML string.
 */
export function blocksToXml(blocks: Block[], rootTag: RootTag = 'stem'): string {
  let x = `<${rootTag} version="1">`
  for (const b of blocks) {
    switch (b.type) {
      case 'p':
        x += serializePBlock(b)
        break
      case 'image':
        x += `<image ref="${escapeXml(b.ref || (rootTag === 'answer' ? 'img-1' : 'stem-local'))}" />`
        break
      case 'choices':
        x += `<choices mode="${escapeXml(b.mode || 'single')}">`
        for (const it of b.items) {
          x += `<choice key="${escapeXml(it.key)}">`
          if (it.imageRef) x += `<image ref="${escapeXml(it.imageRef)}" />`
          if (it.text) x += `<p>${escapeXml(it.text)}</p>`
          x += '</choice>'
        }
        x += '</choices>'
        break
      case 'blanks':
        x += '<blanks>'
        for (const it of b.items) x += `<blank id="${escapeXml(String(it.id))}" />`
        x += '</blanks>'
        break
      case 'answer-area':
        x += `<answer-area lines="${Number(b.lines) || 4}" />`
        break
    }
  }
  x += `</${rootTag}>`
  return x
}

// ──────────────────── Composable ────────────────────

export interface UseStemEditorOptions {
  /** Initial XML to parse into blocks. */
  initialXml?: string
  /** Root tag: 'stem' or 'answer'. Determines supported block types. */
  rootTag?: RootTag
  /** Called whenever blocks change and XML is regenerated. */
  onChange?: (xml: string) => void
}

/**
 * Reactive block-based editor composable.
 *
 * Provides a reactive `blocks` array with all mutation methods,
 * and automatically triggers `onChange` with the serialized XML.
 */
export function useStemEditor(options: UseStemEditorOptions = {}) {
  const rootTag = options.rootTag ?? 'stem'
  const blocks = ref<Block[]>([{ type: 'p', text: '' }]) as Ref<Block[]>
  let suppressWatch = false

  // Block types allowed for this root tag
  const allowedTypes: BlockType[] =
    rootTag === 'answer'
      ? ['p', 'image']
      : ['p', 'choices', 'image', 'blanks', 'answer-area']

  /** Initialize blocks from XML string. */
  function initFromXml(xml: string): void {
    suppressWatch = true
    blocks.value = xmlToBlocks(xml, rootTag)
    suppressWatch = false
  }

  /** Serialize current blocks to XML. */
  function toXml(): string {
    return blocksToXml(blocks.value, rootTag)
  }

  /** Add a new block of given type at the end or after a specific index. */
  function addBlock(type: BlockType, afterIndex?: number): void {
    if (!allowedTypes.includes(type)) return

    let newBlock: Block
    switch (type) {
      case 'p':
        newBlock = { type: 'p', text: '' }
        break
      case 'choices':
        newBlock = {
          type: 'choices',
          mode: 'single',
          items: DEFAULT_CHOICE_ITEMS.map((it) => ({ ...it }))
        }
        break
      case 'image':
        newBlock = {
          type: 'image',
          ref: rootTag === 'answer' ? 'img-1' : 'stem-local'
        }
        break
      case 'blanks':
        newBlock = { type: 'blanks', items: [{ id: '1' }, { id: '2' }] }
        break
      case 'answer-area':
        newBlock = { type: 'answer-area', lines: 4 }
        break
      default:
        return
    }

    const idx = afterIndex !== undefined ? afterIndex + 1 : blocks.value.length
    blocks.value.splice(idx, 0, newBlock)
  }

  /** Remove a block by index. */
  function removeBlock(index: number): void {
    if (blocks.value.length <= 1) return // Keep at least one block
    blocks.value.splice(index, 1)
  }

  /** Move a block from one index to another. */
  function moveBlock(from: number, to: number): void {
    if (to < 0 || to >= blocks.value.length) return
    const [item] = blocks.value.splice(from, 1)
    blocks.value.splice(to, 0, item)
  }

  /** Update a specific block's data. */
  function updateBlock(index: number, data: Partial<Block>): void {
    if (index < 0 || index >= blocks.value.length) return
    Object.assign(blocks.value[index], data)
  }

  /** Add a choice item to a choices block. */
  function addChoiceItem(blockIndex: number): void {
    const block = blocks.value[blockIndex]
    if (block?.type !== 'choices') return
    const nextKey = String.fromCharCode(65 + block.items.length)
    block.items.push({ key: nextKey, text: '', imageRef: '' })
  }

  /** Remove a choice item and re-key remaining items. */
  function removeChoiceItem(blockIndex: number, itemIndex: number): void {
    const block = blocks.value[blockIndex]
    if (block?.type !== 'choices') return
    if (block.items.length <= 2) return // Keep at least 2 choices
    block.items.splice(itemIndex, 1)
    // Re-key A, B, C, D...
    block.items.forEach((item, i) => {
      item.key = String.fromCharCode(65 + i)
    })
  }

  /** Add a blank item to a blanks block. */
  function addBlankItem(blockIndex: number): void {
    const block = blocks.value[blockIndex]
    if (block?.type !== 'blanks') return
    block.items.push({ id: String(block.items.length + 1) })
  }

  // Emit XML when blocks change
  watch(
    blocks,
    () => {
      if (!suppressWatch) {
        options.onChange?.(toXml())
      }
    },
    { deep: true }
  )

  // Initialize
  if (options.initialXml) {
    initFromXml(options.initialXml)
  }

  return {
    blocks,
    allowedTypes,
    initFromXml,
    toXml,
    addBlock,
    removeBlock,
    moveBlock,
    updateBlock,
    addChoiceItem,
    removeChoiceItem,
    addBlankItem
  }
}
