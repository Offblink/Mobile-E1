"""生成电脑端 Windows 图标 desktop/icon.ico（与手机端 launcher 同一套「双气泡」图形）。

手机端图标是 vector（108 视口、淡紫底 + 两个紫色气泡），这里按同一组坐标栅格化：
  python tools/make-icon.py        # 在 desktop/ 目录下执行，或任意目录传输出路径
只依赖 Pillow；改完图形重跑一次，build.gradle.kts 的 nativeDistributions 会把它嵌进 exe。
"""

import os
import sys

from PIL import Image, ImageDraw

VIEWPORT = 108          # 手机端 vector 的 viewportWidth/Height
MASTER = 1024           # 先按大图绘制再缩，边缘更干净
BG = (234, 221, 255, 255)       # md_primary_container #EADDFF
BUBBLE_FRONT = (103, 80, 164, 255)   # #6750A4
BUBBLE_BACK = (33, 0, 93, 255)       # #21005D
SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]


def rounded_bubble(draw, rect, radius, fill):
    draw.rounded_rectangle(rect, radius=radius, fill=fill)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "icon.ico")

    k = MASTER / VIEWPORT
    img = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 底板：圆角方块（Windows 下透明四角，任务栏/开始菜单都是干净的圆角）
    draw.rounded_rectangle([0, 0, MASTER - 1, MASTER - 1],
                           radius=int(MASTER * 0.22), fill=BG)

    def box(x1, y1, x2, y2):
        return [int(x1 * k), int(y1 * k), int(x2 * k), int(y2 * k)]

    # 前气泡（#6750A4）：圆角矩形 + 左下尾巴
    rounded_bubble(draw, box(24, 32, 66, 62), int(6 * k), BUBBLE_FRONT)
    draw.polygon([(int(33 * k), int(62 * k)), (int(33 * k), int(70 * k)),
                  (int(42 * k), int(62 * k))], fill=BUBBLE_FRONT)

    # 后气泡（#21005D）：压在前气泡右下，尾巴朝右下
    rounded_bubble(draw, box(42, 50, 84, 80), int(6 * k), BUBBLE_BACK)
    draw.polygon([(int(66 * k), int(80 * k)), (int(75 * k), int(88 * k)),
                  (int(75 * k), int(80 * k))], fill=BUBBLE_BACK)

    base = img.resize((256, 256), Image.LANCZOS)
    base.save(out, format="ICO", sizes=SIZES)
    print("wrote", out, os.path.getsize(out), "bytes")


if __name__ == "__main__":
    main()
