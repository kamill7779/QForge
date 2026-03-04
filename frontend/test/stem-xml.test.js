const test = require("node:test");
const assert = require("node:assert/strict");

const {
  toStemXmlPayload,
  toEditorStemText,
  toAnswerXmlPayload,
  toEditorAnswerText
} = require("../src/stem-xml.js");

test("wraps plain text into valid stem XML", () => {
  const xml = toStemXmlPayload("this is plain stem text");
  assert.equal(xml, '<stem version="1"><p>this is plain stem text</p></stem>');
});

test("escapes XML-sensitive characters for stem text", () => {
  const xml = toStemXmlPayload("x < 1 & y > 2");
  assert.equal(xml, '<stem version="1"><p>x &lt; 1 &amp; y &gt; 2</p></stem>');
});

test("preserves valid stem XML input", () => {
  const source = '<stem version="1"><p>already xml</p></stem>';
  const xml = toStemXmlPayload(source);
  assert.equal(xml, source);
});

test("falls back to wrapped text when malformed stem XML is provided", () => {
  const malformed = "<stem><p>oops</stem>";
  const xml = toStemXmlPayload(malformed);
  assert.equal(xml, '<stem version="1"><p>&lt;stem&gt;&lt;p&gt;oops&lt;/stem&gt;</p></stem>');
});

test("converts valid stem XML into editor-friendly plain text", () => {
  const xml = '<stem version="1"><p>line one</p><p>line two</p></stem>';
  assert.equal(toEditorStemText(xml), "line one\nline two");
});

test("keeps bbox markdown as plain answer paragraph (backend should pre-convert)", () => {
  const answerText = [
    "first paragraph",
    "![](page=0,bbox=[346, 93, 496, 233])",
    "second paragraph"
  ].join("\n");

  const xml = toAnswerXmlPayload(answerText);
  assert.equal(
    xml,
    '<answer version="1"><p>first paragraph</p><p>![](page=0,bbox=[346, 93, 496, 233])</p><p>second paragraph</p></answer>'
  );
});

test("renders answer XML back to editor text", () => {
  const xml = '<answer version="1"><p>line one</p><image ref="fig-1" /><p>line two</p></answer>';
  assert.equal(toEditorAnswerText(xml), "line one\n[image]line two");
});
