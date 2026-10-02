# Saison-Missionen: Wochen, Tage und Sonnenzyklus

Stand: 2026-07-04

Dieses Dokument beschreibt eine naechste fachliche Ausbaustufe fuer MathWorld. Die zentrale Idee ist: Eine Mission entspricht nicht mehr einem einzelnen isolierten Level, sondern einer Gartenwoche. Eine Woche besteht aus mehreren Tagesaufgaben. Der Garten bleibt ueber die Wochen bestehen, sodass frueh ausgesaehte Pflanzen spaeter sichtbar wachsen und erneut bearbeitet werden koennen.

## Leitidee

MathWorld wird als kleine Garten-Saison gedacht. Lernende bearbeiten ueber mehrere Wochen hinweg denselben Schulgarten. Jede Woche hat ein berufsnahaes Wochenziel, zum Beispiel ein Beet vorbereiten, Saatgut einkaufen, eine neue Flaeche abstecken oder ein Kundenbeet besonders preiswert planen.

Die Tagesaufgaben innerhalb einer Woche nutzen unterschiedliche Werkzeuge. Jedes Werkzeug steht fuer einen eigenen mathematischen Zugang zur Flaeche:

- zaehlen und abschaetzen;
- Flaechen aus Einheitsquadraten zusammensetzen;
- Rechteckflaechen ueber Seitenlaengen berechnen;
- Flaechen durch Ziehen und Abstecken konstruieren;
- Loesungen nach Kosten, Werkzeuganzahl oder Materialverbrauch optimieren.

Dadurch begegnen Lernende derselben Leitfrage mehrfach aus verschiedenen Richtungen: `Wie viel Flaeche ist das und wie kann ich sie sinnvoll bearbeiten?`

## Missionsstruktur

Eine moegliche Struktur:

```text
Saison
- Woche 1: Beet vorbereiten
  - Montag: Beetgroesse schaetzen
  - Dienstag: 1 m²-Felder mit dem Spaten umgraben
  - Mittwoch: Schaetzung und gezaehlte Flaeche vergleichen
  - Donnerstag: erstes Saatgut ausbringen
  - Freitag: Wochenziel pruefen und Garten speichern
- Woche 2: Saatgut einkaufen
  - Montag: benoetigte Flaeche bestimmen
  - Dienstag: passende Saat-Saecke auswaehlen
  - Mittwoch: Beet exakt aussaehen
  - Donnerstag: guenstigere Kombination finden
  - Freitag: Wochenabschluss mit Kostenvergleich
```

Im UI koennen die bisherigen Missionschips `1` bis `8` durch Wochenchips ersetzt werden, zum Beispiel:

```text
W1  W2  W3  W4  W5  W6
```

Die linke Missionsbox zeigt dann die aktive Woche und darunter die Tagesaufgaben:

```text
Woche 2: Saatgut einkaufen
Mo  Di  Mi  Do  Fr
Aktiver Tag: Mittwoch
Aufgabe: Saee das Beet exakt mit den gekauften Saecken aus.
```

Nicht erreichte Wochen oder Tage bleiben ausgegraut. Bereits abgeschlossene Tage bekommen eine kleine Pflanzen-, Haken- oder Sonnenmarkierung.

## Sonnenzyklus als Fortschrittsanzeige

Der Missionsfortschritt einer Woche kann durch einen Sonnenzyklus visualisiert werden. Die Sonne ist dabei kein echter Timer und erzeugt keinen Zeitdruck. Sie ist ein freundlicher Fortschrittsindikator.

Beispiel:

| Tagesfortschritt | Sonnenstand |
| --- | --- |
| Montag | Sonnenaufgang |
| Dienstag | Vormittag |
| Mittwoch | Mittag |
| Donnerstag | Nachmittag |
| Freitag | Abendsonne |
| Wochenabschluss | kurze Nacht- oder Wachstumsanimation |

