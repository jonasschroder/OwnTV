# Min TV Test v0.17 — första QA-etappen

- Senast sedda kanalen visar nu kanalnamn och aktuell/nästa programinformation även när en hockeymatch prioriteras på Home.
- Homes EPG-klocka fortsätter i förgrunden även när en databasläsning tar tid och uppdateras direkt efter återgång.
- Kanalsteg som landar på samma enda kanal skickar ingen onödig spelarförfrågan. Nationella och regionala SVT-kanaler behåller separata identiteter.
- Extra validering av lokala hockeyintervall hindrar ogiltig lokal evidens från att skymma en fortfarande giltig extern tilldelning. Trolig kanal blir inte bekräftad kanal.

Granskningsanteckningar för en utvecklingsbranch. Ingen officiell QA-version är signerad eller publicerad. Fysisk Chromecast-acceptans återstår.
