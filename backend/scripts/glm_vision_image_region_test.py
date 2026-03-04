#!/usr/bin/env python3
"""
GLM-4.6V 视觉模型测试脚本：从试题图片中提取图像区域坐标。

目标：让大模型识别试题图片中的三类图像区域：
  1. stem_image   — 题干配图（至多一张）
  2. option_image  — 选项图（ABCD 等，若干）
  3. inline_image  — 题内图/其他图（几何图形、函数图像等，若干）

并返回每个图像区域的边界框坐标（像素百分比 0~1000）。

用法 (PowerShell):
  $env:ZHIPUAI_API_KEY="your-api-key"

  # 使用本地图片
  python backend/scripts/glm_vision_image_region_test.py --file test.png

  # 使用 URL
  python backend/scripts/glm_vision_image_region_test.py --url https://example.com/question.png

  # 使用示例 base64
  python backend/scripts/glm_vision_image_region_test.py --demo

  # 指定模型（默认 glm-4.1v-thinking）
  python backend/scripts/glm_vision_image_region_test.py --file test.png --model glm-4.1v-thinking
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

# ─── 智谱 AI Chat Completion endpoint ───
DEFAULT_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
DEFAULT_MODEL = "glm-4.1v-thinking"

# ─── System Prompt：图像区域坐标提取 ───
SYSTEM_PROMPT = """\
你是一个试题图像分析引擎。你的任务是分析一张中国高考/考试数学试题的截图，识别其中所有嵌入的**图像/图形区域**（非文字部分），并返回每个区域的**边界框坐标**。

## 图像分类

图像区域分为三类：
1. **stem_image** — 题干配图：帮助理解题意的主图（例如几何图形、函数图像、数据表格等）。至多一张。
2. **option_image** — 选项图：出现在选项 A/B/C/D 中的图片。每个选项最多一张图。用 key 标记（A/B/C/D）。
3. **inline_image** — 题内图：除上述两类之外的其他图像（例如题中插图、参考图等），用序号 id 标记（1, 2, 3...）。

## 坐标格式

使用**千分位坐标**（0~1000 的整数），表示相对于整张图片宽高的比例位置：
- `x1, y1` = 左上角
- `x2, y2` = 右下角
- x 方向：0=最左，1000=最右
- y 方向：0=最顶，1000=最底

## 输出格式

严格输出以下 JSON，不要输出任何其他内容（不要 markdown 代码块，不要解释）：

```
{
  "regions": [
    {
      "type": "stem_image",
      "bbox": [x1, y1, x2, y2],
      "description": "简短描述图像内容"
    },
    {
      "type": "option_image",
      "key": "A",
      "bbox": [x1, y1, x2, y2],
      "description": "简短描述"
    },
    {
      "type": "inline_image",
      "id": 1,
      "bbox": [x1, y1, x2, y2],
      "description": "简短描述"
    }
  ]
}
```

## 重要规则

1. 只识别**非文字的图像/图形区域**。纯文字、公式不算图像。
2. 边界框应尽量**紧密包围**图像内容，不要包含周围文字。
3. 如果选项中既有文字又有图片，只框选图片部分。
4. 如果图片中没有任何图像区域（纯文字题），返回 `{"regions": []}`。
5. stem_image 至多一个；option_image 的 key 与选项字母对应；inline_image 的 id 从 1 递增。
6. 坐标必须是 0~1000 的整数。
7. **只输出 JSON**，不要任何前缀后缀。"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="GLM-4.6V 视觉模型 — 试题图像区域坐标提取测试"
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--file", help="本地图片路径 (png/jpg)")
    group.add_argument("--url", help="图片 URL")
    group.add_argument(
        "--demo",
        action="store_true",
        help="使用内置演示图片 URL（智谱官方示例）",
    )
    parser.add_argument(
        "--model", default=DEFAULT_MODEL, help=f"模型名称 (默认: {DEFAULT_MODEL})"
    )
    parser.add_argument(
        "--endpoint", default=DEFAULT_ENDPOINT, help="API endpoint"
    )
    parser.add_argument(
        "--temperature", type=float, default=0.1, help="采样温度 (默认 0.1)"
    )
    parser.add_argument(
        "--max-tokens", type=int, default=4096, help="最大输出 token 数"
    )
    parser.add_argument(
        "--save-viz",
        action="store_true",
        help="保存可视化结果（在图片上画出边界框）",
    )
    return parser.parse_args()


