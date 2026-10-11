#!/usr/bin/env python3
"""Generate source/lighthouse-mask.svg and source/lighthouse-shine.svg.

The committed SVGs are the source of truth; this script documents how they were
derived and is not run by the asset build. Requires fontTools (4.66.1) and the
Manrope SemiBold latin file named in SOURCE.md.

usage: make_sources.py FONT_TTF OUTPUT_SOURCE_DIR
"""
import math, random, sys
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

W, H, CX, HOR = 1080, 2400, 540, 1500
MINT, WHITE = "#4FD1B0", "#F2F5F4"
EDGE = "#030706"                                   # top and bottom edge colour == the renderer's clear colour
K, LYT = 1.28, 912                                 # tower scale about its base; lantern centre in tower coordinates
LY = round(1396 - (1396 - LYT) * K, 1)             # lantern centre in the picture
WIN = dict(x=410, y=2152, w=260, h=5)              # the loading-bar window: transparent in the mask
PERIOD, SHINE_H = 720, 2160                        # shine texture: repeats every PERIOD px (3.0 s at 240 px/s); must be >= the window's right edge (670)
NAME_BASE, NAME_W, TRACK = 2086, 520, 0.02
random.seed(11)

def kern_pairs(font):
    gpos = font["GPOS"].table; idx = set()
    for fr in gpos.FeatureList.FeatureRecord:
        if fr.FeatureTag == "kern": idx.update(fr.Feature.LookupListIndex)
    subs = []
    for i in sorted(idx):
        lk = gpos.LookupList.Lookup[i]
        for st in lk.SubTable:
            if lk.LookupType == 9: st = st.ExtSubTable
            subs.append(st)
    def adj(l, r):
        for st in subs:
            if not hasattr(st, "Format"): continue
            cov = st.Coverage.glyphs
            if l not in cov: continue
            if st.Format == 1:
                for pv in st.PairSet[cov.index(l)].PairValueRecord:
                    if pv.SecondGlyph == r:
                        v = pv.Value1; return (getattr(v, "XAdvance", 0) or 0) if v else 0
            elif st.Format == 2:
                v = st.Class1Record[st.ClassDef1.classDefs.get(l, 0)].Class2Record[st.ClassDef2.classDefs.get(r, 0)].Value1
                a = (getattr(v, "XAdvance", 0) or 0) if v else 0
                if a: return a
                return 0
        return 0
    return adj

def text_path(font, text, size, tr):
    cmap = font.getBestCmap(); gs = font.getGlyphSet(); upm = font["head"].unitsPerEm
    adj = kern_pairs(font); names = [cmap[ord(c)] for c in text]; sc = size / upm; x = 0.0; out = []
    for i, n in enumerate(names):
        pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
        gs[n].draw(TransformPen(pen, (sc, 0, 0, -sc, x, 0))); out.append(pen.getCommands())
        x += font["hmtx"][n][0] * sc + tr * size
        if i + 1 < len(names): x += adj(n, names[i + 1]) * sc
    return " ".join(out), x

def name(font):
    vis = lambda s: text_path(font, "Sovereign", s, TRACK)[1] + text_path(font, "OS", s, TRACK)[1] - TRACK * s
    size = 100 * NAME_W / vis(100)
    a, wa = text_path(font, "Sovereign", size, TRACK); b, wb = text_path(font, "OS", size, TRACK)
    x0 = CX - (wa + wb - TRACK * size) / 2
    return (f'<path fill="{WHITE}" transform="translate({x0:.2f} {NAME_BASE})" d="{a}"/>'
            f'<path fill="{MINT}" transform="translate({x0+wa:.2f} {NAME_BASE})" d="{b}"/>')

