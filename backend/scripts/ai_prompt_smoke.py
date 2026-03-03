#!/usr/bin/env python3
"""
AI 提示词烟雾测试脚本
——从数据库查询最长题干+答案，构造与生产一致的用户提示词，调用 ZhipuAI，打印完整响应。

用法:
    pip install mysql-connector-python zhipuai
    export ZHIPUAI_API_KEY=<your_key>
    python ai_prompt_smoke.py [--host localhost] [--port 3306] [--db qforge]
                               [--user qforge] [--password qforge]
                               [--model glm-5] [--max-tokens 4096]
"""

import argparse
import json
import os
import sys
import time

# ---------- 系统提示词（与 AiAnalysisTaskConsumer.SYSTEM_PROMPT 保持同步）---------
SYSTEM_PROMPT = (
    "你是教育评估专家。分析题目，完成两项任务并以JSON格式输出结果，不要输出分析过程。\n"
    "任务1：推荐2-5个知识点标签（中文，粒度适中，如\"二次函数\"而非\"数学\"）。\n"
    "任务2：估算P值（通过率0.00-1.00）：0.9+极简单, 0.7-0.9简单, 0.3-0.7中等, 0.1-0.3困难, 0-0.1专家难度。\n"
    "只输出如下JSON，禁止任何额外内容：\n"
    "{\"tags\":[\"标签1\",\"标签2\"],\"difficulty\":0.65,\"reasoning\":\"50字内评分依据\"}"
)

MAX_STEM_CHARS = 8000
MAX_ANSWER_CHARS = 2000
MAX_ANSWERS = 6


def truncate(text: str, max_chars: int) -> str:
    if not text:
        return ""
    text = text.strip()
    if len(text) <= max_chars:
        return text
    head = int(max_chars * 0.7)
    tail = max_chars - head
    return text[:head] + "\n...[内容过长，已截断]...\n" + text[-tail:]


def build_user_prompt(stem: str, answers: list[str]) -> str:
    sb = ["题目内容\n\n题干:\n", truncate(stem, MAX_STEM_CHARS), "\n\n"]
    if answers:
        sb.append("答案:\n")
        for i, ans in enumerate(answers[:MAX_ANSWERS], 1):
            sb.append(f"答案 {i}: {truncate(ans, MAX_ANSWER_CHARS)}\n")
    return "".join(sb)


def fetch_longest_question(conn):
    """找 stem_text 最长的未删除题目及其所有答案。"""
    cursor = conn.cursor(dictionary=True)
    cursor.execute(
        """
        SELECT id, question_uuid, stem_text
        FROM q_question
        WHERE deleted = 0 AND stem_text IS NOT NULL AND stem_text != ''
        ORDER BY CHAR_LENGTH(stem_text) DESC
        LIMIT 1
        """
    )
    q = cursor.fetchone()
    if not q:
        print("[WARN] 数据库中没有有效题目，改为使用样例数据。")
        return None, None, []

    cursor.execute(
        """
        SELECT latex_text
        FROM q_answer
        WHERE question_id = %s AND deleted = 0 AND latex_text IS NOT NULL
        ORDER BY sort_order
        LIMIT %s
        """,
        (q["id"], MAX_ANSWERS),
    )
    answers = [row["latex_text"] for row in cursor.fetchall() if row["latex_text"]]
    cursor.close()
    return q["question_uuid"], q["stem_text"], answers


