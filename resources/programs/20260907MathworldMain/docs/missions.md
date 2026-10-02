# Missionen und fachliches Datenmodell

Stand: 2026-06-09

Dieses Dokument beschreibt die ersten drei Missionen des MathWorld-MVP fachlich. Es soll klaeren, welche Ziele, Werkzeuge, Erfolgskriterien und Feedbackregeln die Missionen haben. Spaeter kann daraus ein technisches Datenmodell fuer `src/data/missions.ts` entstehen.

Eine weiterfuehrende Struktur fuer spaetere Versionen ist in `docs/season-mission-concept.md` notiert: Missionen koennen dort als Wochen gedacht werden, die aus mehreren Tagesaufgaben bestehen.

## Ziel des MVP

Der MVP soll Lernenden der 5. bis 6. Klasse helfen, Flaechen handelnd und visuell zu verstehen. Im Mittelpunkt steht eine Garten-Mission mit Raster, Zielbereich, Formen, Snapping, Zielpruefung und unmittelbarem Feedback.

Die ersten drei Missionen bauen aufeinander auf:

1. Einfache Zielflaechen mit `1 x 1`-Quadraten fuellen.
2. Komplexere Zielflaechen mit vorgegebenen Rechtecken fuellen.
3. Eigene Rechtecke erzeugen und passend kombinieren.

Umfangsaufgaben bleiben Teil der Projektperspektive, sind aber nicht Teil der ersten drei MVP-Missionen.

## Gemeinsame Begriffe

| Begriff | Bedeutung |
| --- | --- |
| Mission | Ein Level mit eigener Aufgabenstellung, erlaubten Werkzeugen und Erfolgskriterien. |
| Arbeitsflaeche | Der gesamte Bereich, in dem Formen erzeugt, verschoben und abgelegt werden koennen. |
| Zielbereich | Die Flaeche im Raster, die korrekt belegt werden soll. |
| Zielflaeche | Inhaltlich dasselbe wie Zielbereich; der Begriff betont die mathematische Flaeche. |
| Zielzelle | Eine einzelne Rasterzelle, die Teil des Zielbereichs ist. |
| Form | Ein Quadrat oder Rechteck, das auf der Arbeitsflaeche platziert wird. |
| Werkzeug | Eine erlaubte Form oder eine Erzeugungsmethode fuer Formen. |
| Snapping | Automatisches Einrasten einer Form auf ganze Rasterpositionen. |
| Erfolgskriterium | Regel, nach der die Mission als geloest gilt. |
| Feedback | Rueckmeldung, warum eine Loesung richtig, unvollstaendig oder falsch ist. |

## Datenmodell-Skizze

Die Missionsdaten liegen im aktuellen MVP noch direkt in `src/App.tsx`. Spaeter koennen sie in eine eigene Datei wie `src/data/missions.ts` ausgelagert und fachlich erweitert werden.

### Aktueller MVP-Stand

Der aktuelle Prototyp nutzt sinngemaess diese Struktur:

```text
Mission
- id
- title
- goal
- tools
- variants
- allowFreeDrawing
- requiredTools
```

### Aktuelle Felder

- `id`: eindeutige Missionsnummer.
- `title`: sichtbarer Name der Mission.
- `goal`: kurze Aufgabenbeschreibung fuer Lernende.
- `tools`: vordefinierte Formen, die per Button erzeugt werden koennen.
- `variants`: randomisierte Zielvarianten mit konkreten Zielzellen.
- `allowFreeDrawing`: erlaubt freie Rechteck-Erzeugung per Eingabe und Drag.
- `requiredTools`: Pflichtformen, die mindestens einmal verwendet werden muessen.

### Spaetere fachliche Erweiterung

Fuer eine besser erweiterbare Struktur koennten spaeter zusaetzliche fachliche Felder ergaenzt werden:

```text
MissionExtension
- shortDescription
- learningGoal
- successCriteria
- feedbackRules
- minimumDistinctShapeSizes
- acceptedSolutionStrategies
```

