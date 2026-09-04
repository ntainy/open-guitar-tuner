#!/usr/bin/env python3
"""Generates the two original headstock VectorDrawables for GuitarTuner.

Everything here is drawn from scratch out of plain geometry: no tracing, no bitmap source.
Run:  python3 gen_headstocks.py  -> writes app/src/main/res/drawable/headstock_*.xml
"""
import math
import os

REPO = "/Users/ntainy/Developer/AndroidStudioProjects/guitar-tuner"
OUT = os.path.join(REPO, "app/src/main/res/drawable")

# "Graphite & brass" palette, fixed colours that read on #0E1117 and on #F3F4F7.
PLATE_DARK = "#1E2431"      # Graphite700 - shaded facet
PLATE_LIT = "#2E3646"       # Graphite500 - lit facet
RIM = "#5B6270"             # SteelDeep   - bevel outline, visible on both grounds
FRETBOARD = "#161B24"       # Graphite800
COVER = "#161B24"
COVER_EDGE = "#3A4252"      # OutlineDark
NUT = "#9AA6BD"             # Steel
NUT_SHADE = "#5B6270"
STRING = "#9AA6BD"
BRASS = "#E2B65A"
BRASS_DEEP = "#9C7118"
BRASS_DARK = "#3A2E12"      # BrassContainer
BRASS_HOLE = "#241A05"      # OnBrass

STRING_WIDTHS = [4.6, 3.9, 3.2, 2.6, 2.0, 1.6]


def circle(cx, cy, r):
    return (f"M{cx},{cy - r:g} A{r:g},{r:g} 0 1,1 {cx},{cy + r:g} "
            f"A{r:g},{r:g} 0 1,1 {cx},{cy - r:g} Z")


def post(cx, cy):
    """Bushing ring, bright brass cap, string hole."""
    return [
        (circle(cx, cy, 22), BRASS_DEEP, None, None),
        (circle(cx, cy, 15), BRASS, None, None),
        (circle(cx, cy, 5), BRASS_HOLE, None, None),
    ]


def key_paddle(cx, cy, inner, outer, half, mirror=False):
    """A tuner button poking out from behind the headstock edge; drawn towards -x, or +x when mirrored."""
    s = -1 if not mirror else 1
    x_in = cx + s * inner
    x_mid = cx + s * (outer - 11)
    x_out = cx + s * outer
    body = (f"M{x_in:g},{cy - half:g} L{x_mid:g},{cy - half:g} "
            f"C{x_out:g},{cy - half:g} {x_out:g},{cy - half + 5:g} {x_out:g},{cy:g} "
            f"C{x_out:g},{cy + half - 5:g} {x_out:g},{cy + half:g} {x_mid:g},{cy + half:g} "
            f"L{x_in:g},{cy + half:g} Z")
    shade = (f"M{x_out:g},{cy + 2:g} C{x_out:g},{cy + half - 4:g} {x_out:g},{cy + half:g} {x_mid:g},{cy + half:g} "
             f"L{x_in:g},{cy + half:g} L{x_in:g},{cy + 2:g} Z")
    return [(body, BRASS_DEEP, None, None), (shade, BRASS_DARK, None, None)]


def path_xml(data, fill=None, stroke=None, width=None, indent="    "):
    lines = [f'{indent}<path']
    lines.append(f'{indent}    android:pathData="{data}"')
    if fill:
        lines.append(f'{indent}    android:fillColor="{fill}"')
    if stroke:
        lines.append(f'{indent}    android:strokeColor="{stroke}"')
        lines.append(f'{indent}    android:strokeWidth="{width:g}"')
        lines.append(f'{indent}    android:strokeLineCap="round"')
        lines.append(f'{indent}    android:strokeLineJoin="round"')
    lines[-1] += " />"
    return "\n".join(lines)


