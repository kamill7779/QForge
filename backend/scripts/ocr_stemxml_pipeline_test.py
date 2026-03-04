#!/usr/bin/env python3
"""
端到端测试脚本：模拟 OCR + StemXml 管道。
1. 调用 glm-ocr (layout_parsing) 获取 OCR 文本
2. 调用 glm-5 (chat completion) 将 OCR 文本转为 stem XML
3. 打印每步的完整输入输出，定位空 XML 的根因

用法:
  $env:ZHIPUAI_API_KEY="your-key"
  $env:GLM_OCR_API_KEY="your-key"
  python backend/scripts/ocr_stemxml_pipeline_test.py --file <image>
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

OCR_ENDPOINT = "https://api.z.ai/api/paas/v4/layout_parsing"
CHAT_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

OCR_PROMPT = "\n".join([
    "You are an OCR for Chinese Gaokao math problems.",
    "1. Extract ONLY the question text, requirements, options (A/B/C/D).",
    "2. IGNORE: all images, graphs, geometry figures, coordinate systems, watermarks, borders, page numbers.",
    "3. Convert all mathematical formulas to standard LaTeX:",
    "   - fractions: \\frac{}{}, square root: \\sqrt{}, subscripts: _ , superscripts: ^",
    "   - vector: \\vec{}, set: \\in, \\cup, \\cap, \\emptyset",
    "   - inline: $...$, display: $$...$$",
    "4. Remove non-question metadata/watermark lines, especially source/channel text such as "
    "'wechat official account', 'source', 'scan code', 'advertisement', page headers/footers.",
    "5. Output ONLY pure LaTeX, no extra words, no comments, no descriptions."
])

STEM_XML_SYSTEM_PROMPT = "\n".join([
    'You are an XML conversion engine. Convert OCR-recognized math problem text into well-formed XML.',
    '',
    'SCHEMA:',
    'Root: <stem version="1">',
    'Allowed tags (ONLY these 8): stem, p, image, choices, choice, blanks, blank, answer-area',
    '',
    'RULES:',
    '1. <stem version="1"> is the root.',
    '2. <p> wraps text paragraphs. Inline LaTeX uses $...$.',
    '3. <choices mode="single"> for single-choice (or mode="multi" for multi-select) must have >= 2 <choice>.',
    '4. <choice key="A"> wraps content in <p>.',
    '5. Fill-in-the-blank: <blank id="N" /> inline in <p>.',
    '6. Essay/proof: <answer-area /> at the end.',
    '7. At most ONE <image /> directly under <stem>. Use ref="original".',
    '8. Math formulas: standard LaTeX (\\frac{}{}, \\sqrt{}, \\vec{}, etc.).',
    '9. Escape XML special chars: &lt; &gt; &amp;',
    '',
    'QUESTION TYPE DETECTION:',
    '- A/B/C/D options → <choices mode="single">',
    '- 多选/multiple-select → <choices mode="multi">',
    '- Blanks to fill → <blank id="N" />',
    '- Proof/detailed solution → <answer-area />',
    '',
    'OUTPUT RULES:',
    '- Output ONLY the XML. No explanations, no markdown fences, no code blocks.',
    '- Preserve all mathematical content exactly.',
    '- Remove question numbers (e.g. \'11.\', \'(1)\') from the beginning.',
    '',
    'EXAMPLES:',
    '',
    'Input: 下列哪个是勾股定理的正确表述？ A. $a^2 + b^2 = c^2$ B. $a + b = c$ C. $a^2 - b^2 = c^2$ D. $a^2 + b^2 = c$',
    'Output:',
    '<stem version="1">',
    '  <p>下列哪个是勾股定理的正确表述？</p>',
    '  <choices mode="single">',
    '    <choice key="A"><p>$a^2 + b^2 = c^2$</p></choice>',
    '    <choice key="B"><p>$a + b = c$</p></choice>',
    '    <choice key="C"><p>$a^2 - b^2 = c^2$</p></choice>',
    '    <choice key="D"><p>$a^2 + b^2 = c$</p></choice>',
    '  </choices>',
    '</stem>',
    '',
    'Input: 已知 $f(x) = x^2$，则 $f(3) = $ ____。若 $f(a) = 16$，则 $a = $ ____。',
    'Output:',
    '<stem version="1">',
    '  <p>已知 $f(x) = x^2$，则 $f(3) = $ <blank id="1" />。</p>',
    '  <p>若 $f(a) = 16$，则 $a = $ <blank id="2" />。</p>',
    '</stem>',
    '',
    'Input: 已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。',
    'Output:',
    '<stem version="1">',
    '  <p>已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。</p>',
    '  <answer-area />',
    '</stem>'
])


def load_image_base64(file_path: str) -> str:
    path = Path(file_path)
    if not path.exists():
        print(f"文件不存在: {file_path}", file=sys.stderr)
        sys.exit(1)
    with open(path, "rb") as f:
        raw = f.read()
    b64 = base64.b64encode(raw).decode("ascii")
    print(f"[INFO] 图片加载: {file_path} ({len(raw)} bytes)")
    return b64


def call_ocr(api_key: str, image_b64: str) -> str:
    """Stage 1: glm-ocr layout_parsing"""
    suffix = "image/png"
    data_uri = f"data:{suffix};base64,{image_b64}"
    payload = {"model": "glm-ocr", "file": data_uri, "prompt": OCR_PROMPT}
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        OCR_ENDPOINT,
        data=data,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    print(f"\n{'='*60}")
    print(f"[Stage 1] 调用 glm-ocr layout_parsing")
    print(f"  payload: {len(data)} bytes")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            status = resp.getcode()
    except urllib.error.HTTPError as exc:
        err = exc.read().decode("utf-8", errors="replace")
        print(f"[ERROR] HTTP {exc.code}: {err}", file=sys.stderr)
        sys.exit(1)
    print(f"  HTTP {status}, 耗时 {time.time()-t0:.2f}s")
    obj = json.loads(body)
    md = obj.get("md_results", "")
    if not md:
        md = str(obj.get("layout_details", ""))
    print(f"  OCR text length: {len(md)} chars")
    print(f"\n[OCR 输出全文]:")
    print("-" * 60)
    print(md)
    print("-" * 60)
    return md


def call_stem_xml(api_key: str, ocr_text: str, model: str = "glm-5") -> str:
    """Stage 1.5: glm-5 chat → stem XML"""
    messages = [
        {"role": "system", "content": STEM_XML_SYSTEM_PROMPT},
        {"role": "user", "content": ocr_text},
    ]
    payload = {"model": model, "messages": messages, "temperature": 0.1, "max_tokens": 4096, "stream": False}
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        CHAT_ENDPOINT,
        data=data,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    print(f"\n{'='*60}")
    print(f"[Stage 1.5] 调用 {model} 转换 stem XML")
    print(f"  payload: {len(data)} bytes, ocr_text: {len(ocr_text)} chars")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            status = resp.getcode()
    except urllib.error.HTTPError as exc:
        err = exc.read().decode("utf-8", errors="replace")
        print(f"[ERROR] HTTP {exc.code}: {err}", file=sys.stderr)
        sys.exit(1)
    elapsed = time.time() - t0
    print(f"  HTTP {status}, 耗时 {elapsed:.2f}s")
    obj = json.loads(body)
    usage = obj.get("usage", {})
    print(f"  tokens: prompt={usage.get('prompt_tokens')}, completion={usage.get('completion_tokens')}, total={usage.get('total_tokens')}")
    
    choices = obj.get("choices", [])
    if not choices:
        print("[ERROR] 响应中没有 choices!")
        print(json.dumps(obj, ensure_ascii=False, indent=2)[:2000])
        return ""
    
    msg = choices[0].get("message", {})
    content = msg.get("content", "")
    finish_reason = choices[0].get("finish_reason", "")
    
    print(f"  finish_reason: {finish_reason}")
    print(f"  content type: {type(content).__name__}")
    print(f"  content length: {len(str(content)) if content else 0}")
    
    # 检查 reasoning_content (thinking models)
    reasoning = msg.get("reasoning_content")
    if reasoning:
        print(f"  reasoning_content length: {len(reasoning)}")
        print(f"\n[reasoning_content 片段]:")
        print(reasoning[:500])
    
    xml = str(content).strip() if content else ""
    
    # Strip code fences
    if xml.startswith("```"):
        lines = xml.split("\n")
        inner = [l for l in lines if not l.strip().startswith("```")]
        xml = "\n".join(inner).strip()
    
    print(f"\n[StemXml 输出] ({len(xml)} chars):")
    print("-" * 60)
    print(xml if xml else "(EMPTY)")
    print("-" * 60)
    
    if not xml:
        print("\n[诊断] content 为空！完整 message 对象:")
        print(json.dumps(msg, ensure_ascii=False, indent=2)[:3000])
        print("\n[诊断] 完整 response:")
        print(json.dumps(obj, ensure_ascii=False, indent=2)[:5000])
    
    return xml


def main():
    parser = argparse.ArgumentParser(description="OCR + StemXml 管道端到端测试")
    parser.add_argument("--file", required=True, help="本地图片路径")
    parser.add_argument("--model", default="glm-5", help="StemXml 转换模型 (默认 glm-5)")
    parser.add_argument("--skip-ocr", action="store_true", help="跳过 OCR，直接输入文本")
    parser.add_argument("--ocr-text", help="直接提供 OCR 文本（跳过 Stage 1）")
    args = parser.parse_args()

    ocr_key = os.getenv("GLM_OCR_API_KEY")
    chat_key = os.getenv("ZHIPUAI_API_KEY")
    
    if not chat_key:
        print("[ERROR] 缺少 ZHIPUAI_API_KEY", file=sys.stderr)
        return 1

    if args.ocr_text:
        ocr_text = args.ocr_text
        print(f"[INFO] 使用提供的 OCR 文本 ({len(ocr_text)} chars)")
    elif args.skip_ocr:
        print("[ERROR] --skip-ocr 需要配合 --ocr-text", file=sys.stderr)
        return 1
    else:
        if not ocr_key:
            print("[ERROR] 缺少 GLM_OCR_API_KEY", file=sys.stderr)
            return 1
        image_b64 = load_image_base64(args.file)
        ocr_text = call_ocr(ocr_key, image_b64)

    if not ocr_text or not ocr_text.strip():
        print("\n[FATAL] OCR 返回空文本，无法继续")
        return 1

    xml = call_stem_xml(chat_key, ocr_text, args.model)
    
    print(f"\n{'='*60}")
    print(f"[最终结果]")
    print(f"  OCR text: {len(ocr_text)} chars")
    print(f"  Stem XML: {len(xml)} chars")
    if xml:
        print("  状态: ✅ 成功")
    else:
        print("  状态: ❌ XML 为空 — 需要修复")
    
    return 0 if xml else 1


if __name__ == "__main__":
    raise SystemExit(main())