- `shortDescription`: ausformulierte Aufgabenbeschreibung fuer Lernende.
- `learningGoal`: fachliches Lernziel.
- `successCriteria`: explizite Regeln, die fuer den Abschluss gelten.
- `feedbackRules`: typische Fehler und passende Rueckmeldungen.
- `minimumDistinctShapeSizes`: fachliche Anforderung, wenn mehrere verschiedene Rechteckgroessen genutzt werden sollen.
- `acceptedSolutionStrategies`: erlaubte Loesungswege, falls mehrere richtige Zerlegungen moeglich sind.

### Form

```text
Form
- id
- type
- width
- height
- area
- isRequired
```

- `type`: zuerst `rectangle`, spaeter moeglich: `triangle` oder `composite`.
- `width` und `height`: Seitenlaengen im Raster.
- `area`: Flaecheninhalt in Quadratmetern.
- `isRequired`: markiert Pflichtformen in Mission 2.

### Zielbereich

```text
TargetArea
- gridCells
- acceptedCoverage
- allowOverlap
- requireExactCoverage
```

- `gridCells`: Rasterzellen, die zur Zielflaeche gehoeren.
- `acceptedCoverage`: Regeln, welche Zellen belegt werden muessen.
- `allowOverlap`: im MVP normalerweise `false`.
- `requireExactCoverage`: im MVP normalerweise `true`.

## Mission 1: Quadrate platzieren

### Ziel

Lernende fuellen eine einfache Zielflaeche mit `1 x 1`-Quadraten. Sie erkennen, dass jede belegte Rasterzelle `1 m²` entspricht und dass die gesamte Flaeche durch Zaehlen oder vollstaendiges Belegen bestimmt werden kann.

### Erlaubte Formen und Werkzeuge

| Werkzeug | Groesse | Flaecheninhalt | Rolle |
| --- | --- | --- | --- |
| Quadrat | `1 x 1` | `1 m²` | Standardform zum Fuellen der Zielflaeche |

### Zielvarianten

Moegliche Zielvarianten sind einfache Formen aus wenigen Rasterzellen:

- L-Form
- Quadrat
- Reihe
- Treppe

### Erfolgskriterien

- Alle Zielzellen sind exakt belegt.
- Keine Zielzelle fehlt.
- Keine Form liegt ausserhalb der Zielflaeche.
- Es gibt keine unzulaessige Ueberlappung.

### Typische Fehler

| Fehler | Bedeutung |
| --- | --- |
| Zielzellen fehlen | Die Zielflaeche ist noch nicht vollstaendig belegt. |
| Form liegt ausserhalb der Zielflaeche | Ein Quadrat wurde neben oder ueber den Zielbereich hinaus platziert. |
| Ueberlappung | Zwei Quadrate liegen auf derselben Rasterzelle. |

### Feedback

- Bei fehlenden Zielzellen: Hinweis, dass noch nicht alle Zielkaestchen bedeckt sind.
- Bei Formen ausserhalb der Zielflaeche: Hinweis, dass eine Form ausserhalb des Zielbereichs liegt.
- Bei Erfolg: positive Rueckmeldung und Freischaltung von `Naechstes Level`.

## Mission 2: Pflichtformen platzieren

### Ziel

Lernende fuellen eine komplexere Zielflaeche mit mehreren vorgegebenen Rechtecken. Sie erkennen, dass eine Gesamtflaeche aus Teilflaechen zusammengesetzt werden kann und dass unterschiedliche Rechtecke denselben Zielbereich gemeinsam abdecken.

### Erlaubte Formen und Werkzeuge

| Werkzeug | Groesse | Flaecheninhalt | Rolle |
| --- | --- | --- | --- |
| Rechteck | `2 x 2` | `4 m²` | Pflichtform |
| Rechteck | `1 x 3` | `3 m²` | Pflichtform |
| Rechteck | `3 x 2` | `6 m²` | Pflichtform |

### Zielvarianten

Die Zielflaeche soll komplexer sein als nebeneinander angeordnete Rechtecke. Mindestens zwei Formen sollen sich an einer Seite beruehren, auch teilweise. Dadurch entsteht eine zusammengesetzte Flaeche.

Aktuelle Zielvarianten im MVP:

- `Kombibeet mit Ecke`
- `Versetztes Kombibeet`
- `Stufiges Kombibeet`

