# Permanenta Min TV-uppdateringar på Mac och TV

Ägaren har skapat och säkerhetskopierat den permanenta nyckeln och bekräftar
att båda miljöerna `mintv-qa-signing` och `mintv-production-signing` har sina
fyra secrets. Skyddsreglerna är verifierade via skrivskyddat GitHub-API.
**Skapa ingen ny nyckel. Inga installerade appar ska ändras nu.**

Efter ägarens uttryckliga beslut används **samma nyckel** för båda apparna.
Det publika SHA256-fingeravtrycket är pinnat för båda i `config/mintv-signing.json`:

```text
D2:0B:2F:58:F4:E1:2B:95:6E:1E:1D:C4:0E:47:87:3D:9B:9E:E6:A6:66:A2:DE:1A:F2:A6:B8:AC:3D:40:54:7C
```

Paket, appdata, kanalmetadata och miljögodkännanden förblir separata. Nyckeln
är däremot gemensam: den ger ingen kryptografisk separation mellan kanalerna.
Båda skyddade miljöerna måste därför skydda samma nyckel lika noggrant.

PR #3 innehåller signering och molnuppdateraren men är inte mergad.
Workflowen finns ännu inte på `main`, så faktisk permanent signering är blockerad.
Secrets och nyckeln har inte lästs av Codex; deras riktighet bevisas först av
ett framtida godkänt signeringsjobb. Ingen Release eller permanent APK finns ännu.
[Implementations- och acceptansrapport](min-tv-cloud-updates.md).

Steg 1–3 beskriver den **redan genomförda** förberedelsen och återställning;
upprepa inte nyckelgenereringen. Steg 5 är redan genomfört enligt ägaren.
Vanlig PR-CI delar rapporter men inga nya tillfälligt signerade installer-APK:er.
Ägaren accepterar att planera en första ren installation av **båda** apparna
när allt är verifierat. Utförandet kräver fortfarande separat godkännande.

## 1. Installera bara verktyget för nycklar