def vector(w, h, dp_w, dp_h, body):
    return ("<!-- Original artwork for GuitarTuner, drawn as plain vector geometry. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{dp_w}dp"\n'
            f'    android:height="{dp_h}dp"\n'
            f'    android:viewportWidth="{w}"\n'
            f'    android:viewportHeight="{h}">\n\n'
            + body + "\n</vector>\n")


# ---------------------------------------------------------------- 3 + 3 -----
W3, H3 = 400, 740
COL_L, COL_R = 106, 294
ROWS = [200, 340, 480]                 # top, middle, bottom (D/G, A/B, E/e)
NUT_TOP, NUT_BOT = 594, 616
NUT_X0, NUT_X1 = 124, 276
STR_NUT_3 = [135, 161, 187, 213, 239, 265]
STR_END_3 = [130, 158, 186, 214, 242, 270]

# Left edge (widest around two thirds up, tapering in again towards the crown), then half the "open book" crown:
# a broad flat lobe falling to a shallow valley on the centre line.
EDGE3 = ("M128,602 C104,562 76,528 68,470 C60,400 54,300 52,190 "
         "C51,150 51,116 54,96 C57,76 66,66 84,62 ")
LOBE3 = "C112,56 148,58 176,70 L200,94 "
SIL3 = (EDGE3 + LOBE3 +
        "L224,70 C252,58 288,56 316,62 C334,66 343,76 346,96 "
        "C349,116 349,150 348,190 C346,300 340,400 332,470 C324,528 296,562 272,602 Z")
FACET3 = EDGE3 + LOBE3 + "L200,602 Z"
COVER3 = ("M187,132 C187,127 192,124 200,124 C208,124 213,127 213,132 "
          "C213,152 216,180 219,208 C220,218 214,226 205,226 L195,226 "
          "C186,226 180,218 181,208 C184,180 187,152 187,132 Z")


def build_3_3():
    p = []
    # fretboard, widening away from the nut
    p.append(path_xml("M126,600 L274,600 L288,770 L112,770 Z", fill=FRETBOARD,
                      stroke=COVER_EDGE, width=2.5))
    # tuner buttons sit behind the plate
    for y in ROWS:
        for d, mirror in ((COL_L, False), (COL_R, True)):
            for data, fill, s, w in key_paddle(d, y, 20, 70, 16, mirror):
                p.append(path_xml(data, fill=fill))
    p.append(path_xml(SIL3, fill=PLATE_DARK))
    p.append(path_xml(FACET3, fill=PLATE_LIT))
    p.append(path_xml(SIL3, stroke=RIM, width=3.5))
    p.append(path_xml(COVER3, fill=COVER, stroke=COVER_EDGE, width=2))
    p.append(path_xml(circle(200, 139, 4), fill=BRASS_DEEP))
    p.append(path_xml(circle(200, 212, 4), fill=BRASS_DEEP))
    p.append(path_xml(f"M{NUT_X0},{NUT_TOP} L{NUT_X1},{NUT_TOP} L{NUT_X1},{NUT_BOT} L{NUT_X0},{NUT_BOT} Z", fill=NUT))
    p.append(path_xml(f"M{NUT_X0},608 L{NUT_X1},608 L{NUT_X1},{NUT_BOT} L{NUT_X0},{NUT_BOT} Z", fill=NUT_SHADE))
    anchors = [(COL_L, ROWS[2]), (COL_L, ROWS[1]), (COL_L, ROWS[0]),
               (COL_R, ROWS[0]), (COL_R, ROWS[1]), (COL_R, ROWS[2])]
    for i, (cx, cy) in enumerate(anchors):
        p.append(path_xml(f"M{cx},{cy} L{STR_NUT_3[i]},{NUT_TOP + 2} L{STR_END_3[i]},770",
                          stroke=STRING, width=STRING_WIDTHS[i]))
    for cx, cy in anchors:
        for data, fill, s, w in post(cx, cy):
            p.append(path_xml(data, fill=fill))
    return anchors, "\n\n".join(p)


