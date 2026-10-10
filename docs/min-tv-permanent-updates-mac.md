# Så förbereder du permanenta Min TV-uppdateringar på din Mac

**Fas A är förberedd. Ägaren har den 10 oktober 2026 bekräftat att QA-nyckeln
har skapats på Mac med JDK 21 och att de krypterade säkerhetskopiorna är klara.
Ägaren har därefter bekräftat att GitHub-miljöns fyra hemligheter är konfigurerade. Ingen Release har
publicerats. Installera inte om någon app nu.**

QA-certifikatets publika SHA256 är incheckat i `config/mintv-signing.json`:

```text
D2:0B:2F:58:F4:E1:2B:95:6E:1E:1D:C4:0E:47:87:3D:9B:9E:E6:A6:66:A2:DE:1A:F2:A6:B8:AC:3D:40:54:7C
```

Steg 1–3 nedan beskriver den redan genomförda nyckelförberedelsen; skapa inte
en ny ersättningsnyckel. Steg 5 har nu genomförts enligt ägarens bekräftelse.
Nyckelfilen har inte lämnats till Codex. Faktisk signering och verifiering av
nyckeln i GitHub återstår. Produktionsnyckeln behandlas separat i steg 4.
Fas B/C har nu en implementation i PR #3; verklig signering, publicering och
acceptanstest är fortfarande spärrade i väntan på separata godkännanden.

Vanliga CI-tester bygger fortfarande båda debug-apparna, men nya APK:er med
tillfälliga CI-certifikat laddas inte längre upp som installationsfiler.
Tidigare artefakter är inte permanenta uppdateringar. Behåll fungerande Min TV v0.1.

Aktuell fortsättning: [molnuppdateringar och verifieringsgränser](min-tv-cloud-updates.md).
Ägaren har nu bekräftat att QA-miljöns fyra secrets är konfigurerade. Skyddsreglerna
är verifierade via API. Faktisk permanent signering väntar på en granskad workflow
på main; PR #3 mergas inte automatiskt. Steg 1–5 är bevarade som återställningsguide.

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

## 4. Håll produktionen helt separat