Nach jeder abgeschlossenen Tagesaufgabe wandert die Sonne ein Stueck weiter ueber den Himmel. Beim Wochenabschluss kann kurz die Nacht einsetzen, danach wachsen Pflanzen aus frueheren Aufgaben sichtbar eine Stufe weiter.

Wichtig fuer die UX:

- Der Sonnenzyklus zeigt Fortschritt, keinen Countdown.
- Es gibt keinen Verlust, wenn Kinder langsam arbeiten.
- Die Sonne sollte im Header oder ueber dem Garten klein und ruhig eingebunden werden.
- Wachstum passiert nach erfolgreichen Tages- oder Wochenabschluessen, nicht waehrend zufaelliger Wartezeit.

## Persistenter Garten

Der Gartenzustand bleibt ueber die Saison bestehen. Eine abgeschlossene Tagesaufgabe kann ein Beet veraendern:

- Erde wird vorbereitet;
- Saatgut wird ausgesaet;
- Pflanzen keimen;
- Pflanzen wachsen;
- ein Beet braucht spaeter Nacharbeit;
- ein altes Beet wird mit einem neuen Werkzeug erneut bearbeitet.

Dadurch koennen spaetere Wochen auf fruehe Entscheidungen Bezug nehmen. Zum Beispiel:

- In Woche 1 wurde ein Beet mit dem Spaten vorbereitet.
- In Woche 2 wird Saatgut fuer genau dieses Beet eingekauft.
- In Woche 3 werden fehlende Flaechen mit dem Massband berechnet.
- In Woche 4 wird ein neues Beet auf der Wiese abgesteckt.
- In Woche 5 soll ein alter Bereich optimiert oder erweitert werden.

## Werkzeugfamilien und mathematische Zugänge

| Werkzeug | Mechanik | Mathematische Grundlage | Typische Frage |
| --- | --- | --- | --- |
| Spaten | Pro Klick wird `1 m²` Erde bearbeitet. | Einheitsquadrate, Zaehlen, Abschaetzen. | `Wie viele Einsaetze brauche ich fuer das ganze Beet?` |
| Saat-Sack | Vorgegebene Saecke decken Rechtecke oder zusammengesetzte Flaechen ab. | Teilflaechen, Zerlegung, Addition von Flaechen. | `Welche Saecke decken die Zielflaeche exakt ab?` |
| Massband | Lernende geben Seitenlaengen ein. | Rechteckformel `Laenge * Breite`. | `Wie viele Quadratmeter hat dieses Beet?` |
| Absteckseil | Lernende ziehen ein neues Rechteck oder Viereck auf der Wiese. | Flaeche konstruieren, Seitenlaengen variieren. | `Wie lege ich eine Flaeche mit genau 12 m² an?` |
| Einkaufskorb | Werkzeuge/Saatgut haben Preise und Mengen. | Multiplikation, Vergleich, Optimierung. | `Welche Loesung ist guenstig und passt trotzdem?` |

Langfristig koennen weitere Werkzeuge hinzukommen:

- Giesskanne: wiederkehrende Pflegeaufgabe, eventuell proportional zur Flaeche.
- Rechen: Flaeche glätten oder Fehler korrigieren.
- Gartenzaun: Umfang und Randlaenge als eigenes Thema.
- Schubkarre: Materialmenge fuer mehrere Beete zusammenfassen.

## Beispiel: Erste sechs Wochen

### Woche 1: Beet vorbereiten

Lernziel: Flaeche als Anzahl von Quadratmetern verstehen.

| Tag | Aufgabe | Werkzeug | Erfolgskriterium |
| --- | --- | --- | --- |
| Montag | Beetgroesse schaetzen. | Schaetzkarte | Schaetzung wird abgegeben. |
| Dienstag | Beet mit `1 m²`-Spatenklicks umgraben. | Spaten | Alle Zielzellen sind vorbereitet. |
| Mittwoch | Schaetzung mit gezaehlter Flaeche vergleichen. | Ergebnisbox | Differenz wird sichtbar. |
| Donnerstag | Erste `1 m²`-Saat ausbringen. | kleiner Saat-Sack | Beet ist exakt ausgesaet. |
| Freitag | Wochenabschluss. | Gartenbuch | Sonne geht unter, Pflanzen keimen. |

