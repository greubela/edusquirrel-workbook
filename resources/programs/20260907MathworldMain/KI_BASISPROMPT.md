# KI-Basisprompt und Projektfahrplan fuer MathWorld

> Ziel dieses Dokuments:  
> Diese Datei ist die gemeinsame "Gedaechtnis- und Arbeitsanweisung" fuer KI-Unterstuetzung im Projekt **MathWorld**. Sie soll im Git-Repository liegen und bei neuen ChatGPT-/Codex-/KI-Sessions immer mitgegeben oder zuerst gelesen werden.
>
> Zielpfad im MathWorld-Repository: `KI_BASISPROMPT.md`

---

## 1. Kurzfassung fuer jede neue KI-Session

Kopiere diesen Abschnitt am Anfang einer neuen KI-Unterhaltung, falls die KI nicht direkt auf das Repository zugreifen kann:

```text
Wir entwickeln im Studienprojekt MathWorld eine Lernanwendung fuer den Mathematikunterricht.

Bitte arbeite mit dem Projektkontext aus KI_BASISPROMPT.md. Wenn du keinen direkten Datei- oder Git-Zugriff hast, gib uns sehr klare Schritt-fuer-Schritt-Anleitungen und PowerShell-Befehle, die wir kopieren koennen. Frage fehlende Projektinformationen beim ersten Mal aktiv ab und hilf danach, diese Datei aktuell zu halten.

Wichtig:
- Git-Repository: https://scm.cms.hu-berlin.de/schrecks/mathworld.git
- Wir nutzen zusaetzlich ein Miro Board fuer Planung, User Stories und Visualisierungen.
- Einige Teammitglieder haben wenig Git-/Programmiererfahrung.
- Sprachregel: Code und Code-Kommentare werden auf Englisch geschrieben. Projektdokumentation im Repository wird auf Deutsch geschrieben. Teammitglieder duerfen in KI-Chats die Sprache ihrer Wahl nutzen.
- Teamregel: Wir arbeiten zu dritt. Aufgaben werden ueber `TASKS.md` vergeben. Eine Aufgabe hat genau einen Owner und wird nicht parallel von mehreren Personen bearbeitet.
- Bitte vermeide riskante Git-Befehle wie force push oder reset --hard, ausser wir bitten ausdruecklich darum.
- Beginne jede Arbeit mit git status und, wenn moeglich, git pull.
- Wenn jemand nach der naechsten Aufgabe fragt, pruefe zuerst `TASKS.md`, Branches und moegliche Dateikonflikte. Weise nur eine offene, nicht blockierte und konfliktarme Aufgabe zu und aktualisiere danach `TASKS.md`.
- Beende groessere Sessions mit einer kurzen Aktualisierung von Projektstand, Roadmap und offenen Punkten.
```

---

## 2. Projektsteckbrief

- **Projektname:** MathWorld
- **Ziel:** Lernanwendung fuer den Mathematikunterricht
- **Repository:** `https://scm.cms.hu-berlin.de/schrecks/mathworld.git`
- **Team:** Studierende im gemeinsamen Entwicklungsprojekt
- **Planung:** Git + Miro Board
- **Zielgruppe:** 5. bis 6. Klasse
- **Aktueller Stand:** erster spielbarer React/Vite-Prototyp der Garten-Mission
- **Stand dieses Dokuments:** 2026-06-08

---

## 3. Was die KI beim ersten Einsatz fragen soll

Wenn diese Datei in einer neuen KI-Session genutzt wird und die folgenden Informationen noch nicht im Repository stehen, soll die KI sie zuerst knapp abfragen. Wenn die Nutzer die Antwort nicht wissen, soll die KI mit sinnvollen Annahmen weiterarbeiten und die Annahmen sichtbar markieren.

1. **Fachlicher Umfang:** Welche mathematischen Themen sollen im ersten MVP vorkommen?  
   Beispiele: Bruchrechnung, lineare Funktionen, Gleichungen, Geometrie, Prozentrechnung.

2. **Zielgruppe:** Fuer welche Klassenstufe oder Altersgruppe ist die Anwendung gedacht?

3. **Nutzungssituation:** Soll MathWorld im Unterricht, zuhause, in Gruppenarbeit oder als Selbstlern-App genutzt werden?

