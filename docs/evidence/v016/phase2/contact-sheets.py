from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image
import argparse
args=argparse.ArgumentParser()
args.add_argument('--output-dir', type=Path, default=Path('output/pdf'))
args=args.parse_args()
out=Path(__file__).resolve().parent;repo=out.parents[3]
font=next((str(p) for p in [Path('/System/Library/Fonts/Supplemental/Arial.ttf'),Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')] if p.is_file()),None)
if font is None:raise SystemExit('Install/provide an existing Swedish-capable TrueType font before rendering')
pdfmetrics.registerFont(TTFont('Review',font))
target=args.output_dir.resolve();target.mkdir(parents=True,exist_ok=True)
screens={'HOME':'Hem','TV':'TV','SPORT':'Sport','VOD':'Film & serier','PROFILE':'Profil & inställningar','UPDATE':'Uppdatering'}
notes={
'HOME':'Godkänd komposition bevarad. Enhetlig fokusring; ingen ny startskärmsstruktur.',
'TV':'Fast plats för två titelrader och metadata. Saknad/sent anländ EPG flyttar inte kortet.',
'SPORT':'Utvald match upprepas inte i matchraderna. Välj kanal och Info är separata, ärliga åtgärder.',
'VOD':'Gemensamt tomt tillstånd visas en gång, med kompaktare Filmer/Serier-ingångar.',
'PROFILE':'OK/Höger öppnar vald kategori; återgång behåller giltigt detaljfokus. Befintliga funktioner kvar.',
'UPDATE':'Verklig MainActivity visar debug-skyddet. Produktionssignering eller installation testas inte här.'}
W,H=1920,1280
for resolution in ['1920x1080','1280x720']:
 dest=target/('MinTV-v016-review-'+resolution+'.pdf')
 c=canvas.Canvas(str(dest),pagesize=(W,H),pageCompression=1)
 c.setTitle('Min TV v0.16 - native före/efter '+resolution)
 def text(x,y,s,size=21,color='#dce6ed'):
  c.setFillColor(color);c.setFont('Review',size);c.drawString(x,y,s)
 def bg(title,subtitle):
  c.setFillColorRGB(.035,.065,.095);c.rect(0,0,W,H,fill=1,stroke=0)
  text(40,H-50,title,34);text(40,H-85,subtitle,20)
 def shot(path,x,y):
  assert path.is_file(),path
  with Image.open(path) as im:assert im.size==tuple(map(int,resolution.split('x'))),(path,im.size)
  c.drawImage(str(path),x,y,width=900,height=506.25,preserveAspectRatio=True,anchor='c')
 for key,title in screens.items():
  bg(title+' - '+resolution,'Verklig native MainActivity i disponibel Android TV-emulator. Ingen ägardata eller fysisk Chromecast.')
  text(40,1160,'Före: PR #36 / 016e956',23);text(980,1160,'Efter: v0.16 nästa fas',23)
  shot(repo/f'docs/evidence/v016/cohesion/native/{resolution}/{key}-{resolution}.png',40,641)
  shot(out/f'empty/{key}-{resolution}.png',980,641)
  text(40,610,'Syntetisk befolkad full app - före',23);text(980,610,'Syntetisk befolkad full app - efter',23)
  shot(out/f'before/POPULATED_{key}-{resolution}.png',40,91)
  shot(out/f'after/POPULATED_{key}-{resolution}.png',980,91)
  text(40,58,notes[key],19)
  text(40,27,'12 000 syntetiska kanaler; svensk lokal, väder av. Relativa testtider; inga äkta programbilder/sändningsbesked.',17)
  c.showPage()
 bg('Uppdatering - fjärrkontrollens dialogresa - '+resolution,'Syntetiska tillstånd efter autentisering i ComponentActivity. Ingen verklig APK, behörighetsändring eller systeminstallation.')
 for i,(stage,label) in enumerate([('review','1. Granska version och nyheter'),('progress','2. Hämtningsprogress'),('permission','3. Explicit behörighetsåtgärd'),('confirmation','4. Bekräfta överlämning till Android')]):
  x=40+(i%2)*940;y=641-(i//2)*550
  text(x,y+518,label,22)
  shot(out/f'journeys/v016-update-flow-{stage}-{resolution}-synthetic.png',x,y)
 text(40,28,'Kryptografisk verifiering täcks separat av ordinarie tester. Signerad QA/Android-installation kräver senare godkännande.',18)
 c.save();print(dest)
