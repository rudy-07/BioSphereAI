"""Builds the BioSphereAI logo set from one geometry source.

Run from the repo root:  python3 branding/build.py
Needs: fontTools (pip install fonttools) and ImageMagick 7 with librsvg (`magick`).

The mark is the app's launcher icon (mobile/app/src/main/res/drawable/ic_launcher_foreground.xml):
a glucose trace with two forecast dots, drawn in a 108-unit adaptive-icon canvas.
"""
from __future__ import annotations

import io
import shutil
import subprocess
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent
FONT = ROOT.parent / "mobile/app/src/main/res/font/manrope.ttf"
NAME = "BioSphereAI"

# Palette (matches the app theme)
GRAPHITE = "#1C1D1F"
PAPER = "#F2F3F5"
INK = "#16181A"
WHITE = "#FFFFFF"
GREEN_ON_DARK = "#7BC67E"
GREEN_ON_LIGHT = "#2F7D32"
MUTED_ON_DARK = "#A3A7AC"
MUTED_ON_LIGHT = "#5F6368"

THEMES = {
    # name: (background, trace, dots, text, muted text)
    "dark": (GRAPHITE, WHITE, GREEN_ON_DARK, "#ECEDEE", MUTED_ON_DARK),
    "light": (PAPER, INK, GREEN_ON_LIGHT, INK, MUTED_ON_LIGHT),
}

# ---------------------------------------------------------------- mark geometry
WAVE = "M30,62 C36,62 38,46 45,46 C52,46 53,66 60,66 C64,66 65.5,59 68,56"
DOTS = [(74, 50, 4), (81, 45, 3)]
STROKE = 5

# Optical variant for 16 to 32 px: thicker trace, bigger dots, same shape.
SMALL_WAVE = WAVE
SMALL_DOTS = [(74.5, 50, 5), (82.5, 43.5, 3.6)]
SMALL_STROKE = 7.5

MARK_BOX = (27.5, 41.5, 56.5, 27.5)  # x, y, w, h of the drawn mark (stroke + dots included)


def mark_paths(trace: str, dots: str, small: bool = False) -> str:
    wave, dot_list, stroke = (SMALL_WAVE, SMALL_DOTS, SMALL_STROKE) if small else (WAVE, DOTS, STROKE)
    circles = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{dots}"/>' for x, y, r in dot_list)
    return (
        f'<path d="{wave}" fill="none" stroke="{trace}" stroke-width="{stroke}" '
        f'stroke-linecap="round" stroke-linejoin="round"/>{circles}'
    )


def svg(view: tuple[float, float, float, float], body: str, title: str) -> str:
    x, y, w, h = view
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x:g} {y:g} {w:g} {h:g}" '
        f'width="{w:g}" height="{h:g}" role="img" aria-label="{title}">'
        f"<title>{title}</title>{body}</svg>\n"
    )


# ------------------------------------------------------------------- wordmark
def wordmark_outline(text: str, weight: int = 700, tracking: float = -20) -> tuple[str, float, float, float]:
    """Text as SVG path data in font units, baseline at y=0, y pointing down.
    Returns (path, advance width, cap top (negative), descender)."""
    vf = TTFont(FONT)
    font = instantiateVariableFont(vf, {"wght": weight}, inplace=False)
    buf = io.BytesIO()
    font.save(buf)
    font = TTFont(io.BytesIO(buf.getvalue()))
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    kern = _pair_kerning(font)

    pen = SVGPathPen(gs)
    x = 0.0
    prev = None
    for ch in text:
        g = cmap[ord(ch)]
        if prev is not None:
            x += kern.get((prev, g), 0) + tracking
        gs[g].draw(TransformPen(pen, (1, 0, 0, -1, x, 0)))
        x += gs[g].width
        prev = g
    cap = font["OS/2"].sCapHeight
    return pen.getCommands(), x, -cap, 0