def load_image_as_base64(file_path: str) -> tuple[str, str]:
    """读取本地图片并转为 base64，返回 (base64_str, mime_type)。"""
    path = Path(file_path)
    if not path.exists():
        print(f"文件不存在: {file_path}", file=sys.stderr)
        sys.exit(1)

    suffix = path.suffix.lower()
    mime_map = {
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".gif": "image/gif",
        ".webp": "image/webp",
        ".bmp": "image/bmp",
    }
    mime_type = mime_map.get(suffix, "image/png")

    with open(path, "rb") as f:
        raw = f.read()

    b64 = base64.b64encode(raw).decode("ascii")
    print(f"[INFO] 图片加载: {file_path} ({len(raw)} bytes, {mime_type})")
    return b64, mime_type


def build_image_content(
    args: argparse.Namespace,
) -> tuple[list[dict], str | None]:
    """构建 messages.content 中的图片部分。返回 (content_parts, local_file_path)。"""

    if args.demo:
        # 使用一个常见的数学试题截图（智谱官方 demo）
        demo_url = "https://cdn.bigmodel.cn/static/logo/introduction.png"
        print(f"[INFO] 使用演示图片: {demo_url}")
        return [
            {"type": "image_url", "image_url": {"url": demo_url}}
        ], None

    if args.url:
        print(f"[INFO] 使用 URL 图片: {args.url}")
        return [
            {"type": "image_url", "image_url": {"url": args.url}}
        ], None

    # 本地文件 → base64
    b64, mime = load_image_as_base64(args.file)
    data_uri = f"data:{mime};base64,{b64}"
    return [
        {"type": "image_url", "image_url": {"url": data_uri}}
    ], args.file


