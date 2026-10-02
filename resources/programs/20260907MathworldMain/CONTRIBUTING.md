# Mitarbeit im Projekt

Dieses Dokument sammelt verbindliche Arbeitsregeln fuer MathWorld. Es soll helfen, dass Code, Dokumentation und KI-Unterstuetzung im Team konsistent bleiben.

## Sprachregeln

### Code

Programmiert wird auf Englisch.

Das gilt fuer:

- Dateinamen und Ordnernamen im Quellcode
- Variablen, Funktionen, Klassen, Komponenten und Typen
- Code-Kommentare
- Testnamen
- technische Fehlermeldungen im Code
- Konfigurationsdateien, soweit das jeweilige Tool englische Begriffe erwartet

Beispiele:

```ts
const gardenArea = length * width;

// Calculate the remaining lawn area after subtracting the flower bed.
const remainingLawnArea = totalArea - flowerBedArea;
```

### Dokumentation

Textdokumente im Repository und allgemeine Projektdokumentation werden auf Deutsch verfasst.

Das gilt fuer:

- README-Dateien
- Konzept- und Planungsdokumente
- Setup-Anleitungen
- Roadmaps
- Protokolle
- Miro-Inhalte, soweit sie zum gemeinsamen Projektkontext gehoeren

### Chats mit KI-Unterstuetzung

Jedes Teammitglied kann mit ChatGPT, Codex oder anderen KI-Werkzeugen in der Sprache arbeiten, die fuer die Person am angenehmsten ist.

Die KI soll die jeweilige Chat-Sprache respektieren. Wenn daraus Code entsteht, bleiben Code und Code-Kommentare trotzdem Englisch. Wenn daraus Projektdokumentation entsteht, wird sie auf Deutsch formuliert.

## Git-Arbeitsweise

Vor jeder Arbeit:

```powershell
git status
git pull
```

Neue Aenderungen sollen nach Moeglichkeit in einem eigenen Branch entstehen:

```powershell
git checkout -b feature/kurzer-name
```

Danach:

```powershell
git add .
git commit -m "Kurze Beschreibung der Aenderung"
git push -u origin feature/kurzer-name
```

Groessere Aenderungen werden ueber einen Merge Request in GitLab zusammengefuehrt.

## Aufgabenvergabe im Dreierteam

Das Projekt wird aktuell fuer ein Team aus drei Personen organisiert. Fuer die Aufgabenvergabe gilt:

- Eine Aufgabe hat immer genau eine verantwortliche Person.
- Eine Aufgabe wird erst begonnen, wenn sie in `TASKS.md` als vergeben oder in Arbeit markiert ist.
- Zwei Personen arbeiten nicht gleichzeitig an derselben Aufgabe.
- Zwei offene Aufgaben sollen moeglichst nicht dieselben Dateien veraendern.
- Wenn eine Aufgabe doch dieselben Dateien betrifft wie eine andere laufende Aufgabe, wird sie erst nach Ruecksprache begonnen.
- Unfertige Arbeit wird frueh in einem eigenen Branch gepusht, damit das Team den Stand sehen kann.
- Branch-Namen sollen Aufgabe und Person erkennbar machen, zum Beispiel `feat/anna-level-1` oder `docs/max-task-board`.

`TASKS.md` ist das gemeinsame Aufgabenboard im Repository. Dort werden Status, verantwortliche Person, geplanter Branch und voraussichtlich betroffene Dateien gepflegt.

Wenn ein Teammitglied die KI fragt, welche Aufgabe als Naechstes bearbeitet werden kann, soll die KI zuerst `TASKS.md`, `git status`, vorhandene Branches und nach Moeglichkeit offene Remote-Branches pruefen. Danach soll sie nur eine Aufgabe vorschlagen oder vergeben, die:

- noch keinen Owner hat,
- nicht blockiert ist,
- keine offensichtlichen Dateikonflikte mit laufenden Aufgaben erzeugt,
- fuer den aktuellen Projektstand sinnvoll als naechster Schritt ist.

Nach einer Vergabe aktualisiert die KI `TASKS.md` mit Owner, Status, Datum, Branch-Vorschlag und betroffenen Dateien. Wenn der Name oder die Initialen der fragenden Person fehlen, fragt die KI kurz danach, bevor sie die Aufgabe fest eintraegt.

## Sicherheitsregeln

Niemals committen:

- Passwoerter
- Tokens
- private SSH-Keys
- lokale `.env`-Dateien
- personenbezogene Daten, die nicht ins Repository gehoeren

Riskante Git-Befehle wie `git push --force` oder `git reset --hard` werden nur verwendet, wenn das Team das ausdruecklich abgesprochen hat.
