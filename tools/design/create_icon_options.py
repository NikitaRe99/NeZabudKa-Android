"""Create editable vector proposals; these are previews, not launcher resources."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "design" / "icon-options"
OUT.mkdir(parents=True, exist_ok=True)
PETAL = 'M54 26 C43 26 42 38 49 45 C50 47 52 48 54 48 C56 48 58 47 59 45 C66 38 65 26 54 26 Z'
flower = ''.join(f'<path d="{PETAL}" fill="white" transform="rotate({angle} 54 54)"/>' for angle in range(0, 360, 72))
flower += '<circle cx="54" cy="54" r="5.7" fill="#FFD166"/>'
note_check = '''<rect x="33" y="28" width="42" height="52" rx="9" fill="white"/>
<path d="M44 40 H61" stroke="#BBDDFE" stroke-width="4" stroke-linecap="round"/>
<path d="M44 59 L52 67 L65 50" fill="none" stroke="#1473D2" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>'''
note_clock = '''<rect x="28" y="29" width="42" height="50" rx="9" fill="white"/>
<path d="M39 42 H57 M39 52 H52" stroke="#BBDDFE" stroke-width="4" stroke-linecap="round"/>
<circle cx="68" cy="65" r="15" fill="#1065C6" stroke="#1782F2" stroke-width="3"/>
<path d="M68 57 V65 L74 69" fill="none" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>'''
OPTIONS = [("1", "Незабудка", flower), ("2", "Заметка и галочка", note_check), ("3", "Заметка и часы", note_clock)]
BG = '<rect width="108" height="108" fill="#1782F2"/>'
for number, title, symbol in OPTIONS:
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108"><defs><clipPath id="mask"><rect width="108" height="108" rx="25"/></clipPath></defs><g clip-path="url(#mask)">{BG}{symbol}</g></svg>'
    (OUT / f"option-{number}.svg").write_text(svg, encoding="utf-8")

parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="700" viewBox="0 0 1440 700">',
         '<rect width="1440" height="700" fill="#F2F6FE"/>',
         '<text x="72" y="68" fill="#121418" font-family="Arial, sans-serif" font-size="30" font-weight="600">НеЗабудьКа · варианты значка</text>']
for index, (number, title, symbol) in enumerate(OPTIONS):
    x = 180 + index * 460
    parts += [f'<defs><clipPath id="round-{number}"><rect width="108" height="108" rx="25"/></clipPath><clipPath id="circle-{number}"><circle cx="54" cy="54" r="54"/></clipPath></defs>',
              f'<g transform="translate({x} 133) scale(2.45)" clip-path="url(#round-{number})">{BG}{symbol}</g>',
              f'<text x="{x+132}" y="452" text-anchor="middle" fill="#121418" font-family="Arial, sans-serif" font-size="28" font-weight="600">{number}. {title}</text>',
              f'<g transform="translate({x+92} 505) scale(.75)" clip-path="url(#circle-{number})">{BG}{symbol}</g>',
              f'<text x="{x+132}" y="631" text-anchor="middle" fill="#5F6875" font-family="Arial, sans-serif" font-size="18">Маленький размер</text>']
parts.append('</svg>')
(OUT / 'options.svg').write_text(''.join(parts), encoding='utf-8')
print(OUT)
