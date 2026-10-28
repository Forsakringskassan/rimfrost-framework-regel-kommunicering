# Krav — rimfrost-framework-regel-kommunicering

Detta ramverk exponerar kommunicering som en Kafka-anropbar regel. En
kommuniceringsförfrågan tas emot, en OUL-uppgift för handläggare skapas,
och svar skickas när handläggaren markerat vilket utfall som resulterade från 
kommuniceringen.

Kraven här beskriver endast det som är implementerat i detta ramverk. Ärvda krav från
underliggande ramverk upprepas inte.

## 1. Funktionella krav

### FRKOMM-FR-01 — Mottagning och svar på kommuniceringsförfrågan

- **FRKOMM-FR-01.1** Ramverket ska ta emot kommuniceringsförfrågningar från en konfigurerad
  Kafka-topic.
- **FRKOMM-FR-01.2** Svar ska skickas till den topic som angavs i förfrågans `replyTo`-fält.
- **FRKOMM-FR-01.3** Vid kommuniceringsutfallet "ACCEPTERAT_BESTRIDANDE" ska svaret ha `utfall = NEJ`.
- **FRKOMM-FR-01.4** Vid övriga kommuniceringsutfall ska svaret ha `utfall = JA`.
- **FRKOMM-FR-01.5** Vid fel ska svaret ha `utfall = ERROR` och innehålla `RegelErrorInformation`
  med felkod och felmeddelande.

### FRKOMM-FR-02 — Handler-flöde

- **FRKOMM-FR-02.1** Vid mottagen förfrågan ska ramverket initiera en kommuniceringsuppgift 
  via `OulUppgiftService.createOulUppgift()` och vänta — inget Kafka-svar skickas i detta steg.
- **FRKOMM-FR-02.2** Om `OulUppgiftService.createOulUppgift()` kastar `OulServiceException` ska ramverket skicka ett felsvar med
  felkod `RIMFROST_OTHER`.
- **FRKOMM-FR-02.3** Om exception kastas vid läsning av handläggning ska ramverket skicka ett felsvar med
  relevant felkod.

### FRKOMM-FR-03 — Kommuniceringsflöde via REST

- **FRKOMM-FR-03.1** Ramverket ska exponera `GET /{handlaggningId}` som returnerar
  den information handläggaren behöver för att hantera kommunicerings uppgifter. Informationen
  hämtas via regelns implementation av `RegelKommuniceringService`, tillsammans med
  informationsaccessdata enligt FRKOMM-FR-07.
- **FRKOMM-FR-03.2** Ramverket ska exponera `PATCH /{handlaggningId}` för
  registrering av uppdaterad information för bestridda uppgifter via regelns implementation av
  `RegelKommuniceringService`.
- **FRKOMM-FR-03.3** Ramverket ska exponera `PATCH /uppgiftskommentar/{handlaggningId}` för registrering
  av kommentar till uppgift.
- **FRKOMM-FR-03.4** Ramverket ska exponera `PATCH /kommuniceringsutfall/{handlaggningId}` för registrering
  av utfall för kommuniceringen.
- **FRKOMM-FR-03.5** Ramverket ska exponera `POST /{handlaggningId}/done`. Vid anrop
  ska kontroll göras att regelutfall finns. Om regelutfall saknas ska HTTP 422 returneras.
- **FRKOMM-FR-03.6** `GET /{handlaggningId}`, `PATCH /{handlaggningId}`, `PATCH /uppgiftskommentar/{handlaggningId}`,
  `PATCH /kommuniceringsutfall/{handlaggningId}` och `POST /done` ska returnera HTTP 500 om korrelationstillståndet
  inte kan hittas, t.ex. för att timeout redan har tömt korrelationstillståndet.
- **FRKOMM-FR-03.7** `POST /done` ska returnera HTTP 404 om handläggning inte kan hittas.
- **FRKOMM-FR-03.8** 404 fel vid REST-anrop för att avsluta OUL uppgift behandlas som lyckat anrop.
- **FRKOMM-FR-03.9** `PATCH /{handlaggningId}` ska svara med HTTP 500 om konflikt fel uppstår
  vid uppdatering av handläggning.
- **FRKOMM-FR-03.10** `PATCH /uppgiftskommentar/{handlaggningId}` ska svara med HTTP 500 om konflikt fel uppstår
  vid uppdatering av handläggning.
