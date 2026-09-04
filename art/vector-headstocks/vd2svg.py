#!/usr/bin/env python3
"""Preview helper: turn the generated VectorDrawables into one HTML page on both grounds."""
import re
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"


def to_svg(path):
    root = ET.parse(path).getroot()
    w = root.get(A + "viewportWidth")
    h = root.get(A + "viewportHeight")
    out = []

    def emit(el, indent="  "):
        tag = el.tag
        if tag == "group":
            rot = el.get(A + "rotation")
            px, py = el.get(A + "pivotX", "0"), el.get(A + "pivotY", "0")
            out.append(f'{indent}<g transform="rotate({rot},{px},{py})">')
            for c in el:
                emit(c, indent + "  ")
            out.append(f"{indent}</g>")
        elif tag == "path":
            d = el.get(A + "pathData")
            fill = el.get(A + "fillColor") or "none"
            stroke = el.get(A + "strokeColor")
            sw = el.get(A + "strokeWidth")
            s = f'{indent}<path d="{d}" fill="{fill}"'
            if stroke:
                s += f' stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round" stroke-linecap="round"'
            out.append(s + " />")

    for child in root:
        emit(child)
    return w, h, f'<svg viewBox="0 0 {w} {h}" width="{float(w) * 0.62:.0f}" height="{float(h) * 0.62:.0f}" xmlns="http://www.w3.org/2000/svg">\n' + "\n".join(out) + "\n</svg>"


files = sys.argv[1:-1]
out_html = sys.argv[-1]
blocks = []
for f in files:
    w, h, svg = to_svg(f)
    blocks.append(svg)
html = ["<html><body style='margin:0'>"]
for bg in ("#0E1117", "#F3F4F7"):
    html.append(f"<div style='background:{bg};padding:24px;display:flex;gap:32px;align-items:flex-start'>")
    html.extend(blocks)
    html.append("</div>")
html.append("</body></html>")
open(out_html, "w").write("\n".join(html))
print(out_html)
