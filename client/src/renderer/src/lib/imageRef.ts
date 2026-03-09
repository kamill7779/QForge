/**
 * imageRef.ts — Merged image reference utilities.
 *
 * Combines the previously duplicated functions from image-runtime.js and answer-runtime.js:
 * - answerTaskSeed / answerRefSeed → refSeed
 * - buildAnswerTaskRef / buildScopedAnswerRef → buildScopedRef
 * - parseRefIndex / refIndex → parseRefIndex
 * Plus answer submission helpers.
 */

// ──────────────────── Ref Utilities ────────────────────

/** Extract an 8-char lowercase hex seed from a UUID (strips dashes, takes first 8). */
export function refSeed(uuid: string): string {
  return uuid.replace(/-/g, '').slice(0, 8).toLowerCase()
}

/**
 * Build a scoped answer image ref: `a${seed}-img-${index}`.
 * The "a" prefix distinguishes answer-scoped refs from stem refs.
 */
export function buildScopedRef(source: string, index: number): string {
  return `a${refSeed(source)}-img-${index}`
}

/**
 * Extract the trailing numeric index from a ref string.
 * Works with: `fig-2`, `img-3`, `a12345678-img-1`, `img-4`.
 * Returns 0 if no numeric suffix is found.
 */
export function parseRefIndex(ref: string): number {
  const m = ref.match(/(\d+)$/)
  return m ? parseInt(m[1], 10) : 0
}

/**
 * Given existing refs, compute the next `fig-N` ref.
 * Scans ALL ref styles (fig-N, img-N, aXXX-img-N) to avoid index collision.
 */
export function nextFigureRef(existingRefs: string[]): string {
  let max = 0
  for (const r of existingRefs) {
    const idx = parseRefIndex(r)
    if (idx > max) max = idx
  }
  return `fig-${max + 1}`
}

/**
 * Convert a local ref (fig-N) to a scoped ref for submission.
 * If the ref is already scoped (contains `-img-`), return as-is.
 */
export function toScopedRef(seedSource: string, ref: string): string {
  if (ref.includes('-img-')) return ref
  const idx = parseRefIndex(ref)
  return buildScopedRef(seedSource, idx || 1)
}

// ──────────────────── Answer Submission ────────────────────

export interface InlineImage {
  mimeType: string
  imageData: string
}

export interface ImageEntry {
  questionUuid: string
  answerImages?: Record<string, string>
  inlineImages?: Record<string, InlineImage>
}

export interface Block {
  type: string
  ref?: string
  text?: string
  [key: string]: unknown
}

function buildInlineImage(base64: string): InlineImage {
  return { mimeType: 'image/png', imageData: base64 }
}

function pickImageBase64(
  entry: ImageEntry,
  originalRef: string,
  targetRef: string,
  resolveAnswerImage: (ref: string) => string
): string {
  if (entry.answerImages?.[originalRef]) return entry.answerImages[originalRef]
  if (entry.answerImages?.[targetRef]) return entry.answerImages[targetRef]
  if (entry.inlineImages?.[originalRef]?.imageData) return entry.inlineImages[originalRef].imageData
  if (entry.inlineImages?.[targetRef]?.imageData) return entry.inlineImages[targetRef].imageData
  return resolveAnswerImage(originalRef)
}

export interface PrepareAnswerSubmitParams {
  entry: ImageEntry
  blocks: Block[]
  answerUuid: string | null
  resolveAnswerImage: (ref: string) => string
}

export interface AnswerSubmitData {
  blocks: Block[]
  inlineImages: Record<string, InlineImage>
}

/**
 * Prepare answer blocks and inline images for submission.
 *
 * - Converts local refs (fig-N) to scoped refs (aXXX-img-N)
 * - Already-scoped refs are kept unchanged
 * - Resolves image base64 data from multiple sources (answerImages → inlineImages → resolver)
 */
export function prepareAnswerSubmitData(params: PrepareAnswerSubmitParams): AnswerSubmitData {
  const { entry, blocks, answerUuid, resolveAnswerImage } = params
  const seedSource = answerUuid || entry.questionUuid

  const outBlocks: Block[] = []
  const outImages: Record<string, InlineImage> = {}

  for (const block of blocks) {
    if (block.type === 'image' && block.ref) {
      const originalRef = block.ref
      const targetRef = toScopedRef(seedSource, originalRef)
      const base64 = pickImageBase64(entry, originalRef, targetRef, resolveAnswerImage)

      outBlocks.push({ ...block, ref: targetRef })
      if (base64) outImages[targetRef] = buildInlineImage(base64)
    } else {
      outBlocks.push({ ...block })
    }
  }

  return { blocks: outBlocks, inlineImages: outImages }
}