def _pair_kerning(font: TTFont) -> dict[tuple[str, str], int]:
    """Flattens GPOS PairPos (format 1 and 2) x-advance adjustments. Good enough for one word."""
    out: dict[tuple[str, str], int] = {}
    if "GPOS" not in font:
        return out
    gpos = font["GPOS"].table
    for lookup in gpos.LookupList.Lookup:
        for st in lookup.SubTable:
            if lookup.LookupType == 9:
                st = st.ExtSubTable
            if getattr(st, "LookupType", lookup.LookupType) != 2:
                continue
            firsts = st.Coverage.glyphs
            if st.Format == 1:
                for g1, ps in zip(firsts, st.PairSet):
                    for rec in ps.PairValueRecord:
                        v = getattr(rec.Value1, "XAdvance", 0) if rec.Value1 else 0
                        if v:
                            out.setdefault((g1, rec.SecondGlyph), v)
            elif st.Format == 2:
                c1 = st.ClassDef1.classDefs
                c2 = st.ClassDef2.classDefs
                seconds: dict[int, list[str]] = {}
                for g, c in c2.items():
                    seconds.setdefault(c, []).append(g)
                for g1 in firsts:
                    row = st.Class1Record[c1.get(g1, 0)]
                    for ci, rec in enumerate(row.Class2Record):
                        v = getattr(rec.Value1, "XAdvance", 0) if rec.Value1 else 0
                        if v and ci in seconds:
                            for g2 in seconds[ci]:
                                out.setdefault((g1, g2), v)
    return out


WORD_PATH, WORD_ADV, WORD_CAP, _ = wordmark_outline(NAME)
TAG_TEXT = "Virtual CGM patients, glucose forecasting and insulin safety"
TAG_PATH, TAG_ADV, _, _ = wordmark_outline(TAG_TEXT, weight=500, tracking=0)


# -------------------------------------------------------------------- pieces
def badge(theme: str, shape: str, small: bool = False) -> str:
    bg, trace, dots, *_ = THEMES[theme]
    if shape == "circle":
        back = f'<circle cx="54" cy="54" r="36" fill="{bg}"/>'
        view = (18, 18, 72, 72)
    elif shape == "rounded":
        back = f'<rect x="18" y="18" width="72" height="72" rx="16" fill="{bg}"/>'
        view = (18, 18, 72, 72)
    else:  # square, full bleed: avatars and stores that apply their own mask
        back = f'<rect x="18" y="18" width="72" height="72" fill="{bg}"/>'
        view = (18, 18, 72, 72)
    if theme == "light":  # hairline edge so the pale badge still reads on a white page
        back = back.replace("/>", ' stroke="#DADCE0" stroke-width="0.75"/>', 1)
    if small:  # tighter crop so the mark reads at favicon size
        view = (22, 22, 64, 64)
        r = 32
        back = back.replace('cx="54" cy="54" r="36"', f'cx="54" cy="54" r="{r}"').replace(
            'x="18" y="18" width="72" height="72"', 'x="22" y="22" width="64" height="64"'
        ).replace('rx="16"', 'rx="14"')
    return svg(view, back + mark_paths(trace, dots, small), NAME)


def bare_mark(trace: str, dots: str) -> str:
    x, y, w, h = MARK_BOX
    pad = 2
    return svg((x - pad, y - pad, w + 2 * pad, h + 2 * pad), mark_paths(trace, dots), NAME)