### Erfolgskriterien

- Alle drei Pflichtformen wurden verwendet.
- Die Formen belegen zusammen exakt die Zielflaeche.
- Keine Form liegt ausserhalb der Zielflaeche.
- Es gibt keine Ueberlappung.
- Keine Pflichtform fehlt.

### Typische Fehler

| Fehler | Bedeutung |
| --- | --- |
| Pflichtform fehlt | Eine der Formen `2 x 2`, `1 x 3` oder `3 x 2` wurde nicht verwendet. |
| Form liegt ausserhalb der Zielflaeche | Eine Form ist nicht vollstaendig im Zielbereich. |
| Ueberlappung | Formen bedecken dieselben Rasterzellen. |
| Zielflaeche unvollstaendig | Es bleiben Zielzellen frei. |

### Feedback

- Bei fehlender Pflichtform: Hinweis, welche Pflichtform noch verwendet werden muss.
- Bei Ueberlappung: Hinweis, dass sich Formen nicht gegenseitig ueberdecken duerfen.
- Bei Form ausserhalb der Zielflaeche: Hinweis, dass eine Form ausserhalb des Zielbereichs liegt.
- Bei Erfolg: positive Rueckmeldung und Freischaltung von `Naechstes Level`.

## Mission 3: Eigene Rechtecke erzeugen

### Ziel

Lernende erzeugen eigene Rechtecke und kombinieren sie, um eine Zielflaeche zu fuellen. Fachlich ist gewuenscht, dass Lernende die Zielflaeche durch mehrere passende Rechtecke zerlegen. Der aktuelle MVP prueft vor allem die korrekte Belegung der Zielflaeche; eine explizite technische Pruefung auf mindestens zwei verschiedene Rechteckgroessen sollte spaeter im Datenmodell ergaenzt werden.

### Erlaubte Formen und Werkzeuge

| Werkzeug | Wertebereich | Rolle |
| --- | --- | --- |
| Eingabe `Seite a` und `Seite b` | jeweils `1` bis `9` | Rechteck per Zahlenwerten erzeugen |
| Vorschau | aus den Eingaben abgeleitet | Form vor dem Platzieren ansehen |
| Drag-Erzeugung auf der Arbeitsflaeche | ganzzahlige Seitenlaengen | Rechteck direkt im Raster aufziehen |

### Zielvarianten

Die Zielflaechen sollen so gestaltet sein, dass mehrere Rechtecke sinnvoll kombiniert werden. Dadurch geht es nicht nur um eine einzelne Flaeche, sondern um Zerlegung und Zusammensetzung.

Aktuelle Zielvarianten im MVP:

- `Eckbeet aus zwei Formen`
- `Stufenbeet`
- `Breites Eckbeet`

### Erfolgskriterien

- Die Zielflaeche ist exakt belegt.
- Fachlich sollen mehrere passende Rechtecke kombiniert werden.
- Keine Form liegt ausserhalb der Zielflaeche.
- Es gibt keine Ueberlappung.
- Die erzeugten Rechtecke haben gueltige Seitenlaengen.

### Typische Fehler

| Fehler | Bedeutung |
| --- | --- |
| Nur eine Form verwendet | Fachlich ist eine Zerlegung in mehrere passende Rechtecke gewuenscht; eine explizite technische Pruefung darauf ist eine spaetere Datenmodell-Anforderung. |
| Ungueltige Seitenlaenge | Eingaben ausserhalb von `1` bis `9` sind nicht erlaubt. |
| Form liegt ausserhalb der Zielflaeche | Ein erzeugtes Rechteck passt nicht vollstaendig in den Zielbereich. |
| Zielflaeche unvollstaendig | Die erzeugten Rechtecke fuellen den Zielbereich noch nicht. |
| Ueberlappung | Zwei Rechtecke bedecken dieselben Rasterzellen. |

### Feedback