### Woche 2: Saatgut einkaufen

Lernziel: Gesamtflaechen durch passende Teilflaechen abdecken und Kosten beachten.

| Tag | Aufgabe | Werkzeug | Erfolgskriterium |
| --- | --- | --- | --- |
| Montag | Benoetigte Gesamtflaeche bestimmen. | Flaechenanzeige | Richtige Quadratmeterzahl erkannt. |
| Dienstag | Saat-Saecke einkaufen. | Einkaufskorb | Genug Saatgut gekauft. |
| Mittwoch | Saecke passend platzieren. | Saat-Saecke | Zielflaeche exakt ausgesaet. |
| Donnerstag | Gunstigere Kombination suchen. | Preisvergleich | Kostenlimit wird eingehalten. |
| Freitag | Wochenabschluss. | Gartenbuch | Pflanzen wachsen eine Stufe weiter. |

### Woche 3: Massband und Rechtecke

Lernziel: Rechteckflaechen ueber Seitenlaengen berechnen.

- Montag: Seitenlaengen am Beet ablesen.
- Dienstag: `Laenge * Breite` als Flaeche eingeben.
- Mittwoch: Saatgut nach berechneter Flaeche einkaufen.
- Donnerstag: Rechteck mit passender Groesse platzieren.
- Freitag: Ergebnis mit gezaehlten Quadratmetern vergleichen.

### Woche 4: Neue Beete abstecken

Lernziel: Flaechen selbst erzeugen und Nebenbedingungen beachten.

- Lernende ziehen neue Beete auf der Wiese.
- Ziel kann zum Beispiel sein: `Lege ein 12 m² Beet an, das hoechstens 3 m breit ist.`
- Mehrere Loesungen sind erlaubt.
- Die App kann unterschiedliche richtige Rechtecke vergleichen.

### Woche 5: Gartenauftrag optimieren

Lernziel: Loesungen nach Kosten, Werkzeuganzahl oder Materialverbrauch vergleichen.

- Ein Auftraggeber wuenscht mehrere Beete.
- Lernende sollen genug Saatgut kaufen, aber moeglichst wenig Geld ausgeben.
- Alternative Loesungen werden nicht nur als richtig/falsch, sondern als guenstiger, kuerzer oder materialschonender bewertet.

### Woche 6: Alte Beete nachbearbeiten

Lernziel: Flaechenwissen auf bestehende Gartenbereiche uebertragen.

- Frueh ausgesaete Beete sind gewachsen.
- Manche Bereiche brauchen Nacharbeit.
- Lernende nutzen neue Werkzeuge an alten Beeten.
- Dadurch wird sichtbar: Die Saison ist zusammenhaengend, nicht nur eine Levelreihe.

## Datenmodell-Skizze

Eine moegliche fachliche Struktur:

```text
Season
- id
- title
- weeks: WeekMission[]

WeekMission
- id
- label              // W1, W2, W3
- title
- storyGoal
- unlockRule
- days: DayTask[]
- weekSuccessCriteria
- growthTickOnComplete

DayTask
- id
- weekday            // monday, tuesday, ...
- title
- instruction
- toolMode           // spade, seedBags, measuringTape, plotDrawing, shopping
- targetArea
- budgetLimit
- requiredTools
- successCriteria
- feedbackRules
- growthEffect

GardenState
- currentWeekId
- currentDayId
- completedDayIds
- completedWeekIds
- beds
- inventory
- budget
- toolUsage

GardenBed
- id
- cells
- textureState       // grass, preparedEarth, seeded, planted
- cropKind
- plantedAtWeek
- plantedAtDay
- growthStage        // empty, soil, seed, sprout, grown, harvestable
- needsFollowUp
```

Der aktuelle MVP muss dafuer nicht sofort voll umgebaut werden. Wichtig ist nur, dass neue Felder in `missions.ts` spaeter nicht gegen die Wochenlogik arbeiten.

