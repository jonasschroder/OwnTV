# Min TV v0.14 – tillförlitligare start av TV-kanaler

Förberett granskningsunderlag, ingen publicerad release.

- TV-vyns städning kan inte längre avbryta en pågående övergång till fullscreen eller stoppa den nya uppspelningen.
- Home, TV, programguide, Sport och kanalhistorik använder en gemensam kontrollerad uppspelningsingång. Fullscreen öppnas först efter att en intern uppspelningsbegäran accepterats; extern spelare hanteras separat.
- Avbrutna och ersatta begäranden får inte starta senare i bakgrunden. Källor, profilbehörighet och aktuella kanaluppgifter kontrolleras på nytt.
- Sparade käll- och kvalitetsval, femkolumnsgriden, fjärrnavigation, undertexter och befintlig motorfallback behålls.
- Smal, minnesbegränsad diagnostik skiljer accepterad begäran från öppnad media och visar avbrott/fallback utan IPTV-adresser, tokens eller profiluppgifter.

Bekräftade livscykelproblem har reproducerats med syntetiska ingångar och den pinnade Core-controllern. Det återstår fysisk Chromecast-QA för att avgöra om samtliga observerade SVT1/SVT2-problem har samma orsak. Inga nya försöksslingor, godtyckliga väntetider eller externa datakällor har lagts till.