def call_glm_vision(
    endpoint: str,
    api_key: str,
    model: str,
    image_content: list[dict],
    temperature: float,
    max_tokens: int,
) -> dict:
    """调用 GLM 视觉模型 Chat Completion API。"""

    messages = [
        {
            "role": "system",
            "content": SYSTEM_PROMPT,
        },
        {
            "role": "user",
            "content": image_content
            + [
                {
                    "type": "text",
                    "text": "请分析这张试题图片，识别所有图像/图形区域并返回坐标。",
                }
            ],
        },
    ]

    payload = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": False,
    }

    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        endpoint,
        data=data,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )

    print(f"[INFO] 调用 {model} (endpoint={endpoint})")
    print(f"[INFO] payload 大小: {len(data)} bytes")
    t0 = time.time()

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            status = resp.getcode()
    except urllib.error.HTTPError as exc:
        err_body = exc.read().decode("utf-8", errors="replace")
        print(f"[ERROR] HTTP {exc.code}: {err_body}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(f"[ERROR] 请求失败: {exc}", file=sys.stderr)
        sys.exit(1)

    elapsed = time.time() - t0
    print(f"[INFO] HTTP {status}, 耗时 {elapsed:.2f}s, 响应大小 {len(body)} bytes")

    try:
        return json.loads(body)
    except json.JSONDecodeError:
        print("[ERROR] 非 JSON 响应:", file=sys.stderr)
        print(body[:2000], file=sys.stderr)
        sys.exit(1)


def extract_content(response: dict) -> str:
    """从 Chat Completion 响应中提取 assistant 的文本内容。"""
    choices = response.get("choices", [])
    if not choices:
        print("[ERROR] 响应中没有 choices", file=sys.stderr)
        print(json.dumps(response, ensure_ascii=False, indent=2)[:2000])
        sys.exit(1)

    message = choices[0].get("message", {})
    content = message.get("content", "")
    return content.strip() if isinstance(content, str) else str(content)


def parse_regions(content: str) -> dict | None:
    """尝试从模型输出中解析 JSON 区域数据。"""
    # 去除可能的 markdown 代码块
    text = content.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        inner = []
        for line in lines:
            if not line.strip().startswith("```"):
                inner.append(line)
        text = "\n".join(inner).strip()

    # 尝试直接解析
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # 尝试提取 JSON 对象
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        try:
            return json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            pass

    return None


def visualize_regions(image_path: str, regions: list[dict], output_path: str):
    """在图片上画出边界框并保存。需要 Pillow。"""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        print("[WARN] 可视化需要 Pillow: pip install Pillow", file=sys.stderr)
        return

    img = Image.open(image_path)
    draw = ImageDraw.Draw(img)
    w, h = img.size

    colors = {
        "stem_image": (255, 0, 0),       # 红色
        "option_image": (0, 128, 255),    # 蓝色
        "inline_image": (0, 200, 0),      # 绿色
    }

    for region in regions:
        rtype = region.get("type", "inline_image")
        bbox = region.get("bbox", [])
        if len(bbox) != 4:
            continue

        # 千分位 → 像素
        x1 = int(bbox[0] / 1000 * w)
        y1 = int(bbox[1] / 1000 * h)
        x2 = int(bbox[2] / 1000 * w)
        y2 = int(bbox[3] / 1000 * h)

        color = colors.get(rtype, (128, 128, 128))
        draw.rectangle([x1, y1, x2, y2], outline=color, width=3)

        # 标签
        label = rtype
        if rtype == "option_image":
            label = f"option_{region.get('key', '?')}"
        elif rtype == "inline_image":
            label = f"inline_{region.get('id', '?')}"
        desc = region.get("description", "")
        if desc:
            label += f": {desc[:20]}"

        draw.text((x1 + 2, max(0, y1 - 16)), label, fill=color)

    img.save(output_path)
    print(f"[INFO] 可视化已保存: {output_path}")


def print_regions_table(regions: list[dict]):
    """以表格形式打印识别结果。"""
    print("\n" + "=" * 80)
    print("  图像区域识别结果")
    print("=" * 80)

    if not regions:
        print("  （未识别到任何图像区域 — 可能是纯文字题目）")
        return

    print(f"  共识别到 {len(regions)} 个区域:")
    print("-" * 80)
    print(f"  {'类型':<16} {'标识':<8} {'坐标 [x1,y1,x2,y2]':<28} {'描述'}")
    print("-" * 80)

    for r in regions:
        rtype = r.get("type", "?")
        bbox = r.get("bbox", [])
        desc = r.get("description", "")

        if rtype == "option_image":
            ident = f"key={r.get('key', '?')}"
        elif rtype == "inline_image":
            ident = f"id={r.get('id', '?')}"
        else:
            ident = "-"

        bbox_str = str(bbox) if bbox else "N/A"
        print(f"  {rtype:<16} {ident:<8} {bbox_str:<28} {desc[:30]}")

    print("-" * 80)

    # 统计
    type_counts = {}
    for r in regions:
        t = r.get("type", "unknown")
        type_counts[t] = type_counts.get(t, 0) + 1
    print(f"  统计: {type_counts}")
    print()


def main() -> int:
    args = parse_args()

    api_key = os.getenv("ZHIPUAI_API_KEY")
    if not api_key:
        print("[ERROR] 缺少环境变量: ZHIPUAI_API_KEY", file=sys.stderr)
        print("  设置方法 (PowerShell): $env:ZHIPUAI_API_KEY='your-key'")
        return 2

    # 构建图片内容
    image_content, local_file = build_image_content(args)

    # 调用视觉模型
    response = call_glm_vision(
        endpoint=args.endpoint,
        api_key=api_key,
        model=args.model,
        image_content=image_content,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
    )

    # 打印原始响应元信息
    print(f"\n[响应] model: {response.get('model')}")
    print(f"[响应] id: {response.get('id')}")
    usage = response.get("usage", {})
    print(f"[响应] tokens: prompt={usage.get('prompt_tokens')}, "
          f"completion={usage.get('completion_tokens')}, "
          f"total={usage.get('total_tokens')}")

    # 提取文本内容
    content = extract_content(response)
    print(f"\n[原始输出] ({len(content)} chars):")
    print("-" * 60)
    print(content)
    print("-" * 60)

    # 解析 JSON
    parsed = parse_regions(content)
    if parsed is None:
        print("\n[WARN] 无法解析为 JSON，请检查上方原始输出。")
        return 1

    regions = parsed.get("regions", [])
    print_regions_table(regions)

    # 输出完整 JSON（格式化）
    print("[解析后 JSON]:")
    print(json.dumps(parsed, ensure_ascii=False, indent=2))

    # 可视化
    if args.save_viz and local_file:
        out_path = Path(local_file).stem + "_regions.png"
        visualize_regions(local_file, regions, out_path)
    elif args.save_viz:
        print("[WARN] --save-viz 仅支持本地文件 (--file)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
