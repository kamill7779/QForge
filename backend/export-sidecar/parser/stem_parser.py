"""stem-xml / answer-xml → AST Node Tree 解析器。

兼容前端 stemXml.ts 生成的 XML 结构，包括:
  <stem>, <answer>, <p>, <choices>, <choice>, <image>,
  <blanks>, <blank>, <answer-area>, <table>, <thead>, <tbody>, <tr>, <th>, <td>
"""
from __future__ import annotations
import re
import xml.etree.ElementTree as ET
from typing import List

from .nodes import (
    SegmentType, Segment, ParagraphNode, ImageNode,
    ChoiceNode, ChoicesNode, BlankNode, BlanksNode,
    AnswerAreaNode, TableNode, TableCell, StemNode, AnswerRootNode,
)

# ── LaTeX 正则: $$...$$ (block) 或 $...$ (inline) ──
LATEX_RE = re.compile(
    r'(\$\$(?:[^$]|\$(?!\$))+?\$\$'   # block $$...$$
    r'|\$(?:[^$\\]|\\.)+?\$)'          # inline $...$
)


def _parse_text_segments(text: str) -> List[Segment]:
    """将含 LaTeX 的纯文本拆分为 Text / Latex / LatexBlock segments。"""
    if not text:
        return []
    segments: List[Segment] = []
    last = 0
    for m in LATEX_RE.finditer(text):
        before = text[last:m.start()]
        if before:
            segments.append(Segment(SegmentType.TEXT, before))
        raw = m.group(0)
        if raw.startswith("$$") and raw.endswith("$$"):
            segments.append(Segment(SegmentType.LATEX_BLOCK, raw[2:-2].strip()))
        else:
            segments.append(Segment(SegmentType.LATEX, raw[1:-1].strip()))
        last = m.end()
    tail = text[last:]
    if tail:
        segments.append(Segment(SegmentType.TEXT, tail))
    return segments


def _parse_p_children(elem: ET.Element) -> List[Segment]:
    """解析 <p> 内混合内容: 文本 + <blank/> + LaTeX。"""
    segs: List[Segment] = []
    if elem.text:
        segs.extend(_parse_text_segments(elem.text))
    for child in elem:
        tag = (child.tag or "").lower()
        if tag == "blank":
            segs.append(Segment(SegmentType.INLINE_BLANK, child.get("id", "")))
        if child.tail:
            segs.extend(_parse_text_segments(child.tail))
    return segs


def _parse_cell(elem: ET.Element) -> TableCell:
    """解析 <th> 或 <td> 为 TableCell。"""
    return TableCell(segments=_parse_text_segments(
        (elem.text or "").strip()
    ))


def _parse_element(elem: ET.Element):
    """递归解析单个 XML 元素 → AST Node。"""
    tag = (elem.tag or "").lower()

    if tag == "p":
        return ParagraphNode(segments=_parse_p_children(elem))

    if tag == "image":
        return ImageNode(ref=elem.get("ref", ""))

    if tag == "choices":
        cn = ChoicesNode(mode=elem.get("mode", "single"))
        for ch in elem:
            if (ch.tag or "").lower() == "choice":
                choice = ChoiceNode(key=ch.get("key", ""))
                for sub in ch:
                    n = _parse_element(sub)
                    if n:
                        choice.children.append(n)
                # 简短文本直接写在 <choice> 内部
                if not choice.children and ch.text and ch.text.strip():
                    choice.children.append(
                        ParagraphNode(segments=_parse_text_segments(ch.text.strip()))
                    )
                cn.choices.append(choice)
        return cn

    if tag == "blanks":
        bn = BlanksNode()
        for ch in elem:
            if (ch.tag or "").lower() == "blank":
                bn.blanks.append(BlankNode(blank_id=ch.get("id", "")))
        return bn

    if tag == "blank":
        return BlankNode(blank_id=elem.get("id", ""))

    if tag == "answer-area":
        lines = 4
        try:
            lines = int(elem.get("lines", "4"))
        except ValueError:
            pass
        return AnswerAreaNode(lines=lines)

    if tag == "table":
        return _parse_table(elem)

    return None


