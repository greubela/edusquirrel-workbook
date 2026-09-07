# MathWorld

MathWorld ist eine webbasierte Lernanwendung fuer den Mathematikunterricht in
der 5. bis 6. Klasse. In einer wachsenden Schulgarten-Kampagne bearbeiten
Lernende Flaechen, Umfaenge, Brueche und erste Optimierungsaufgaben handelnd
und visuell, statt Formeln nur isoliert anzuwenden.

## Aktueller Stand

Der aktuelle Stand ist ein fortgeschrittener, lokal spielbarer
React/Vite-Prototyp mit zwei Ansichten:

- `Neue Kampagne`: eine isometrische 2,5D-Gartenkarte mit acht aufeinander
  aufbauenden Missionsbereichen
- `Bisherige Demo`: der vorherige Missionsverlauf als vorzeigbare
  Rueckfalloption waehrend der Kampagnenentwicklung

Die Gartenbereiche werden nacheinander freigeschaltet. Innerhalb eines
Bereichs gibt es mehrere Levelvarianten, durch die das zugehoerige Beet
schrittweise bepflanzt wird. Kampagnenfortschritt, abgeschlossene Missionen
und verwendete Pflanzen werden lokal im Browser gespeichert.

Einige sichtbare Bonus- und Erweiterungslevel sind bereits als Ausblick in
der Kampagne angelegt, aber noch nicht spielbar.

## Kampagnenbereiche

| Bereich | Mathematischer Schwerpunkt | Zentrale Interaktion |
| --- | --- | --- |
| Das Startbeet | Flaeche als Anzahl von Quadratmetern | Einzelne `1 x 1`-Felder platzieren und bepflanzen |
| Das Saatgutlager | Gesamtflaechen aus rechteckigen Teilflaechen | Unterschiedlich grosse Saatgut-Saecke passend auf Beete verteilen |
| Die Planungswiese | Rechtecke und zusammengesetzte Flaechen konstruieren | Beete mit Absteckfaden planen, mit dem Spaten umgraben und aussaeen |
| Der Vorratsschuppen | Brueche als Teil eines Ganzen lesen und kuerzen | Samen- und Duengersaecke auf gleich grosse Teilbeete verteilen |
| Das Gewaechshaus | Rechteckflaechen mit Laenge mal Breite berechnen | Beete mit dem Massband ausmessen und Teilflaechen addieren |
| Die Zaunwerkstatt | Umfang sowie Meter und Quadratmeter unterscheiden | Beetraender mit einer Zaunrolle markieren und Materialbedarf bestimmen |
| Die Parkecke | Restflaechen, Dreiecke und zusammengesetzte Flaechen | Hilfsrechtecke verwenden, Teilflaechen zerlegen und Ergebnisse addieren |
| Der Gartenmarkt | Mengen, Kosten und Optimierung | Saatgut einkaufen, Budgets einhalten und guenstige Loesungen finden |

## Implementierte Spielfunktionen

- isometrische Kampagnenkarte mit anklickbaren Gartenbereichen,
  Freischaltbedingungen und Fortschrittsvorschau
- mehrere Level pro Missionsbereich mit steigender Komplexitaet
- SVG-Arbeitsflaechen mit Meter-Raster, Snapping und Kantenbeschriftungen
- Drag-and-Drop fuer Quadrate, Rechtecke und parametrisch skalierte Saecke
- regelbasierte Pruefung von Zielabdeckung, Ueberlappungen,
  Pflichtwerkzeugen und ungueltigen Platzierungen
- unmittelbares, aufgabenspezifisches Feedback
- Pflanzenwahl pro Saatgut-Sack mit Blumen- und Gemuesesorten
- mehrstufiger Arbeitsablauf zum Abstecken, Umgraben und Aussaeen eigener
  Beete
- animierte Spatenstiche, Aussaat und Pflanzenwachstum
- spezialisierte Werkzeuge fuer Messen, Einzaeunen, Flaechenzerlegung und
  Einkauf
- Speicherung von Kampagnenfortschritt und Bepflanzung in `localStorage`
- Rueckkehr von jeder Kampagnenmission zur Gartenkarte

## Technologie

### Frontend

