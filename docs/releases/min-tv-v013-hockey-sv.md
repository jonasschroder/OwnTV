# Min TV v0.13 – automatisk kanalidentifiering för hockey

Förberett granskningsunderlag; ingen release är publicerad.

- Sport visar bekräftad TV-kanal direkt på matchkorten. Kort OK startar en entydigt identifierad IPTV-kanal; långt OK öppnar matchinformation och befintliga manuella kanalval.
- Den lokala programguiden kan nu hitta kommande matcher även när de ligger efter kanalens första 128 program. Befintliga EPG-kopplingar, tidsförskjutningar, profiler och källval används.
- Kanalidentifiering för en exakt match fungerar även när lagen inte är favoriter. Favoritlagens prioritering och sparade inställningar behålls.
- För gamla eller ofullständiga kanalbevis ger ingen automatisk direktstart. TV4 Sport Live-kanalernas nummer hålls åtskilda från varandra och från TV4 Play.
- Inga nya permanenta menyer, bakgrundshämtningar, abonnemang, spelarmotorer eller källor har lagts till.

Automatiken kräver att en godkänd programguide innehåller specifik matchinformation. Om kanalen inte kan verifieras visas det tydligt och manuellt val finns kvar. Något nytt licensierat externt broadcast-API är ännu inte anslutet. TVmatchen-experimentets befintliga begränsningar och frivilliga aktivering är oförändrade.

Regressioner omfattar match efter rad 128, ändrad EPG, behörighet, nummer/streaming-alias, för gamla bevis, osäkra källval samt kort/långt OK och fokus i 1080p/720p. Riktiga matchblad har kontrollerats separat; detta ersätter inte fysisk Chromecast-QA.
