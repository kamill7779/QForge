"""LaTeX → OMML 引擎 (latex2word — 纯 Python)。"""
from __future__ import annotations


class LatexEngine:
    """将 LaTeX 表达式渲染为 Word OMML 数学对象，追加到段落。"""

    def __init__(self):
        self._available = False
        try:
            from latex2word import LatexToWordElement as _cls
            self._cls = _cls
            self._available = True
        except ImportError:
            self._cls = None

    @property
    def available(self) -> bool:
        return self._available

    def add_latex_to_paragraph(
        self, paragraph, latex: str, block: bool = False
    ) -> bool:
        """将 LaTeX 追加到已有 paragraph 中。返回 True 成功, False 降级。"""
        if not self._available or not self._cls:
            return False
        try:
            elem = self._cls(latex)
            elem.add_latex_to_paragraph(paragraph)
            return True
        except Exception:
            return False