def stars():
    out = []
    for _ in range(110):
        y = max(8, int(1250 * random.random() ** 1.7)); x = random.randint(10, 1070)
        r = random.choice([1.2, 1.5, 1.8, 2.2]); o = random.choice([.22, .32, .45, .6])
        out.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#EAF4F1" opacity="{o}"/>')
    for _ in range(7):
        x, y = random.randint(60, 1020), random.randint(60, 760)
        out.append(f'<circle cx="{x}" cy="{y}" r="9" fill="#BFE9DD" opacity=".10"/><circle cx="{x}" cy="{y}" r="2.6" fill="#F2FAF7" opacity=".85"/>')
    return "".join(out)

def wave_rows():
    out = []; y = HOR + 34; k = 0
    while y < 2340:
        if not (1930 < y < 2210):
            p = 120 + k * 34; a = 3 + k * 1.1; n = int((W + 2 * p) / p) + 2
            d = f"M{-p} {y:.0f}q{p/4:.1f} {-2*a:.1f} {p/2:.1f} 0" + f"t{p/2:.1f} 0" * (2 * n)
            out.append(f'<path d="{d}" fill="none" stroke="#2A5A52" stroke-width="{2.2+k*.35:.1f}" stroke-linecap="round" opacity="{max(.16,.55-k*.035):.2f}"/>')
        y += 24 + k * 9; k += 1
    return "".join(out)

def reflect():
    out = []
    for i in range(16):
        y = HOR + 20 + i * 30 + i * i * 1.2; w = 26 + i * 16 + random.randint(0, 20)
        if 1930 < y < 2210: continue
        out.append(f'<rect x="{CX-w/2+random.randint(-10,10):.0f}" y="{y:.0f}" width="{w}" height="{3+i*.35:.1f}" rx="2" fill="{MINT}" opacity="{max(.05,.42-i*.03):.2f}"/>')
    return "".join(out)

def lamp():
    beam = ('<g><polygon points="0,0 640,-72 640,72" fill="url(#bg)" filter="url(#bb)"/>'
            '<polygon points="0,0 640,-13 640,13" fill="url(#bc)" filter="url(#bk)"/></g>')
    return f'<g transform="translate({CX} {LY})">{beam}<g transform="scale(-1 1)">{beam}</g><circle r="330" fill="url(#halo)" opacity=".92"/></g>'

