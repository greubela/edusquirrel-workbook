# Quick Start Guide - Girls Day Pflanzen-Workshop

## Zielbild (neue technische Basis)
Der Plant-Workshop wird schrittweise von der monolithischen `PlantWorkshopApp` auf die Workbook-Architektur migriert:
- `Workbook` / `WorkbookSection` / `HtmlExerciseContainer`
- InteractionVariable-basierte Interaktionen (persistierbar/synchronisierbar)
- sprachdateibasierte Inhalte (`PlantWorkshop-en.json`, `PlantWorkshop-de.json`)
- möglichst viele wiederverwendbare HtmlWorkbookElemente, möglichst wenig Speziallogik

## Aktueller Stand

### Legacy (weiterhin aktiv)
1. **`src/main/scala/content/plantworkshop/PlantWorkshopApp.scala`**
   - vollständige, produktive Alt-Implementierung
   - alle 6 Module inkl. bestehender Drag&Drop-/CodeEditor-Logik

2. **`homepage/css/plantWorkshop.css`**

3. **`docs/arduino_reference_code.ino`**

### Neue Workbook-Basis (im Aufbau)
1. **`src/main/scala/content/CreatePlantworkshopWorkbook.scala`**
   - neue Workbook-Fabrik mit 6 Sektionen
   - Checklisten als InteractionVariable-basierte Checkboxen
   - Wiring-Slideshow über den neuen Slideshow-Plugin-Pfad

2. **`src/main/scala/interactionPlugins/slideshow/`**
   - `SlideDeckExercise.scala`
   - `SlidePanel.scala`

3. **`src/main/scala/workbook/htmlElements/interactions/`**
   - `HtmlBasicCheckboxInteraction.scala`
   - `HtmlReorderInteraction.scala`

4. **`resources/languageMaps/PlantWorkshop-en.json` / `PlantWorkshop-de.json`**
   - zentrale Texte für die neue Basis

## Modulüberblick (fachlich)

0. Motivation
- Einführung & Überblick
- Lernziele
- Sicherheitshinweise (Wasser & Strom)

1. Bauteile & Aufbau
- Interaktive Checkliste
- Verkabelungsplan / Slideshow
- Erklärung: Warum Relais?

2. Feuchtigkeit messen
- **Anfänger**: Drag&Drop Bausteine
- **Fortgeschritten**: Code-Lückentext

3. Pumpe steuern
- **Anfänger**: Drag&Drop Bausteine
- **Fortgeschritten**: Code-Editor

4. Gesamtsystem
- **Anfänger**: Drag&Drop (inkl. if-Statement)
- **Fortgeschritten**: vollständiges Programm mit Lücken

5. Test & Bonus
- Test-Checkliste
- Fehlersuche-Guide
- Bonus-Aufgaben

## Nächste Schritte zur vollständigen Migration

> **Hinweis zu TODOs:** Ein Teil der offenen TODO-Texte liegt bewusst in den Sprachdateien
> `resources/languageMaps/PlantWorkshop-en.json` und `resources/languageMaps/PlantWorkshop-de.json`
> (z. B. `missingPumpInteraction`, `missingMoistureInteraction`, `missingCombinedInteraction`, `missingArduinoExport`).
> Bitte diese Dateien bei der Migration immer mit prüfen, damit keine offenen Punkte übersehen werden.


### Phase 1 – Strukturelle Parität
- [x] Workbook-Fabrik für PlantWorkshop angelegt
- [x] Sprachdateien für PlantWorkshop eingebunden
- [x] Wiring-Inhalt als Slideshow-Interaktion eingebaut
- [ ] Fehlende Platzhalter-Inhalte gezielt pro Sektion durch echte Elemente ersetzen

### Phase 2 – Interaktions-Parität
- [ ] Task 2 (Feuchtigkeit) vollständig migrieren:
  - `HtmlReorderInteraction` für Anfänger-Modus nutzen
  - InteractionVariable-basierten Code-Editor für Fortgeschrittenen-Modus einführen
- [ ] Task 3 (Pumpe) analog migrieren
- [ ] Task 4 (Gesamtsystem) analog migrieren
- [ ] Validierungs-Feedback aus Legacy-Logik in wiederverwendbare Komponenten überführen

### Phase 3 – Abschluss & Umschaltung
- [ ] Arduino-Export in Workbook-Version integrieren
- [ ] Test-/Troubleshooting-Teil ohne Platzhalter vollständig übernehmen
- [ ] Sicht- und Funktionsabgleich Legacy vs. Workbook durchführen
- [ ] `MainApp` standardmäßig auf `CreatePlantworkshopWorkbook` schalten
- [ ] Legacy `PlantWorkshopApp` nach erfolgreicher Parität entfernen

## Migration checklist (6)
- [ ] Section structure mapped to workbook architecture
- [ ] Wiring slideshow migrated to slideshow interaction
- [ ] All checklist boxes backed by InteractionVariable
- [ ] Beginner/advanced mode components migrated
- [ ] Arduino export flow migrated
- [ ] Legacy PlantWorkshopApp can be removed