**Skapa inte en ny produktionsnyckel som ersättning för v0.1 ännu.** Först
behöver vi läsa v0.1:s publika certifikat och versionCode samt hitta motsvarande
privata nyckel från den ursprungliga byggdatorn eller en säkerhetskopia.
[Den äldre signeringsguiden](min-tv-signing.md#verify-before-choosing-a-key)
visar de skrivskyddade APK-kontrollerna. En redan sparad exakt v0.1-APK går också
att undersöka. Om ADB inte redan är auktoriserat: lämna TV:n oförändrad tills
du separat väljer att sätta upp anslutningen.

Om nyckeln finns och certifikatet stämmer: spara den med separata krypterade
kopior, helt skild från QA. Pinna publika certifikatet och installerat versionCode
i `stable`-delen av `config/mintv-signing.json`. Workflow blockerar Stable tills
alla dessa värden finns och certifikaten stämmer överens. Om v0.1-nyckeln är
borta kan ett nytt certifikat **inte** uppdatera v0.1 på plats. Ingen avinstallation,
rensning eller produktionsmigrering ingår i detta arbete.

## 5. Konfigurera GitHub i webbläsaren

Återanslut nätverket när nyckeln och **båda** offlinekopiorna är verifierade.
Öppna [repository Settings → Environments](https://github.com/jonasschroder/OwnTV/settings/environments).
Du behöver ägar-/administratörsbehörighet.

Skapa **mintv-qa-signing**. Under skyddsregler:

1. Aktivera **Required reviewers** och välj ditt GitHub-konto som godkännare.
   Om en annan betrodd person kan godkänna rekommenderas även Prevent self-review.
   Aktivera inte det för en ensam ägare som själv måste starta och godkänna bygget.
2. Välj **Selected branches and tags** och tillåt exakt **main**, typ **Branch**.
   Lägg inte till taggar, PR-grenar eller `*`. Preflight blockerar sådana regler.
3. Stäng av administratörers förbikoppling av skyddsregler där GitHub erbjuder det.
4. Lägg dessa fyra värden i **Environment secrets**, aldrig repository secrets:

| Secret | Värde för QA |
|---|---|
| `MINTV_KEYSTORE_BASE64` | Kopia av QA-filens Base64, enligt kommandot nedan |
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

Skapa separat **mintv-production-signing** med samma skyddsregler. Lägg dess
fyra secrets först när den riktiga produktionsnyckeln har identifierats och
säkerhetskopierats. Namnen är samma, men **värdena måste komma från den andra
nyckeln**. QA-jobbet kommer endast åt QA-miljön; Stable endast produktionen.
Miljöhemligheter injiceras aldrig i vanliga PR-, fork-, debug- eller Gradle-jobb.

Stoppa här och meddela Codex: **QA-säkerhetskopior verifierade**, **QA-miljö
konfigurerad**, och det **publika SHA256-fingeravtrycket**. Produktionsstatus
kan vara **ännu okänd**. Inga hemliga värden behövs i svaret.

## 6. Det första godkända QA-bygget — efter nästa verifieringsfas

Workflow är förberedd i PR #3 och blir körbar först när **du** har granskat och
valt att lägga de färdiga ändringarna på `main`. Codex slår inte ihop PR:n.
Det behövs inte någon merge nu för att göra steg 1–5.

När pins, skyddsregler, implementation och tester är klara:

1. Öppna **Actions → Min TV approved signing → Run workflow** på **main**.
2. Välj `qa`, klistra in exakt **40 tecken långt commit-ID** från det granskade
   `main`-bygget, ett namn såsom `0.2.0-beta.1` och korta ändringsanteckningar.
3. Preflight kräver gröna, exakta main-CI-körningar (fulla enhetstester/lint,
   Home-regressioner, uppgraderingstest och i18n). Därefter byggs en **osignerad
   release-APK**, separat från nyckelmiljön.
4. Workflow stannar vid **Review deployments**. Kontrollera SHA, kanal och
   version, välj endast QA-miljön och godkänn manuellt.
5. Signering sker utanför Gradle. Fel nyckel, signerad debug-input, fel paket,
   fel ABI, saknad pin eller saknade secrets ger **ingen** signerad kandidat.
6. Hämta den godkända kandidatens ZIP från workflowens **Artifacts**.
   `release-candidate.json`, `SIGNING-CERTIFICATE.txt` och `SHA256SUMS` visar
   paket, fingerprint, kod, commit och checksumma. **Inget är publicerat.**

Det signerade ARM-bygget är för `se.jonasschroder.mintv.qa`. Appnamnet är
Min TV Test. Stable kan aldrig väljas av QA-jobbet. Permanenta kandidater är
release-byggen med debuggable avstängt. Separat QA-/produktionsnyckel behövs
fortfarande för att verifiera två verkliga permanent signerade versioner.

**Installera inte om ännu.** Du har godkänt att vi planerar en enda ren QA-installation
om gamla signeringsnyckeln är förlorad, men inte att Codex utför den. Innan det
stegvis installationsförfarande lämnas för utförande ska kandidatens signerare
vara verifierad och de permanent signerade uppgraderingstesterna vara klara.
Gamla QA-data kan då förloras enligt ditt val. En valfri krypterad IPTV-backup
enligt [QA-backupguiden](min-tv-safe-qa-update.md) kan bevara källor/favoriter;
lagval, Twitch och vissa lokala inställningar ingår inte i `.own`-backupen.
Vanlig Min TV v0.1 ska lämnas installerad och orörd.

## 7. Följande versioner och uppdatering direkt i TV:n

Detta är **nästa fas**, inte fungerande funktion i fas A. Tills säker
kanalvalidering är implementerad är Core-uppdateraren blockerad i appen;
Inställningar visar att uppdateringar förbereds och gör inga uppdateringsanrop.

En enda workflow äger signeringssekvensen: `versionCode = 1 000 000 + run_number`.
Nya körningar ökar koden för båda paketen, även när QA och Stable turas om.
Android-koden avgör installationsordning; versionsnamnet är bara presentation.
**Re-run jobs** är förbjudet för signering. Starta en ny manuell körning efter fel.
Byt inte namn/fil på workflow eller återställ sekvensen utan en separat granskad
plan med kod över alla redan installerade/publicerade versioner.
En äldre köad körning blockeras även om en nyare kandidat i samma kanal redan
signerats, eftersom GitHub inte garanterar köordningen.

Fas B ska ge två explicit åtskilda, manuellt godkända Release-kanaler och
autentiserad metadata/checksumma. GitHub Releases är enklast utan egen server,
men i detta publika repository är **alla publicerade APK:er publikt nedladdningsbara**.
Actions-artefakter går ut och behöver ofta inloggning; appen ska inte använda
dem som permanent uppdateringskälla. Ingen GitHub-token får bäddas in i APK:n.
Release-kandidatsfilen från fas A är byggbevis, **inte** betrodd uppdateringsmetadata.

Fas C ska återanvända TV-dialogerna och granskade PackageInstaller-mönster,
men ersätta det osäkra kanalvalet och lägga till signatur/checksumma/version/
ABI/Android-/lagringskontroller före installationssessionen. Det behövs stöd
för installationsbehörighet, avbrytande och processåterskapning samt tester av
Uppdatera/Senare, framsteg, About och fjärrkontroll. Ingen bakgrundstjänst eller
WebView behövs. Fullskärmsuppspelning får inte avbrytas. Först efter denna fas
kan TV:n hämta godkända uppdateringar och visa Androids installationsbekräftelse.

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

Molnet kan verifiera byggen, signeringspolicy och en verklig Android 14-
uppgradering med **två separata engångsnycklar för tom emulator**, två byggen
per paket, syntetiska profiler/källor/favoriter/datastore/lagval och appprivata
filer. Testet försöker även fel signerare och nedgradering; QA ska lämna
vanliga appens data och version oförändrade. Test-APK:er och testnycklar delas inte.

Detta ersätter **inte** ett test med dina permanenta QA-/produktionsnycklar.
Det är blockerat tills du konfigurerat dem. Inga riktiga IPTV-uppgifter,
v0.1-certifikat, offlinekopior eller Chromecast-installationer är åtkomliga i molnet.
Installationsdialog, behörighet, avbrytande, fysisk dataöverlevnad och beteende
under riktig IPTV-uppspelning återstår efter fas C på Chromecast.
