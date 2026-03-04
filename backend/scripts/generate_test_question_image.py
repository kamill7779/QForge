#!/usr/bin/env python3
"""生成一张模拟的高考数学选择题图片（含题干配图 + 4个选项图），用于测试视觉模型坐标提取。"""

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 800, 1000
BG = (255, 255, 255)
FG = (0, 0, 0)


def get_font(size: int):
    """尝试加载中文字体。"""
    for name in [
        "C:/Windows/Fonts/msyh.ttc",  # 微软雅黑
        "C:/Windows/Fonts/simsun.ttc",  # 宋体
        "C:/Windows/Fonts/simhei.ttf",  # 黑体
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    ]:
        try:
            return ImageFont.truetype(name, size)
        except (OSError, IOError):
            continue
    return ImageFont.load_default()


def draw_triangle(draw: ImageDraw.Draw, cx: int, cy: int, r: int):
    """画一个等边三角形并标注顶点。"""
    pts = []
    for i in range(3):
        angle = math.radians(-90 + i * 120)
        pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(pts, outline=FG, width=2)
    labels = ["A", "B", "C"]
    font = get_font(16)
    offsets = [(-5, -22), (-18, 10), (8, 10)]
    for (px, py), label, (ox, oy) in zip(pts, labels, offsets):
        draw.text((px + ox, py + oy), label, fill=FG, font=font)
    # 标注边长
    mid01 = ((pts[0][0] + pts[1][0]) / 2 - 15, (pts[0][1] + pts[1][1]) / 2 - 15)
    draw.text(mid01, "3", fill=(200, 0, 0), font=font)
    mid12 = ((pts[1][0] + pts[2][0]) / 2, (pts[1][1] + pts[2][1]) / 2 + 5)
    draw.text(mid12, "4", fill=(200, 0, 0), font=font)


def draw_circle_graph(draw: ImageDraw.Draw, cx: int, cy: int, r: int):
    """画一个圆+内接正方形 (选项A图)。"""
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=FG, width=2)
    s = int(r / math.sqrt(2))
    draw.rectangle([cx - s, cy - s, cx + s, cy + s], outline=(0, 0, 200), width=2)


def draw_parabola(draw: ImageDraw.Draw, cx: int, cy: int, w: int, h: int):
    """画一个抛物线 (选项B图)。"""
    # 坐标轴
    draw.line([(cx - w, cy), (cx + w, cy)], fill=(150, 150, 150), width=1)
    draw.line([(cx, cy + h), (cx, cy - h)], fill=(150, 150, 150), width=1)
    # 抛物线
    pts = []
    for x in range(-w, w + 1, 2):
        y = -(x * x) / (w * 2)
        pts.append((cx + x, cy + y))
    if len(pts) >= 2:
        draw.line(pts, fill=(200, 0, 0), width=2)


def draw_sine(draw: ImageDraw.Draw, cx: int, cy: int, w: int, h: int):
    """画一个正弦曲线 (选项C图)。"""
    draw.line([(cx - w, cy), (cx + w, cy)], fill=(150, 150, 150), width=1)
    draw.line([(cx, cy + h), (cx, cy - h)], fill=(150, 150, 150), width=1)
    pts = []
    for x in range(-w, w + 1, 2):
        y = h * 0.8 * math.sin(x / w * 2 * math.pi)
        pts.append((cx + x, cy - y))
    if len(pts) >= 2:
        draw.line(pts, fill=(0, 128, 0), width=2)


def draw_bar_chart(draw: ImageDraw.Draw, cx: int, cy: int, w: int, h: int):
    """画一个柱状图 (选项D图)。"""
    draw.line([(cx - w, cy), (cx + w, cy)], fill=FG, width=1)
    draw.line([(cx - w, cy), (cx - w, cy - h)], fill=FG, width=1)
    bar_w = w // 3
    heights = [0.6, 0.9, 0.4, 0.75]
    colors = [(66, 133, 244), (234, 67, 53), (251, 188, 4), (52, 168, 83)]
    for i, (bh, c) in enumerate(zip(heights, colors)):
        x0 = cx - w + 5 + i * (bar_w + 3)
        y0 = cy - int(h * bh)
        draw.rectangle([x0, y0, x0 + bar_w, cy], fill=c, outline=FG)


def main():
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    font_title = get_font(22)
    font_body = get_font(18)
    font_small = get_font(14)

    y = 30
    # 题号
    draw.text((30, y), "5. （2025·全国卷Ⅱ·选择题）", fill=FG, font=font_title)
    y += 40

    # 题干文字
    lines = [
        "如图所示，在△ABC中，AB = 3，BC = 4，",
        "∠ABC = 90°，点D为AC的中点。",
        "则下列函数图像中，哪个最接近 BD 的",
        "长度随∠BAC变化的函数关系？",
    ]
    for line in lines:
        draw.text((30, y), line, fill=FG, font=font_body)
        y += 30

    # === 题干配图：三角形 ===
    stem_img_y = y + 20
    draw_triangle(draw, 600, stem_img_y + 70, 80)
    # 画边框标识
    draw.rectangle([510, stem_img_y, 710, stem_img_y + 160], outline=(180, 180, 180), width=1)

    y = stem_img_y + 180

    # === 选项 ===
    draw.text((30, y), "选项：", fill=FG, font=font_body)
    y += 35

    option_regions = []

    # A — 圆+内接正方形
    draw.text((30, y + 50), "A.", fill=FG, font=font_body)
    ax, ay = 130, y + 55
    draw_circle_graph(draw, ax, ay, 45)
    option_regions.append(("A", ax - 50, y, ax + 55, y + 110))

    # B — 抛物线
    draw.text((230, y + 50), "B.", fill=FG, font=font_body)
    bx, by = 340, y + 55
    draw_parabola(draw, bx, by, 50, 45)
    option_regions.append(("B", bx - 55, y, bx + 55, y + 110))

    y2 = y + 130

    # C — 正弦
    draw.text((30, y2 + 50), "C.", fill=FG, font=font_body)
    cx_, cy_ = 130, y2 + 55
    draw_sine(draw, cx_, cy_, 50, 40)
    option_regions.append(("C", cx_ - 55, y2, cx_ + 55, y2 + 110))

    # D — 柱状图
    draw.text((230, y2 + 50), "D.", fill=FG, font=font_body)
    dx, dy = 340, y2 + 55
    draw_bar_chart(draw, dx, dy, 50, 45)
    option_regions.append(("D", dx - 55, y2, dx + 55, y2 + 110))

    # 底部提示
    y_bottom = y2 + 150
    draw.text((30, y_bottom), "（本题来源：模拟测试 — 仅供算法测试使用）", fill=(150, 150, 150), font=font_small)

    # 保存
    out_path = Path(__file__).parent / "test_question_with_figures.png"
    img.save(str(out_path), "PNG")
    print(f"生成测试图片: {out_path}  ({W}x{H})")

    # 打印真实区域（作为 ground truth）
    print("\n=== Ground Truth 区域 ===")
    stem_box = (510, stem_img_y, 710, stem_img_y + 160)
    print(f"  stem_image: bbox_px={stem_box}  →  千分位=[{stem_box[0]*1000//W}, {stem_box[1]*1000//H}, {stem_box[2]*1000//W}, {stem_box[3]*1000//H}]")
    for key, x1, y1, x2, y2 in option_regions:
        print(f"  option_{key}: bbox_px=({x1},{y1},{x2},{y2})  →  千分位=[{x1*1000//W}, {y1*1000//H}, {x2*1000//W}, {y2*1000//H}]")


if __name__ == "__main__":
    main()
