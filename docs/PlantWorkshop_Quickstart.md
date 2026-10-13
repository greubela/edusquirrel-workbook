# Girls Day Pflanzen-Workshop

## Aktueller Stand

Die Workbook-Version ist unter [plantWorkshopWorkbook](../homepage/plantWorkshopWorkbook/index.html) erreichbar. [CreatePlantworkshopWorkbook.scala](../modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreatePlantworkshopWorkbook.scala) erzeugt sieben Sektionen mit persistierbaren Interaktionen. Die alte HTML-Einstiegsseite [plantWorkshop](../homepage/plantWorkshop/index.html) ist noch vorhanden, aber `PlantWorkshopApp.scala` wurde entfernt. Die aktuelle Startup-Logik registriert für `plantWorkshopApp` keine eigene Workbook-Factory; die vorhandene Seite belegt daher keine weiterhin aktive Alt-Anwendung.

| Sektion | Inhalte der Workbook-Version |
| --- | --- |
| 0: Motivation | Einführung, Lernziele und Sicherheit |
| 1: Bauteile und Aufbau | Bauteil-Checkliste und erste Verkabelungs-Slideshow |
| 2: Sensor erkunden | Sensor auslesen, Code-Aufgabe, Arduino-Sketch-Download und Mess-Checkliste |
| 3: Feuchtigkeit | Grenzwert/Fallunterscheidung, Code-Aufgabe und Sketch-Download |
| 4: Pumpe | Zweite Verkabelungs-Slideshow, Code-Aufgabe, Sketch-Download und Checkliste |
| 5: Gesamtsystem | Kombinierte Code-Aufgabe, Sketch-Download und Checkliste |
| 6: Test und Bonus | Test-Checkliste, Fehlersuche, Bonus und Abschluss |

Die Code-Aufgaben verwenden `CodeTaskToggle`: Reihenfolge-Aufgaben im Anfänger-Modus und Code-Vorlagen mit Anforderungen im Fortgeschrittenen-Modus. Downloads verwenden die vorhandenen Sketch-Download-Elemente. `TODO_*` in den Code-Vorlagen sind absichtliche Lücken für Lernende, keine fehlenden Implementierungen.

## Modelle, Texte und CSS

Wiederverwendbare Modelle liegen unter `modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/`: `basic/LabeledCheckboxInteraction.scala`, `slideshow/`, `codeTaskToggle/` und `reorderExercise/`. Die Browser-Renderer liegen unter `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/`.

Sprachdateien:

- [map-de.json](../resources/languageMaps/eva/plantworkshop/map-de.json)
- [map-en.json](../resources/languageMaps/eva/plantworkshop/map-en.json)
- [map-universal.json](../resources/languageMaps/eva/plantworkshop/map-universal.json)

Die Workbook-Version nutzt die gemeinsamen Workbook-Styles. [plantWorkshop.css](../homepage/css/plantWorkshop.css) gehört zur separaten Alt-Anwendung. Der [Arduino-Referenzcode](arduino_reference_code.ino) bleibt als fachliche Referenz erhalten.

## Verbleibende Migration

Die frühere Liste fehlender Sensor-, Pumpen-, Gesamtsystem- und Download-Interaktionen ist überholt: diese Elemente sind eingebaut. Für die Bereinigung des alten Einstiegspunkts bleiben ein fachlicher und visueller Paritätsvergleich, die Prüfung der realen Hardware-Anleitungen und die Umstellung bzw. Weiterleitung des alten Einstiegspunkts notwendig. Die alte HTML-Seite benötigt eine geprüfte Weiterleitung bzw. Bereinigung; die neue Workbook-Version nutzt `MainApp`/`HomepageStartupLogic`.

## Prüfung

```sh
sbt 'client/testOnly *PlantWorkshopWorkbookRoundTripSpec'
sbt buildJS
```

Die Roundtrip-Suite prüft die Serialisierung des Workbook-Inhalts. Hardware-Verhalten und didaktische Parität erfordern zusätzlich einen manuellen Durchlauf.
