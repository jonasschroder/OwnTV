# Min TV v0.11 — interaktivt designlabb, iteration 1

Öppna `index.html` direkt i en vanlig webbläsare. Hela prototypen är självförsörjande: HTML, CSS, JavaScript och egen SVG-grafik i samma fil. Inga beroenden, externa bilder, kontakter med leverantörer eller nätverksanrop krävs.

Lokal server, om önskat: `python3 -B serve.py`. Servern skriver sin loopback-adress och publicerar inget på internet. Den aktuella förhandsvisningen är http://127.0.0.1:57820/ och finns så länge processen körs.

## Prova

- Piltangenter flyttar fokus inom fasta, logiska rader. TV har fem kanaler per rad.
- Enter öppnar, väljer eller simulerar uppspelning. Escape/Backspace går tillbaka.
- Toppnavigation: Left/Right flyttar bara fokus; Enter aktiverar sidan; Down aktiverar och går in i innehållet. Up från den första innehållsraden återgår till aktiv toppdestination.
- Back stänger spelare/detalj/katalog i ordning och återställer ursprungskortet. Back från innehåll går till aktiv toppdestination; ytterligare Back återgår till Hem.
- Film & serier har staplade rader: Down från Filmer går direkt till Serier. Left/Right bläddrar bland respektive titlar; inget sidbyte sker bara för att fokus flyttas.
- Developer-raden under TV-bilden har fyra matchscenarier samt separata EPG- och Fortsätt titta-val. Fokusinfo visar komponent och sektion. Klicka Till TV för att återgå till fjärrkontrollsläget efter ett utvecklarreglage.
- Mus och vanlig Tab fungerar också; modalernas Tab-fokus hålls inom den öppna dialogen.

## Scenarier och ärliga tillstånd

Matchdag visar favoritlagets match. Live/verifierad visar en verifierad **designfixtur**, inte en riktig sändningslänk. Live/okänd erbjuder uttryckligt manuellt kanalval utan att påstå att matchen sänds där. Utan match idag leder Hem med sparat innehåll, annars live-TV; Sport visar nästa lagmatch. Saknad EPG visar kanalidentitet utan påhittat program/progress. Tom historik ersätts av en icke-fokuserbar tomrad.

## Verifierat i webbläsaren

- Fyra huvudvyer passar inom TV-ytan vid 1920×1080 och 1280×720.
- Left/Right aktiverar inte en annan toppdestination; Down aktiverar rätt sida.
- TV: Down från första raden går till nästa rad i samma kolumn; Right går till nästa kanal.
- Fokus återställs på exakt kanalkort efter spelare och efter byte mellan sidor.
- Film → Serie via Down, katalog → detalj → spelare, tre Back återgår till samma serieingång.
- Alla fyra matchlägen, saknad EPG, tom Fortsätt titta, manuell/okänd kanal och fokusoverlay har provats.

## Begränsningar

Allt innehåll, klocka, resultat, tittarhistorik, bilder och förenklade märken är syntetiska designfixturer. Brynäs är fast favoritlag i denna iteration. Profilvalet är en lokal visningsfixtur. Inga autentiska EPG-, sport-, IPTV- eller Twitch-integrationer finns. Spelare/undertextknapp simulerar bara gränssnitt; ingen video, textning eller decoder körs. Val och fokus finns bara i minnet och återställs vid omladdning. Webbnavigering bevisar inte Android-fokus eller fysisk TV-läsbarhet.

Read-only referens: OwnTV main `10f02c658640457de86c926ebd95064b7c8b673d` (träd `7157d002d019186b1eae9cae96e6c6cc57aeb0c2`). Inga Android-/Core-/Gradle-/CI-/signeringsändringar, Git-commits, pushar eller PR:er har gjorts. Ingen Site har registrerats eller publicerats.

`screenshots/` innehåller Hem, TV, Sport och Film & serier i 1920×1080 samt Hem i 1280×720. `?capture=1&page=0..3` döljer enbart utvecklarraden för TV-bilder; standardadressen visar designreglagen.


## Source readiness iteration
Run `python3 -B serve.py` for the same localhost preview on port57820. Select Data: MOCK/LIVE/UNAVAILABLE; Källstatus shows provenance and errors. LIVE requires permitted outbound www.thesportsdb.com access; no credentials are needed for its limited public development sample. See SOURCE-READINESS.md. No private provider data is connected. `python3 -B -m unittest test_readiness.py` runs three focused tests.

## Iteration 2
Same preview URL and TV design language. TV:5 artwork-led cards/row,20 mock channels with original first10 order, natural scroll, current/next details and missing-EPG/art fallback. Sport:artwork-backed fixture cards, ordered hockey channels, manual unverified selection. D-pad now retains the actual column across unequal rows and restores the column after page re-entry. Film entry Down reaches Series. Actual keyboard evidence:iteration2-keyboard-results.json; screenshots in screenshots/iteration2-*.jpg. Existing public SHL adapter and explicit MOCK/LIVE/UNAVAILABLE remain; no private sources connected.

Iteration2 validation:actual keyboard TV column3→fourth row; player Back; inactive nav Left/Right; Down activation; Film→Series; catalog/detail/player Back chain; column-preserving Film/Series traversal; page focus restoration; missing EPG gives zero progress bars; manual unverified channel dialog/player; 720p focused item remains visible with no horizontal overflow.3 adapter unit tests pass. No Android or Git changes.

## Iteration 3
See ITERATION-3.md for verification,source matrix and decisions. DESIGNLABB03 is the current footer. Scenarier opens mock team/Twitch/EPG/library cases; Källstatus separates the four sports contracts. Keyboard tests:27assertions at1080p and720p;19scenario assertions;3server adapter unit tests. Actual source response evidence:iteration3-live-evidence.txt. Android repository unchanged.

## Iteration 4
DESIGNLABB04 is current. See ITERATION-4.md. UI extension is iteration4.js, served by the existing fixed-route loopback server. TV short OK plays; held OK opens available sources; Continue Watching plays directly. Färjestad defaults and separate bounded mock profiles persist in localStorage key mintv-designlab04. Scenarier includes Reset Demo Data, which resets only this demo namespace.30keyboard checks at1080p/720p,23scenario checks,23persistence checks and3adapter unit tests passed. No real IPTV sources or tokens connected.