def lockup(theme: str, layout: str, transparent: bool = True) -> str:
    bg, trace, dots, text, _ = THEMES[theme]
    mx, my, mw, mh = MARK_BOX
    cap_h = -WORD_CAP  # font units
    if layout == "horizontal":
        # Mark height = 1.55 x cap height of the text; text baseline aligned to optical middle.
        s_text = (mh * 0.62) / cap_h  # font units -> mark units
        gap = 9.0
        tx = mx + mw + gap
        baseline = my + mh / 2 + cap_h * s_text / 2
        w = mw + gap + WORD_ADV * s_text
        h = mh
        view = (mx - 6, my - 6, w + 12, h + 12)
    else:  # stacked
        s_text = (mh * 0.5) / cap_h
        word_w = WORD_ADV * s_text
        w = max(mw, word_w)
        tx = mx + (mw - word_w) / 2
        baseline = my + mh + 14 + cap_h * s_text
        h = baseline - my
        view = (min(mx, tx) - 6, my - 6, w + 12, h + 12)
    word = (
        f'<path transform="translate({tx:.3f} {baseline:.3f}) scale({s_text:.6f})" '
        f'd="{WORD_PATH}" fill="{text}"/>'
    )
    back = "" if transparent else f'<rect x="{view[0]:g}" y="{view[1]:g}" width="{view[2]:g}" height="{view[3]:g}" fill="{bg}"/>'
    return svg(view, back + mark_paths(trace, dots) + word, NAME)


def mono(color: str, kind: str) -> str:
    if kind == "mark":
        return bare_mark(color, color)
    # badge knocked out: solid disc with the mark cut out, single colour
    body = (
        '<mask id="m"><rect x="0" y="0" width="108" height="108" fill="#fff"/>'
        f'{mark_paths("#000", "#000")}</mask>'
        f'<circle cx="54" cy="54" r="36" fill="{color}" mask="url(#m)"/>'
    )
    return svg((18, 18, 72, 72), body, NAME)


def social(theme: str, w: int, h: int) -> str:
    bg, trace, dots, text, muted = THEMES[theme]
    mx, my, mw, mh = MARK_BOX
    cap_h = -WORD_CAP
    # lay out in pixels
    mark_scale = 150 / mh  # mark 150 px tall
    s_text = (mh * 0.62 * mark_scale) / cap_h
    gap = 9 * mark_scale
    word_w = WORD_ADV * s_text
    total = mw * mark_scale + gap + word_w
    left = (w - total) / 2
    top = h / 2 - mh * mark_scale / 2 - 30
    mark = (
        f'<g transform="translate({left - mx * mark_scale:.2f} {top - my * mark_scale:.2f}) scale({mark_scale:.4f})">'
        f"{mark_paths(trace, dots)}</g>"
    )
    baseline = top + mh * mark_scale / 2 + cap_h * s_text / 2
    word = (
        f'<path transform="translate({left + mw * mark_scale + gap:.2f} {baseline:.2f}) scale({s_text:.6f})" '
        f'd="{WORD_PATH}" fill="{text}"/>'
    )
    tag_s = 30 / cap_h
    tag_w = TAG_ADV * tag_s
    tag = (
        f'<path transform="translate({(w - tag_w) / 2:.2f} {top + mh * mark_scale + 95:.2f}) scale({tag_s:.6f})" '
        f'd="{TAG_PATH}" fill="{muted}"/>'
    )
    body = f'<rect width="{w}" height="{h}" fill="{bg}"/>' + mark + word + tag
    return svg((0, 0, w, h), body, NAME)


# ------------------------------------------------------------------- output
def write(rel: str, content: str) -> Path:
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
    return p


def render(src: Path, dst: Path, width: int, height: int | None = None) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    geo = f"{width}x{height}!" if height else f"{width}x"
    # Render large with librsvg, then downsample: crisper than rsvg at tiny sizes.
    subprocess.run(
        ["magick", "-background", "none", "-density", "1200", f"RSVG:{src}",
         "-filter", "Lanczos", "-resize", geo, "-strip", str(dst)],
        check=True,
    )