def _parse_table(elem: ET.Element) -> TableNode:
    """解析 <table> (可包含 <thead>/<tbody> 或直接 <tr>)。"""
    node = TableNode()
    thead = elem.find("thead")
    tbody = elem.find("tbody")

    if thead is not None:
        for tr in thead.findall("tr"):
            for th in tr:
                if (th.tag or "").lower() in ("th", "td"):
                    node.headers.append(_parse_cell(th))
    if tbody is not None:
        for tr in tbody.findall("tr"):
            row = []
            for td in tr:
                if (td.tag or "").lower() in ("td", "th"):
                    row.append(_parse_cell(td))
            if row:
                node.rows.append(row)
    else:
        # 无 tbody 时直接从 <table> 子级 <tr> 读取
        trs = elem.findall("tr")
        for i, tr in enumerate(trs):
            cells = []
            for cell in tr:
                if (cell.tag or "").lower() in ("th", "td"):
                    cells.append(_parse_cell(cell))
            if i == 0 and not node.headers:
                node.headers = cells
            else:
                if cells:
                    node.rows.append(cells)
    return node


# ── 公开 API ──

def parse_stem_xml(xml_str: str) -> StemNode:
    """解析 stem XML 字符串 → StemNode AST。"""
    root_node = StemNode()
    if not xml_str or not xml_str.strip():
        return root_node
    try:
        root = ET.fromstring(xml_str.strip())
    except ET.ParseError:
        root_node.children.append(
            ParagraphNode(segments=[Segment(SegmentType.TEXT, xml_str)])
        )
        return root_node
    for child in root:
        n = _parse_element(child)
        if n:
            root_node.children.append(n)
    return root_node


def _unwrap_double_encoded(root: ET.Element) -> ET.Element | None:
    """检测并解包双重编码的 answer XML。

    某些答案在 DB 中以双重包装形式存储:
      <answer><p>&lt;answer&gt;...&lt;/answer&gt;</p></answer>
    外层 <p> 的文本内容本身是一段 answer XML。需要提取内层真实内容。
    """
    import html
    children = list(root)
    if len(children) != 1:
        return None
    p = children[0]
    if (p.tag or "").lower() != "p":
        return None
    text = (p.text or "")
    # 检测内层是否以 <answer 或 <p 开头
    stripped = text.lstrip()
    if not (stripped.startswith("<answer") or stripped.startswith("<p")):
        return None
    # 替换 literal \n 为真实换行
    inner = text.replace("\\n", "\n")
    try:
        return ET.fromstring(inner)
    except ET.ParseError:
        # 尝试 html unescape 后再解析
        try:
            return ET.fromstring(html.unescape(inner))
        except ET.ParseError:
            return None


def _strip_xml_tags(s: str) -> str:
    """从原始 XML 字符串中剥离标签，保留文本内容。"""
    import html
    # 去掉 literal \n
    s = s.replace("\\n", " ")
    # 去掉所有标签
    cleaned = re.sub(r'<[^>]+>', '', html.unescape(s))
    return cleaned.strip()


def parse_answer_xml(xml_str: str) -> AnswerRootNode:
    """解析 answer XML 字符串 → AnswerRootNode AST。

    处理多种格式:
    1. 标准 <answer><p>文本</p></answer>
    2. 双重包装 <answer><p>&lt;answer&gt;...&lt;/answer&gt;</p></answer>
    3. 纯文本（无 XML 包装）
    4. XML 解析失败时的 fallback
    """
    root_node = AnswerRootNode()
    if not xml_str or not xml_str.strip():
        return root_node
    s = xml_str.strip()
    if not s.startswith("<answer"):
        # 纯文本，直接用 LaTeX 解析
        root_node.children.append(
            ParagraphNode(segments=_parse_text_segments(s))
        )
        return root_node
    try:
        root = ET.fromstring(s)
    except ET.ParseError:
        # XML 解析失败 → 剥离标签后做 LaTeX 解析
        cleaned = _strip_xml_tags(s)
        root_node.children.append(
            ParagraphNode(segments=_parse_text_segments(cleaned))
        )
        return root_node

    # 检查是否双重包装
    unwrapped = _unwrap_double_encoded(root)
    if unwrapped is not None:
        root = unwrapped

    for child in root:
        n = _parse_element(child)
        if n:
            root_node.children.append(n)
    return root_node
