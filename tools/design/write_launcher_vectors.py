"""Install the three approved vector options as adaptive launcher resources."""
from pathlib import Path
from create_icon_options import PETAL

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res"
HEAD = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'

def path(data, fill="#FFFFFF", stroke=None, width=None, evenodd=False):
    attrs = f'android:pathData="{data}" android:fillColor="{fill}"'
    if stroke:
        attrs += f' android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round"'
    if evenodd:
        attrs += ' android:fillType="evenOdd"'
    return f'    <path {attrs}/>\n'

def vector(name, body):
    (RES / "drawable" / f"{name}.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n' + HEAD + body + '</vector>\n', encoding='utf-8')

flower = ''.join(f'    <group android:rotation="{angle}" android:pivotX="54" android:pivotY="54">\n{path(PETAL)}    </group>\n' for angle in range(0,360,72))
circle = 'M59.7,54 A5.7,5.7 0,1 0,48.3,54 A5.7,5.7 0,1 0,59.7,54Z'
vector('ic_launcher_flower', flower + path(circle, '#FFD166'))
vector('ic_launcher_flower_monochrome', flower + path(circle))

paper = 'M42,28 H66 A9,9 0,0 1,75,37 V71 A9,9 0,0 1,66,80 H42 A9,9 0,0 1,33,71 V37 A9,9 0,0 1,42,28Z'
vector('ic_launcher_foreground', path(paper) + path('M44,40 H61', '#00000000', '#BBDDFE', 4) + path('M44,59 L52,67 L65,50', '#00000000', '#1473D2', 5))
vector('ic_launcher_monochrome', path(paper + ' M42,38 H63 V42 H42Z M41,59 L45,55 L51,61 L63,46 L67,49 L53,70Z', evenodd=True))

clock_paper = 'M37,29 H61 A9,9 0,0 1,70,38 V70 A9,9 0,0 1,61,79 H37 A9,9 0,0 1,28,70 V38 A9,9 0,0 1,37,29Z'
clock_circle = 'M83,65 A15,15 0,1 0,53,65 A15,15 0,1 0,83,65Z'
vector('ic_launcher_clock', path(clock_paper) + path('M39,42 H57 M39,52 H52', '#00000000', '#BBDDFE', 4) + path(clock_circle, '#1065C6', '#1782F2', 3) + path('M68,57 V65 L74,69', '#00000000', '#FFFFFF', 4))
clock_silhouette = 'M37,29 H61 Q70,29 70,38 V50.13 C77.34,51.11 83,57.42 83,65 C83,73.28 76.28,80 68,80 C65.93,80 63.97,79.58 62.18,78.83 Q61.6,79 61,79 H37 Q28,79 28,70 V38 Q28,29 37,29Z'
vector('ic_launcher_clock_monochrome', path(clock_silhouette + ' M39,40 H57 V44 H39Z M39,50 H52 V54 H39Z M66,57 H70 V64 L77,68 L74,72 L66,66Z', evenodd=True))

for name, foreground, monochrome in [('ic_launcher_flower','ic_launcher_flower','ic_launcher_flower_monochrome'),('ic_launcher_clock','ic_launcher_clock','ic_launcher_clock_monochrome')]:
    for directory in ['mipmap-anydpi','mipmap-anydpi-v33']:
        body = f'    <background android:drawable="@color/ic_launcher_background"/>\n    <foreground android:drawable="@drawable/{foreground}"/>\n'
        if directory.endswith('v33'):
            body += f'    <monochrome android:drawable="@drawable/{monochrome}"/>\n'
        (RES / directory / f'{name}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'+body+'</adaptive-icon>\n',encoding='utf-8')
