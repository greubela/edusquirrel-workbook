# Aufgabenboard

Stand: 2026-06-13

Dieses Dokument ist die gemeinsame Quelle fuer Aufgabenvergabe im MathWorld-Team. Es verhindert, dass mehrere Personen gleichzeitig dieselbe Aufgabe oder dieselben Dateien bearbeiten.

Hinweis zum aktuellen Stand: Der erste spielbare Prototyp liegt nach dem aktuellen Push in `origin/main`. Vor neuen Arbeiten bitte `git pull`, `npm.cmd install` und bei Code-Aenderungen mindestens `npm.cmd run build` ausfuehren.

## Regeln fuer die Vergabe

- Das Team arbeitet mit drei Personen.
- Jede Aufgabe hat genau einen Owner.
- Eine Aufgabe mit Owner gilt als vergeben und wird nicht an eine zweite Person vergeben.
- Neue Aufgaben werden nur vergeben, wenn keine offensichtlichen Konflikte mit laufenden Aufgaben entstehen.
- Vor einer Vergabe werden `TASKS.md`, `git status`, lokale Branches und nach Moeglichkeit Remote-Branches geprueft.
- Nach einer Vergabe werden Status, Owner, Branch und betroffene Dateien hier aktualisiert.
- Wenn ein Teammitglied nach der naechsten Aufgabe fragt, wird zuerst eine offene, nicht blockierte und konfliktarme Aufgabe ausgewaehlt.
- Wenn der Name oder die Initialen des Teammitglieds fehlen, wird vor der festen Eintragung kurz danach gefragt.

## Statuswerte

- `Offen`: noch nicht vergeben
- `Vergeben`: Owner steht fest, Arbeit noch nicht gestartet oder Branch noch nicht gepusht
- `In Arbeit`: Arbeit laeuft aktiv
- `Review`: Merge Request oder fachliche Pruefung laeuft
- `Blockiert`: wartet auf Entscheidung, Merge oder externe Information
- `Erledigt`: abgeschlossen und zusammengefuehrt oder nicht mehr noetig

## Aktuelle Aufgaben

