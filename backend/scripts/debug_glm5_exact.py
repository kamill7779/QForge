#!/usr/bin/env python3
"""
从 DB 提取真实 OCR 文本，调用 GLM-5 复现 xml_len=0 问题。
"""
import json, time, requests, jwt, mysql.connector

API_KEY = "9d94bc7088374d1596120c5930bc13d7.jUWvkwd5OgTHrkfR"
GLM_OCR_API_KEY = "7f254e118e2c4882b9a305afdee63a43.x0fNWcsy11zEMyUK"
CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
OCR_URL = "https://api.z.ai/api/paas/v4/layout_parsing"

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


def gen_token(api_key):
    parts = api_key.split(".")
    return jwt.encode(
        {"api_key": parts[0], "exp": int(time.time()) + 3600, "timestamp": int(time.time())},
        parts[1], algorithm="HS256", headers={"alg": "HS256", "sign_type": "SIGN"})


def run_ocr(file_uri):
    """调用 GLM layout_parsing OCR"""
    token = gen_token(GLM_OCR_API_KEY)
    prompt = (
        "You are an OCR for Chinese Gaokao math problems.\n"
        "1. Extract ONLY the question text, requirements, options (A/B/C/D).\n"
        "2. IGNORE: all images, graphs, geometry figures, coordinate systems, watermarks, borders, page numbers.\n"
        "3. Convert all mathematical formulas to standard LaTeX.\n"
        "4. Remove non-question metadata/watermark lines.\n"
        "5. Output ONLY pure LaTeX, no extra words, no comments, no descriptions."
    )
    payload = {"model": "glm-ocr", "file": file_uri, "prompt": prompt}
    resp = requests.post(OCR_URL, headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json"
    }, json=payload, timeout=120)
    data = resp.json()
    if "md_results" in data:
        return str(data["md_results"])
    if "layout_details" in data:
        return str(data["layout_details"])
    return json.dumps(data, ensure_ascii=False)[:500]


def call_glm_chat(ocr_text, model="glm-5", max_tokens=4096):
    token = gen_token(API_KEY)
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
    print(f"Model: {model}, max_tokens: {max_tokens}, text_len: {len(ocr_text)}")
    start = time.time()
    resp = requests.post(CHAT_URL, headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json"
    }, json=payload, timeout=600)
    elapsed = time.time() - start
    data = resp.json()
    print(f"Status: {resp.status_code}, Elapsed: {elapsed:.1f}s")

    if "choices" in data:
        msg = data["choices"][0]["message"]
        content = msg.get("content", None)
        reasoning = msg.get("reasoning_content", None)
        finish_reason = data["choices"][0].get("finish_reason", "?")
        print(f"finish_reason: {finish_reason}")
        print(f"content_len: {len(content) if content else 0}")
        if content:
            print(f"content: {content[:600]}")
        else:
            print(f"content: {repr(content)}")
        print(f"reasoning_len: {len(reasoning) if reasoning else 0}")
        if reasoning:
            print(f"reasoning_first_300: {reasoning[:300]}")
    else:
        print(f"ERROR: {json.dumps(data, ensure_ascii=False)[:500]}")

    if "usage" in data:
        u = data["usage"]
        print(f"Tokens: prompt={u.get('prompt_tokens')}, completion={u.get('completion_tokens')}, total={u.get('total_tokens')}")

    return data


def extract_image_from_db(task_uuid):
    conn = mysql.connector.connect(host="localhost", port=3306, user="qforge", password="qforge", database="qforge")
    cursor = conn.cursor()
    cursor.execute("SELECT image_base64 FROM q_ocr_task WHERE task_uuid = %s", (task_uuid,))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else None


if __name__ == "__main__":
    import sys

    # 失败任务 UUID
    FAILED_TASK = "415a1bbd-067f-44d9-a908-c94d6accbeee"

    print(f"Extracting image for failed task: {FAILED_TASK}")
    img_b64 = extract_image_from_db(FAILED_TASK)
    if not img_b64:
        print("ERROR: Image not found")
        sys.exit(1)

    # 确保有 data URI 前缀
    if not img_b64.startswith("data:"):
        img_b64_uri = f"data:image/png;base64,{img_b64}"
    else:
        img_b64_uri = img_b64

    print(f"Image base64 length: {len(img_b64)}")

    # Step 1: 重新跑 OCR 获取原始文本
    print("\n--- Step 1: Running OCR ---")
    ocr_text = run_ocr(img_b64_uri)
    print(f"OCR text length: {len(ocr_text)}")
    print(f"OCR text:\n{ocr_text}")

    # Step 2: 用 GLM-5 + max_tokens=4096（和 Java 一样）
    print("\n\n--- Step 2: GLM-5 max_tokens=4096 (same as Java) ---")
    r1 = call_glm_chat(ocr_text, model="glm-5", max_tokens=4096)

    # Step 3: 用 GLM-5 + max_tokens=8192（翻倍 token 预算）
    print("\n\n--- Step 3: GLM-5 max_tokens=8192 (double budget) ---")
    r2 = call_glm_chat(ocr_text, model="glm-5", max_tokens=8192)

    # Step 4: 用 glm-4-flash（非推理模型）
    print("\n\n--- Step 4: glm-4-flash (no reasoning, fast) ---")
    r3 = call_glm_chat(ocr_text, model="glm-4-flash", max_tokens=4096)

    print("\n\n=== SUMMARY ===")
    for label, r in [("glm-5/4096", r1), ("glm-5/8192", r2), ("glm-4-flash", r3)]:
        if "choices" in r:
            c = r["choices"][0]["message"].get("content")
            print(f"  {label}: content_len={len(c) if c else 0}, "
                  f"finish={r['choices'][0].get('finish_reason')}, "
                  f"tokens={r.get('usage',{}).get('total_tokens','?')}")
