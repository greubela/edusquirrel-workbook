# Implementierungsstand

Stand: 2026-06-13

Dieses Dokument beschreibt den aktuellen App-Stand nach dem spielbaren Prototypen, den UI-Verbesserungen und der Aufteilung des App-Codes in Daten, Komponenten und Logik. Es ist als Einstiegspunkt fuer Teammitglieder gedacht, die nach `git pull` direkt weiterarbeiten moechten.

## Aktuell lauffaehig

Die Web-App basiert auf:

- React
- TypeScript
- Vite
- SVG fuer Arbeitsflaeche, Raster, Zielflaechen, Kantenbeschriftung und Formen

Start lokal unter Windows:

```powershell
npm.cmd install
npm.cmd run dev
```

Build pruefen:

```powershell
npm.cmd run build
```

## Projektstruktur

- `src/App.tsx`: App-Orchestrierung, State, Levelwechsel und Pointer-Interaktion
- `src/types.ts`: gemeinsame Typen fuer Missionen, Formen, Rasterzellen und Fortschritt
- `src/config/workspace.ts`: SVG-, Grid- und Workspace-Konstanten
- `src/data/missions.ts`: Missionsdaten und Zielvarianten
- `src/logic/geometry.ts`: Geometrie, Zellenberechnung, Snapping, Kanten und Formgroessen
- `src/logic/missionProgress.ts`: Zielabdeckung, Pflichtformen, Ueberlappung und Abschlusspruefung
- `src/logic/missionNavigation.ts`: Levelstatus und zufaellige Variantenwahl
- `src/components/`: `TopBar`, `MissionPanel`, `Workspace`, `ToolPanel`, `GoalPanel`

## Implementierte Funktionen

### App-Grundgeruest

- Vite/React/TypeScript-Projektstruktur ist eingerichtet.
- Die App startet lokal im Browser.
- Die Oberflaeche besteht aus Missionen links, Arbeitsflaeche in der Mitte und Werkzeug-/Zielbereich rechts.
- Der Build laeuft erfolgreich.

### Arbeitsflaeche

- SVG-Arbeitsflaeche mit 1-m-Raster.
- Rasterlinien sind mit den Kanten der Vierecke ausgerichtet.
- Formen koennen innerhalb der Arbeitsflaeche verschoben werden.
- Beim Loslassen innerhalb der Arbeitsflaeche snappen Formen ins Raster.
- Wird eine Form aus der Arbeitsflaeche gezogen und losgelassen, wird sie entfernt.
- Belegte Zielzellen werden visuell markiert.
- Zielbereiche werden ohne Innenkanten und mit aeusserer Kontur dargestellt.
- Ziel- und erzeugte Formen zeigen Kantenbeschriftungen.
- Flaechenangaben verwenden `m²`.

### Formen auswaehlen und bearbeiten

- Neu erzeugte Formen werden automatisch ausgewaehlt.
- Formen koennen per Klick ausgewaehlt werden.
- Die ausgewaehlte Form erhaelt eine sichtbare Markierung.
- Im Auswahlbereich werden Masse und Flaeche der Form angezeigt.
- Ausgewaehlte Rechtecke koennen ueber Seitenschritte in Breite und Hoehe angepasst werden.
- Ausgewaehlte Formen koennen ueber `Form loeschen` entfernt werden.
- Umgeformte Pflichtformen zaehlen nur dann als Pflichtwerkzeug, wenn ihre Masse weiterhin zum urspruenglichen Werkzeug passen.

### Mission 1

- Zielvarianten werden randomisiert, zum Beispiel L-Form, Quadrat, Reihe oder Treppe.
- `1 x 1`-Quadrate koennen erzeugt und in die Zielflaeche gezogen werden.
- Mission ist erfuellt, wenn alle Zielzellen exakt belegt sind.

### Mission 2

- Werkzeuge: `2 x 2`, `1 x 3`, `3 x 2`.
- Die Mission verlangt, dass alle drei Formen benutzt werden.
- Zielflaechen sind komplexere Kombiformen, bei denen die Formen mindestens Seitenkontakt haben und nicht nur nebeneinander liegen.
- Feedback erkennt fehlende Pflichtformen, Ueberlappung, ungenutzte Formen und Formen ausserhalb der Zielflaeche.

### Mission 3

- Rechtecke koennen frei erzeugt werden.
- Methode 1: Eingabe von `Seite a` und `Seite b`, jeweils auf `1-9` begrenzt.
- Die Eingabefelder koennen zum bequemen Neueingeben geleert werden.
- Eine Vorschau zeigt die eingegebene Form proportional an.
- Klick auf die Vorschau erzeugt die Form.
- Methode 2: Rechteck direkt auf der Arbeitsflaeche mit gedrueckter linker Maustaste aufziehen.
- Die per Drag erzeugten Seitenlaengen rasten auf ganze Meter.
- Zielflaechen benoetigen mindestens zwei verschiedene Rechtecke.

### Levelsteuerung

- `Neustarten` setzt das aktuelle Level zurueck und randomisiert die Zielvariante neu.
- `Naechstes Level` wird nach erfolgreichem Abschluss des aktuellen Levels aktiviert.
- `Dev: Naechstes Level` wechselt immer weiter und dient nur dem Entwicklungsmodus. Dieser Button soll im finalen Programm ausgeblendet oder entfernt werden.

## Bekannte Einschraenkungen

- Es gibt noch keine automatisierten Tests.
- Es gibt noch keine echte Persistenz, Benutzerkonten oder Speicherung pro Lernenden.
- Die Groessenaenderung von Rechtecken erfolgt aktuell ueber Schaltflaechen im Auswahlbereich, noch nicht per Handles direkt an der Form.
- Der Dev-Levelbutton ist sichtbar und muss spaeter hinter einem Entwicklungsmodus versteckt werden.
- Dreiecke und zusammengesetzte Flaechen sind fachlich vorgesehen, aber noch nicht implementiert.

## Naechste logische Schritte

1. Dev-Button kontrollierbar machen:
   - per Konstante oder Environment Flag
   - im finalen Build ausblenden

2. Formenbearbeitung verbessern:
   - Bedienkonzept fuer Skalierung didaktisch pruefen
   - optional Resize-Handles direkt an der Form ergaenzen
   - optional Rotation oder Spiegelung erst nach fachlicher Entscheidung

3. UX verbessern:
   - klarere Hinweise, wenn eine Form falsch liegt
   - bessere visuelle Unterscheidung zwischen Ziel, belegter Flaeche und falsch liegender Form
   - bessere Hinweise, dass Herausziehen aus der Arbeitsflaeche eine Form entfernt

4. Tests ergaenzen:
   - Geometrie- und Missionslogik automatisiert pruefen
   - manuelle Browserpruefung fuer Level 1-3 regelmaessig ausfuehren

## Langfristigere Ziele

- Dreiecke als weiteren Formtyp integrieren.
- Zusammengesetzte Flaechen aus mehreren Rechtecken und Dreiecken unterstuetzen.
- Umfangsaufgaben ergaenzen.
- Aufgaben mit mehreren Loesungswegen erlauben.
- Fortschritt optional in `localStorage` speichern.
- Leveldaten aus einer externen Datei oder einem kleinen Editor pflegen.
- Barrierefreiheit und Tastaturbedienung verbessern.
- Deployment als statische Website vorbereiten, zum Beispiel ueber GitLab Pages.
