# Projektkontext MathWorld

Stand: 2026-06-08

Dieses Dokument haelt den aktuellen MVP-Zuschnitt fuer MathWorld fest. Es ist die Grundlage fuer die naechsten technischen Aufgaben und kann spaeter erweitert werden, wenn weitere Missionen hinzukommen.

## Zielgruppe

Der erste Prototyp wird fuer die 5. bis 6. Klasse entwickelt.

Die Anwendung soll Lernenden helfen, Flaechen nicht nur rechnerisch, sondern auch handelnd und visuell zu verstehen. Im Vordergrund steht zuerst eine einfache, stabile Interaktionsstruktur: Flaechen erzeugen, verschieben, bemassen, pruefen und in Missionen verwenden.

## MVP-Ziel

Der MVP soll eine primitive, aber funktionsfaehige Garten-Mission im Browser zeigen. Wichtig ist zuerst, dass alle noetigen Grundstrukturen integriert sind. Komplexere Funktionen duerfen fuer fruehe Level deaktiviert oder eingeschraenkt werden.

Der Prototyp gilt als vorzeigbar, wenn die ersten drei Missionen spielbar sind und die zentrale Arbeitsflaeche mit Drag-and-Drop, Snapping, Eingabe und Feedback funktioniert.

## Muss-Funktionen

Der MVP muss koennen:

- eine primitive Oberflaeche anzeigen
- eine dedizierte Arbeitsflaeche bereitstellen, auf der Vierecke erstellt und bearbeitet werden koennen
- verhindern, dass Vierecke ausserhalb der Arbeitsflaeche erstellt oder bearbeitet werden
- frei waehlbare Vierecke per Linksklick, Gedrueckthalten und Ziehen erzeugen
- erstellte Flaechen per Drag-and-Drop verschieben
- Vierecke in der Arbeitsflaeche durch Zahleneingaben bearbeiten
- live die Seitenlaengen und den Flaecheninhalt der erzeugten Flaeche anzeigen
- ein Missionsziel pruefen, zum Beispiel: Erzeuge ein Viereck mit bestimmten Massen
- Feedback geben, wenn das Missionsziel erfuellt wurde
- die ersten drei Missionen bereitstellen
- Levelvarianten randomisieren, damit nicht immer dieselbe Zielform erscheint
- einen Button `Neustarten` anbieten, der das aktuelle Level neu startet und neu randomisiert
- einen Button `Naechstes Level` anbieten, der erst nach einmaligem Erfuellen des aktuellen Levels verfuegbar wird
- zwischen den ersten drei Missionen wechseln koennen, sobald die jeweils vorherige Mission geschafft wurde

## Missionen im MVP

### Mission 1: L-Form mit Quadraten fuellen

Eine L-foermige oder vergleichbar einfache Zielflaeche mit `4 m^2` soll mit vorgegebenen `1 m^2`-Quadraten befuellt werden. Die L-Form ist nur eine erste Beispielvariante; die konkrete Zielform soll spaeter randomisiert werden.

Interaktion:

- vordefinierte `1 m^2`-Quadrate per Drag-and-Drop bewegen
- Quadrate snappen in ein Ziel-Grid
- die Mission ist erfuellt, wenn die L-Form korrekt belegt ist

### Mission 2: Komplexere Formen einsetzen

Die Aufgabe funktioniert aehnlich wie Mission 1, nutzt aber komplexere vordefinierte Vierecke.

Beispielformen:

- `2 x 2`
- `3 x 2`
- `1 x 3`

Interaktion:

- vordefinierte Formen per Drag-and-Drop bewegen
- Formen snappen in ein Ziel-Grid
- die Mission ist erfuellt, wenn alle drei Formen benutzt wurden und die Zielflaeche passend belegt ist
- Zielflaechen sollen komplexer sein als nur nebeneinander angeordnete Rechtecke; mindestens eine Seite soll sich beruehren, auch teilweise

### Mission 3: Vierecke selbst erzeugen

Die Aufgabe funktioniert aehnlich wie Mission 2, aber ohne vordefinierte Vierecke. Lernende erzeugen eigene Rechtecke und kombinieren mindestens zwei verschiedene Vierecke, um die Zielflaeche zu fuellen.

Interaktion:

- Vierecke werden auf der Arbeitsflaeche selbst erzeugt
- Erzeugung per Eingabe von `Seite a` und `Seite b`
- Vorschau der eingegebenen Form, die per Klick gespawnt wird
- Erzeugung per Drag auf der Arbeitsflaeche mit ganzzahligen Seitenlaengen
- Eingaben sind auf `1-9` begrenzt und koennen zum Neueingeben geleert werden
- Seitenlaengen und Flaecheninhalt werden live angezeigt
- die Mission prueft, ob die erzeugte Flaeche zum Ziel passt

## Technische Leitentscheidung

Der geplante Stack bleibt:

- React
- TypeScript
- Vite
- SVG fuer Arbeitsflaeche, Grid, Flaechen, Masslinien und Markierungen
- lokaler Zustand im Browser
- kein Backend im MVP

## Erweiterbarkeit fuer spaetere Themen

Dreiecke, zusammengesetzte Flaechen und anspruchsvollere Aufgaben bleiben Teil der Projektperspektive. Sie sind nur nicht das erste spielbare MVP-Ziel. Die technische Struktur soll deshalb nicht ausschliesslich auf `1 x 1`-Quadrate oder Rechtecke zugeschnitten werden.

Der erste Prototyp darf Rechtecke als einfachsten Formtyp priorisieren. Datenmodell, Arbeitsflaeche und Zielpruefung sollen aber so vorbereitet werden, dass spaeter weitere Formtypen ergaenzt werden koennen, zum Beispiel:

- Dreiecke mit Grundseite und Hoehe
- zusammengesetzte Flaechen aus mehreren Teilformen
- frei erzeugte Vierecke oder Rechtecke mit variabler Groesse
- Aufgaben, bei denen Flaecheninhalt, Umfang und Zerlegung verglichen werden

## Wichtige technische Strukturen

Damit die Missionen spaeter leicht erweitert werden koennen, braucht der Prototyp frueh diese Strukturen:

- ein Datenmodell fuer Missionen, Ziele und erlaubte Werkzeuge
- ein Datenmodell fuer erzeugte oder vordefinierte Flaechen
- eine Arbeitsflaeche mit klaren Grenzen
- Drag-and-Drop fuer Flaechen
- Erzeugung von Vierecken per Ziehen
- Grid- und Snap-Logik
- numerische Bearbeitung von Flaechenmassen
- Live-Bemassung fuer Seiten und Flaecheninhalt
- Pruefung von Missionszielen
- Feedback bei erfuellten oder noch nicht erfuellten Zielen

## Nicht-Ziele des ersten MVP

Die Nicht-Ziele aus `KONZEPTENTWURF_MathWorld.md` bleiben gueltig. Insbesondere gehoeren nicht zum ersten MVP:

- Benutzerkonten
- Datenbank
- Lehrer-Dashboard
- echte KI-Auswertung
- vollstaendige Spielwelt
- alle spaeter denkbaren Mathematikthemen