- React 19
- TypeScript 5 im Strict-Modus
- HTML und CSS
- SVG fuer Karte, Arbeitsflaechen, Pflanzen, Werkzeuge und Masslinien
- eigener JavaScript-Generator fuer parametrische Saatgut- und Duengersaecke
- Vite 7 als Entwicklungsserver und Build-Werkzeug
- npm fuer Abhaengigkeiten und Skripte

### Backend

Der Prototyp besitzt bewusst kein Backend, keine Datenbank und keine
Benutzerkonten. Aufgabenlogik und Auswertung laufen vollstaendig im Browser.
Dadurch kann MathWorld als statische Website gebaut und ohne
Server-Infrastruktur vorgefuehrt werden.

Der aktuelle Fortschritt ist deshalb an den jeweiligen Browser gebunden.
Eine Synchronisation zwischen Geraeten, Klassenverwaltung oder ein
Lehrkraefte-Dashboard gehoeren zu moeglichen spaeteren Erweiterungen.

### Versionierung

- Git fuer lokale Versionsverwaltung und nachvollziehbare Commits
- HU-GitLab als zentrales Remote-Repository
- separate Branches fuer groessere Funktionen und sichere Prototypen
- Merge Requests fuer die gemeinsame Pruefung und Zusammenfuehrung

## Lokaler Start

Voraussetzung ist eine aktuelle Node.js-Installation mit npm.

### Schnellstart unter Windows

`start-mathworld.bat` per Doppelklick starten. Das Skript installiert bei
Bedarf die Abhaengigkeiten, startet Vite und oeffnet die Anwendung im
Browser.

### Manueller Start

Abhaengigkeiten installieren:

```powershell
npm.cmd install
```

Entwicklungsserver starten:

```powershell
npm.cmd run dev
```

Produktions-Build pruefen:

```powershell
npm.cmd run build
```

Den erzeugten Build lokal anzeigen:

```powershell
npm.cmd run preview
```

Auf macOS und Linux koennen dieselben Befehle ohne die Endung `.cmd`
verwendet werden.

## Projektstruktur

- `src/App.tsx`: Wechsel zwischen Kampagnenkarte, Kampagnenmission und
  bisheriger Demo sowie lokale Speicherung
- `src/LegacyApp.tsx`: Orchestrierung der spielbaren Missionen und
  Werkzeugzustaende
- `src/data/campaign.ts`: Gartenbereiche, Levelreihenfolge und Kartenpositionen
- `src/data/missions.ts`: Missionsziele und Varianten
- `src/data/plants.ts`: verfuegbare Pflanzenarten
- `src/components/`: Karte, Arbeitsflaechen, Werkzeuge und Missionsoberflaechen
- `src/logic/`: Geometrie, Fortschritt, Messen, Umfang, Park- und Marktlogik
- `parametrische-svg-saecke-v3/`: Generator und Vorlagen fuer skalierbare
  Saecke
- `src/styles.css`: Layout, Kinderbuch-Optik und Animationen

## Bekannte Grenzen

- keine Benutzerkonten oder zentrale Speicherung
- keine Synchronisation des Fortschritts zwischen Browsern oder Geraeten
- einzelne Bonus- und Erweiterungslevel sind noch Platzhalter
- kein Lehrkraefte-Dashboard und keine Lernstandsstatistik
- noch keine automatisierte Testsuite im aktuellen Branch
- noch keine fest eingerichtete Online-Bereitstellung

## Weiterfuehrende Dokumentation

- `CONTRIBUTING.md`: Arbeitsregeln, Sprachregeln und Git-Grundsaetze
- `SETUP.md`: ausfuehrliche Installations- und Git-Anleitung
- `TASKS.md`: Aufgabenboard fuer das Team
- `PROJECT_CONTEXT.md`: urspruenglicher MVP-Zuschnitt
- `docs/IMPLEMENTATION_STATUS.md`: historischer Implementierungsstand des
  ersten Prototyps
- `docs/deployment.md`: Optionen fuer lokale Vorschau und statische
  Bereitstellung
- `KONZEPTENTWURF_MathWorld.md`: Lernziele und konzeptioneller Ausgangspunkt
- `MathWorld Konzept.pdf`: urspruengliches Konzeptdokument

## Repository

```text
https://scm.cms.hu-berlin.de/schrecks/mathworld.git
```

Vor einer neuen Arbeitssession:

```powershell
git status
git pull
```

Weitere Regeln fuer Branches, Aufgabenverteilung und Merge Requests stehen
in `CONTRIBUTING.md`.
