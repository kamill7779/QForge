"""组卷数据模型。"""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional


class AnswerPosition(str, Enum):
    AFTER_ALL = "AFTER_ALL"
    AFTER_EACH_QUESTION = "AFTER_EACH_QUESTION"


@dataclass
class ExportOptions:
    include_answers: bool = True
    answer_position: AnswerPosition = AnswerPosition.AFTER_ALL


@dataclass
class ComposeItem:
    item_id: int
    sort_order: int
    question_uuid: str


@dataclass
class ComposeSection:
    section_id: int
    section_title: str
    sort_order: int
    items: List[ComposeItem] = field(default_factory=list)


@dataclass
class ComposeData:
    compose_uuid: str
    title: str
    description: Optional[str] = None
    total_questions: int = 0
    sections: List[ComposeSection] = field(default_factory=list)


@dataclass
class ExportSectionDef:
    """用户在散题导出时自定义的分区定义。"""
    title: str                          # e.g. "一、单选题"
    question_uuids: List[str] = field(default_factory=list)