| ID | Aufgabe | Status | Owner | Branch-Vorschlag | Betroffene Dateien | Abhaengigkeiten / Konflikte | Naechster Schritt |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T-001 | MVP fuer die erste Garten-Mission festlegen | Erledigt | Stephan | `docs/mvp-garten-mission` | `PROJECT_CONTEXT.md`, `TASKS.md`, optional `README.md` | Grundlage fuer Leveldaten und technische Umsetzung | MVP-Kontext liegt in `PROJECT_CONTEXT.md` |
| T-002 | React/Vite/TypeScript-Grundgeruest anlegen | Erledigt | Stephan | `feat/app-scaffold` | `package.json`, `package-lock.json`, `index.html`, `src/`, Konfigurationsdateien, `README.md`, `SETUP.md` | Grundlage fuer App-Entwicklung | Projekt startet lokal mit `npm.cmd run dev`; Build laeuft |
| T-003 | Missionen und Datenmodell fachlich ausarbeiten | Review | Vera | `docs/mission-data-model` | `docs/missions.md`, spaeter `src/data/missions.ts` | Baut auf T-001 auf, kann parallel zu T-002 als Dokument starten | Mission 1-3, Ziele, erlaubte Werkzeuge, Zielflaechen und Erfolgskriterien detaillieren |
| T-004 | Arbeitsflaeche und Viereck-Interaktion entwerfen | Erledigt | Stephan | `feat/workspace-shapes` | `src/App.tsx`, `src/styles.css`, spaeter `src/components/Workspace.tsx`, `src/components/ShapeLayer.tsx` | Haengt technisch von T-002 ab; Datenmodell aus T-003 hilfreich | Erster funktionaler Slice ist im Prototypen enthalten |
| T-005 | Snapping, Bemassung und Missionsfeedback planen | Review | Stephan | `feat/measurement-feedback` | `src/logic/`, `src/components/MeasurementOverlay.tsx`, `src/components/MissionPanel.tsx` | Haengt technisch von T-002 und inhaltlich von T-003 ab | Grundfunktionen sind implementiert; spaeter in eigene Logik/Komponenten auslagern |
| T-006 | Entwicklungsanleitung nach App-Setup aktualisieren | Erledigt | Stephan | `docs/dev-start` | `README.md`, `SETUP.md`, `docs/IMPLEMENTATION_STATUS.md` | Haengt von T-002 ab | Start, Build und aktueller Implementierungsstand sind dokumentiert |
| T-007 | Manuelle Testliste fuer MVP erstellen | Review | Vera | `docs/manual-testplan` | `docs/testplan.md` | Kann nach T-001 starten, wird nach T-002 bis T-005 ergaenzt | Testfaelle fuer Level 1, Feedback und Browserdarstellung sammeln |
| T-008 | Deployment-Option fuer statische Demo klaeren | Review | Vera | `docs/deployment-plan` | `docs/deployment.md`, optional `README.md` | Spaeter sinnvoll, sobald Build funktioniert | GitLab Pages oder einfache statische Auslieferung bewerten |
| T-009 | Randomisierte Levelvarianten planen | Review | Stephan | `docs/randomized-levels` | `docs/missions.md`, spaeter `src/data/missions.ts` | Baut auf T-003 auf; sollte fachlich dokumentiert werden | Randomisierung ist technisch implementiert; Varianten sollen noch in `docs/missions.md` dokumentiert werden |
| T-010 | Level-Navigation mit Neustart und Naechstes-Level-Button umsetzen | Erledigt | Stephan | `feat/level-navigation` | `src/App.tsx`, spaeter `src/components/MissionPanel.tsx`, `src/logic/` | Grundlage fuer Spielfluss | `Neustarten`, `Naechstes Level` und `Dev: Naechstes Level` sind implementiert |
| T-011 | App-Code in Daten, Komponenten und Logik aufteilen | Erledigt | Stephan | `refactor/app-structure` | `src/App.tsx`, `src/types.ts`, `src/config/`, `src/data/`, `src/components/`, `src/logic/` | Abgeschlossen; weitere App-Code-Aenderungen koennen jetzt gezielter in Komponenten oder Logik erfolgen | App ist in Daten, Komponenten, Workspace-Konstanten und Logikmodule aufgeteilt |
| T-012 | Dev-Levelbutton fuer finalen Build ausblendbar machen | Review | Vera | `feat/dev-mode-toggle` | `src/App.tsx`, optional `.env.example`, `README.md` | Klein, aber App-nah; nach T-011 oder bewusst davor abstimmen | `Dev: Naechstes Level` per Konstante oder Env-Flag steuern |
| T-013 | Formen loeschen und bearbeiten | Review | Stephan | `feat/shape-editing` | `src/App.tsx`, `src/components/ToolPanel.tsx`, `src/components/Workspace.tsx`, `src/logic/geometry.ts`, `src/styles.css` | Grundfunktion ist implementiert; Skalierungsbedienung soll noch didaktisch bewertet werden | Auswahl, Loeschen, Groessenaenderung und Entfernen durch Herausziehen pruefen |
| T-014 | Dreiecke und zusammengesetzte Flaechen vorbereiten | Review | Vera | `feat/future-shapes` | `src/logic/geometry.ts`, `src/data/missions.ts`, `docs/missions.md` | Langfristiger Ausbau nach stabiler Rechtecklogik | Formmodell fuer Dreiecke und zusammengesetzte Flaechen erweitern |
| T-016 | Zusaetzliche Level fachlich planen | Review | Vera | `docs/additional-levels` | `docs/missions.md` | Reine Dokumentationsaufgabe; kein App-Code | Neue Garten-Level mit Lernzielen, Werkzeugen, Feedback und Belohnungen beschreiben |
| T-017 | Radieschen-Beet umsetzen | Review | Vera | `feat/radish-bed-level` | `src/data/missions.ts` | Kleine Datenaufgabe; kein App-Code | Neues rechteckiges `2 x 3`-Level mit `1 x 1`-Quadraten ergaenzen |
| T-018 | Karotten-Streifen umsetzen | Review | Vera | `feat/carrot-strip-level` | `src/data/missions.ts` | Kleine Datenaufgabe; kein App-Code | Neues `1 x 6`-Streifen-Level mit `1 x 1`, `1 x 2` und `1 x 4`-Rechtecken ergaenzen |
| T-019 | Garten-Belohnung nach Missionserfolg anzeigen | Review | Vera | `feat/garden-reward-plants` | `src/components/Workspace.tsx`, `src/styles.css` | Kleine UI-Aufgabe; keine Geometrie- oder Missionsdaten-Aenderung | Bei abgeschlossener Mission neutrale Pflanzen in den Zielzellen anzeigen |
| T-020 | Resize fuer vorgegebene Werkzeugformen sperren | Review | Vera | `feat/lock-tool-shape-resize` | `src/App.tsx`, `src/components/ToolPanel.tsx` | Keine Missionsdaten- oder Geometrie-Aenderung | Vorgegebene Werkzeugformen fest lassen, freie Formen weiter bearbeitbar halten |
| T-021 | Missionsspezifische Garten-Belohnungen anzeigen | Review | Vera | `feat/mission-reward-kinds` | `src/types.ts`, `src/data/missions.ts`, `src/components/Workspace.tsx`, `src/styles.css` | Keine externen Bibliotheken, keine Animation | Blumen, Radieschen und Karotten je nach Mission als SVG-Belohnung anzeigen |
| T-022 | Garten-Fortschritt und Belohnungskonzept dokumentieren | Review | Vera | `docs/garden-progress` | `docs/garden-progress.md` | Reine Dokumentationsaufgabe; kein App-Code | Fortschritt, Gartenkarte, Sterne und Pflanzensammlung fachlich beschreiben |
| T-023 | Garten-Fortschrittsanzeige hinzufuegen | Review | Vera | `feat/garden-progress-summary` | `src/App.tsx`, `src/components/TopBar.tsx`, optional `src/styles.css` | Keine Missionsdaten- oder Geometrie-Aenderung | Sichtbare Anzeige wie `3 von 5 Gartenbeeten bepflanzt` ergaenzen |
| T-024 | Abgeschlossene Missionen in Missionsliste markieren | Review | Vera | `feat/completed-mission-badges` | `src/App.tsx`, `src/components/MissionPanel.tsx`, `src/styles.css` | Keine Geometrie- oder Missionsdaten-Aenderung | Abgeschlossene Missionen mit `bepflanzt`-Badge sichtbar markieren |
| T-025 | Abschlussmeldung fuer den fertigen Garten anzeigen | Review | Vera | `feat/garden-complete-message` | `src/components/TopBar.tsx`, optional `src/styles.css` | Keine Missionsdaten-, Geometrie- oder Navigations-Aenderung | Wenn alle Missionen abgeschlossen sind, kindgerechte Abschlussmeldung anzeigen |
| T-026 | Gemuese-Mix-Beet umsetzen | Review | Vera | `feat/vegetable-mix-bed` | `src/data/missions.ts` | Reine Missionsdaten-Aufgabe; keine UI-, Geometrie- oder Typ-Aenderung | Zusammengesetztes Beet mit Pflichtformen `2 x 2`, `1 x 3` und `3 x 1` ergaenzen |
| T-027 | Blumen-Ecke umsetzen | Review | Vera | `feat/flower-corner-level` | `src/data/missions.ts` | Reine Missionsdaten-Aufgabe; keine UI-, Geometrie- oder Typ-Aenderung | L-foermige Blumen-Ecke mit freier Rechteck-Erzeugung ergaenzen |
| T-028 | Freies Gartenbeet umsetzen | Review | Vera | `feat/free-garden-bed-level` | `src/data/missions.ts` | Reine Missionsdaten-Aufgabe; keine UI-, Geometrie- oder Typ-Aenderung | Groesseres freies Gartenbeet mit mehreren richtigen Rechteck-Zerlegungen ergaenzen |
| T-029 | Regressionstest fuer alle Garten-Missionen | Review | Vera | `docs/regression-testplan` | `docs/testplan.md` | Reine Dokumentations- und Testaufgabe; kein App-Code | Manuellen Regressionstest fuer alle 8 Garten-Missionen dokumentieren |