def scene():
    tw0, tw1, ty0, ty1 = 92, 62, 1396, 968
    def tx(y, side):
        t = (ty0 - y) / (ty0 - ty1); return CX + side * (tw0 + (tw1 - tw0) * t) / 2
    def band(y0, y1, fill):
        return f'<polygon points="{tx(y0,-1):.1f},{y0} {tx(y0,1):.1f},{y0} {tx(y1,1):.1f},{y1} {tx(y1,-1):.1f},{y1}" fill="{fill}"/>'
    win = "".join(f'<path d="M{CX-w/2} {y+h} V{y+w/2} A{w/2} {w/2} 0 0 1 {CX+w/2} {y+w/2} V{y+h}Z" fill="#0B1614"/><path d="M{CX-w/2-2} {y+h+2} H{CX+w/2+2}" stroke="#9FB2AD" stroke-width="3"/>' for y, w, h in ((1196,18,30),(1020,16,26)))
    corb = "".join(f'<polygon points="{CX-54+i*21.6:.1f},{ty1} {CX-42+i*21.6:.1f},{ty1} {CX-48+i*21.6:.1f},{ty1+13}" fill="#1B3631"/>' for i in range(6))
    posts = "".join(f'<line x1="{CX-56+i*16:.1f}" y1="{ty1-52}" x2="{CX-56+i*16:.1f}" y2="{ty1-16}"/>' for i in range(8))
    return f'''
<rect width="{W}" height="{HOR}" fill="url(#sky)"/><rect y="{HOR}" width="{W}" height="{H-HOR}" fill="url(#sea)"/>
{stars()}
<path d="M0 {HOR} L0 1468 Q60 1452 130 1470 Q190 1456 250 1482 L300 {HOR}Z M780 {HOR} Q840 1474 900 1462 Q990 1446 1080 1472 V{HOR}Z" fill="#0A1C19"/>
<rect y="{HOR-90}" width="{W}" height="120" fill="url(#haze)"/>
{lamp()}{wave_rows()}{reflect()}
<path d="M250 {HOR+6} L318 1458 L392 1418 L470 1394 L610 1394 L694 1422 L768 1460 L836 {HOR+6}Z" fill="#12261F"/>
<path d="M540 1394 L610 1394 L694 1422 L768 1460 L836 {HOR+6} L540 {HOR+6}Z" fill="#0C1B17"/>
<path d="M318 1458 L392 1418 L470 1394 L540 1394 L540 {HOR+6} L260 {HOR+6}Z" fill="#17302A" opacity=".55"/>
<polygon points="392,1418 470,1394 500,1440 430,1470" fill="#1E3D36" opacity=".6"/><polygon points="610,1394 694,1422 650,1462 590,1430" fill="#0A1815" opacity=".7"/><polygon points="318,1458 392,1418 430,1470 360,1500 300,1500" fill="#1A332D" opacity=".5"/><polygon points="694,1422 768,1460 740,1500 690,1500 650,1462" fill="#08130F" opacity=".6"/>
<g stroke="#E4F0ED" stroke-opacity=".30" stroke-width="3" stroke-linecap="round" fill="none"><path d="M280 {HOR+10} q22 -9 44 0"/><path d="M350 {HOR+14} q18 -8 36 0"/><path d="M690 {HOR+12} q20 -9 40 0"/><path d="M770 {HOR+10} q22 -9 44 0"/></g>
<path d="M236 {HOR+8} Q540 {HOR+34} 844 {HOR+8}" stroke="#E4F0ED" stroke-opacity=".16" stroke-width="3" fill="none"/>
<g transform="translate({CX} {ty0}) scale({K}) translate({-CX} {-ty0})">
<rect x="{CX-56}" y="{ty0-14}" width="112" height="16" rx="2" fill="#D3DEDB"/><rect x="{CX-56}" y="{ty0-14}" width="112" height="16" rx="2" fill="url(#cyl)"/>
<polygon points="{tx(ty0,-1):.1f},{ty0} {tx(ty0,1):.1f},{ty0} {tx(ty1,1):.1f},{ty1} {tx(ty1,-1):.1f},{ty1}" fill="#EDF3F1"/>
{band(1236,1308,"#264A43")}{band(1090,1148,"#264A43")}
<polygon points="{tx(ty0,-1):.1f},{ty0} {tx(ty0,1):.1f},{ty0} {tx(ty1,1):.1f},{ty1} {tx(ty1,-1):.1f},{ty1}" fill="url(#cyl)"/>
<polygon points="{tx(ty0,-1)+7:.1f},{ty0} {tx(ty0,-1)+14:.1f},{ty0} {tx(ty1,-1)+9:.1f},{ty1} {tx(ty1,-1)+4:.1f},{ty1}" fill="#fff" opacity=".35"/>
<path d="M{CX-11} {ty0-2} V{ty0-28} A11 11 0 0 1 {CX+11} {ty0-28} V{ty0-2}Z" fill="#0B1614"/><rect x="{CX-18}" y="{ty0-4}" width="36" height="4" fill="#B9C9C5"/>
{win}
<rect x="{CX-50}" y="{ty1-1}" width="100" height="9" fill="#DCE6E3"/><rect x="{CX-50}" y="{ty1-1}" width="100" height="9" fill="url(#cyl)"/>
<rect x="{CX-60}" y="{ty1-16}" width="120" height="15" rx="2" fill="#24423C"/>{corb}
<g stroke="#24423C" stroke-width="3">{posts}</g>
<rect x="{CX-60}" y="{ty1-54}" width="120" height="4" rx="2" fill="#24423C"/><rect x="{CX-58}" y="{ty1-34}" width="116" height="2.5" fill="#24423C"/>
<rect x="{CX-36}" y="{LYT+32}" width="72" height="12" rx="2" fill="#1D3B35"/>
<rect x="{CX-30}" y="{LYT-30}" width="14" height="62" fill="url(#sideL)"/><rect x="{CX+16}" y="{LYT-30}" width="14" height="62" fill="url(#sideR)"/>
<rect x="{CX-16}" y="{LYT-30}" width="32" height="62" fill="url(#pane)"/>
<ellipse cx="{CX}" cy="{LYT+2}" rx="11" ry="19" fill="#fff" opacity=".85" filter="url(#gl)"/>
<g fill="#fff" opacity=".42"><rect x="{CX-16}" y="{LYT-14}" width="32" height="1.6"/><rect x="{CX-16}" y="{LYT-4}" width="32" height="1.6"/><rect x="{CX-16}" y="{LYT+6}" width="32" height="1.6"/><rect x="{CX-16}" y="{LYT+16}" width="32" height="1.6"/></g>
<rect x="{CX-16}" y="{LYT-30}" width="32" height="62" fill="#fff" opacity=".22"/>
<g fill="#10302A"><rect x="{CX-31.5}" y="{LYT-31}" width="3" height="64"/><rect x="{CX-17.5}" y="{LYT-31}" width="3" height="64"/><rect x="{CX+14.5}" y="{LYT-31}" width="3" height="64"/><rect x="{CX+28.5}" y="{LYT-31}" width="3" height="64"/><rect x="{CX-31.5}" y="{LYT+1}" width="63" height="2.5"/></g>
<rect x="{CX-36}" y="{LYT-40}" width="72" height="11" rx="2" fill="#E8EFED"/><rect x="{CX-36}" y="{LYT-40}" width="72" height="11" rx="2" fill="url(#cyl)"/>
<path d="M{CX-33} {LYT-40} Q{CX} {LYT-92} {CX+33} {LYT-40}Z" fill="#EDF3F1"/><path d="M{CX-33} {LYT-40} Q{CX} {LYT-92} {CX+33} {LYT-40}Z" fill="url(#cyl)"/>
<rect x="{CX-3}" y="{LYT-100}" width="6" height="14" fill="#E8EFED"/><circle cx="{CX}" cy="{LYT-102}" r="6" fill="#E8EFED"/><rect x="{CX-1.5}" y="{LYT-132}" width="3" height="26" fill="#E8EFED"/>
</g>'''

