"""渲染器注册表 — 按 AST NodeType 分发到对应 renderer。"""
from __future__ import annotations

from parser.nodes import (
    ParagraphNode, ImageNode, ChoicesNode,
    BlanksNode, BlankNode, AnswerAreaNode, TableNode,
)
from .context import RenderContext
from .paragraph import ParagraphRenderer
from .image import ImageRenderer
from .choices import ChoicesRenderer
from .blanks import BlanksRenderer
from .answer_area import AnswerAreaRenderer
from .table import TableRenderer

RENDERER_MAP = {
    ParagraphNode: ParagraphRenderer,
    ImageNode: ImageRenderer,
    ChoicesNode: ChoicesRenderer,
    BlanksNode: BlanksRenderer,
    BlankNode: BlanksRenderer,
    AnswerAreaNode: AnswerAreaRenderer,
    TableNode: TableRenderer,
}


def render_node(node, ctx: RenderContext, container) -> None:
    """将 AST node 渲染到 python‑docx container (Document / Cell)。"""
    cls = RENDERER_MAP.get(type(node))
    if cls:
        cls(ctx).render(node, container)
