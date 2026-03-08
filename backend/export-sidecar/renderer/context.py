"""渲染上下文 — 由 Assembler 创建，传递给每个 Renderer。"""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Dict, Optional

from docx import Document
from docx.shared import Pt

from parser.latex_engine import LatexEngine
from models.question import AssetData


@dataclass
class RenderContext:
    document: Document
    latex_engine: LatexEngine
    image_resolver: Dict[str, AssetData] = field(default_factory=dict)
    question_number: Optional[int] = None
    font_name_cn: str = "宋体"
    font_name_en: str = "Times New Roman"
    font_size: Pt = Pt(11)