- Bei nur einer Form: spaeter moeglicher Hinweis, dass mehrere passende Rechtecke genutzt werden sollen.
- Bei ungueltiger Eingabe: Hinweis auf den erlaubten Wertebereich `1` bis `9`.
- Bei Form ausserhalb der Zielflaeche: Meldung `Eine Form liegt ausserhalb der Zielflaeche`.
- Bei unvollstaendiger Zielflaeche: Hinweis, dass noch Zielkaestchen frei sind.
- Bei Erfolg: positive Rueckmeldung zum korrekten Fuellen der Zielflaeche.

## Uebergreifende Feedbackregeln

| Situation | Feedbackziel | Aktueller MVP-Feedbacktext |
| --- | --- | --- |
| Zielflaeche korrekt gefuellt | Erfolg sichtbar machen und naechsten Schritt erlauben. | `Mission erfuellt` |
| Zielflaeche unvollstaendig | Lernende auf fehlende Zielzellen hinweisen. | `<x> von <y> Ziel-Feldern belegt` |
| Form vollstaendig ausserhalb der Zielflaeche | Erklaeren, dass alle Formen den Zielbereich beruehren muessen. | `Eine Form liegt ausserhalb der Zielflaeche` |
| Form teilweise ausserhalb der Zielflaeche | Erklaeren, dass alle Formen vollstaendig im Zielbereich liegen muessen. | `Eine Form ragt ueber die Zielflaeche hinaus` |
| Ueberlappung | Darauf hinweisen, dass Teilflaechen nicht doppelt zaehlen duerfen. | `Auf einem Ziel-Feld liegen mehrere Formen` |
| Pflichtform fehlt | Konkret nennen, dass alle Pflichtformen genutzt werden sollen. | `Benutze alle drei Formen mindestens einmal` |
| Ungueltige Eingabe | Erlaubten Zahlenbereich nennen. | Noch kein eigener MVP-Feedbacktext; Eingaben werden auf `1` bis `9` begrenzt. |

## Zusätzliche Level-Ideen für die nächste Version

Die folgenden Level sind fachliche Vorschlaege fuer eine naechste MathWorld-Version. Sie beschreiben noch keine neuen Missionsdaten im Code, sondern helfen, Lernziele, Gartenidee, Werkzeuge und Feedback vor der Umsetzung zu klaeren.

### Radieschen-Beet

| Aspekt | Beschreibung |
| --- | --- |
| Lernziel | Flaecheninhalt durch Zaehlen einzelner Quadratmeter verstehen. |
| Zielbereich | Kleines rechteckiges Beet, zum Beispiel `2 x 3` Felder. |
| Erlaubte Werkzeuge und Formen | Nur `1 x 1`-Quadrate als einzelne Radieschen-Felder. |
| Schwierigkeitsgrad | Sehr leicht; Einstieg nach Mission 1. |
| Erfolgskriterien | Alle Zielzellen sind belegt, keine Form liegt ausserhalb, keine Ueberlappung. |
| Typische Fehler | Einzelne Felder bleiben frei; ein Quadrat wird neben das Beet gelegt. |
| Kindgerechtes Feedback | `Fast fertig: Ein Radieschen-Feld ist noch leer.` oder `Super, dein Radieschen-Beet ist voll bepflanzt!` |
| Moegliche Garten-Belohnung | Rote Radieschen erscheinen auf allen korrekt belegten Feldern. |

### Karotten-Streifen

| Aspekt | Beschreibung |
| --- | --- |
| Lernziel | Reihen und Spalten als Multiplikation erkennen, zum Beispiel `1 x 4`, `2 x 4` oder `1 x 6`. |
| Zielbereich | Langes, schmales Beet als Streifen oder Doppelreihe. |
| Erlaubte Werkzeuge und Formen | `1 x 1`-Quadrate und einfache Rechtecke wie `1 x 2`, `1 x 3` oder `1 x 4`. |
| Schwierigkeitsgrad | Leicht; baut auf Zaehlen und einfachen Rechtecken auf. |
| Erfolgskriterien | Der Streifen ist exakt gefuellt; Rechtecke passen vollstaendig in den Zielbereich. |
| Typische Fehler | Rechteck wird gedreht gedacht, passt aber nicht in den schmalen Streifen; ein Feld bleibt am Ende frei. |
| Kindgerechtes Feedback | `Die Karottenreihe braucht noch Platz fuer alle Karotten.` oder `Prima, du hast die Karotten in ordentlichen Reihen gepflanzt!` |
| Moegliche Garten-Belohnung | Karotten wachsen als Reihe aus der Erde; bei Erfolg gibt es eine kleine Ernte-Kiste. |

