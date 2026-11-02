# rimfrost-framework-regel-kommunicering

Ramverkskomponent som exponerar kommunicering som en Kafka-anropbar regel.
En kommuniceringsförfrågan tas emot, en OUL-uppgift för handläggare skapas,
och svar skickas när handläggaren markerat vilket utfall som resulterade från
kommuniceringen.

Baseras på [rimfrost-framework-regel](https://github.com/Forsakringskassan/rimfrost-framework-regel)
och [rimfrost-framework-regel-oul](https://github.com/Forsakringskassan/rimfrost-framework-regel-oul).

## Aktörer

| Aktör                             | Roll                                                                                                                                     |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Kundbehovsflödet                  | Initierar kommuniceringsförfrågan via Kafka och tar emot svar                                                                            |
| Regelimplementationer             | Överskriver implementerar `RegelKommuniceringService` och överskriver `read()` och `update()` |
| Handläggarportalen                | Anropar REST-gränssnittet för att läsa, uppdatera och markera kommunicering som klar                                                     |
| OUL (Operativt Uppgiftslager)     | Tar emot kommuniceringsuppgiften och publicerar statusnotifieringar                                                                      |

## Ansvarsområden

- **Kafka request/response** — konsumerar kommuniceringsförfrågningar och skickar svar
  dynamiskt till förfrågans `replyTo`-topic.
- **REST-flöde för kommunicering** — exponerar `GET/PATCH /{handlaggningId}`, `POST /{handlaggningId}/done`, 
  `PATCH /uppgiftskommentar/{handlaggningId}` och `PATCH /kommuniceringsutfall/{handlaggningId}` för handläggarportalen.
- **Automatisk kontroll av identitet vid REST fråga** — kontrollerar att det identitets-token som inkluderas med `Authorization`-headern är giltigt och sparar läst identitet i `UserIdentityHolder`.
- **Guard util för access kontroll** — tillhandahåller en guard util klass `RegelAccessGuard` för kontroll att användare är tilldelad till uppgift samt att de har de rättigheter som behövs ifall uppgiften innehåller skyddade identiteter.
- **Automatisk access kontroll för REST-flöde** — `GET/PATCH/POST /{handlaggningId}`, `PATCH /uppgiftskommentar/{handlaggningId}` och `PATCH /kommuniceringsutfall/{handlaggningId}` kontrolleras automatiskt via `RegelAccessGuard` innan operationen utförs.