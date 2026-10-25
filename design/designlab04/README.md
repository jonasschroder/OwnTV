# Min TV — DESIGNLABB 04

Godkänd interaktiv UX-baslinje för Home, TV, Sport och Film & serier. Hela den bevarade, körbara prototypen finns i [`preview/`](preview/), inklusive lokala SVG-bilder, tangentbordsnavigation, scenarier, persistens, testskript och tidigare testresultat.

## Starta lokalt

Från repots rot, med Python 3 (inga paket behöver installeras):

```sh
python3 -B design/designlab04/preview/serve.py
```

Öppna **http://127.0.0.1:57820/**. Servern lyssnar endast lokalt. Om porten används av en tidigare förhandsvisning, stoppa den processen först.

Piltangenter navigerar. Vänster/höger i toppmenyn flyttar fokus; Enter aktiverar; ned aktiverar och går in i innehållet. Kort Enter på TV simulerar uppspelning, håll Enter för tillgängliga källalternativ. Escape/Backspace går tillbaka och återställer fokus. Film → Serier nås med ned.

`Scenarier` styr designfallen. Endast demo-namespace `mintv-designlab04` används i webbläsarens localStorage; `Reset Demo Data` återställer enbart detta. För en reproducerbar visning används MOCK. Optional LIVE visar endast begränsade publika sportdata via befintlig adapter; källstatus skiljer verifierade svar från otillgängliga data. Inga privata IPTV-källor eller tokens ingår. Spelare, tittarhistorik och sändningslänkar i MOCK är designfixturer, inte verklig IPTV.

## Skärmbilder — godkänd iteration 4, 1920×1080

| Home | TV | Sport | Film & serier |
|---|---|---|---|
| [Home](preview/screenshots/iteration4-home.jpg) | [TV](preview/screenshots/iteration4-tv.jpg) | [Sport](preview/screenshots/iteration4-sport.jpg) | [Film & serier](preview/screenshots/iteration4-film-series.jpg) |

## Tester och begränsningar

```sh
cd design/designlab04/preview
python3 -B -m unittest test_readiness.py
```

Tre isolerade adaptertester. Tidigare webbläsarresultat finns i `iteration4-*-results.json`; tangentbords-/scenariomodulerna kräver den dokumenterade CUA-tab-API:n och är inte fristående Node CLI-tester. Se [beslut och begränsningar](preview/ITERATION-4.md) och [datakällornas readiness](preview/SOURCE-READINESS.md). Nätverksbevisen är historiska och utgör ingen garanti för aktuell tillgänglighet.

Detta är en webbaserad designreferens, inte Android-appen eller bevis på Chromecast-funktion. Android, Core, CI, signering och releaser har inte ändrats av denna designpublicering. `preview/README.md` och `PRESERVATION.md` bevarar den tidigare lokala historiken; startinstruktionen ovan gäller denna repo-kopia.