# ------------------------------------------------------------ 6 in line -----
W6, H6 = 420, 700
POSTS6 = [(108, 500), (114, 418), (129, 336), (156, 254), (195, 172), (246, 90)]
NUT6 = (152, 308, 554, 578)
STR_NUT_6 = [168, 193, 218, 243, 268, 293]
STR_END_6 = [164, 191, 218, 245, 272, 299]

EDGE6 = ("M156,566 C126,562 86,548 68,516 C63,507 60,500 59,486 "
         "C57,430 86,300 113,237 C130,190 170,120 207,66 C216,46 230,32 250,30 ")
SIL6 = (EDGE6 + "C270,28 284,40 292,58 "
        "C328,104 352,180 358,260 C362,330 348,420 322,486 C314,510 308,540 306,566 Z")
FACET6 = (EDGE6 + "C272,90 280,200 274,300 C268,400 252,500 236,567 Z")


def key_angles():
    """Each button points along the outward normal of the post line."""
    angles = []
    for i, (x, y) in enumerate(POSTS6):
        prev = POSTS6[i - 1] if i > 0 else None
        nxt = POSTS6[i + 1] if i + 1 < len(POSTS6) else None
        dx = dy = 0.0
        for other, sign in ((nxt, 1), (prev, -1)):
            if other:
                dx += sign * (other[0] - x)
                dy += sign * (y - other[1])
        angles.append(math.degrees(math.atan2(dx, dy)))
    return angles


def build_6_in_line():
    p = []
    p.append(path_xml("M160,566 L300,566 L316,730 L146,730 Z", fill=FRETBOARD,
                      stroke=COVER_EDGE, width=2.5))
    for (cx, cy), ang in zip(POSTS6, key_angles()):
        inner = "\n".join(path_xml(d, fill=f, indent="        ")
                          for d, f, s, w in key_paddle(cx, cy, 20, 82, 16))
        p.append(f'    <group\n        android:pivotX="{cx}"\n        android:pivotY="{cy}"\n'
                 f'        android:rotation="{ang:.1f}">\n{inner}\n    </group>')
    p.append(path_xml(SIL6, fill=PLATE_DARK))
    p.append(path_xml(FACET6, fill=PLATE_LIT))
    p.append(path_xml(SIL6, stroke=RIM, width=3.5))
    x0, x1, y0, y1 = NUT6
    p.append(path_xml(f"M{x0},{y0} L{x1},{y0} L{x1},{y1} L{x0},{y1} Z", fill=NUT))
    p.append(path_xml(f"M{x0},{y1 - 8} L{x1},{y1 - 8} L{x1},{y1} L{x0},{y1} Z", fill=NUT_SHADE))
    for i, (cx, cy) in enumerate(POSTS6):
        p.append(path_xml(f"M{cx},{cy} L{STR_NUT_6[i]},{y0 + 2} L{STR_END_6[i]},730",
                          stroke=STRING, width=STRING_WIDTHS[i]))
    for cx, cy in POSTS6:
        for data, fill, s, w in post(cx, cy):
            p.append(path_xml(data, fill=fill))
    return POSTS6, "\n\n".join(p)


anchors3, body3 = build_3_3()
anchors6, body6 = build_6_in_line()
os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, "headstock_3_3.xml"), "w") as f:
    f.write(vector(W3, H3, 200, 370, body3))
with open(os.path.join(OUT, "headstock_6_in_line.xml"), "w") as f:
    f.write(vector(W6, H6, 210, 350, body6))

print("3+3", W3, "x", H3, " ring", round(30 / W3, 4))
for i, (x, y) in enumerate(anchors3):
    print(f"  {i}: Offset({x / W3:.4f}f, {y / H3:.4f}f),")
print("6-in-line", W6, "x", H6, " ring", round(30 / W6, 4))
for i, (x, y) in enumerate(anchors6):
    print(f"  {i}: Offset({x / W6:.4f}f, {y / H6:.4f}f),")
print("y step", (POSTS6[0][1] - POSTS6[1][1]) / H6)