DEFS = f'''<defs>
 <linearGradient id="sky" x1="0" y1="0" x2="0" y2="{HOR}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{EDGE}"/><stop offset=".6" stop-color="#07130F"/><stop offset="1" stop-color="#0F2A25"/></linearGradient>
 <linearGradient id="sea" x1="0" y1="{HOR}" x2="0" y2="{H}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#0E2723"/><stop offset=".5" stop-color="#071413"/><stop offset="1" stop-color="{EDGE}"/></linearGradient>
 <radialGradient id="halo" cx="0" cy="0" r="330" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{MINT}" stop-opacity=".55"/><stop offset=".25" stop-color="{MINT}" stop-opacity=".20"/><stop offset="1" stop-color="{MINT}" stop-opacity="0"/></radialGradient>
 <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="640" y2="0"><stop offset="0" stop-color="{MINT}" stop-opacity=".82"/><stop offset=".3" stop-color="{MINT}" stop-opacity=".40"/><stop offset=".7" stop-color="{MINT}" stop-opacity=".10"/><stop offset="1" stop-color="{MINT}" stop-opacity="0"/></linearGradient>
 <linearGradient id="bc" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="640" y2="0"><stop offset="0" stop-color="#D6FFF4" stop-opacity=".92"/><stop offset=".3" stop-color="{MINT}" stop-opacity=".42"/><stop offset="1" stop-color="{MINT}" stop-opacity="0"/></linearGradient>
 <filter id="bb" x="-5%" y="-40%" width="115%" height="180%"><feGaussianBlur stdDeviation="11"/></filter>
 <filter id="bk" x="-5%" y="-150%" width="115%" height="400%"><feGaussianBlur stdDeviation="3.5"/></filter>
 <filter id="gl" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="6"/></filter>
 <linearGradient id="cyl" gradientUnits="userSpaceOnUse" x1="{CX-48}" y1="0" x2="{CX+48}" y2="0"><stop offset="0" stop-color="#000" stop-opacity="0"/><stop offset=".3" stop-color="#000" stop-opacity=".02"/><stop offset="1" stop-color="#000" stop-opacity=".46"/></linearGradient>
 <linearGradient id="pane" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8FEBD3"/><stop offset=".5" stop-color="#E9FFF8"/><stop offset="1" stop-color="#5FD0B6"/></linearGradient>
 <linearGradient id="sideL" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#1E7A68"/><stop offset="1" stop-color="#46C2A6"/></linearGradient>
 <linearGradient id="sideR" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#46C2A6"/><stop offset="1" stop-color="#1E7A68"/></linearGradient>
 <linearGradient id="haze" x1="0" y1="{HOR-90}" x2="0" y2="{HOR+30}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{MINT}" stop-opacity="0"/><stop offset=".8" stop-color="{MINT}" stop-opacity=".10"/><stop offset="1" stop-color="{MINT}" stop-opacity="0"/></linearGradient>
 <mask id="win" maskUnits="userSpaceOnUse" x="0" y="0" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="#fff"/><rect x="{WIN['x']}" y="{WIN['y']}" width="{WIN['w']}" height="{WIN['h']}" rx="{WIN['h']/2}" fill="#000"/></mask>
</defs>'''