Du behöver inte Android Studio, Gradle eller Homebrew för detta steg.
Installera **Eclipse Temurin JDK 21** från [Adoptium](https://adoptium.net/temurin/releases/?version=21).
Välj macOS, JDK och `.pkg`: **aarch64** för Apple Silicon (M1/M2/M3/M4), **x64**
för Intel. Apple-menyn → Om den här datorn visar vilken processor du har.
Öppna det officiella paketet och följ installationen. Sänk inte macOS säkerhet
och använd inte en okänd nedladdningssida. Spara två USB-minnen för säkerhetskopior.

Öppna Terminal (Program → Verktygsprogram). Klistra in:

```bash
/usr/libexec/java_home -v 21
"$(/usr/libexec/java_home -v 21)/bin/keytool" -help
```

Du ska få en Java-sökväg och hjälptext för `keytool`. Om det misslyckas: stanna
och kontrollera installationen. Inget ska installeras på Chromecast i detta steg.

## 2. Skapa QA-nyckeln utan nätverk

Stäng andra program, koppla bort Wi-Fi/Ethernet och använd en betrodd Mac.
Mappen nedan ligger utanför projektet och ska **inte** läggas i iCloud, Dropbox,
GitHub, delad APK-mapp eller chatt. Aktiverad FileVault rekommenderas.

```bash
mkdir -p "$HOME/MinTV-private-signing"
chmod 700 "$HOME/MinTV-private-signing"
"$(/usr/libexec/java_home -v 21)/bin/keytool" -genkeypair \
  -keystore "$HOME/MinTV-private-signing/mintv-qa.p12" \
  -storetype PKCS12 -alias mintv-qa -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$HOME/MinTV-private-signing/mintv-qa.p12"
```

Om filen redan finns: **stanna**, skapa inte en ersättningsnyckel. Spara den
befintliga nyckeln. Första körningen frågar efter ett nytt starkt lösenord.
Spara det i din lösenordshanterare som **Min TV QA keystore**. Terminal visar
inget när du skriver lösenordet; det är normalt. Använd samma lösenord för
nyckeln om PKCS12 frågar. Följ frågorna om certifikatets namn och organisation;
de är publika och du kan använda neutrala uppgifter för personlig användning.
Bekräfta uppgifterna när verktyget frågar. Skriv aldrig lösenord i kommandoraden.

Läs därefter det **publika** certifikatet:

```bash
"$(/usr/libexec/java_home -v 21)/bin/keytool" -list -v \
  -keystore "$HOME/MinTV-private-signing/mintv-qa.p12" -alias mintv-qa
```

Spara raden **SHA256** samt aliaset `mintv-qa` i en separat anteckning. SHA256
är ett publikt fingeravtryck, inte lösenordet. Det ska bli 64 små hexadecimala
tecken när kolon/mellanslag tas bort. Skicka endast detta publika fingeravtryck
till Codex för granskad incheckning i `config/mintv-signing.json` → `qa.certificate_sha256`.
Skicka **aldrig** `.p12`, lösenord eller Base64-strängen i chatt/PR.

## 3. Gör två krypterade säkerhetskopior och prova återställning

Nyckeln är redan lösenordsskyddad. Gör dessutom krypterade offlinekopior:

1. Öppna **Skivverktyg → Arkiv → Ny avbild → Avbild från mapp**.
2. Välj `MinTV-private-signing` i din hemmapp.
3. Välj **256-bitars AES-kryptering**, ett separat starkt lösenord och
   **läs/skriv** som avbildsformat. Spara `.dmg` på första USB-minnet.
4. Spara en andra kopia på ett annat USB-minne; förvara ett på en annan plats.
5. Mata ut, anslut och öppna varje avbild med lösenordet. Prova `keytool -list -v`
   på den monterade kopians `.p12` med QA-lösenordet. Kontrollera att SHA256 är
   exakt samma som originalet. Skriv in kopians filväg mellan citattecken.
6. Mata ut avbilder och USB-minnen. Spara avbildslösenordet separat i en
   lösenordshanterare vars återställning fungerar även utan just denna Mac.

Säkerhetskopiera också publika fingeravtryck, alias och vilken app varje nyckel
tillhör. Förlita dig inte på GitHub Secrets som enda säkerhetskopia: GitHub låter
dig inte läsa tillbaka den lagrade hemligheten.

## 4. Samma nyckel, separata appar

| App | Paket | Uppdateringskanal | Skyddad signeringsmiljö |
|---|---|---|---|
| Min TV Test | `se.jonasschroder.mintv.qa` | Test / `qa` | `mintv-qa-signing` |
| Min TV | `se.jonasschroder.mintv` | Stabil / `stable` | `mintv-production-signing` |

Använd den redan skapade `mintv-qa.p12` och dess befintliga alias/lösenord
även i produktionsmiljön, enligt ägarens senaste beslut. Byt inte filnamn,
alias eller lösenord bara för att appen heter Min TV i stället för Min TV Test.
Apparna delar inte databas, IPTV-uppgifter, favoriter eller inställningar.
En signerad QA-manifest/QA-APK accepteras aldrig av Stable och tvärtom.

Det nya certifikatet är inte bevisat kompatibelt med de befintliga
installationernas certifikat. En ny nyckel kan inte uppdatera en annan signerare
på plats. Den godkända planen är en första ren adoption för båda apparna;
inget i appen eller workflowen avinstallerar, rensar eller migrerar automatiskt.
Om du senare vill undersöka kompatibilitet utan dataförlust finns
[skrivskyddade APK-kontroller](min-tv-signing.md#verify-before-choosing-a-key).

## 5. Konfigurera GitHub i webbläsaren

Återanslut nätverket när nyckeln och **båda** offlinekopiorna är verifierade.
Öppna [repository Settings → Environments](https://github.com/jonasschroder/OwnTV/settings/environments).
Du behöver ägar-/administratörsbehörighet.

Kontrollera **båda** befintliga miljöerna. Under skyddsregler:

1. Aktivera **Required reviewers** och välj ditt GitHub-konto som godkännare.
   Om en annan betrodd person kan godkänna rekommenderas även Prevent self-review.
   Aktivera inte det för en ensam ägare som själv måste starta och godkänna bygget.
2. Välj **Selected branches and tags** och tillåt exakt **main**, typ **Branch**.
   Lägg inte till taggar, PR-grenar eller `*`. Preflight blockerar sådana regler.
3. Stäng av administratörers förbikoppling av skyddsregler där GitHub erbjuder det.
4. Lägg dessa fyra värden i **Environment secrets**, aldrig repository secrets:

| Secret | Värde för QA |
|---|---|
| `MINTV_KEYSTORE_BASE64` | Kopia av den gemensamma nyckelfilens Base64, enligt kommandot nedan |
| `MINTV_KEYSTORE_PASSWORD` | QA-nyckelfilens lösenord från lösenordshanteraren |
| `MINTV_KEY_ALIAS` | `mintv-qa` |
| `MINTV_KEY_PASSWORD` | QA-nyckelns lösenord, samma som filen för denna PKCS12 |

Kopiera QA-filen till urklipp utan att skriva dess innehåll på skärmen:

```bash
base64 < "$HOME/MinTV-private-signing/mintv-qa.p12" | tr -d '\n' | pbcopy
```

Klistra in direkt i GitHubs **secret value** för `MINTV_KEYSTORE_BASE64`.
Base64 är **inte kryptering**. Ta inte skärmbilder och klistra inte in någon
annanstans. Töm urklipp direkt efter att hemligheten har sparats:

```bash
printf '' | pbcopy
```

Båda miljöerna använder samma fyra secret-namn och samma nyckelvärden enligt
ägarens beslut. QA-jobbet får endast QA-miljön; Stable endast produktionen.
Miljöhemligheter injiceras aldrig i vanliga PR-, fork-, debug- eller Gradle-jobb.
Ägaren har redan bekräftat konfigurationen; inga värden ska skickas i chatt.

## 6. Första godkända kandidaterna — efter granskad main-integration

Workflow är förberedd i PR #3 och blir körbar först när **du** har granskat och
valt att lägga de färdiga ändringarna på `main`. Codex slår inte ihop PR:n.
Det behövs inte någon merge nu för att göra steg 1–5.

När pins, skyddsregler, implementation och tester är klara:

1. Öppna **Actions → Min TV approved signing → Run workflow** på **main**.
2. Välj först `qa` (senare en ny körning för `stable`), klistra in exakt **40 tecken långt commit-ID** från det granskade
   `main`-bygget, ett namn såsom `0.2.0-beta.1` för QA eller `0.2.0` för Stable och korta ändringsanteckningar.
3. Preflight kräver gröna, exakta main-CI-körningar (fulla enhetstester/lint,
   Home-regressioner, uppgraderingstest och i18n). Därefter byggs en **osignerad
   release-APK**, separat från nyckelmiljön.
4. Workflow stannar vid **Review deployments**. Kontrollera SHA, kanal och
   version och godkänn endast den valda kanalens miljö manuellt.
5. Signering sker utanför Gradle. Fel nyckel, signerad debug-input, fel paket,
   fel ABI, saknad pin eller saknade secrets ger **ingen** signerad kandidat.
6. Hämta den godkända kandidatens ZIP från workflowens **Artifacts**.
   `release-candidate.json`, `SIGNING-CERTIFICATE.txt` och `SHA256SUMS` visar
   paket, fingerprint, kod, commit och checksumma. **Inget är publicerat.**

QA-bygget är för `se.jonasschroder.mintv.qa`, Min TV Test. Stable-bygget är
för `se.jonasschroder.mintv`, Min TV. Varje jobb kontrollerar sitt exakta paket;
kanalerna kan inte bytas. Permanenta kandidater är ARM-release-byggen med
debuggable avstängt, pinnat certifikat, verifierade v2/v3-signaturer och en
kryptografiskt signerad uppdateringsmanifest.

**Installera inte om ännu.** Innan den första rena installationen av båda
apparna rekommenderas behövs verklig permanent signering och A→B-acceptanstest.
Gamla lokala data kan förloras vid ren adoption. Exportera därför en krypterad
`.own`-backup för varje app via Backup & Restore, spara dem på Mac med tydliga
appnamn och kontrollera en återställning av en kopia enligt
[backupguiden](min-tv-safe-qa-update.md). En sparad fil ensam är inte bevis på
fungerande återställning. Lagval, Twitch och vissa lokala inställningar ingår
inte i `.own`; dokumentera dessa separat för manuell återkonfiguration.
Inget av dessa steg har utförts på dina appar i molnet.

## 7. Följande versioner och uppdatering direkt i TV:n

Molnuppdateraren är nu implementerad och testas i PR #3. Verklig
signerings-/publicerings-/Chromecast-acceptans återstår. Den gamla Core-
uppdateraren är blockerad; debug-appar och appar vars installerade certifikat
inte motsvarar pinnen gör inga uppdateringsanrop.

En enda workflow äger signeringssekvensen: `versionCode = 1 000 000 + run_number`.
Nya körningar ökar koden för båda paketen, även när QA och Stable turas om.
Android-koden avgör installationsordning; versionsnamnet är bara presentation.
**Re-run jobs** är förbjudet för signering. Starta en ny manuell körning efter fel.
Byt inte namn/fil på workflow eller återställ sekvensen utan en separat granskad
plan med kod över alla redan installerade/publicerade versioner.
En äldre köad körning blockeras även om en nyare kandidat i samma kanal redan
signerats, eftersom GitHub inte garanterar köordningen.

Den separata workflowen **Min TV approved distribution** kräver ett godkänt
signeringsjobb, exakt kanal och `PUBLISH-QA` eller `PUBLISH-STABLE`.
Dess miljöer `mintv-qa-distribution`/`mintv-production-distribution` behöver
samma reviewer/main-only/admin-bypass-skydd men inga signeringssecrets.
De är framtida förutsättningar, inte verifierat konfigurerade här.
Publicering kräver separat godkännande: i detta publika repository blir
**alla Release-APK:er publikt nedladdningsbara**. Actions-artefakter är
begränsade i tid och är inte appens molnuppdateringskälla. Ingen token finns i appen.

Efter den första verifierade och separat godkända adoptionen använder du på TV:n
**Inställningar → Om Min TV → Sök efter uppdateringar**, eller den lilla
**Uppdatera/Senare**-rutan på Home. Läs anteckningarna och välj Uppdatera.
Om Android frågar om installationsbehörighet väljer du själv om den ska ges
för just appen och återvänder sedan. Godkänn installationen i Androids dialog.
Back/Senare/avböj lämnar appdata kvar. Inget installeras tyst. Fullskärms-
uppspelning avbryts inte av ett automatiskt uppdateringsfönster.

## 8. Om Macen försvinner

Installera JDK 21 på den nya betrodda Macen. Öppna en av de krypterade
offlineavbilderna, återställ rätt `.p12` till en privat mapp och prova `keytool
-list -v`. Jämför publika SHA256 med den granskade pinnen. Behåll **samma** nyckel,
alias och lösenord; skapa inte en ny nyckel för att datorn är ny.
GitHub kan fortsätta signera med samma miljöhemligheter utan gamla Macen,
men säkerhetskopian behövs för långsiktig återställning eller flytt av CI.
Om både nyckel och alla säkerhetskopior är förlorade kan ett nytt certifikat
inte uppdatera befintlig app på plats. Stanna och planera separat migrering.

## Kontroller och återstående acceptans

Molnet testar båda apparna med ett gemensamt **engångscertifikat för tom
emulator**, två byggen per paket och syntetiska profiler/källor/favoriter/EPG/
datastore/lagval/appprivata filer. Ett annat engångscertifikat används endast
för att bevisa att Android avvisar fel signerare. Nedgradering ska också
avvisas. QA-uppdateringen ska lämna vanliga appens data/version oförändrade.
Testnycklar och test-APK:er delas inte. Utfall redovisas i acceptansrapporten.

Det ersätter **inte** tester med din permanenta nyckel och riktig Chromecast.
Båda signeringsmiljöerna är rapporterat konfigurerade, men faktisk signering
är blockerad av den ännu omergade main-workflowen. Installation, behörighet,
avbrytande, processåterskapning, fysisk dataöverlevnad och beteende under
riktig IPTV-uppspelning återstår. Ingen ny installation rekommenderas ännu.