- **FRKOMM-FR-03.11** `PATCH /kommuniceringsutfall/{handlaggningId}` ska svara med HTTP 500 om konflikt fel uppstår
  vid uppdatering av handläggning.
- **FRKOMM-FR-03.12** REST-anrop mot externa tjänster (SID-tjänsten, identitetstjänst och behörighetstjänsten) ska
  returnera HTTP 404 om tjänsten svarar med `NOT_FOUND`.
- **FRKOMM-FR-03.13** REST-anrop mot externa tjänster ska returnera HTTP 400 om tjänsten svarar
  med `BAD_REQUEST`.
- **FRKOMM-FR-03.14** REST-anrop mot externa tjänster ska returnera HTTP 503 om tjänsten svarar
  med `SERVICE_UNAVAILABLE`.
- **FRKOMM-FR-03.15** REST-anrop mot externa tjänster ska returnera HTTP 500 vid övriga fel.

### FRKOMM-FR-04 — Kontroll av skyddad identitet (SID)

- **FRKOMM-FR-04.1** Innan läsning, uppdatering och avslut av uppgift kopplad till handläggningsärende ska ramverket kontrollera
  om någon av handläggningsärendets individer har skyddad identitet via SID-tjänsten.
- **FRKOMM-FR-04.2** Individerna hämtas från `handlaggning.yrkande().rollerIYrkande()` och
  skickas i en `POST /sid/status`-förfrågan till SID-tjänsten.
- **FRKOMM-FR-04.3** Om en eller flera individer har skyddad identitet och handläggaren saknar
  SID-rättigheter ska ramverket returnera HTTP 403 och den efterfrågade operationen ska inte utföras.
- **FRKOMM-FR-04.4** Fel från SID-tjänsten ska resultera i väldefinierade HTTP-statuskoder på samma
  sätt som fel mot handläggningstjänsten: 404, 400, 503 respektive 500.
- **FRKOMM-FR-04.5** SID-kontrollen ska ingå i ramverket och gälla automatiskt för alla
  regelimplementationer utan kodändringar. Varje regelimplementation måste konfigurera `sid.api.base-url`
  med adressen till SID-tjänsten.
- **FRKOMM-FR-04.6** Om en eller flera individer har skyddad identitet och handläggaren saknar
  SID-rättigheter ska ramverket ta bort tilldelningen av OUL-uppgiften
  innan HTTP 403 returneras, via `tryUnassignOulUppgift` som tillhandahålls av
  `rimfrost-framework-regel-oul`, så att uppgiften återgår till otilldelat läge och kan tilldelas
  handläggare med SID-rättigheter.
- **FRKOMM-FR-04.7** Unassign ska alltid försökas oavsett om uppgiften är tilldelad eller inte —
  OUL-tjänsten förväntas hantera anropet korrekt i båda fallen. Om inget uppgifts-ID finns lagrat
  för handläggningsärendet (t.ex. vid en oväntad timingrelaterad situation) ska unassign-försöket
  hoppas över utan fel — HTTP 403 ska ändå returneras.
- **FRKOMM-FR-04.8** Fel vid unassign av OUL-uppgiften ska loggas men ska inte påverka det
  returnerade HTTP 403-svaret. `tryUnassignOulUppgift` hanterar loggning och sväljer felet internt.
- **FRKOMM-FR-04.9** Om SID detekteras ska ramverket kontrollera om den inloggade handläggaren har
  SID-rättigheter via `PermissionsAdapter.hasSidPermission()`. Handläggarens identitet (`idTyp`
  och `idVarde`) ska hämtas via `UserIdentityHolder.getIdentity()`, som håller den identitet som sparats av
  `UserIdentityFilter` (FRKOMM-FR-06.2).
- **FRKOMM-FR-04.10** Om handläggaren har SID-rättigheter ska den efterfrågade operationen utföras utan begränsning.
- **FRKOMM-FR-04.11** *(ej testbar — konfigurationskrav)* Ramverket ska läsa adressen till SID-tjänsten från property `sid.api.base-url`.
- **FRKOMM-FR-04.12** *(ej testbar — konfigurationskrav)* Ramverket ska läsa adressen till behörighetstjänsten från property `permissions.api.base-url`.
- **FRKOMM-FR-04.13** *(ej testbar — konfigurationskrav)* Ramverket ska läsa adressen till identity-tjänsten från property `quarkus.rest-client.identity-api.url`.

