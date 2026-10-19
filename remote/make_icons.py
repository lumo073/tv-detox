"""Draws the desktop-shortcut icons (remote/icons/*.ico). Needs Pillow. Run once:
    python remote/make_icons.py
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "icons")
ORANGE, DARK, WHITE = (232, 100, 47), (31, 26, 28), (255, 255, 255)
S = 256  # drawn big, saved at every Windows icon size


def base():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((8, 8, S - 8, S - 8), radius=56, fill=DARK)
    return im, d


def remote():
    im, d = base()
    d.rounded_rectangle((82, 34, 174, 222), radius=40, fill=ORANGE)       # the remote
    d.ellipse((100, 58, 156, 114), outline=WHITE, width=10)               # d-pad ring
    d.ellipse((119, 77, 137, 95), fill=WHITE)                             # OK
    for y in (140, 172):
        d.rounded_rectangle((100, y, 120, y + 16), radius=8, fill=WHITE)
        d.rounded_rectangle((136, y, 156, y + 16), radius=8, fill=WHITE)
    return im


def screen():
    im, d = base()
    d.rounded_rectangle((36, 56, 220, 172), radius=16, outline=ORANGE, width=14)  # the TV
    d.polygon([(112, 92), (112, 136), (150, 114)], fill=WHITE)                    # play
    d.rounded_rectangle((96, 190, 160, 204), radius=7, fill=ORANGE)               # stand
    return im


def check():
    im, d = base()
    d.rounded_rectangle((36, 56, 220, 172), radius=16, outline=ORANGE, width=14)
    d.line([(92, 116), (118, 140), (166, 90)], fill=WHITE, width=18, joint="curve")  # tick
    d.rounded_rectangle((96, 190, 160, 204), radius=7, fill=ORANGE)
    return im


os.makedirs(OUT, exist_ok=True)
for name, fn in (("remote", remote), ("screen", screen), ("check", check)):
    fn().save(os.path.join(OUT, name + ".ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("icons/%s.ico" % name)
