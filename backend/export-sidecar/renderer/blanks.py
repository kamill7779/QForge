"""填空渲染器。"""
from __future__ import annotations

from parser.nodes import BlankNode, BlanksNode
from .context import RenderContext
from .helpers import style_run


class BlanksRenderer:

    def __init__(self, ctx: RenderContext):
        self.ctx = ctx

    def render(self, node, container) -> None:
        if isinstance(node, BlankNode):
            blanks = [node]
        elif isinstance(node, BlanksNode):
            blanks = node.blanks
        else:
            return
        if not blanks:
            return
        p = container.add_paragraph()
        for i, b in enumerate(blanks):
            if i > 0:
                p.add_run("    ")
            run = p.add_run(f"({b.blank_id}) ____________")
            style_run(run, self.ctx)
