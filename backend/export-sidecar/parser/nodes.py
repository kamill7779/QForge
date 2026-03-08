"""AST 节点类型，描述 stem-xml / answer-xml 的结构化表示。"""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import List, Optional


class SegmentType(Enum):
    TEXT = auto()
    LATEX = auto()
    LATEX_BLOCK = auto()
    INLINE_BLANK = auto()


@dataclass
class Segment:
    kind: SegmentType
    value: str


@dataclass
class ParagraphNode:
    segments: List[Segment] = field(default_factory=list)


@dataclass
class ImageNode:
    ref: str = ""


@dataclass
class ChoiceNode:
    key: str = ""
    children: list = field(default_factory=list)


@dataclass
class ChoicesNode:
    mode: str = "single"
    choices: List[ChoiceNode] = field(default_factory=list)


@dataclass
class BlankNode:
    blank_id: str = ""


@dataclass
class BlanksNode:
    blanks: List[BlankNode] = field(default_factory=list)


@dataclass
class AnswerAreaNode:
    lines: int = 4


@dataclass
class TableCell:
    segments: List[Segment] = field(default_factory=list)


@dataclass
class TableNode:
    """<table> 节点 — thead + tbody。"""
    headers: List[TableCell] = field(default_factory=list)
    rows: List[List[TableCell]] = field(default_factory=list)


@dataclass
class StemNode:
    children: list = field(default_factory=list)


@dataclass
class AnswerRootNode:
    children: list = field(default_factory=list)