def main():
    parser = argparse.ArgumentParser(description="ZhipuAI 提示词烟雾测试")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "localhost"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--db", default=os.getenv("MYSQL_DB", "qforge"))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "qforge"))
    parser.add_argument("--password", default=os.getenv("MYSQL_PASSWORD", "qforge"))
    parser.add_argument("--model", default=os.getenv("ZHIPUAI_MODEL", "glm-5"))
    parser.add_argument("--max-tokens", type=int, default=int(os.getenv("ZHIPUAI_MAX_TOKENS", "4096")))
    parser.add_argument(
        "--api-key", default=os.getenv("ZHIPUAI_API_KEY", ""),
        help="ZhipuAI API Key（也可通过 ZHIPUAI_API_KEY 环境变量设置）"
    )
    args = parser.parse_args()

    api_key = args.api_key
    if not api_key:
        print("[ERROR] 未找到 ZHIPUAI_API_KEY，请设置环境变量或传入 --api-key 参数。")
        sys.exit(1)

    # -------- 1. 从 DB 获取题目 --------
    question_uuid = None
    stem = None
    answers = []

    try:
        import mysql.connector  # type: ignore
        conn = mysql.connector.connect(
            host=args.host,
            port=args.port,
            database=args.db,
            user=args.user,
            password=args.password,
            charset="utf8mb4",
        )
        print(f"[INFO] 已连接数据库 {args.host}:{args.port}/{args.db}")
        question_uuid, stem, answers = fetch_longest_question(conn)
        conn.close()
    except ImportError:
        print("[WARN] 未安装 mysql-connector-python，使用内置样例数据。")
        print("       pip install mysql-connector-python")
    except Exception as e:
        print(f"[WARN] 数据库连接失败: {e}，使用内置样例数据。")

    if not stem:
        question_uuid = "sample-question-uuid"
        stem = (
            "设函数 $f(x) = ax^2 + bx + c$（$a \\neq 0$），其图像过点 $(1, 3)$ 和 $(-1, 1)$，"
            "且对称轴为直线 $x = 1$。求：\n"
            "(1) $f(x)$ 的表达式；\n"
            "(2) $f(x)$ 在 $[-2, 3]$ 上的最大值和最小值。"
        )
        answers = [
            r"(1) 由对称轴 $x=-\frac{b}{2a}=1$ 得 $b=-2a$。\n"
            r"代入 $(1,3)$：$a+b+c=3$；代入 $(-1,1)$：$a-b+c=1$。\n"
            r"解方程组得 $a=1, b=-2, c=3$，故 $f(x)=x^2-2x+3$。\n"
            r"(2) $f(x)=(x-1)^2+2$，顶点 $(1,2)$。\n"
            r"在 $[-2,3]$ 上，$x=1$ 时最小值 $f(1)=2$；$x=-2$ 时 $f(-2)=11$，\n"
            r"$x=3$ 时 $f(3)=6$，故最大值为 $f(-2)=11$。"
        ]
        print("[INFO] 使用内置样例数据（二次函数题）")

    # -------- 2. 构造提示词 --------
    user_prompt = build_user_prompt(stem, answers)
    print(f"\n{'='*60}")
    print(f"[INFO] question_uuid: {question_uuid}")
    print(f"[INFO] stem 长度: {len(stem)} 字符，答案数: {len(answers)}")
    print(f"\n--- 用户提示词 (前500字) ---\n{user_prompt[:500]}")
    if len(user_prompt) > 500:
        print(f"... (共 {len(user_prompt)} 字符)")
    print(f"{'='*60}\n")

    # -------- 3. 调用 ZhipuAI --------
    try:
        from zhipuai import ZhipuAI  # type: ignore
    except ImportError:
        print("[ERROR] 未安装 zhipuai SDK，请执行：pip install zhipuai")
        sys.exit(1)

    client = ZhipuAI(api_key=api_key)
    print(f"[INFO] 调用模型: {args.model}，max_tokens: {args.max_tokens}")
    t0 = time.time()

    try:
        response = client.chat.completions.create(
            model=args.model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=args.max_tokens,
            temperature=0.1,
        )
    except Exception as e:
        print(f"[ERROR] API 调用失败: {e}")
        sys.exit(1)

    elapsed = time.time() - t0
    print(f"[INFO] 响应耗时: {elapsed:.2f}s\n")

    # -------- 4. 打印完整响应 --------
    choice = response.choices[0] if response.choices else None
    if not choice:
        print("[ERROR] 响应中没有 choices")
        sys.exit(1)

    finish_reason = choice.finish_reason
    msg = choice.message
    content = getattr(msg, "content", None) or ""
    reasoning_content = getattr(msg, "reasoning_content", None) or ""

    usage = response.usage
    prompt_tokens = getattr(usage, "prompt_tokens", "N/A")
    completion_tokens = getattr(usage, "completion_tokens", "N/A")
    total_tokens = getattr(usage, "total_tokens", "N/A")

    print(f"{'='*60}")
    print(f"finish_reason : {finish_reason}")
    print(f"prompt_tokens : {prompt_tokens}")
    print(f"completion_tokens: {completion_tokens}")
    print(f"total_tokens  : {total_tokens}")
    print(f"{'='*60}")

    if reasoning_content:
        print(f"\n--- reasoning_content ({len(reasoning_content)} 字符) ---")
        print(reasoning_content[:2000])
        if len(reasoning_content) > 2000:
            print(f"... (截断，共 {len(reasoning_content)} 字符)")

    print(f"\n--- content ({len(content)} 字符) ---")
    print(content if content else "(空)")

    # 尝试解析 JSON
    raw = content.strip()
    if not raw and reasoning_content:
        # fallback: 从 reasoning 中提取 JSON
        import re
        m = re.search(r'\{[^{}]*"tags"[^{}]*\}', reasoning_content, re.DOTALL)
        if m:
            raw = m.group(0)
            print(f"\n[INFO] 从 reasoning_content 提取到 JSON: {raw}")

    if raw:
        try:
            parsed = json.loads(raw)
            print(f"\n{'='*60}")
            print("[OK] JSON 解析成功:")
            print(f"  tags       : {parsed.get('tags')}")
            print(f"  difficulty : {parsed.get('difficulty')}")
            print(f"  reasoning  : {parsed.get('reasoning')}")
            print(f"{'='*60}")
        except json.JSONDecodeError as e:
            print(f"\n[WARN] JSON 解析失败: {e}")
            print(f"原始内容: {raw[:500]}")
    else:
        print("\n[ERROR] content 和 reasoning_content 均未包含有效 JSON。")
        if finish_reason == "length":
            print("[HINT] finish_reason=length，说明 max_tokens 太小。请尝试增大 --max-tokens。")


if __name__ == "__main__":
    main()
