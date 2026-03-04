const test = require("node:test");
const assert = require("node:assert/strict");

const { answerTaskSeed, buildAnswerTaskRef, createImageRuntime } = require("../src/runtime/image-runtime.js");

test("answerTaskSeed normalizes uuid into 8-char lowercase seed", () => {
  assert.equal(answerTaskSeed("A92F6C03-3742-48ca-bf50-2de1678b4118"), "a92f6c03");
});

test("buildAnswerTaskRef builds answer-prefixed ref", () => {
  assert.equal(buildAnswerTaskRef("a92f6c03-3742-48ca-bf50-2de1678b4118", 2), "aa92f6c03-img-2");
});

test("resolveAnswerImage maps fig-N to answer task ref", () => {
  const runtime = createImageRuntime({ entries: new Map() });
  const entry = {
    lastAnswerOcrTaskUuid: "a92f6c03-3742-48ca-bf50-2de1678b4118",
    answerImages: {
      "aa92f6c03-img-1": "BASE64_1"
    },
    inlineImages: {}
  };

  assert.equal(runtime.resolveAnswerImage(entry, "fig-1"), "BASE64_1");
});

test("generateAnswerImageRef auto-increments by seed", () => {
  const runtime = createImageRuntime({ entries: new Map() });
  const entry = {
    questionUuid: "a92f6c03-3742-48ca-bf50-2de1678b4118",
    answerImages: {
      "aa92f6c03-img-1": "X"
    }
  };

  assert.equal(runtime.generateAnswerImageRef(entry, null), "fig-2");
});