## Hinweise fuer konfliktarme Vergabe

Am Anfang sollten maximal diese drei Aufgaben parallel laufen:

- Eine Person prueft T-001 oder arbeitet danach an T-003 im Dokumentationsbereich.
- Eine Person bearbeitet T-002 am technischen Grundgeruest.
- Eine Person bearbeitet T-007 oder wartet mit T-004, bis T-002 steht.

Aktuell konfliktarm:

- T-003 kann als Dokumentationsaufgabe in `docs/missions.md` starten.
- T-007 kann als Testdokument in `docs/testplan.md` starten.
- T-008 kann als Deployment-Dokument in `docs/deployment.md` starten.

App-nahe Aufgaben wie T-011, T-012, T-013 und T-014 sollten nicht gleichzeitig dieselben Dateien bearbeiten. Wenn mehrere Personen am App-Code arbeiten, zuerst T-011 angehen oder klare Datei-Grenzen vereinbaren.

## Vergabeprotokoll

| Datum | Aufgabe | Owner | Statusaenderung | Notiz |
| --- | --- | --- | --- | --- |
| 2026-06-01 | Alle Startaufgaben | - | angelegt | Aufgabenboard fuer Dreierteam erstellt; noch keine Aufgabe vergeben |
| 2026-06-08 | T-001 | Stephan | Offen -> Review | MVP-Zielgruppe, Muss-Funktionen und Mission 1-3 in `PROJECT_CONTEXT.md` festgehalten |
| 2026-06-08 | T-002 | Stephan | Offen -> In Arbeit | React/Vite/TypeScript-Grundgeruest wird angelegt |
| 2026-06-08 | T-002 | Stephan | In Arbeit -> Review | App-Shell angelegt, Dependencies installiert, Build erfolgreich, Dev-Server lokal erreichbar |
| 2026-06-08 | T-004 | Stephan | Offen -> In Arbeit | Erster funktionaler Slice fuer 1x1-Quadrate, Drag-and-Drop und Grid-Snapping wird umgesetzt |
| 2026-06-08 | T-004 | Stephan | In Arbeit -> Review | 1x1-Quadrate koennen erzeugt, verschoben, ins Raster gesnappt und gegen die L-Form geprueft werden |
| 2026-06-08 | T-005 | Stephan | Offen -> Review | Snapping, Live-Bemassung, Zielpruefung und Feedback sind im Prototypen grundlegend implementiert |
| 2026-06-08 | T-009/T-010 | - | angelegt | Randomisierte Levelvarianten, Neustart und Naechstes-Level-Button als offene Features aufgenommen |
| 2026-06-08 | T-010 | Stephan | Offen -> In Arbeit | Levelzustand, Neustart, Randomisierung und Naechstes-Level-Button werden umgesetzt |
| 2026-06-08 | T-010 | Stephan | In Arbeit -> Review | Levelvarianten werden randomisiert; `Neustarten` und `Naechstes Level` sind implementiert |
| 2026-06-08 | T-010 | Stephan | Review aktualisiert | Level 2 verlangt alle drei Formen; Level 3 unterstuetzt freie Rechtecke per Eingabe und Drag-Erzeugung |
| 2026-06-08 | T-010 | Stephan | Review aktualisiert | Dev-Levelwechsel, komplexere Level-2-Zielformen und verbesserte Level-3-Eingaben 1-9 umgesetzt |
| 2026-06-08 | T-002/T-004/T-010 | Stephan | Review -> Erledigt | Erster spielbarer Prototyp wird nach `origin/main` gepusht |
| 2026-06-08 | T-011-T-014 | - | angelegt | Naechste technische und langfristige Aufgaben fuer die Weiterarbeit erfasst |
| 2026-06-08 | T-007 | Vera | Offen -> In Arbeit | Manuelle Testliste fuer den MVP wird erstellt |
| 2026-06-09 | T-007 | Vera | In Arbeit -> Review | Manueller Testplan erstellt; Mission 1-3 inklusive Negativtest wurden getestet |
| 2026-06-09 | T-003 | Vera | Offen -> In Arbeit | Missionen und fachliche Skizze fuer ein spaeteres Datenmodell werden in `docs/missions.md` ausgearbeitet |
| 2026-06-09 | T-003 | Vera | In Arbeit -> Review | `docs/missions.md` fachlich ausgearbeitet und mit dem aktuellen MVP abgeglichen |
| 2026-06-09 | T-008 | Vera | Offen -> In Arbeit | Deployment-Optionen fuer eine statische Demo werden in `docs/deployment.md` dokumentiert |
| 2026-06-09 | T-008 | Vera | In Arbeit -> Review | Deployment-Optionen dokumentiert und lokaler statischer Preview erfolgreich getestet |
| 2026-06-13 | T-013 | Stephan | Offen -> Review | Formen koennen ausgewaehlt, geloescht, ueber Seitenschritte skaliert und durch Herausziehen entfernt werden; Kanten- und Flaechenbeschriftungen wurden verbessert |
| 2026-06-13 | T-011 | Stephan | Offen -> Erledigt | App-Code wurde in Typen, Workspace-Konstanten, Missionsdaten, Komponenten und Logikmodule aufgeteilt; Build erfolgreich |
| 2026-06-23 | T-012 | Vera | Offen -> In Arbeit | Dev-Levelbutton wird per Vite-Umgebungsvariable fuer finale Builds steuerbar gemacht |
| 2026-06-23 | T-012 | Vera | In Arbeit -> Review | Dev-Levelbutton ist im Entwicklungsmodus sichtbar und im finalen Build standardmaessig ausgeblendet; Umschaltung ueber `VITE_SHOW_DEV_CONTROLS` wurde getestet |
| 2026-06-23 | T-014 | Vera | Offen -> In Arbeit | Erster sicherer Dokumentationsschritt fuer Dreiecke und zusammengesetzte Flaechen wird in `docs/missions.md` vorbereitet |
| 2026-06-23 | T-016 | Vera | Offen -> In Arbeit | Zusaetzliche Garten-Level werden als reine Dokumentationsaufgabe in `docs/missions.md` fachlich geplant |
| 2026-06-23 | T-017 | Vera | Offen -> In Arbeit | Radieschen-Beet wird als erstes zusaetzliches Daten-Level in `src/data/missions.ts` umgesetzt |
| 2026-06-23 | T-017 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test von `Radieschen-Beet` war erfolgreich |
| 2026-06-23 | T-018 | Vera | Offen -> In Arbeit | Karotten-Streifen wird als weiteres Daten-Level in `src/data/missions.ts` umgesetzt |
| 2026-06-23 | T-018 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test von `Karotten-Streifen` war erfolgreich |
| 2026-06-23 | T-019 | Vera | Offen -> In Arbeit | Garten-Belohnung nach Missionserfolg wird als kleine SVG-Pflanzenschicht im Workspace umgesetzt |
| 2026-06-23 | T-019 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt sichtbare Pflanzen, weiterhin funktionierende Auswahl, Verschieben, Resize und Pointer-/Drag-Verhalten |
| 2026-06-23 | T-020 | Vera | Offen -> In Arbeit | Resize fuer vorgegebene Werkzeugformen wird gesperrt; freie Formen bleiben bearbeitbar |
| 2026-06-23 | T-020 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt feste Werkzeugformen in Mission 1, 2, Radieschen-Beet und Karotten-Streifen, weiterhin resizebare freie Formen in Mission 3 sowie funktionierendes Verschieben und Loeschen |
| 2026-06-23 | T-021 | Vera | Offen -> In Arbeit | Missionsspezifische Garten-Belohnungen fuer Blumen, Radieschen und Karotten werden ohne externe Bibliotheken vorbereitet |
| 2026-06-23 | T-021 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt Blumen in Mission 1, Radieschen im Radieschen-Beet, Karotten im Karotten-Streifen, verschwindende Belohnungen bei unvollstaendiger Zielflaeche sowie funktionierende Auswahl, Verschieben und Loeschen |
| 2026-06-23 | T-022 | Vera | Offen -> In Arbeit | Garten-Fortschritt und Belohnungskonzept werden als reine Dokumentationsaufgabe in `docs/garden-progress.md` beschrieben |
| 2026-06-23 | T-022 | Vera | In Arbeit -> Review | `docs/garden-progress.md` wurde vollstaendig erstellt |
| 2026-06-23 | T-014 | Vera | In Arbeit -> Review | Fachliches Konzept fuer Dreiecke und zusammengesetzte Flaechen ist dokumentiert; `TriangleOrientation` ist im Datenmodell vorbereitet; spielbares Rendering, Snapping und Zielpruefung waren ausdruecklich nicht Teil dieses vorbereitenden Schritts |
| 2026-06-23 | T-016 | Vera | In Arbeit -> Review | Fuenf zusaetzliche Garten-Level wurden vollstaendig fachlich beschrieben; Lernziele, Werkzeuge, Erfolgskriterien, typische Fehler, Feedback und Belohnungen sind dokumentiert; Radieschen-Beet und Karotten-Streifen wurden bereits umgesetzt |
| 2026-06-25 | T-023 | Vera | Offen -> In Arbeit | Garten-Fortschrittsanzeige wird in der TopBar ergaenzt; Missionsdaten und Geometrie bleiben unveraendert |
| 2026-06-25 | T-023 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt Fortschritt von 1 auf 2 Gartenbeete, erhaltenen Fortschritt beim Missionswechsel sowie sichtbare Mission- und m²-Anzeige |
| 2026-06-25 | T-024 | Vera | Offen -> In Arbeit | Abgeschlossene Missionen werden in der Missionsliste mit einem `bepflanzt`-Badge markiert; Geometrie und Missionsdaten bleiben unveraendert |
| 2026-06-25 | T-024 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt `bepflanzt`-Badge, erneutes Oeffnen abgeschlossener Missionen mit leerem Workspace, erhaltenen Fortschritt, kindgerechten Hinweis und weiterhin gesperrte locked Missionen |
| 2026-06-25 | T-025 | Vera | Offen -> In Arbeit | Abschlussmeldung fuer den fertigen Garten wird in der TopBar ergaenzt; Missionsdaten, Geometrie und Navigation bleiben unveraendert |
| 2026-06-25 | T-025 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt Abschlussmeldung nur bei vollstaendig bepflanztem Garten, weiterhin sichtbare Mission-, m²- und Fortschrittsanzeige sowie erhaltene Meldung beim erneuten Oeffnen abgeschlossener Missionen |
| 2026-06-25 | T-026 | Vera | Offen -> In Arbeit | Gemuese-Mix-Beet wird als reine Missionsdaten-Aufgabe in `src/data/missions.ts` ergaenzt; UI, Geometrie und Typen bleiben unveraendert |
| 2026-06-25 | T-026 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt Mission 6 nach Karotten-Streifen, gesperrten Zustand bis Mission 5, feste Pflichtformen `2 x 2`, `1 x 3` und `3 x 1`, erkannte Fehlerfaelle, Blumen-Belohnung, Fortschritt `6 von 6` und Abschlussmeldung |
| 2026-06-25 | T-027 | Vera | Offen -> In Arbeit | Blumen-Ecke wird als reine Missionsdaten-Aufgabe in `src/data/missions.ts` ergaenzt; freie Rechteck-Erzeugung wird genutzt, UI, Geometrie und Typen bleiben unveraendert |
| 2026-06-25 | T-027 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt Freischaltung nach Mission 6, freie Rechteck-Erzeugung, 14 Zielzellen, Loesungen mit `4 x 2` und `2 x 3` sowie alternativer Zerlegung, erkannte Fehlerfaelle, Blumen-Belohnung, Fortschritt `7 von 7` und Abschlussmeldung |
| 2026-06-25 | T-028 | Vera | Offen -> In Arbeit | Freies Gartenbeet wird als reine Missionsdaten-Aufgabe in `src/data/missions.ts` ergaenzt; freie Rechteck-Erzeugung und mehrere richtige Zerlegungen werden genutzt, UI, Geometrie und Typen bleiben unveraendert |
| 2026-06-25 | T-028 | Vera | In Arbeit -> Review | Build erfolgreich; manueller Test bestaetigt Freischaltung nach Mission 7, freie Rechteck-Erzeugung, 18 eindeutige Zielzellen, mindestens zwei passende Zerlegungen, erkannte Fehlerfaelle, Blumen-Belohnung, Fortschritt `8 von 8` und Abschlussmeldung |
| 2026-06-25 | T-029 | Vera | Offen -> In Arbeit | Manueller Regressionstestplan fuer alle 8 Garten-Missionen wird in `docs/testplan.md` aktualisiert; kein App-Code |
| 2026-06-25 | T-029 | Vera | In Arbeit -> Review | Manueller Regressionstest fuer alle 8 Missionen durchgefuehrt; Freischaltung, Fortschritt, Replay, Resize, Belohnungen und Abschlussmeldung geprueft; alle Testfaelle bestanden |
