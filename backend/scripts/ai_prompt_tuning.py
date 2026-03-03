#!/usr/bin/env python3
"""Prompt budget tuner for AI analysis (stem + answers are mandatory).

Usage examples:
  python backend/scripts/ai_prompt_tuning.py \
    --stem-file sample_stem.xml \
    --answers-file sample_answers.txt

  python backend/scripts/ai_prompt_tuning.py \
    --stem-file sample_stem.xml \
    --answers-file sample_answers.txt \
    --max-stem 4000,5000,6000 \
    --max-answer 800,1200 \
    --max-answers 3,4,5
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Budget:
    max_stem: int
    max_answer: int
    max_answers: int


def parse_csv_ints(value: str) -> list[int]:
    return [int(x.strip()) for x in value.split(",") if x.strip()]


def truncate(text: str, limit: int) -> str:
    text = (text or "").strip()
    if len(text) <= limit:
        return text
    head = int(limit * 0.7)
    tail = max(1, limit - head)
    return text[:head] + "\n...[内容过长，已截断]...\n" + text[-tail:]


def build_prompt(stem: str, answers: list[str], budget: Budget) -> str:
    trimmed_stem = truncate(stem, budget.max_stem)
    picked = [truncate(x, budget.max_answer) for x in answers[: budget.max_answers]]

    lines = ["题目内容", "", "题干:", trimmed_stem, ""]
    if picked:
        lines.append("答案:")
        for i, answer in enumerate(picked, start=1):
            lines.append(f"答案 {i}: {answer}")
        if len(answers) > len(picked):
            lines.append(f"(其余 {len(answers) - len(picked)} 条答案已截断)")
    return "\n".join(lines)


def count_chars(text: str) -> int:
    return len(text or "")


def estimate_tokens(text: str) -> int:
    # 粗略估算：中文/混合文本约 1 token ≈ 1.5~2 chars，这里用保守值 1.8
    return int(count_chars(text) / 1.8)


def load_answers(path: Path) -> list[str]:
    raw = path.read_text(encoding="utf-8")
    parts = [x.strip() for x in raw.split("\n\n")]
    return [x for x in parts if x]


def main() -> int:
    parser = argparse.ArgumentParser(description="Tune prompt budget for AI analysis")
    parser.add_argument("--stem-file", required=True, help="Stem content file path")
    parser.add_argument("--answers-file", required=True, help="Answers file path; separate answers by blank line")
    parser.add_argument("--max-stem", default="4000,5000,6000")
    parser.add_argument("--max-answer", default="800,1200")
    parser.add_argument("--max-answers", default="3,4")
    parser.add_argument("--system-prompt-chars", type=int, default=220)
    parser.add_argument("--output-max-tokens", type=int, default=1536)
    args = parser.parse_args()

    stem = Path(args.stem_file).read_text(encoding="utf-8")
    answers = load_answers(Path(args.answers_file))

    if not stem.strip():
        raise SystemExit("stem 不能为空")
    if not answers:
        raise SystemExit("answers 不能为空（必须注入）")

    stem_budgets = parse_csv_ints(args.max_stem)
    answer_budgets = parse_csv_ints(args.max_answer)
    answer_counts = parse_csv_ints(args.max_answers)

    print("budget,max_stem,max_answer,max_answers,user_chars,user_tokens_est,total_tokens_est,output_max_tokens,headroom")
    idx = 1
    for ms in stem_budgets:
        for ma in answer_budgets:
            for mc in answer_counts:
                budget = Budget(ms, ma, mc)
                prompt = build_prompt(stem, answers, budget)
                user_chars = count_chars(prompt)
                user_tokens = estimate_tokens(prompt)
                total_tokens = user_tokens + estimate_tokens("x" * args.system_prompt_chars)
                headroom = args.output_max_tokens - total_tokens
                print(
                    f"{idx},{ms},{ma},{mc},{user_chars},{user_tokens},{total_tokens},{args.output_max_tokens},{headroom}"
                )
                idx += 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
