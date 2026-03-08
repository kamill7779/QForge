"""题目数据模型 — 与 question-service 的 DTO 对齐。"""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Dict, List


@dataclass
class AssetData:
    ref_key: str
    image_data: str       # base64 string
    mime_type: str = "image/png"


@dataclass
class AnswerData:
    answer_uuid: str
    latex_text: str       # answer XML 或纯文本
    sort_order: int = 1
    assets: Dict[str, AssetData] = field(default_factory=dict)


@dataclass
class QuestionData:
    question_uuid: str
    stem_xml: str
    difficulty: float | None = None
    answers: List[AnswerData] = field(default_factory=list)
    stem_assets: Dict[str, AssetData] = field(default_factory=dict)
    main_tags: List[dict] = field(default_factory=list)
    secondary_tags: List[str] = field(default_factory=list)