### Gemüse-Mix-Beet

| Aspekt | Beschreibung |
| --- | --- |
| Lernziel | Eine Gesamtflaeche aus mehreren vorgegebenen Teilflaechen zusammensetzen. |
| Zielbereich | Zusammengesetztes Beet aus Rechtecken, zum Beispiel Ecke oder Stufe. |
| Erlaubte Werkzeuge und Formen | Vorgegebene Rechtecke wie `2 x 2`, `1 x 3`, `3 x 1` und optional `2 x 3`. |
| Schwierigkeitsgrad | Mittel; passend nach Mission 2. |
| Erfolgskriterien | Alle Pflichtformen werden verwendet, die Zielflaeche ist exakt belegt, keine Ueberlappung. |
| Typische Fehler | Eine Pflichtform fehlt; zwei Formen ueberdecken sich; eine Form ragt aus dem Beet. |
| Kindgerechtes Feedback | `Dem Beet fehlt noch eine Gemuese-Sorte.` oder `Achtung, hier wachsen zwei Pflanzen auf demselben Feld.` |
| Moegliche Garten-Belohnung | Unterschiedliche Gemuesearten erscheinen je nach verwendeter Form, zum Beispiel Salat, Kohl und Bohnen. |

### Blumen-Ecke

| Aspekt | Beschreibung |
| --- | --- |
| Lernziel | Eine L-Form in passende Rechtecke zerlegen und mehrere Loesungswege vergleichen. |
| Zielbereich | L-foermige Blumenrabatte mit klar sichtbarer Ecke. |
| Erlaubte Werkzeuge und Formen | Eigene Rechtecke per Eingabe oder Drag-Erzeugung; optional kleine Hilfsquadrate. |
| Schwierigkeitsgrad | Mittel bis anspruchsvoll; baut auf Mission 3 auf. |
| Erfolgskriterien | Die gesamte L-Form ist exakt gefuellt; fachlich sollen mindestens zwei Rechtecke sinnvoll kombiniert werden. |
| Typische Fehler | Nur ein grosses Rechteck wird ueber die Ecke gelegt; ein Rechteck ragt in den leeren Bereich; die Ecke bleibt frei. |
| Kindgerechtes Feedback | `Die Blumen sollen nur im Beet wachsen, nicht auf dem Weg.` oder `Schoen zerlegt: Die Blumen-Ecke ist komplett!` |
| Moegliche Garten-Belohnung | Bunte Blumen erscheinen in zwei Farben, passend zu den genutzten Teilrechtecken. |

### Freies Gartenbeet

| Aspekt | Beschreibung |
| --- | --- |
| Lernziel | Verschiedene richtige Zerlegungen einer Flaeche finden und begruenden. |
| Zielbereich | Groesseres Beet mit einfacher zusammengesetzter Form, aber mehreren moeglichen Loesungen. |
| Erlaubte Werkzeuge und Formen | Frei erzeugte Rechtecke mit Seitenlaengen im erlaubten Bereich; spaeter optional Dreiecke. |
| Schwierigkeitsgrad | Anspruchsvoll; Abschluss einer Rechteck-Levelreihe. |
| Erfolgskriterien | Zielbereich ist exakt belegt, keine Ueberlappung, keine Form ausserhalb; mehrere Loesungswege sind erlaubt. |
| Typische Fehler | Zu viele kleine Formen ohne Plan; Ueberlappungen; Zielzellen bleiben versteckt frei. |
| Kindgerechtes Feedback | `Dein Gartenplan ist fast fertig: Suche noch die freien Erdkaestchen.` oder `Toller eigener Gartenplan! Diese Zerlegung passt genau.` |
| Moegliche Garten-Belohnung | Kinder erhalten ein frei gestaltetes Beet mit Mix aus Blumen und Gemuese sowie einen goldenen Gartenstern. |

## Zukünftige Formtypen: Dreiecke und zusammengesetzte Flächen