def mask_svg(font):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">\n'
            f'<title>SovereignOS boot picture: lighthouse mask</title>\n'
            f'<desc>Static full-screen picture for the built-in boot renderer. The 260 x 5 window at ({WIN["x"]},{WIN["y"]}) is transparent so the scrolling texture shows through as the loading bar.</desc>\n'
            f'{DEFS}\n<g mask="url(#win)">{scene()}{name(font)}</g>\n</svg>\n')

def shine_svg():
    x, w, h, y = 0, PERIOD, SHINE_H, WIN["y"]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{PERIOD}" height="{SHINE_H}" viewBox="0 0 {PERIOD} {SHINE_H}">\n'
            f'<title>SovereignOS boot picture: loading texture</title>\n'
            f'<desc>Repeats every {PERIOD} px; the renderer scrolls it at a fixed 240 px/s (one pass every {PERIOD/240:.1f} s). Only the rows behind the mask window matter.</desc>\n'
            f'<defs><linearGradient id="seg"><stop offset="0" stop-color="{MINT}" stop-opacity="0"/><stop offset=".35" stop-color="{MINT}" stop-opacity=".9"/><stop offset=".65" stop-color="{MINT}" stop-opacity=".9"/><stop offset="1" stop-color="{MINT}" stop-opacity="0"/></linearGradient></defs>\n'
            f'<rect width="{PERIOD}" height="{SHINE_H}" fill="{EDGE}"/>\n'
            f'<rect y="{y-6}" width="{PERIOD}" height="{WIN["h"]+12}" fill="#1A3B35"/>\n'
            f'<rect x="305" y="{y-6}" width="110" height="{WIN["h"]+12}" fill="url(#seg)"/>\n</svg>\n')

if __name__ == "__main__":
    font = TTFont(sys.argv[1]); out = sys.argv[2]
    open(f"{out}/lighthouse-mask.svg", "w").write(mask_svg(font))
    open(f"{out}/lighthouse-shine.svg", "w").write(shine_svg())
