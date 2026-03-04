const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildScopedAnswerRef,
  nextFigureRef,
  prepareAnswerSubmitData
} = require("../src/runtime/answer-runtime.js");

test("nextFigureRef uses fig-N style and avoids existing scoped suffix indexes", () => {
  const next = nextFigureRef(["fig-1", "aa92f6c03-img-2", "img-4", "random"]);
  assert.equal(next, "fig-5");
});

test("buildScopedAnswerRef converts seed source into stable answer scoped ref", () => {
  const ref = buildScopedAnswerRef("A92F6C03-3742-48ca-bf50-2de1678b4118", 3);
  assert.equal(ref, "aa92f6c03-img-3");
});

test("prepareAnswerSubmitData converts fig refs for submit and keeps image payload aligned", () => {
  const entry = {
    questionUuid: "b2345f67-3742-48ca-bf50-2de1678b4118",
    answerImages: {
      "fig-1": "BASE64_MANUAL"
    },
    inlineImages: {}
  };

  const blocks = [
    { type: "p", text: "answer text" },
    { type: "image", ref: "fig-1" }
  ];

  const out = prepareAnswerSubmitData({
    entry,
    blocks,
    answerUuid: null,
    resolveAnswerImage: () => ""
  });

  assert.equal(out.blocks[1].ref, "ab2345f67-img-1");
  assert.equal(out.inlineImages["ab2345f67-img-1"].imageData, "BASE64_MANUAL");
});

test("prepareAnswerSubmitData keeps existing scoped ref unchanged", () => {
  const entry = {
    questionUuid: "q-1",
    answerImages: {
      "aa92f6c03-img-2": "BASE64_OCR"
    },
    inlineImages: {}
  };

  const blocks = [{ type: "image", ref: "aa92f6c03-img-2" }];
  const out = prepareAnswerSubmitData({
    entry,
    blocks,
    answerUuid: "99999999-0000-0000-0000-000000000000",
    resolveAnswerImage: () => "BASE64_OCR"
  });

  assert.equal(out.blocks[0].ref, "aa92f6c03-img-2");
  assert.equal(out.inlineImages["aa92f6c03-img-2"].imageData, "BASE64_OCR");
});
