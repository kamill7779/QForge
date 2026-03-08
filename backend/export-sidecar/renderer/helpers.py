"""共享工具函数 — run 字体设置。"""
from __future__ import annotations

from docx.oxml.ns import qn
from lxml import etree

from .context import RenderContext


def ensure_rpr(run):
    """确保 run._element 有 rPr/rFonts 子节点。"""
    rpr = run._element.find(qn("w:rPr"))
    if rpr is None:
        rpr = etree.SubElement(run._element, qn("w:rPr"))
    rFonts = rpr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = etree.SubElement(rpr, qn("w:rFonts"))
    return rFonts


def ensure_rpr_style(style):
    """对 style.element 做 rPr/rFonts 确保。"""
    elem = style.element
    rpr = elem.find(qn("w:rPr"))
    if rpr is None:
        rpr = etree.SubElement(elem, qn("w:rPr"))
    rf = rpr.find(qn("w:rFonts"))
    if rf is None:
        rf = etree.SubElement(rpr, qn("w:rFonts"))
    return rf


def style_run(run, ctx: RenderContext, italic: bool = False):
    """统一设置 run 字体（中英双字体）。"""
    run.font.size = ctx.font_size
    run.font.name = ctx.font_name_en
    rFonts = ensure_rpr(run)
    rFonts.set(qn("w:eastAsia"), ctx.font_name_cn)
    if italic:
        run.italic = True