4. **MVP-Ziel:** Was muss am Ende minimal funktionieren, damit das Projekt als vorzeigbar gilt?

5. **Tech-Stack:** Gibt es Vorgaben der Uni oder des Teams?  
   Beispiele: React, Vue, Svelte, Python, Java, Node.js, Datenbank, keine Datenbank.

6. **Aufgabenverteilung:** Wer arbeitet an Frontend, Backend, Inhalt, Design, Dokumentation und Git/Miro?

7. **Git-Arbeitsweise:** Arbeiten alle direkt auf `main`/`master`, oder nutzt das Team Feature-Branches und Merge Requests?

8. **Miro Board:** Welche Bereiche soll das Miro Board enthalten?  
   Beispiele: User Story Map, Sprint Board, Architektur, Lerninhalte, Tests, Praesentation.

9. **Abgabeform:** Muss am Ende Code, Dokumentation, Praesentation, Demo-Video oder ein Bericht abgegeben werden?

10. **Termine:** Welche Deadlines, Zwischenpraesentationen oder Meilensteine gibt es?

Nach der Klaerung soll die KI vorschlagen, die Antworten in diese Datei oder in separate Dateien wie `PROJECT_CONTEXT.md`, `ROADMAP.md` und `MIRO_BOARD_EXPORT.md` zu uebernehmen.

---

## 4. Grundregeln fuer KI-Unterstuetzung

Die KI soll:

- zuerst den aktuellen Projektstand lesen, nicht raten
- die Sprachregeln aus `CONTRIBUTING.md` beachten: Code und Code-Kommentare auf Englisch, Projektdokumentation auf Deutsch, Chat-Sprache nach Wahl des jeweiligen Teammitglieds
- bei Git-Arbeiten immer vorsichtig und nachvollziehbar vorgehen
- Befehle fuer Windows PowerShell bevorzugen
- bei Chat-Umgebungen ohne Terminalzugriff den Nutzer bitten, Befehle auszufuehren und die Ausgabe einzufuegen
- Aufgaben nur nach `TASKS.md` vergeben und immer nur an eine verantwortliche Person gleichzeitig
- bei Aufgabenvergabe auf Dateikonflikte mit laufenden Aufgaben achten und `TASKS.md` direkt aktualisieren
- nie Zugangsdaten, Tokens, Passwoerter oder private Keys in Git speichern
- vor riskanten Aktionen erklaeren, was passiert
- nach Code-Aenderungen Tests, Build oder zumindest sinnvolle Checks vorschlagen
- am Ende einer Session kurz zusammenfassen:
  - was geaendert wurde
  - welche Dateien betroffen sind
  - welche Befehle erfolgreich waren
  - was als Naechstes offen ist

Die KI soll nicht:

- `git push --force` vorschlagen
- `git reset --hard` vorschlagen
- unbekannte lokale Aenderungen ueberschreiben
- grosse Architekturentscheidungen treffen, ohne Alternativen und Folgen zu nennen
- Dateien mit Secrets committen

---

## 5. Repository einrichten

### 5.1 Git pruefen

PowerShell:

```powershell
git --version
```

Falls Git nicht installiert ist, bitte Git for Windows installieren:  
https://git-scm.com/download/win

### 5.2 Projekt klonen

In PowerShell einen Ordner waehlen, zum Beispiel `C:\Users\<DEIN_NAME>\Desktop`:

```powershell
cd "$env:USERPROFILE\Desktop"
git clone https://scm.cms.hu-berlin.de/schrecks/mathworld.git
cd mathworld
git status --short
```

Wenn Git nach Benutzername und Passwort fragt, nutzt die HU-Git-Zugangsdaten oder einen persoenlichen Access Token. Zugangsdaten niemals in eine Datei schreiben und niemals an ChatGPT senden.

### 5.3 Bestehendes lokales Projekt mit Remote verbinden

Nur nutzen, wenn der Projektordner lokal schon existiert und noch kein Remote gesetzt ist:

```powershell
cd "C:\Pfad\zu\mathworld"
git remote -v
git remote add origin https://scm.cms.hu-berlin.de/schrecks/mathworld.git
git remote -v
```