def main() -> None:
    for d in ("svg", "png", "favicon", "social"):
        shutil.rmtree(ROOT / d, ignore_errors=True)

    sv: dict[str, Path] = {}
    for theme in THEMES:
        for shape in ("circle", "rounded", "square"):
            sv[f"icon-{shape}-{theme}"] = write(f"svg/icon-{shape}-{theme}.svg", badge(theme, shape))
            sv[f"icon-small-{shape}-{theme}"] = write(
                f"svg/icon-small-{shape}-{theme}.svg", badge(theme, shape, small=True))
        _, trace, dots, *_ = THEMES[theme]
        # "on-dark" = for use on dark backgrounds
        sv[f"mark-on-{theme}"] = write(f"svg/mark-on-{theme}.svg", bare_mark(trace, dots))
        for layout in ("horizontal", "stacked"):
            sv[f"logo-{layout}-on-{theme}"] = write(
                f"svg/logo-{layout}-on-{theme}.svg", lockup(theme, layout))
        sv[f"social-{theme}"] = write(f"social/github-social-preview-{theme}.svg", social(theme, 1280, 640))
        sv[f"og-{theme}"] = write(f"social/og-image-{theme}.svg", social(theme, 1200, 630))
    sv["mono-mark-black"] = write("svg/mono-mark-black.svg", mono("#000000", "mark"))
    sv["mono-mark-white"] = write("svg/mono-mark-white.svg", mono("#FFFFFF", "mark"))
    sv["mono-badge-black"] = write("svg/mono-badge-black.svg", mono("#000000", "badge"))
    sv["mono-badge-white"] = write("svg/mono-badge-white.svg", mono("#FFFFFF", "badge"))

    # Icons: small optical variant below 48 px, regular above.
    for theme in THEMES:
        for shape in ("circle", "rounded", "square"):
            for size in (16, 24, 32, 48, 64, 96, 128, 180, 192, 256, 512, 1024):
                key = f"icon-{shape}-{theme}"
                if size <= 32:
                    key = f"icon-small-{shape}-{theme}"
                render(sv[key], ROOT / f"png/icon-{shape}-{theme}/{size}.png", size)
        for name in (f"mark-on-{theme}", f"logo-horizontal-on-{theme}", f"logo-stacked-on-{theme}"):
            for w in (256, 512, 1024, 2048):
                render(sv[name], ROOT / f"png/{name}/{w}w.png", w)
        render(sv[f"social-{theme}"], ROOT / f"social/github-social-preview-{theme}.png", 1280, 640)
        render(sv[f"og-{theme}"], ROOT / f"social/og-image-{theme}.png", 1200, 630)
    for name in ("mono-mark-black", "mono-mark-white", "mono-badge-black", "mono-badge-white"):
        render(sv[name], ROOT / f"png/{name}/512w.png", 512)

    # Website favicon kit (dark badge works on both light and dark browser chrome).
    fav = ROOT / "favicon"
    fav.mkdir(exist_ok=True)
    shutil.copy(sv["icon-small-circle-dark"], fav / "favicon.svg")
    for s in (16, 32, 48):
        render(sv["icon-small-circle-dark"] if s <= 32 else sv["icon-circle-dark"], fav / f"favicon-{s}.png", s)
    subprocess.run(["magick", *(str(fav / f"favicon-{s}.png") for s in (16, 32, 48)), str(fav / "favicon.ico")], check=True)
    render(sv["icon-square-dark"], fav / "apple-touch-icon.png", 180)
    render(sv["icon-circle-dark"], fav / "icon-192.png", 192)
    render(sv["icon-circle-dark"], fav / "icon-512.png", 512)
    render(sv["icon-square-dark"], fav / "icon-maskable-512.png", 512)
    (fav / "site.webmanifest").write_text(
        '{\n  "name": "BioSphereAI",\n  "short_name": "BioSphere",\n  "icons": [\n'
        '    {"src": "/icon-192.png", "sizes": "192x192", "type": "image/png"},\n'
        '    {"src": "/icon-512.png", "sizes": "512x512", "type": "image/png"},\n'
        '    {"src": "/icon-maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"}\n'
        '  ],\n  "theme_color": "#1C1D1F",\n  "background_color": "#1C1D1F",\n  "display": "standalone"\n}\n'
    )
    print("done:", sum(1 for _ in ROOT.rglob("*") if _.is_file()), "files")


if __name__ == "__main__":
    main()