Dieser Abschnitt beschreibt eine fachliche Vorbereitung fuer spaetere Formtypen. Im aktuellen MVP wird noch keine spielbare Dreieck-Funktion implementiert. Die ersten drei Missionen bleiben weiterhin auf Quadrate und Rechtecke ausgerichtet.

### Dreiecke im Raster

Ein Dreieck im Raster kann fachlich als halbes Rechteck verstanden werden, wenn Grundseite und Hoehe auf Rasterlinien liegen. Lernende koennen daran erkennen, dass die Flaeche eines Dreiecks aus einem passenden Rechteck abgeleitet wird:

```text
Flaeche Dreieck = Grundseite * Hoehe / 2
```

Fuer ein spaeteres Datenmodell waeren mindestens diese Angaben noetig:

- Grundseite in Rastereinheiten.
- Hoehe in Rastereinheiten.
- Flaecheninhalt in Quadratmetern.
- Lage im Raster.
- Orientierung, zum Beispiel rechtwinklig links unten, rechts unten, links oben oder rechts oben.

### Zusammengesetzte Flaechen

Eine zusammengesetzte Flaeche besteht aus mehreren Teilflaechen, die gemeinsam eine Ziel- oder Werkzeugform bilden. Das koennen mehrere Rechtecke sein, spaeter aber auch Kombinationen aus Rechtecken und Dreiecken. Fachlich ist wichtig, dass Teilflaechen nicht doppelt gezaehlt werden und dass die Gesamtflaeche aus der Summe der Teilflaechen entsteht.

Spaeter koennten Lernende zum Beispiel:

- ein Rechteck mit einem Dreieck zu einer neuen Zielform kombinieren;
- ein zusammengesetztes Beet aus mehreren Rechtecken und Dreiecken fuellen;
- eine Gesamtflaeche in bekannte Teilflaechen zerlegen;
- erklaeren, warum zwei Dreiecke zusammen ein Rechteck ergeben koennen.

### Offene Fragen vor der technischen Umsetzung

- Sollen Dreiecke immer rechtwinklig sein oder auch andere Dreiecksformen erlauben?
- Duerfen Dreiecke nur halbe Rasterrechtecke sein oder auch freie Grundseiten und Hoehen haben?
- Wie wird die Orientierung eines Dreiecks im UI ausgewaehlt und sichtbar gemacht?
- Wie genau soll Snapping fuer Dreiecke funktionieren?
- Soll die Zielpruefung mit ganzen Rasterzellen, halben Rasterzellen oder geometrischen Flaechen arbeiten?
- Wie werden Ueberlappungen zwischen Rechtecken und Dreiecken erkannt?
- Welche Feedbacktexte brauchen Lernende, wenn ein Dreieck falsch liegt oder falsch orientiert ist?

### Technische Gap-Liste

| Bereich | Spaeter zu klaeren |
| --- | --- |
| `types.ts` | Formtypen fuer Dreiecke und zusammengesetzte Flaechen fachlich genauer modellieren, inklusive Orientierung und Teilformen. |
| `geometry.ts` | Flaechenberechnung, belegte Flaechen, Snapping, Ueberlappung und Zielpruefung fuer Dreiecke erweitern. |
| `Workspace.tsx` | Dreiecke und zusammengesetzte Formen sichtbar zeichnen, auswahlen, verschieben und beschriften. |
| `missions.ts` | Missionsdaten um Dreiecks-Werkzeuge, zusammengesetzte Zielflaechen und passende Erfolgskriterien erweitern. |

## Offene Fragen

- Sollen Zielvarianten fachlich dokumentierte Namen bekommen, zum Beispiel `L-Form`, `Treppe` oder `Kombiflaeche A`?
- Soll das Datenmodell zuerst nur Rasterzellen speichern oder auch abstrakte Formen wie Rechtecke und spaeter Dreiecke?
- Sollen Feedbacktexte zentral pro Fehlertyp gepflegt werden?
- Soll eine Mission mehrere richtige Loesungswege ausdruecklich erlauben?
- Wie detailliert sollen Lernziele spaeter pro Mission im UI angezeigt werden?
