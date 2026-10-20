#!/bin/sh
# Assemble review artifacts without changing the reference photographs.
set -eu
cd "$(dirname "$0")"
font=${CLASSIC_COMPARISON_FONT:-/System/Library/Fonts/Helvetica.ttc}
# Draw the SVG polygon data directly: ImageMagick's built-in SVG reader can
# incorrectly apply fill opacity to strokes on builds without librsvg.
python3 - "$font" <<'PY'
import subprocess, sys, xml.etree.ElementTree as ET
root=ET.parse('blueprint-overlay.svg').getroot()
cmd=['magick','plan-reference.jpg','-background','white','-gravity','north',
     '-extent','1200x1020','-font',sys.argv[1],'-fill','none',
     '-stroke','#e06000','-strokewidth','1.5']
for p in root.findall('{http://www.w3.org/2000/svg}polygon'):
    cmd+=['-draw','polygon '+p.attrib['points']]
cmd+=['-stroke','none','-fill','#f2f4f7','-draw','rectangle 0,879 1200,1020',
      '-fill','#18212b','-gravity','northwest']
for t in root.findall('{http://www.w3.org/2000/svg}text'):
    cmd+=['-pointsize',t.attrib['font-size'],'-draw',
          f"text {t.attrib['x']},{t.attrib['y']} '{t.text}'"]
subprocess.run(cmd+['blueprint-overlay.png'],check=True)
PY
magick montage \
  -label 'Photo 7 - supplied reference' textures/classic-photo.jpg \
  -label 'Model - primary view' comparison-primary.png \
  -label 'Photo 8 - supplied reference' textures/classic-opposite.jpg \
  -label 'Model - opposite view' comparison-opposite.png \
  -label 'Photo 9 - supplied cockpit reference' textures/cockpit-reference.jpg \
  -label 'Model - cockpit reconstruction' comparison-cockpit.png \
  -font "$font" -pointsize 22 -background '#e9edf0' -fill '#18212b' \
  -geometry 800x600+12+12 -tile 2x3 -quality 92 photo-comparison.jpg