### FRKOMM-FR-05 — Utility för kontroll av access till uppgift

- **FRKOMM-FR-05.1** Ramverket ska definiera en utility som kan användas vid förfrågan till
  REST-endpoints för att kontrollera att handläggare har rätt att arbeta med en uppgift.
  Om handläggaren inte har rätt att jobba med uppgiften så ska HTTP 403 returneras som svar.
- **FRKOMM-FR-05.2** Kontrollen ska verifiera att handläggaren är tilldelad som ansvarig för uppgiften.
- **FRKOMM-FR-05.3** SID kontroll enligt FRKOMM-FR-04 ska utföras som del av kontrollen för att säkerställa att handläggaren har rätt att se uppgiften.
- **FRKOMM-FR-05.4** Kontrollen ska returnera så snabbt som möjligt. Om kontroll av tilldelning misslyckas ska inte kontroll av SID göras.
- **FRKOMM-FR-05.5** Om handläggarens identitet saknas i UserIdentityHolder eller om UserIdentityHolder inte är tillgänglig så ska HTTP 401 returneras.

### FRKOMM-FR-06 — Kontroll av identitet vid REST förfrågan

- **FRKOMM-FR-06.1** Användarens identitet ska verifieras vid REST förfrågan via `IdentityAdapter.getIdentity()`. Om identity-tjänsten svarar med 401 så ska HTTP 401 returneras. Övriga fel ska hanteras enligt FRKOMM-FR-03.12 till FRKOMM-FR-03.15.
- **FRKOMM-FR-06.2** Om användarens identitet kunde verifieras så ska den sparas till UserIdentityHolder för att enkelt kunna återanvändas utan att en ny förfrågan till identity-tjänsten ska behövas.

### FRKOMM-FR-07 — Informationsaccessdata i GET-svaret

En BFF som visar regelns data ska kunna publicera ett informationsaccess-event enligt
`rimfrost-service-informationsaccess-asyncapi`. Eventet ska ange
handläggare, processinstans, uppgiftsinstans, uppgiftsspecifikation, tidpunkt och den data
som visas i GUI:t. Individerna listas inte separat, eftersom personuppgifterna finns i den
data som visas. Processinstansen anges med `handlaggningId`, som BFF:en redan har, tillsammans
med `uppgiftId`. Svaret bär därför den data som BFF:en inte själv har (FKPOC-1103).

- **FRKOMM-FR-07.1** `GET /{handlaggningId}` ska returnera ett objekt, en container, med två
  fält: `data` med regelns eget svar oförändrat, och `informationsaccess` med
  informationsaccessdata. Fältet `data` är det som BFF:en lägger i eventets
  `visadInformation`.
- **FRKOMM-FR-07.2** `informationsaccess.uppgiftId` ska vara OUL-uppgiftens id från lagrad
  korrelationsdata. Det anger uppgiftsinstansen. Om korrelationsdata saknas ska ramverket
  returnera HTTP 500, eftersom uppgiften inte kan identifieras utan den.
- **FRKOMM-FR-07.3** `informationsaccess.regelId` och `informationsaccess.regelVersion` ska hämtas
  från regelns `config.yaml`.
- **FRKOMM-FR-07.4** Containern ska bara returneras när SID-kontrollen har passerat.
- **FRKOMM-FR-07.5** `informationsaccess.anvandare` ska vara den inloggade handläggarens
  identitet (`typId` och `varde`), som ramverket hämtar ur anropets token. Då behöver
  BFF:en inte själv tolka token.

## 2. Icke-funktionella krav

### FRKOMM-NFR-01 — Tillförlitlighet

- **FRKOMM-NFR-01.1** Ramverket ska garantera att ett regelsvar alltid skickas för varje mottagen
  regelförfrågan — antingen med utfall eller med felinformation.

### FRKOMM-NFR-02 — Observerbarhet

- **FRKOMM-NFR-02.1** Alla fel i integrationer mot OUL, handläggningstjänsten och Kafka-lagringen
  ska loggas med tillräcklig information för felsökning.

### FRMM-NFR-03 — Testbarhet

- **FRMM-NFR-03.1** Ramverket ska leverera ett test-JAR med abstrakta testbasklasser och
  hjälpklasser som regelimplementationer kan ärva och använda i sina egna tester.