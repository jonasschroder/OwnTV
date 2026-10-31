# Min TV v0.16 — samlad granskningskandidat

- Gemensam kandidat för snabb D-pad-inmatning, fokus tillbaka från Sport-uppspelning och den guidade uppdateringsdialogen i #33–35.
- Kompaktare Home med tydligare senaste kanal och aktuell/nästa programinformation. Saknad EPG ersätter inte kanalens identitet.
- TV-kort visar kommande starttid, tydlig annonserad kvalitet och separata tillstånd för saknad guide respektive saknat nästa program. Fem kolumner och kort/lång OK bevaras.
- Sport skiljer matchinformation från kanal/åtgärd och inläsning från tomt kanalutbud. Sannolik sändning blir inte en bekräftad sändning.
- Inställningar återgår med Vänster till aktiv kategori och visar tydligare kategorirubrik. Alla befintliga funktioner och djupvägar finns kvar.
- Mindre arbete vid kanalindexering genom förberäknade uppslag och återanvändning av befintlig kategoripolicy.

Ingen release eller Chromecast-installation har utförts. Den bekräftade uppspelningen av TV4 Sport Live 3 i QA 0.15 bevisar inte vilken kanal som sänder den kommande matchen.

Nästa sammanhängande granskningsfas:

- TV-kort behåller samma storlek när guiden anländer, även med långa titlar. Kanalnamn och nästa program har egna stabila rader.
- Sport visar den utvalda matchen en gång och erbjuder tydligt manuellt kanalval när sändande kanal saknas eller inte kan avgöras. Matchinfo finns separat. Håll OK på en spelbar match för kanalalternativ.
- Fokus efter Sport-uppspelning inväntar färdig kanalinläsning och återgår till tidigare giltig kanal. Ny fjärrkontrollinmatning avbryter automatisk återgång.
- OK och Höger går in i vald inställningskategori. Vänster och återgång bevarar giltigt detaljfokus.
- Film & serier har kompaktare tomma ingångar med en gemensam förklaring. Befintlig fortsättning och uppspelning bevaras.
- Den guidade uppdateringsdialogens steg och felreturer har fjärrkontrolltester i båda TV-upplösningarna. Verklig signerad uppdatering på Chromecast återstår efter godkännande.

Slutlig UI-förfining:

- Uppdateringsdialogen har en helt täckande yta och tydligare text. Version och knappar ligger kvar när nyheter rullas med fjärrkontrollen, även i 720p och med större text.
- Home öppnar rätt matchinformation direkt när sändande kanal saknas. Håll OK för att välja kanal; Back återställer samma fokus.
- Inställningar har kompaktare rubrik och kortare kategorinamn. Aktiv kategori markeras uttryckligen och skiljs från den ruta som har fokus.
- Sport har tydligare matchkort och större spelbara hockeykanaler. Bekräftad, trolig och okänd sändning hålls fortfarande isär.
