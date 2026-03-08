"""LaTeX → OMML 引擎 (latex2word — 纯 Python)。"""
from __future__ import annotations

import re


# ── LaTeX 预处理: 替换 latex2word 不支持的命令 ──
_LATEX_PREPROCESS = [
    # \overrightarrow{X} → \overset{\rightarrow}{X}   (groupChr bug)
    (re.compile(r'\\overrightarrow\{([^}]*)\}'), r'\\overset{\\rightarrow}{\1}'),
    # \vec{X} → \overset{\rightarrow}{X}
    (re.compile(r'\\vec\{([^}]*)\}'), r'\\overset{\\rightarrow}{\1}'),
    # \bar{X} → \overline{X}
    (re.compile(r'\\bar\{([^}]*)\}'), r'\\overline{\1}'),
]


def _preprocess_latex(latex: str) -> str:
    """将 latex2word 不支持的命令替换为等效的兼容命令。"""
    s = latex
    for pattern, repl in _LATEX_PREPROCESS:
        s = pattern.sub(repl, s)
    return s


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
            processed = _preprocess_latex(latex)
            elem = self._cls(processed)
            elem.add_latex_to_paragraph(paragraph)
            return True
        except Exception:
            return False