## Konkrete technische Umsetzungsphasen

### Phase 1: Konzept im Datenmodell vorbereiten

- `Mission` fachlich zu `WeekMission` erweitern oder eine neue Saison-Schicht ueber den bestehenden Missionen einfuehren.
- Tagesaufgaben zuerst rein als Metadaten modellieren.
- Die bestehende Zielpruefung weiterverwenden.
- UI zeigt weiterhin nur eine aktive Aufgabe, kennt aber schon Woche und Tag.

### Phase 2: UI von Missionsnummern auf Wochen umstellen

- TopBar zeigt `W1`, `W2`, `W3` statt `1`, `2`, `3`.
- Linke Missionsbox zeigt Tageschips `Mo`, `Di`, `Mi`, `Do`, `Fr`.
- Der bisherige Missionsfortschritt wird zu einem Tages- und Wochenfortschritt.

### Phase 3: Sonnenzyklus einbauen

- Sonnenstand aus abgeschlossenen Tagesaufgaben berechnen.
- Kleine SVG- oder CSS-Animation im Header oder ueber dem Garten.
- Kein Echtzeit-Timer, sondern Fortschrittsanimation nach Abschluss.

### Phase 4: Persistenten Garten speichern

- Gartenzustand zunaechst im lokalen Browserzustand halten.
- Danach optional in `localStorage` speichern.
- Abgeschlossene Beete bleiben sichtbar und bekommen Wachstumsstufen.

### Phase 5: Werkzeugmechaniken systematisieren

- Werkzeugtypen als eigene fachliche Kategorie modellieren.
- Pro Werkzeug definieren:
  - Interaktion;
  - mathematischer Zugang;
  - Kostenmodell;
  - Erfolgskriterien;
  - Feedbackregeln.

## UI-Vorschlag

Grober Aufbau:

```text
Header
- Logo
- Wochenchips: W1 W2 W3 W4
- kleiner Sonnenbogen mit aktuellem Sonnenstand

Linke Infobox
- Woche und Wochenziel
- Tageschips: Mo Di Mi Do Fr
- aktuelle Tagesaufgabe
- Ziel, Budget, Fortschritt

Mitte
- Persistenter Garten
- aktives Beet hervorgehoben
- alte Beete sichtbar mit Wachstumsstufen

Rechts
- Werkzeuge fuer den aktuellen Tag
- Einkaufskorb / Werkzeugkosten / Auswahl
```

## Didaktischer Nutzen

Die Wochenstruktur verbindet mathematische Teilfertigkeiten zu einem realistischeren Handlungszusammenhang:

- Montag kann eine Schaetzung verlangen.
- Dienstag prueft dieselbe Flaeche durch Zaehlen.
- Mittwoch nutzt Seitenlaengen.
- Donnerstag entscheidet ueber Einkauf oder Werkzeugwahl.
- Freitag vergleicht Kosten, Flaeche und Materialverbrauch.

So entsteht nicht nur Formeltraining, sondern flexible Flaechenkompetenz. Lernende sehen, dass `m²` in verschiedenen beruflichen Situationen gebraucht werden: planen, einkaufen, vorbereiten, aussaeen, optimieren und nacharbeiten.

## Offene Fragen

- Soll eine Woche immer genau fuenf Tagesaufgaben haben oder duerfen kurze Wochen nur drei Tage enthalten?
- Sollen Tagesaufgaben einzeln wiederholbar sein oder nur die ganze Woche?
- Wie viel Freiheit duerfen Lernende beim Ueberspringen von Tagen bekommen?
- Soll der Einkaufskorb sofort echte Preise nutzen oder zuerst nur Mengen?
- Soll Wachstum pro Tag, pro Woche oder pro abgeschlossenem Kapitel passieren?
- Wie stark sollen alte Beete in spaeteren Wochen spielrelevant werden?
- Brauchen Lehrkraefte eine Option, direkt zu einer Woche oder einem Tag zu springen?
