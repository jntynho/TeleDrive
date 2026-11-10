#!/usr/bin/env python3
"""Package the approved cloud/folder/plane artwork as Android adaptive layers (stdlib only)."""
from pathlib import Path
import argparse
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / 'docs/brand'
RES = ROOT / 'app/src/main/res'
ANDROID = 'http://schemas.android.com/apk/res/android'
AAPT = 'http://schemas.android.com/aapt'
SVG = '{http://www.w3.org/2000/svg}'
E.register_namespace('android', ANDROID)
E.register_namespace('aapt', AAPT)

# Android draws 108dp adaptive layers through a nominal central 72dp mask.
# The opaque emblem spans 52dp and fits within the 66dp safe circle.
ARTBOARD = 1254
MARK_BOUNDS = (155, 209, 1087, 1032)
MARK_CENTER = (621.0, 620.5)
MARK_WIDTH_DP = 52.0
DP_PER_PIXEL = MARK_WIDTH_DP / (MARK_BOUNDS[2] - MARK_BOUNDS[0])


def a(name):
    return '{' + ANDROID + '}' + name


def xml(root):
    E.indent(root)
    return E.tostring(root, encoding='utf-8', xml_declaration=True) + b'\n'


def background():
    source = E.parse(BRAND / 'tg-drive-background.svg').getroot()
    gradient = source.find(SVG + 'defs').find(SVG + 'linearGradient')
    vector = E.Element('vector', {a('width'): '108dp', a('height'): '108dp',
                                a('viewportWidth'): '108', a('viewportHeight'): '108'})
    path = E.SubElement(vector, 'path', {a('pathData'): 'M0 0H108V108H0Z'})
    attr = E.SubElement(path, '{' + AAPT + '}attr', {'name': 'android:fillColor'})
    native = E.SubElement(attr, 'gradient', {a('type'): 'linear', a('startX'): gradient.get('x1'),
        a('startY'): gradient.get('y1'), a('endX'): gradient.get('x2'), a('endY'): gradient.get('y2')})
    for stop in gradient:
        E.SubElement(native, 'item', {a('offset'): stop.get('offset'), a('color'): stop.get('stop-color')})
    return xml(vector)


def foreground():
    # Fractions, rather than fixed dp insets, also scale correctly in About and
    # launcher zoom animations. There is no baked-in circle, square or backdrop.
    size = ARTBOARD * DP_PER_PIXEL
    left = (54 - MARK_CENTER[0] * DP_PER_PIXEL) / 108
    top = (54 - MARK_CENTER[1] * DP_PER_PIXEL) / 108
    insets = (left, top, 1 - left - size / 108, 1 - top - size / 108)
    node = E.Element('inset', {a('inset' + side): f'{value * 100:.8f}%'
        for side, value in zip(('Left', 'Top', 'Right', 'Bottom'), insets)})
    E.SubElement(node, 'bitmap', {a('src'): '@drawable/launcher_mark', a('gravity'): 'fill', a('filter'): 'true'})
    return xml(node)


def monochrome():
    vector = E.Element('vector', {a('width'): '108dp', a('height'): '108dp',
        a('viewportWidth'): str(ARTBOARD), a('viewportHeight'): str(ARTBOARD)})
    scale = DP_PER_PIXEL * ARTBOARD / 108
    group = E.SubElement(vector, 'group', {a('scaleX'): f'{scale:.9f}', a('scaleY'): f'{scale:.9f}',
        a('translateX'): f'{ARTBOARD / 2 - scale * MARK_CENTER[0]:.9f}',
        a('translateY'): f'{ARTBOARD / 2 - scale * MARK_CENTER[1]:.9f}'})
    for path in E.parse(BRAND / 'tg-drive-monochrome.svg').getroot().findall(SVG + 'path'):
        E.SubElement(group, 'path', {a('pathData'): path.get('d'), a('fillColor'): '#FFFFFF',
            a('fillType'): 'evenOdd' if path.get('fill-rule') == 'evenodd' else 'nonZero'})
    return xml(vector)


def adaptive(themed=False):
    root = E.Element('adaptive-icon')
    for kind in ('background', 'foreground') + (('monochrome',) if themed else ()):
        E.SubElement(root, kind, {a('drawable'): '@drawable/ic_launcher_' + kind})
    return xml(root)


def generate():
    mono = monochrome()
    return {
        RES / 'drawable/ic_launcher_background.xml': background(),
        RES / 'drawable/ic_launcher_foreground.xml': foreground(),
        RES / 'drawable/ic_launcher_monochrome.xml': mono,
        RES / 'drawable-nodpi/launcher_mark.webp': (BRAND / 'tg-drive-mark.webp').read_bytes(),
        RES / 'mipmap-anydpi/ic_launcher.xml': adaptive(),
        RES / 'mipmap-anydpi-v26/ic_launcher.xml': adaptive(),
        RES / 'mipmap-anydpi-v33/ic_launcher.xml': adaptive(themed=True),
        ROOT / 'ui/src/commonMain/composeResources/drawable/ic_launcher_monochrome.xml': mono,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if packaged resources differ')
    args = parser.parse_args()
    for path, content in generate().items():
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                raise SystemExit(f'Out of date: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    print('TeleDrive adaptive launcher resources verified.' if args.check else 'Generated TeleDrive adaptive launcher resources.')


if __name__ == '__main__':
    main()
