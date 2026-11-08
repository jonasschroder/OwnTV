# Sport 0.19 — datakällor, granskningsunderlag

- Nya avgränsade läsare för OpenLigaDB och SportsEvents24 utan personliga API-nycklar.
- Verifierade svar för NHL, Liiga, DEL, National League, Extraliga, ICEHL, CHL,
  Premier League och Allsvenskan; SHL och HockeyAllsvenskan behåller sina befintliga läsare.
- Tydlig ålder, ofullständig täckning, saknade resultat och felaktiga tidsuppgifter.
  Inga gissade livesiffror, tabeller eller TV-kanaler.
- Beständig begränsning av anrop, cache, avbrytbara hämtningar och skydd mot motstridiga käll-ID:n.

Detta är ett granskningssteg för datalagret. Nya källor är ännu inte aktiverade i gränssnittet.
NHL-källan har påvisade tidskonflikter och begränsad täckning; produktionsrättigheter och
övriga sporters täckning behöver fortsatt granskas. Ingen QA/Stable-release har skapats.
