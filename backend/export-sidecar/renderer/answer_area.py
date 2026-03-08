"""解答区渲染器 — 预留空白行。"""
from __future__ import annotations

from docx.shared import Pt

from parser.nodes import AnswerAreaNode
from .context import RenderContext
from .helpers import style_run


class AnswerAreaRenderer:

    def __init__(self, ctx: RenderContext):
        self.ctx = ctx

    def render(self, node: AnswerAreaNode, container) -> None:
        for _ in range(node.lines):
            p = container.add_paragraph()
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = Pt(24)
            run = p.add_run(" ")
            style_run(run, self.ctx)
