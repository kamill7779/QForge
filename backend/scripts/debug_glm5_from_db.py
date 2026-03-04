#!/usr/bin/env python3
"""
从数据库提取失败任务的图片，重新跑 OCR 获取文本，再用不同 max_tokens 测试 GLM-5。
用于诊断 StemXmlConverter 返回 xml_len=0 的根本原因。
"""
import json, time, base64, sys, os, requests, jwt
import mysql.connector

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
</stem>"""


def gen_token(api_key):
    parts = api_key.split(".")
    return jwt.encode(
        {"api_key": parts[0], "exp": int(time.time()) + 3600, "timestamp": int(time.time())},
        parts[1], algorithm="HS256", headers={"alg": "HS256", "sign_type": "SIGN"})


def run_ocr(file_uri):
    """调用 GLM layout_parsing OCR（与 Java GlmOcrClientImpl 相同格式）"""
    token = gen_token(GLM_OCR_API_KEY)
    prompt = (
        "You are an OCR for Chinese Gaokao math problems.\n"
        "1. Extract ONLY the question text, requirements, options (A/B/C/D).\n"
        "2. IGNORE: all images, graphs, geometry figures, coordinate systems, watermarks, borders, page numbers.\n"
        "3. Convert all mathematical formulas to standard LaTeX:\n"
        "   - fractions: \\frac{}{}, square root: \\sqrt{}, subscripts: _ , superscripts: ^\n"
        "   - vector: \\vec{}, set: \\in, \\cup, \\cap, \\emptyset\n"
        "   - inline: $...$, display: $$...$$\n"
        "4. Remove non-question metadata/watermark lines.\n"
        "5. Output ONLY pure LaTeX, no extra words, no comments, no descriptions."
    )
    payload = {"model": "glm-ocr", "file": file_uri, "prompt": prompt}
    resp = requests.post(OCR_URL, headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json"
    }, json=payload, timeout=120)
    data = resp.json()
    print(f"OCR response keys: {list(data.keys())}")
    # Java code checks md_results first, then layout_details
    if "md_results" in data:
        return str(data["md_results"])
    if "layout_details" in data:
        return str(data["layout_details"])
    return json.dumps(data, ensure_ascii=False)[:500]


def call_glm_chat(ocr_text, model="glm-5", max_tokens=4096):
    """调用 GLM-5 Chat API"""
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
    }, json=payload, timeout=300)
    elapsed = time.time() - start
    data = resp.json()

    print(f"Status: {resp.status_code}, Elapsed: {elapsed:.1f}s")

    if "choices" in data:
        msg = data["choices"][0]["message"]
        content = msg.get("content", None)
        reasoning = msg.get("reasoning_content", None)
        finish_reason = data["choices"][0].get("finish_reason", "?")
        print(f"finish_reason: {finish_reason}")
        print(f"content len: {len(content) if content else 0}")
        print(f"content: {repr(content[:500]) if content else repr(content)}")
        print(f"reasoning len: {len(reasoning) if reasoning else 0}")
        if reasoning:
            print(f"reasoning (first 300): {reasoning[:300]}...")
    else:
        print(f"ERROR: {json.dumps(data, ensure_ascii=False)[:500]}")

    if "usage" in data:
        u = data["usage"]
        print(f"Tokens: prompt={u.get('prompt_tokens')}, completion={u.get('completion_tokens')}, total={u.get('total_tokens')}")

    return data


def extract_image_from_db(task_uuid):
    """从 MySQL 提取任务图片 base64"""
    conn = mysql.connector.connect(host="localhost", port=3306, user="qforge", password="qforge", database="qforge")
    cursor = conn.cursor()
    cursor.execute("SELECT image_base64 FROM q_ocr_task WHERE task_uuid = %s", (task_uuid,))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else None


if __name__ == "__main__":
    # 失败任务 UUID（OCR 607 chars → xml_len=0）
    FAILED_TASK = "415a1bbd-067f-44d9-a908-c94d6accbeee"
    # 成功任务 UUID（OCR 472 chars → xml_len=454）
    SUCCESS_TASK = "3d741946-6212-450e-a01a-17ed77f9c5e8"

    target = FAILED_TASK
    if "--success" in sys.argv:
        target = SUCCESS_TASK

    print(f"Extracting image for task: {target}")
    img_b64 = extract_image_from_db(target)
    if not img_b64:
        print("ERROR: Image not found in DB")
        sys.exit(1)
    print(f"Image base64 length: {len(img_b64)}")

    # Step 1: Re-run OCR (need data URI prefix)
    print("\n--- Step 1: Running OCR ---")
    if not img_b64.startswith("data:"):
        img_b64_uri = f"data:image/png;base64,{img_b64}"
    else:
        img_b64_uri = img_b64
    ocr_text = run_ocr(img_b64_uri)
    print(f"OCR text length: {len(ocr_text)}")
    print(f"OCR text:\n{ocr_text}")

    # Step 2: Call GLM-5 with default max_tokens
    print("\n--- Step 2: GLM-5 with max_tokens=4096 ---")
    call_glm_chat(ocr_text, model="glm-5", max_tokens=4096)

    # Step 3: Compare with glm-4-flash (non-reasoning model)
    if "--compare" in sys.argv:
        print("\n--- Step 3: glm-4-flash (no reasoning) ---")
        call_glm_chat(ocr_text, model="glm-4-flash", max_tokens=4096)