Wenn `origin` bereits existiert, nicht blind ersetzen. Dann zuerst die Ausgabe von `git remote -v` mit der KI teilen.

---

## 6. Standardablauf fuer jede Arbeitssession

Dieser Ablauf ist absichtlich einfach gehalten, damit auch Teammitglieder mit wenig Git-Erfahrung sicher arbeiten koennen.

### 6.1 Start

PowerShell:

```powershell
cd "C:\Pfad\zu\mathworld"
git status --short
git branch --show-current
git pull --rebase
git log -5 --oneline
```

Wenn `git pull --rebase` Fehler oder Konflikte meldet, nicht weiterarbeiten. Ausgabe kopieren und der KI geben.

### 6.2 Arbeiten

Vor Aenderungen:

```powershell
git status --short
```

Nach Aenderungen:

```powershell
git status --short
git diff --stat
```

Optional fuer Details:

```powershell
git diff
```

### 6.3 Commit vorbereiten

Nur Dateien aufnehmen, die wirklich zur Aufgabe gehoeren:

```powershell
git add KI_BASISPROMPT.md
git status --short
```

Oder mehrere konkrete Dateien:

```powershell
git add src/App.tsx src/styles.css ROADMAP.md
git status --short
```

### 6.4 Commit erstellen

Commit-Nachrichten kurz und eindeutig:

```powershell
git commit -m "docs: add KI basesprompt and project roadmap"
```

Empfohlene Praefixe:

- `docs:` Dokumentation
- `feat:` neues Feature
- `fix:` Fehlerbehebung
- `chore:` Wartung, Setup, Abhaengigkeiten
- `refactor:` interne Umstrukturierung ohne neues Verhalten
- `test:` Tests

### 6.5 Push

```powershell
git push
```

Wenn Git sagt, dass kein Upstream gesetzt ist:

```powershell
git push -u origin HEAD
```

Wenn Push wegen neuer Remote-Aenderungen fehlschlaegt:

```powershell
git pull --rebase
git push
```

Bei Konflikten: stoppen und Ausgabe an die KI geben.

---

## 7. Branch-Empfehlung fuer Teamarbeit

Wenn mehrere Personen gleichzeitig arbeiten, sind Feature-Branches sicherer als direkte Arbeit auf `main` oder `master`.

Aktuellen Branch pruefen:

```powershell
git branch --show-current
```

Neuen Branch fuer eine Aufgabe erstellen:

```powershell
git switch -c feat/kurzer-aufgabenname
```

Beispiele:

```powershell
git switch -c feat/brueche-uebungen
git switch -c feat/startseite
git switch -c docs/miro-roadmap
```

Branch pushen:

```powershell
git push -u origin HEAD
```

Danach im HU-Git eine Merge Request anlegen, falls das Team diesen Workflow nutzt.

Wenn das Team anfangs direkt auf `main` oder `master` arbeitet, dann besonders wichtig:

- vor jeder Arbeit `git pull --rebase`
- kleine Commits
- haeufig pushen
- nicht gleichzeitig dieselben Dateien bearbeiten

---

## 8. Wenn die KI keinen direkten Terminalzugriff hat

Dann soll die KI nicht so tun, als haette sie den Stand gesehen. Sie soll den Nutzer bitten, diese Befehle in PowerShell auszufuehren und die Ausgabe einzufuegen:

```powershell
cd "C:\Pfad\zu\mathworld"
git status --short
git branch --show-current
git log -5 --oneline
git remote -v
```

Wenn Dateien relevant sind:

```powershell
Get-ChildItem -Force
```

Oder fuer eine Projektstruktur:

```powershell
cmd /c "tree /A /F"
```

Bei sehr langer Ausgabe soll der Nutzer nur die relevanten Ausschnitte kopieren.

---

## 9. Datei aus ChatGPT heraus lokal speichern

Falls ChatGPT nur Text liefert und keine Datei erstellen kann:

1. Im Projektordner eine neue Datei `KI_BASISPROMPT.md` erstellen.
2. Den Markdown-Inhalt aus dem Chat einfuegen.
3. Speichern.
4. Danach in PowerShell:

```powershell
cd "C:\Pfad\zu\mathworld"
git status --short
git add KI_BASISPROMPT.md
git commit -m "docs: add KI basesprompt"
git push
```

Alternative ueber PowerShell ist moeglich, aber fuer lange Markdown-Dateien fehleranfaelliger. Fuer Anfaenger ist Editor/VS Code/Notepad meistens sicherer.

---

## 10. Miro Board: KI-Artefakte erzeugen

Das Team nutzt ein Miro Board. Die KI soll bei Bedarf Inhalte so erzeugen, dass sie leicht in Miro eingefuegt werden koennen.

Empfohlene Dateien:

- `miro/MIRO_USER_STORIES.md`  
  User Stories nach Rollen und Lernzielen

- `miro/MIRO_SPRINT_BOARD.csv`  
  Spalten fuer Backlog, To Do, In Progress, Review, Done

- `miro/MIRO_ROADMAP.md`  
  Meilensteine und Zeitplan

- `miro/MIRO_ARCHITEKTUR.md`  
  einfache Architektur-Skizze als Mermaid-Diagramm und kurze Erklaerung

- `miro/MIRO_TESTPLAN.md`  
  Testfaelle fuer Lernaufgaben, UI, Fortschritt, Speichern/Laden

Beispiel fuer eine CSV, die man in Miro oder Tabellenprogramme kopieren kann:

```csv
Bereich,Titel,Beschreibung,Prioritaet,Status,Verantwortlich
Backlog,Themenumfang klaeren,Klassenstufe und erste Mathethemen festlegen,Hoch,Offen,
Backlog,MVP definieren,Minimalen Demoumfang beschreiben,Hoch,Offen,
To Do,Git Setup pruefen,Alle Teammitglieder koennen pullen und pushen,Hoch,Offen,
To Do,Startseite skizzieren,Erster Screen mit Navigation und Lernpfad,Mittel,Offen,
```

Beispiel fuer User Stories:

```text
Als Schuelerin moechte ich eine Aufgabe Schritt fuer Schritt loesen, damit ich nicht nur das Ergebnis sehe, sondern den Loesungsweg verstehe.

Als Lehrkraft moechte ich Themen und Schwierigkeitsgrade auswaehlen, damit ich die App passend zum Unterricht einsetzen kann.

Als Teammitglied moechte ich klare Git-Anweisungen haben, damit ich Aenderungen sicher synchronisieren kann.
```

---

## 11. Projektfahrplan

Dieser Fahrplan ist ein Startpunkt und soll im Team angepasst werden.

### Phase 0: Setup und gemeinsame Arbeitsweise

- [x] Git-Repository angelegt
- [x] KI-Basisprompt vorbereitet
- [ ] Alle Teammitglieder koennen Repository klonen
- [ ] Alle Teammitglieder koennen `git pull` und `git push` sicher ausfuehren
- [ ] Miro Board mit Grundstruktur angelegt
- [ ] Tech-Stack entschieden
- [ ] README mit Startanleitung angelegt

### Phase 1: Fachliches Konzept

- [ ] Zielgruppe / Klassenstufe festgelegt
- [ ] erste Mathethemen festgelegt
- [ ] didaktisches Grundprinzip beschrieben
- [ ] MVP-Umfang festgelegt
- [ ] Beispielaufgaben gesammelt
- [ ] User Stories geschrieben
- [ ] Bewertungskriterien der Lehrveranstaltung geklaert

### Phase 2: Technischer MVP

- [ ] Projektstruktur angelegt
- [ ] Entwicklungsserver startet lokal
- [ ] erste Startseite oder Hauptansicht sichtbar
- [ ] Navigation zwischen Bereichen funktioniert
- [ ] erste interaktive Matheaufgabe funktioniert
- [ ] Rueckmeldung bei richtiger/falscher Antwort funktioniert
- [ ] einfacher Fortschritt oder Ergebnisanzeige vorhanden

### Phase 3: Lerninhalte und Interaktion

- [ ] mehrere Aufgaben pro Thema
- [ ] Schwierigkeitsgrade oder Lernpfade
- [ ] Hilfestellungen / Tipps
- [ ] Loesungsweg oder Erklaerung
- [ ] Fehlerfreundlichkeit und Feedback
- [ ] visuelle Elemente fuer mathematische Konzepte

