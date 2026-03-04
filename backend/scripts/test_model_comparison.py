#!/usr/bin/env python3
"""
对比测试: 不同模型做 StemXml 转换的效果
确认 glm-4-flash 能否替代 glm-5 用于 OCR 后处理。
"""
import json, time, requests, jwt

API_KEY = "9d94bc7088374d1596120c5930bc13d7.jUWvkwd5OgTHrkfR"
CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

# 完整的 StemXmlConverter SYSTEM_PROMPT（与 Java 一致）
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
7. <image ref="..."> tags are already placed in the text. Keep them as-is.
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
- Remove exam source tags (e.g. '(2022·新Ⅱ卷)') from the beginning.

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
</stem>"""

# 清洗后的 OCR 文本（bbox 已替换为 image 标签）
CLEANED_OCR_TEXT = """图1是中国古代建筑中的举架结构， $ AA^{\\prime}, BB^{\\prime}, CC^{\\prime}, DD^{\\prime} $ 是桁，相邻桁的水平距离称为步，垂直距离称为举，图2是某古代建筑屋顶截面的示意图.

其中 $ DD_{1}, CC_{1}, BB_{1}, AA_{1} $是举， $ OD_{1}, DC_{1}, CB_{1}, BA_{1} $是相等的步，相邻桁的举步之比分别为 $ \\frac{DD_{1}}{OD_{1}} $ $ = 0. 5, \\frac{C C_{1}}{D C_{1}} = k_{1}, \\frac{B B_{1}}{C B_{1}} = k_{2}, \\frac{A A_{1}}{B A_{1}} = k_{3}. $已知 $ k_{1}, k_{2}, k_{3} $成公差为0.1的等差数列，且直线OA的斜率为0.725，则 $ k_{3} = $ （    ）

<image ref="fig-1" />

<image ref="fig-2" />

A. 0.75 B. 0.8 C. 0.85 D. 0.9"""


def gen_token(api_key):
    parts = api_key.split(".")
    return jwt.encode(
        {"api_key": parts[0], "exp": int(time.time()) + 3600, "timestamp": int(time.time())},
        parts[1], algorithm="HS256", headers={"alg": "HS256", "sign_type": "SIGN"})


def test_model(model, text, max_tokens=4096):
    token = gen_token(API_KEY)
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": text},
        ],
        "temperature": 0.1,
        "max_tokens": max_tokens,
        "stream": False,
    }
    print(f"\n{'='*60}")
    print(f"Model: {model}")
    start = time.time()
    resp = requests.post(CHAT_URL, headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json"
    }, json=payload, timeout=300)
    elapsed = time.time() - start
    data = resp.json()

    if "choices" in data:
        msg = data["choices"][0]["message"]
        content = msg.get("content", "")
        reasoning = msg.get("reasoning_content", "")
        finish = data["choices"][0].get("finish_reason", "?")
        usage = data.get("usage", {})

        print(f"  elapsed: {elapsed:.1f}s")
        print(f"  finish_reason: {finish}")
        print(f"  content_len: {len(content) if content else 0}")
        print(f"  reasoning_len: {len(reasoning) if reasoning else 0}")
        print(f"  tokens: prompt={usage.get('prompt_tokens','?')}, "
              f"completion={usage.get('completion_tokens','?')}, "
              f"total={usage.get('total_tokens','?')}")

        # 检查质量
        has_stem = "<stem" in (content or "")
        has_choices = "<choices" in (content or "")
        has_image = "<image" in (content or "")
        has_latex = "$" in (content or "") or "\\frac" in (content or "")
        print(f"  quality: stem={has_stem}, choices={has_choices}, image={has_image}, latex={has_latex}")
        print(f"  content:\n{content}")
        return {"model": model, "elapsed": elapsed, "content_len": len(content) if content else 0,
                "reasoning_len": len(reasoning) if reasoning else 0, "content": content,
                "quality": has_stem and has_choices}
    else:
        err = json.dumps(data, ensure_ascii=False)[:300]
        print(f"  ERROR: {err}")
        return {"model": model, "error": err}


if __name__ == "__main__":
    models = ["glm-4-flash", "glm-4-air", "glm-4-0520", "glm-4-plus"]

    results = []
    for m in models:
        try:
            r = test_model(m, CLEANED_OCR_TEXT)
            results.append(r)
        except Exception as e:
            print(f"  EXCEPTION: {e}")
            results.append({"model": m, "error": str(e)})

    print(f"\n\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")
    print(f"{'Model':<16} {'Time':>6} {'Content':>8} {'Reason':>8} {'Quality':<8}")
    print("-" * 56)
    for r in results:
        if "error" in r:
            print(f"{r['model']:<16} {'ERROR':>6}")
        else:
            q = "✅" if r.get("quality") else "❌"
            print(f"{r['model']:<16} {r['elapsed']:>5.1f}s {r['content_len']:>7} {r['reasoning_len']:>7} {q}")
