from pathlib import Path
import argparse
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--output-dir',type=Path,default=Path('output/pdf'));args=p.parse_args()
root=Path(__file__).resolve().parent;args.output_dir.mkdir(parents=True,exist_ok=True)
font=next(p for p in [Path('/System/Library/Fonts/Supplemental/Arial.ttf'),Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')] if p.exists())
pdfmetrics.registerFont(TTFont('Review',str(font)))
labels={'HOME':'Hem','TV':'TV','SPORT':'Sport','VOD':'Film & serier','PROFILE':'Profil & inställningar','UPDATE':'Uppdatering'}
notes={
'HOME':'Matchinformation på kort OK; håll OK för matchens kanalval. Back återställer samma Home-fokus.',
'TV':'Fem kolumner, skroll och fasta EPG-rader bevarade. Andra raden är avsiktligt fokuserad.',
'SPORT':'Större match- och kanalkort med riktiga åtgärder; ingen påhittad sändande kanal.',
'VOD':'Befintlig Filmer/Serier-upplevelse bevarad. Syntetisk fortsättning, inga fejkade programbilder.',
'PROFILE':'Kompaktare rubrik, kortare kategorier och tydlig Aktiv-markör skild från fjärrkontrollfokus.',
'UPDATE':'Helt täckande yta. Fast version och knappar; mellantext/nyheter kan rullas med D-pad.'}
for res in ['1920x1080','1280x720']:
 path=args.output_dir/f'MinTV-v016-final-review-{res}.pdf';c=canvas.Canvas(str(path),pagesize=(1920,760),pageCompression=1)
 c.setTitle(f'Min TV v0.16 - final native review {res}')
 def text(x,y,s,size=22,color='#dce6ed'):
  c.setFillColor(color);c.setFont('Review',size);c.drawString(x,y,s)
 def page(title,sub):
  c.setFillColorRGB(.035,.065,.095);c.rect(0,0,1920,760,fill=1,stroke=0)
  text(40,710,title,34);text(40,673,sub,20)
 def shot(path,x):
  with Image.open(path) as im:assert im.size==tuple(map(int,res.split('x')))
  c.drawImage(str(path),x,100,width=900,height=506.25)
 for key,title in labels.items():
  page(title+' - '+res,'Native MainActivity i disponibel emulator. Syntetiska data, inga ägaruppgifter. Teststämpel 0.14.0-dev/2.')
  text(40,630,'Före: #36 / 7698b1a',23);text(980,630,'Efter: slutlig UI-förfining',23)
  shot(root/f'before/POPULATED_{key}-{res}.png',40);shot(root/f'after/POPULATED_{key}-{res}.png',980)
  text(40,59,notes[key],21)
  text(40,25,'12 000 syntetiska kanaler; svenska; väder av. Relativa testtider. Ingen fysisk Chromecast eller signerad uppdatering.',18)
  c.showPage()
 for left,right in [('review','progress'),('permission','confirmation')]:
  page('Uppdatering - '+res,'Syntetiska tillstånd efter autentisering i ComponentActivity. Ingen hämtning, behörighetsändring eller APK-installation.')
  for stage,x in [(left,40),(right,980)]:
   text(x,630,{'review':'Granska och läs nyheter','progress':'Hämtningsprogress','permission':'Explicit behörighetsåtgärd','confirmation':'Bekräfta överlämning till Android'}[stage],23)
   shot(root/f'journeys/v016-update-flow-{stage}-{res}-synthetic.png',x)
  text(40,50,'56 pixelkontroller: 14 tillstånd x 2 textstorlekar x 2 upplösningar. Version och kontroller hålls synliga.',21)
  text(40,24,'Riktig signering, Android-installation och processåterstart måste verifieras senare med godkänd officiell QA.',18)
  c.showPage()
 c.save();print(path.resolve())