### Phase 4: Qualitaet und Tests

- [ ] Build laeuft fehlerfrei
- [ ] grundlegende Tests oder manuelle Testliste vorhanden
- [ ] Bedienung auf typischen Bildschirmgroessen geprueft
- [ ] Texte verstaendlich und altersgerecht
- [ ] bekannte Bugs dokumentiert
- [ ] README und Projektstand aktualisiert

### Phase 5: Abgabe / Praesentation

- [ ] Demo-Szenario vorbereitet
- [ ] Praesentation oder Bericht vorbereitet
- [ ] Miro Board aufraeumen
- [ ] finale Roadmap und Reflexion dokumentiert
- [ ] finaler Git-Stand gepusht
- [ ] Team prueft, ob alle wichtigen Dateien im Repository sind

---

## 12. Aktueller Projektstand

Stand: 2026-06-08

Erledigt:

- Git-Repository wurde angelegt: `https://scm.cms.hu-berlin.de/schrecks/mathworld.git`
- Teamregeln und Aufgabenvergabe sind in `CONTRIBUTING.md` und `TASKS.md` dokumentiert
- MVP-Kontext ist in `PROJECT_CONTEXT.md` festgehalten
- React/Vite/TypeScript-Grundgeruest ist eingerichtet
- erster spielbarer Prototyp der Garten-Mission ist implementiert
- lokale Entwicklung ist in `README.md`, `SETUP.md` und `docs/IMPLEMENTATION_STATUS.md` dokumentiert

Aktuell implementiert:

- Mission 1 mit randomisierten einfachen Zielflaechen und `1 x 1`-Quadraten
- Mission 2 mit den Pflichtformen `2 x 2`, `1 x 3` und `3 x 2`
- Mission 3 mit freier Rechteck-Erzeugung per Eingabe und per Drag auf der Arbeitsflaeche
- Raster, Snapping, Zielpruefung, Feedback, Neustart und Levelnavigation
- temporaerer Button `Dev: Naechstes Level` fuer Entwicklung und Test

Naechste sinnvolle Schritte:

```text
1. Nach git pull npm.cmd install ausfuehren
2. npm.cmd run dev oder npm.cmd run build pruefen
3. TASKS.md lesen und eine offene, konfliktarme Aufgabe waehlen
4. Besonders sinnvoll: T-003, T-007, T-008 oder T-011
5. App-Code mittelfristig aus src/App.tsx in Daten, Komponenten und Logik aufteilen
```

---

## 13. Empfohlene Projektdateien

Sobald das Repository eingerichtet ist, sollte die Struktur ungefaehr so aussehen:

```text
mathworld/
  README.md
  KI_BASISPROMPT.md
  ROADMAP.md
  PROJECT_CONTEXT.md
  miro/
    MIRO_USER_STORIES.md
    MIRO_SPRINT_BOARD.csv
    MIRO_ROADMAP.md
    MIRO_ARCHITEKTUR.md
    MIRO_TESTPLAN.md
  docs/
    entscheidungen.md
    session-log.md
  src/
    ...
```

Nicht jede Datei muss sofort existieren. Wichtig ist, dass Projektstand und offene Punkte nicht nur im Chat bleiben.

---

## 14. Sessionabschluss-Vorlage

Am Ende einer groesseren KI-Session soll die KI diese Vorlage ausfuellen und vorschlagen, sie in `docs/session-log.md` oder hier in den Projektstand zu uebernehmen.

```markdown
## Session YYYY-MM-DD - Kurztitel

### Erledigt
- 

### Geaenderte Dateien
- 

### Verifikation
- 

### Entscheidungen
- 

### Offene Punkte
- 

### Naechster Schritt
- 
```

---

## 15. Prompt fuer Git-Hilfe

Wenn ihr nur Git-Hilfe braucht, koennt ihr diesen kurzen Prompt in ChatGPT einfuegen:

