const test = require("node:test");
const assert = require("node:assert/strict");

const { toStemXmlPayload, toEditorStemText } = require("../src/stem-xml.js");

test("wraps plain text into valid stem XML", () => {
  const xml = toStemXmlPayload("这是纯文本题干");
  assert.equal(xml, "<stem version=\"1\"><p>这是纯文本题干</p></stem>");
});

test("escapes XML-sensitive characters", () => {
  const xml = toStemXmlPayload("x < 1 & y > 2");
  assert.equal(xml, "<stem version=\"1\"><p>x &lt; 1 &amp; y &gt; 2</p></stem>");
});

test("preserves valid stem XML input", () => {
  const source = "<stem version=\"1\"><p>已是XML</p></stem>";
  const xml = toStemXmlPayload(source);
  assert.equal(xml, source);
});

test("falls back to wrapped text when malformed XML is provided", () => {
  const malformed = "<stem><p>oops</stem>";
  const xml = toStemXmlPayload(malformed);
  assert.equal(xml, "<stem version=\"1\"><p>&lt;stem&gt;&lt;p&gt;oops&lt;/stem&gt;</p></stem>");
});

test("converts valid stem XML into editor-friendly plain text", () => {
  const xml = "<stem version=\"1\"><p>第一行</p><p>第二行</p></stem>";
  assert.equal(toEditorStemText(xml), "第一行\n第二行");
});
