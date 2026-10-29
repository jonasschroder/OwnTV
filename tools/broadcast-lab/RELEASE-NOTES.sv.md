# Broadcast Lab Phase 2 – utvecklingsverktyg

- Verkliga, begränsade anrop till TV-Programidags dokumenterade gratis-API visar att kanaltablån innehåller flera dagar framåt.
- Separat normaliserad JSON för exakta matchkopplingar, obekräftade arenakandidater, källor, tidsstämplar och utgångstid. TV4 Play och äldre kanalnamn översätts inte till moderna linjära kanaler.
- Jämförelse av gratis-API, Ronin, Bombus och TheSportsDB med åtkomst-, pris- och licensblockerare.
- Syntetiska regressionstester och lokalt granskningsgränssnitt. Ingen ändring i Android-appen eller befintliga releaser.

Ingen automatisk match–kanal-koppling är ännu godkänd för produktion.

## Godkänd arenamatchning

- En unik och färsk SHL-träff på officiell arena och sändningstid kan nu visas som **Trolig kanal**. Exakta laguppgifter kan ge **Bekräftad kanal**.
- Konflikter, flyttade matcher, utgångna uppgifter och okända arenanamn ger manuell kanalväljare.
- Webblabbets uppdateringsknapp fungerar via en lokal Python-process. Ingen betalning, provprenumeration eller molnserver krävs.