```text
Bitte hilf mir sicher mit Git in unserem MathWorld-Projekt.

Ich arbeite unter Windows mit PowerShell. Gib mir nur Befehle, die ich Schritt fuer Schritt kopieren kann. Erklaere kurz, was jeder Befehl macht. Vermeide riskante Befehle wie force push oder reset --hard.

Ich fuehre jetzt aus:

cd "C:\Pfad\zu\mathworld"
git status --short
git branch --show-current
git log -5 --oneline
git remote -v

Danach kopiere ich dir die Ausgabe.
```

---

## 16. Prompt fuer Entwicklungs-Hilfe

Wenn ihr eine neue Funktion bauen wollt:

```text
Bitte hilf mir beim Entwickeln einer neuen Funktion im MathWorld-Projekt.

Lies zuerst KI_BASISPROMPT.md, README.md und falls vorhanden ROADMAP.md / PROJECT_CONTEXT.md.
Wenn du keinen Dateizugriff hast, frage mich nach den relevanten Ausschnitten.

Arbeitsweise:
1. Erst aktuellen Git-Stand pruefen.
2. Kurz erklaeren, welche Dateien vermutlich betroffen sind.
3. Aenderungen klein halten.
4. Nach der Umsetzung passende Checks nennen.
5. Am Ende Commit-Vorschlag und kurze Aktualisierung fuer Projektstand geben.

Funktion:
[HIER BESCHREIBEN]
```

---

## 17. Prompt fuer Aufgabenvergabe

Wenn ein Teammitglied eine neue Aufgabe sucht, kann dieser Prompt genutzt werden:

```text
Bitte hilf mir bei der Aufgabenvergabe im MathWorld-Projekt.

Lies zuerst KI_BASISPROMPT.md, PROJECT_CONTEXT.md und TASKS.md.
Pruefe danach git status, den aktuellen Branch und vorhandene Branches.

Ich bin ein Teammitglied und moechte eine Aufgabe uebernehmen.
Weise mir nur eine Aufgabe zu, die in TASKS.md noch offen ist, nicht blockiert ist und keine offensichtlichen Dateikonflikte mit laufenden Aufgaben erzeugt.

Wenn du meinen Namen oder meine Initialen fuer den Owner-Eintrag brauchst, frage kurz danach.
Aktualisiere TASKS.md nach der Vergabe mit Owner, Status, Branch-Vorschlag, betroffenen Dateien und Vergabeprotokoll.
```

Empfohlene konfliktarme Aufgaben, solange am App-Code gearbeitet wird:

- `T-003`: Missionen und Datenmodell fachlich ausarbeiten
- `T-007`: Manuelle Testliste fuer MVP erstellen
- `T-008`: Deployment-Option fuer statische Demo klaeren
- `T-009`: Randomisierte Levelvarianten planen, am besten nach oder zusammen mit `T-003`

App-nahe Aufgaben wie `T-005` und `T-010` sollen erst vergeben werden, wenn der aktuelle App-Code-Stand gemerged oder ausdruecklich abgestimmt ist.

---

## 18. Prompt fuer Miro-Hilfe

```text
Bitte hilf uns, Inhalte fuer unser Miro Board im MathWorld-Projekt zu erzeugen.

Erzeuge die Inhalte so, dass wir sie einfach kopieren koennen:
- User Stories
- Sprint Board als CSV
- Roadmap
- Architekturuebersicht
- offene Fragen
- Risiken

Nutze klare, kurze Karten-Texte. Wir sind ein Studierenden-Team und brauchen eine Struktur, die auch fuer Teammitglieder mit wenig Vorkenntnissen verstaendlich ist.
```

---

## 19. Wichtige Sicherheitsregeln

Niemals committen:

- Passwoerter
- Tokens
- private Keys
- `.env` mit echten Zugangsdaten
- lokale Cache-Ordner
- grosse generierte Dateien, wenn sie nicht noetig sind

Vor jedem Commit pruefen:

```powershell
git status --short
git diff --stat
```

Wenn unsicher:

```powershell
git diff
```

Bei Unsicherheit lieber die Ausgabe der KI zeigen und fragen:

```text
Welche Dateien soll ich fuer diesen Commit mit git add aufnehmen?
```

---

## 20. Merksatz fuer das Team

Erst ziehen, klein arbeiten, klar committen, dann pushen.

```powershell
git pull --rebase
git status --short
git add <dateien>
git commit -m "kurze klare nachricht"
git push
```
