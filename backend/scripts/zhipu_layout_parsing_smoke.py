#!/usr/bin/env python3
"""Simple smoke test for official Z.AI GLM-OCR layout parsing API.

Usage (PowerShell):
  $env:ZHIPU_API_KEY="your-api-key"
  python backend/scripts/zhipu_layout_parsing_smoke.py

Optional arguments:
  --file https://cdn.bigmodel.cn/static/logo/introduction.png
  --model glm-ocr
  --endpoint https://api.z.ai/api/paas/v4/layout_parsing
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Z.AI GLM-OCR smoke test")
    parser.add_argument(
        "--file",
        default="https://cdn.bigmodel.cn/static/logo/introduction.png",
        help="Public file URL for OCR parsing.",
    )
    parser.add_argument("--model", default="glm-ocr", help="Model name.")
    parser.add_argument(
        "--endpoint",
        default="https://api.z.ai/api/paas/v4/layout_parsing",
        help="Layout parsing endpoint.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    api_key = os.getenv("ZHIPU_API_KEY")
    if not api_key:
        print("Missing env var: ZHIPU_API_KEY", file=sys.stderr)
        return 2

    payload = {
        "model": args.model,
        "file": args.file,
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        args.endpoint,
        data=data,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            status = resp.getcode()
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        print(f"HTTP error: {exc.code}", file=sys.stderr)
        print(body, file=sys.stderr)
        return 1
    except Exception as exc:  # noqa: BLE001
        print(f"Request failed: {exc}", file=sys.stderr)
        return 1

    print(f"HTTP status: {status}")
    try:
        obj = json.loads(body)
    except json.JSONDecodeError:
        print("Non-JSON response:")
        print(body[:2000])
        return 0

    print("Response keys:", list(obj.keys()))
    if "id" in obj:
        print("id:", obj.get("id"))
    if "model" in obj:
        print("model:", obj.get("model"))

    # Common output fields observed in docs/responses.
    md_results = obj.get("md_results")
    if isinstance(md_results, str):
        print("md_results_preview:")
        print(md_results[:1200])

    data_field = obj.get("data")
    if data_field is not None:
        print("data_preview:")
        print(json.dumps(data_field, ensure_ascii=False)[:1200])

    if md_results is None and data_field is None:
        print("raw_response_preview:")
        print(json.dumps(obj, ensure_ascii=False)[:1600])

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

