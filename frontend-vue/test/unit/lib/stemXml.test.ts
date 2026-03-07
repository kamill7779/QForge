import { describe, it, expect } from 'vitest'
import {
  toStemXmlPayload,
  toEditorStemText,
  toAnswerXmlPayload,
  toEditorAnswerText,
  escapeXml,
  escapeXmlPreserveEntities,
  decodeXml,
  isValidStemXml,
  isValidAnswerXml,
  toXmlPayload,
  toEditorText
} from '@/lib/stemXml'

describe('stemXml — escaping', () => {
  it('escapeXml escapes all XML-special characters', () => {
    expect(escapeXml('a & b < c > d " e \' f')).toBe(
      'a &amp; b &lt; c &gt; d &quot; e &apos; f'
    )
  })

  it('escapeXmlPreserveEntities keeps existing entities intact', () => {
    expect(escapeXmlPreserveEntities('x &amp; y & z < w')).toBe('x &amp; y &amp; z &lt; w')
  })

  it('decodeXml restores common entities', () => {
    expect(decodeXml('&amp; &lt; &gt; &quot; &apos;')).toBe('& < > " \'')
  })
})

describe('stemXml — stem functions', () => {
  it('wraps plain text into valid stem XML', () => {
    const xml = toStemXmlPayload('this is plain stem text')
    expect(xml).toBe('<stem version="1"><p>this is plain stem text</p></stem>')
  })

  it('escapes XML-sensitive characters for stem text', () => {
    const xml = toStemXmlPayload('x < 1 & y > 2')
    expect(xml).toBe('<stem version="1"><p>x &lt; 1 &amp; y &gt; 2</p></stem>')
  })

  it('preserves valid stem XML input', () => {
    const source = '<stem version="1"><p>already xml</p></stem>'
    expect(toStemXmlPayload(source)).toBe(source)
  })

  it('falls back to wrapped text when malformed stem XML is provided', () => {
    const malformed = '<stem><p>oops</stem>'
    const xml = toStemXmlPayload(malformed)
    expect(xml).toBe(
      '<stem version="1"><p>&lt;stem&gt;&lt;p&gt;oops&lt;/stem&gt;</p></stem>'
    )
  })

  it('converts valid stem XML into editor-friendly plain text', () => {
    const xml = '<stem version="1"><p>line one</p><p>line two</p></stem>'
    expect(toEditorStemText(xml)).toBe('line one\nline two')
  })

  it('returns empty string for empty input', () => {
    expect(toEditorStemText('')).toBe('')
    expect(toEditorStemText('  ')).toBe('')
  })

  it('returns empty XML for empty input', () => {
    expect(toStemXmlPayload('')).toBe('<stem version="1"><p></p></stem>')
  })
})

describe('stemXml — answer functions', () => {
  it('keeps bbox markdown as plain answer paragraph', () => {
    const answerText = [
      'first paragraph',
      '![](page=0,bbox=[346, 93, 496, 233])',
      'second paragraph'
    ].join('\n')

    const xml = toAnswerXmlPayload(answerText)
    expect(xml).toBe(
      '<answer version="1"><p>first paragraph</p><p>![](page=0,bbox=[346, 93, 496, 233])</p><p>second paragraph</p></answer>'
    )
  })

  it('validates answer XML', () => {
    expect(isValidAnswerXml('<answer version="1"><p>ok</p></answer>')).toBe(true)
    expect(isValidAnswerXml('<stem version="1"><p>wrong root</p></stem>')).toBe(false)
    expect(isValidAnswerXml('plain text')).toBe(false)
  })
})

describe('stemXml — parameterized core', () => {
  it('toXmlPayload works for both stem and answer', () => {
    const stemXml = toXmlPayload('hello', 'stem')
    const answerXml = toXmlPayload('hello', 'answer')
    expect(stemXml).toBe('<stem version="1"><p>hello</p></stem>')
    expect(answerXml).toBe('<answer version="1"><p>hello</p></answer>')
  })

  it('toEditorText works for both root tags', () => {
    expect(toEditorText('<stem version="1"><p>a</p><p>b</p></stem>', 'stem')).toBe('a\nb')
    expect(toEditorText('<answer version="1"><p>x</p></answer>', 'answer')).toBe('x')
  })

  it('isValidStemXml rejects answer XML and vice versa', () => {
    expect(isValidStemXml('<answer version="1"><p>x</p></answer>')).toBe(false)
    expect(isValidAnswerXml('<stem version="1"><p>x</p></stem>')).toBe(false)
  })

  it('toXmlPayload sanitizes LaTeX with bare < instead of double-encoding', () => {
    // OCR often produces XML with unescaped < in LaTeX math: $A < B$
    const ocrXml = '<stem version="1"><p>given $A < B$ solve</p></stem>'
    const result = toStemXmlPayload(ocrXml)
    // Must NOT double-wrap — should contain sanitized content
    expect(result).not.toContain('&lt;stem')
    expect(isValidStemXml(result)).toBe(true)
    // The < inside LaTeX should be escaped for XML validity
    expect(result).toContain('&lt;')
    expect(result).toContain('solve')
  })

  it('toXmlPayload sanitizes LaTeX with bare & inside math', () => {
    const ocrXml = '<stem version="1"><p>$x & y$</p></stem>'
    const result = toStemXmlPayload(ocrXml)
    expect(result).not.toContain('&lt;stem')
    expect(isValidStemXml(result)).toBe(true)
  })
})
