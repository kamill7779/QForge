#!/usr/bin/env python3
"""
Debug script: 直接调用 GLM-5 API 查看 reasoning_content 和 content 的原始响应。
用于诊断 StemXmlConverter 返回 xml_len=0 的根本原因。
"""

import json
import time
import requests
import jwt

API_KEY = "9d94bc7088374d1596120c5930bc13d7.jUWvkwd5OgTHrkfR"
API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

# 从成功日志里提取的 SYSTEM_PROMPT（与 StemXmlConverter.java 一致）
SYSTEM_PROMPT = """You are an XML conversion engine. Convert OCR-recognized math problem text into well-formed XML.

SCHEMA:
Root: <stem version="1">
Allowed tags (ONLY these 8): stem, p, image, choices, choice, blanks, blank, answer-area

RULES:
1. <stem version="1"> is the root.
2. <p> wraps text paragraphs. Inline LaTeX uses $...$.
3. <choices mode="single"> for single-choice (or mode="multi" for multi-select) must have >= 2 <choice>.
4. <choice key="A"> wraps content in <p>.
5. Fill-in-the-blank: <blank id="N" /> inline in <p>.
6. Essay/proof: <answer-area /> at the end.
7. At most ONE <image /> directly under <stem>. Use ref="original".
8. Math formulas: standard LaTeX (\\frac{}{}, \\sqrt{}, \\vec{}, etc.).
9. Escape XML special chars: &lt; &gt; &amp;

QUESTION TYPE DETECTION:
- A/B/C/D options → <choices mode="single">
- 多选/multiple-select → <choices mode="multi">
- Blanks to fill → <blank id="N" />
- Proof/detailed solution → <answer-area />

OUTPUT RULES:
- Output ONLY the XML. No explanations, no markdown fences, no code blocks.
- Preserve all mathematical content exactly.
- Remove question numbers (e.g. '11.', '(1)') from the beginning.

EXAMPLES:

Input: 下列哪个是勾股定理的正确表述？ A. $a^2 + b^2 = c^2$ B. $a + b = c$ C. $a^2 - b^2 = c^2$ D. $a^2 + b^2 = c$
Output:
<stem version="1">
  <p>下列哪个是勾股定理的正确表述？</p>
  <choices mode="single">
    <choice key="A"><p>$a^2 + b^2 = c^2$</p></choice>
    <choice key="B"><p>$a + b = c$</p></choice>
    <choice key="C"><p>$a^2 - b^2 = c^2$</p></choice>
    <choice key="D"><p>$a^2 + b^2 = c$</p></choice>
  </choices>
</stem>

Input: 已知 $f(x) = x^2$，则 $f(3) = $ ____。若 $f(a) = 16$，则 $a = $ ____。
Output:
<stem version="1">
  <p>已知 $f(x) = x^2$，则 $f(3) = $ <blank id="1" />。</p>
  <p>若 $f(a) = 16$，则 $a = $ <blank id="2" />。</p>
</stem>

Input: 已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。
Output:
<stem version="1">
  <p>已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。</p>
  <answer-area />
</stem>"""

# 真实的 OCR 文本样本（约 600 字符，模拟失败场景）
SAMPLE_OCR_TEXT_COMPLEX = """（2022新II卷）如图，在三棱锥A-BCD中，侧面ABD为等边三角形，
AB=2，BC=CD=1，M为AD的中点。

（1）证明：BM⊥CD；

（2）求二面角B-MC-D的正弦值。

A. 0.75    B. 0.8    C. 0.85    D. 0.9"""

# 简短的 OCR 文本样本（模拟成功场景）
SAMPLE_OCR_TEXT_SIMPLE = """如图所示，在 △ABC 中，AB=3，BC=4，∠ABC=90°，点D为AC的中点。则下列函数图像中，能大致表示 BD 长度关于角 α 变化的是
A. 图像1
B. 图像2
C. 图像3
D. 图像4"""


def generate_token(api_key: str) -> str:
    """Generate JWT token for ZhipuAI API."""
    parts = api_key.split(".")
    api_id, secret = parts[0], parts[1]
    payload = {
        "api_key": api_id,
        "exp": int(time.time()) + 3600,
        "timestamp": int(time.time()),
    }
    return jwt.encode(payload, secret, algorithm="HS256",
                      headers={"alg": "HS256", "sign_type": "SIGN"})


def call_glm5(ocr_text: str, model: str = "glm-5", max_tokens: int = 4096) -> dict:
    """Call GLM-5 API and return the raw response."""
    token = generate_token(API_KEY)
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": ocr_text},
        ],
        "temperature": 0.1,
        "max_tokens": max_tokens,
        "stream": False,
    }

    print(f"\n{'='*60}")
    print(f"Model: {model}, max_tokens: {max_tokens}")
    print(f"Input text length: {len(ocr_text)} chars")
    print(f"Calling API...")

    start = time.time()
    resp = requests.post(API_URL, headers=headers, json=payload, timeout=300)
    elapsed = time.time() - start

    print(f"Response status: {resp.status_code}")
    print(f"Elapsed: {elapsed:.1f}s")

    data = resp.json()

    # Print full response structure
    print(f"\n--- RAW RESPONSE ---")
    print(json.dumps(data, ensure_ascii=False, indent=2)[:3000])

    if "choices" in data:
        for i, choice in enumerate(data["choices"]):
            msg = choice.get("message", {})
            content = msg.get("content", None)
            reasoning = msg.get("reasoning_content", None)
            finish_reason = choice.get("finish_reason", "?")

            print(f"\n--- Choice {i} ---")
            print(f"  finish_reason: {finish_reason}")
            print(f"  content type: {type(content).__name__}, len: {len(str(content)) if content else 0}")
            print(f"  content: {repr(content[:500]) if content else repr(content)}")
            print(f"  reasoning_content type: {type(reasoning).__name__}, len: {len(str(reasoning)) if reasoning else 0}")
            if reasoning:
                print(f"  reasoning_content (first 500): {reasoning[:500]}")

    if "usage" in data:
        usage = data["usage"]
        print(f"\n--- Token Usage ---")
        print(f"  prompt_tokens: {usage.get('prompt_tokens', '?')}")
        print(f"  completion_tokens: {usage.get('completion_tokens', '?')}")
        print(f"  total_tokens: {usage.get('total_tokens', '?')}")
        # GLM-5 可能有 reasoning_tokens
        if "reasoning_tokens" in usage:
            print(f"  reasoning_tokens: {usage['reasoning_tokens']}")

    return data


if __name__ == "__main__":
    import sys

    if "--simple" in sys.argv:
        text = SAMPLE_OCR_TEXT_SIMPLE
    else:
        text = SAMPLE_OCR_TEXT_COMPLEX

    model = "glm-5"
    max_tokens = 4096

    for arg in sys.argv[1:]:
        if arg.startswith("--model="):
            model = arg.split("=", 1)[1]
        elif arg.startswith("--max-tokens="):
            max_tokens = int(arg.split("=", 1)[1])

    result = call_glm5(text, model=model, max_tokens=max_tokens)

    # Also try with glm-4-flash for comparison
    if "--compare" in sys.argv:
        print("\n\n" + "=" * 60)
        print("COMPARISON: Using glm-4-flash (non-reasoning model)")
        print("=" * 60)
        call_glm5(text, model="glm-4-flash", max_tokens=max_tokens)
