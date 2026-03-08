"""选项渲染器 — 三级自适应布局 (4列 / 2列 / 逐行)。"""
from __future__ import annotations

from docx.shared import Pt, Cm
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from lxml import etree

from parser.nodes import ChoicesNode, ChoiceNode, ParagraphNode, ImageNode
from .context import RenderContext
from .paragraph import ParagraphRenderer
from .image import ImageRenderer
from .helpers import style_run


class ChoicesRenderer:
    VERY_SHORT = 8
    SHORT = 20

    def __init__(self, ctx: RenderContext):
        self.ctx = ctx

    def render(self, node: ChoicesNode, container) -> None:
        para_r = ParagraphRenderer(self.ctx)
        has_img = any(self._has_image(c) for c in node.choices)

        if has_img:
            self._render_list(node, container, para_r, with_choice_images=True)
            return

        max_len = max(
            (self._choice_text_len(c) for c in node.choices), default=0
        )
        n = len(node.choices)

        if max_len <= self.VERY_SHORT and n == 4:
            self._render_grid(node, container, para_r, cols=4)
        elif max_len <= self.SHORT and n in (4, 2):
            self._render_grid(node, container, para_r, cols=2)
        else:
            self._render_list(node, container, para_r)

    # ── 逐行 ──
    def _render_list(self, node, container, para_r, with_choice_images=False):
        for choice in node.choices:
            p = container.add_paragraph()
            pf = p.paragraph_format
            pf.space_before = Pt(1)
            pf.space_after = Pt(1)
            pf.left_indent = Cm(0.8)
            run = p.add_run(f"{choice.key}. ")
            run.font.bold = True
            style_run(run, self.ctx)
            for child in choice.children:
                if isinstance(child, ParagraphNode):
                    para_r.render(child, container, paragraph=p)
                elif isinstance(child, ImageNode):
                    ir = ImageRenderer(self.ctx)
                    ir.render(
                        child,
                        container,
                        max_w=ImageRenderer.CHOICE_MAX_WIDTH,
                        max_h=ImageRenderer.CHOICE_MAX_HEIGHT,
                    )

    # ── 表格网格 ──
    def _render_grid(self, node, container, para_r, cols=2):
        table = container.add_table(rows=0, cols=cols)
        table.alignment = WD_TABLE_ALIGNMENT.LEFT
        tbl = table._tbl
        tblPr = tbl.tblPr
        if tblPr is None:
            tblPr = etree.SubElement(tbl, qn("w:tblPr"))
        borders = etree.SubElement(tblPr, qn("w:tblBorders"))
        for bn in ("top", "left", "bottom", "right", "insideH", "insideV"):
            b = etree.SubElement(borders, qn(f"w:{bn}"))
            b.set(qn("w:val"), "none")
            b.set(qn("w:sz"), "0")
            b.set(qn("w:space"), "0")
            b.set(qn("w:color"), "auto")

        row = None
        for i, choice in enumerate(node.choices):
            if i % cols == 0:
                row = table.add_row()
            cell = row.cells[i % cols]  # type: ignore[union-attr]
            p = cell.paragraphs[0] if cell.paragraphs else cell.add_paragraph()
            run = p.add_run(f"{choice.key}. ")
            run.font.bold = True
            style_run(run, self.ctx)
            for child in choice.children:
                if isinstance(child, ParagraphNode):
                    para_r.render(child, container=None, paragraph=p)

    @staticmethod
    def _choice_text_len(c: ChoiceNode) -> int:
        total = 0
        for ch in c.children:
            if isinstance(ch, ParagraphNode):
                total += sum(len(s.value) for s in ch.segments)
        return total

    @staticmethod
    def _has_image(c: ChoiceNode) -> bool:
        return any(isinstance(ch, ImageNode) for ch in c.children)
