# MathWorld - Konzeptentwurf fuer das Planungstreffen

Stand: 2026-05-28

## Kurzidee

MathWorld wird eine webbasierte Lernanwendung fuer den Mathematikunterricht. Die Anwendung vermittelt Flaechen- und Umfangsberechnung nicht als reine Formelsammlung, sondern ueber kleine alltagsnahe Missionen. Der erste spielbare Bereich ist eine Garten-Mission: Lernende planen Schritt fuer Schritt einen Schulgarten, berechnen Beet-, Rasen-, Wege- und spaeter komplexere Gruenflaechen und sehen direkt, wie ihre Berechnungen die Gestaltung beeinflussen.

Der Kern ist nicht "mehr Aufgaben in schoener Verpackung", sondern ein didaktischer Transfer: Die Lernenden sollen erkennen, welche reale Flaeche gemeint ist, welche Masse relevant sind, welche Formel passt und warum das Ergebnis sinnvoll ist.

## Zielgruppe und Lernziele

Vorlaeufige Zielgruppe:

- Klasse 5-7
- Lernende mit Grundlagen in Geometrie
- besonders geeignet fuer Lernende, die Formeln auswendig kennen, aber Probleme beim Anwenden und Uebertragen haben

Erste Lernziele:

- Rechteckflaechen berechnen
- Umfang von Rechtecken berechnen
- Dreiecksflaechen als halbe Rechtecke verstehen
- zusammengesetzte Flaechen zerlegen und berechnen
- Masse aus einer Alltagssituation entnehmen
- Ergebnis und Einheit plausibel interpretieren
- erklaeren, warum eine bestimmte Formel passt

## Didaktischer Leitgedanke

Die Anwendung verbindet drei Ebenen:

1. Reale Situation: "Wir planen einen Garten."
2. Mathematisches Modell: "Welche Figur steckt darin?"
3. Berechnung und Feedback: "Welche Formel passt, was bedeutet das Ergebnis?"

Dadurch soll die App mehrere Zugangswege bieten:

- visuell: Flaechen sind sichtbar, farbig markiert und veraenderbar
- handelnd: Lernende ziehen oder veraendern Objekte wie Beete, Wege oder Dreiecksbereiche
- numerisch: Masse und Ergebnisse werden eingegeben
- sprachlich: Hinweise und Erklaerungen begleiten die Rechnung

Fehler sollen als Diagnose genutzt werden. Eine falsche Antwort fuehrt nicht einfach zu "falsch", sondern zu einem passenden Hinweis, etwa: "Du hast die Beetflaeche richtig berechnet. Jetzt musst du sie noch von der gesamten Gartenflaeche abziehen."

## MVP: Was zuerst fertig werden sollte

Der erste vorzeigbare Prototyp sollte klein, stabil und didaktisch klar sein.

Mindestumfang:

- Startansicht mit der aktiven Garten-Mission
- mehrere aufeinander aufbauende Level
- interaktive Darstellung eines Gartens mit Massangaben
- Eingabefelder fuer Rechenschritte und Ergebnis
- visuelles Markieren der gerade berechneten Flaeche
- unmittelbares Feedback mit Hinweisen
- sichtbarer Fortschritt, zum Beispiel freigeschaltete Gartenelemente
- lauffaehig lokal im Browser und spaeter einfach online deploybar

Nicht Teil des ersten MVP:

- Benutzerkonten
- komplexe Datenbank
- echte KI-Auswertung
- vollstaendige Spielwelt
- alle Mathethemen gleichzeitig
- Lehrer-Dashboard

## Level-Vorschlag fuer die Garten-Mission

### Level 1: Das Schulbeet

Kontext: Ein rechteckiges Beet soll angelegt werden.

Aufgabe:

- Laenge und Breite erkennen
- Flaeche berechnen
- optional Umfang fuer eine Beetkante berechnen

Mathematischer Fokus:

- Rechteck: `A = a * b`
- Umfang: `U = 2a + 2b`

Interaktion:

- Werte per Eingabefeld oder Slider aendern
- Rechteck veraendert sichtbar seine Groesse

### Level 2: Rasenflaeche ohne Beet

Kontext: Der Garten ist rechteckig, darin liegt ein Beet.

Aufgabe:

- Gesamtflaeche berechnen
- Beetflaeche berechnen
- Beetflaeche abziehen

Mathematischer Fokus:

- zusammengesetzte Situation
- Subtraktion von Teilflaechen
- Einheiten `m²`

Interaktion:

- Beet kann innerhalb des Gartens verschoben werden
- App markiert Gesamtflaeche, Beet und Restflaeche unterschiedlich

### Level 3: Zaun oder Weg

Kontext: Um ein Beet oder den Garten soll eine Begrenzung angelegt werden.

Aufgabe:

- Umfang berechnen
- Materialbedarf abschaetzen

Mathematischer Fokus:

- Unterschied zwischen Flaeche und Umfang
- Einheit `m` statt `m²`

Interaktion:

- Lernende waehlen, ob Flaeche oder Rand gefragt ist
- Feedback erkennt typische Verwechslungen zwischen `m` und `m²`

### Level 4: Dreieckiges Blumenbeet

Kontext: Eine Ecke des Gartens wird als dreieckiges Blumenbeet gestaltet.

Aufgabe:

- Grundseite und Hoehe erkennen
- Dreiecksflaeche berechnen

Mathematischer Fokus:

- Dreieck als halbes Rechteck
- `A = (g * h) / 2`

Interaktion:

- Dreieck liegt sichtbar in einem Hilfsrechteck
- App kann das Hilfsrechteck ein- und ausblenden

### Level 5: Kleiner Stadtpark

Kontext: Aus dem Schulgarten wird eine kleine oeffentliche Gruenflaeche.

Aufgabe:

- mehrere Rechtecke und Dreiecke kombinieren
- Flaechen zerlegen
- sinnvolle Reihenfolge der Berechnung waehlen

Mathematischer Fokus:

- Strategie statt nur Formel
- Transfer vom Bild zur Formel
- Begruendung des Rechenwegs

Interaktion:

- Lernende waehlen Teilflaechen aus
- jede ausgewaehlte Teilflaeche bekommt passende Eingabefelder

## Interaktionsmodell

Die App sollte nicht nur Endergebnisse pruefen. Besser ist ein Rechenschritt-Modell:

- Schritt 1: "Welche Flaeche ist gesucht?"
- Schritt 2: "Welche Masse brauchst du?"
- Schritt 3: "Welche Formel passt?"
- Schritt 4: "Berechne die Teilflaeche."
- Schritt 5: "Was bedeutet das Ergebnis in der Situation?"

So kann Feedback genauer werden. Die App erkennt dann zum Beispiel:

- Ergebnis der Gesamtflaeche stimmt, aber die Teilflaeche wurde nicht abgezogen
- Flaeche und Umfang wurden verwechselt
- Formel passt, aber Einheit fehlt oder ist falsch
- Rechenweg ist richtig, aber Ergebnis hat einen Rechenfehler

## Empfehlung zur technischen Umsetzung

Ich wuerde fuer den ersten Prototyp eine Web-App empfehlen:

- React + TypeScript + Vite
- SVG fuer die interaktiven 2D-Flaechen
- Aufgaben als strukturierte Daten in TypeScript oder JSON
- lokaler Zustand im Browser, spaeter optional `localStorage`
- kein Backend im MVP
- Deployment spaeter als statische Website, zum Beispiel ueber Uni-Webspace, GitLab Pages oder einen einfachen Webserver

Warum diese Richtung sinnvoll ist:

- laeuft lokal und online
- gut praesentierbar im Browser
- keine Server- oder Login-Komplexitaet am Anfang
- SVG eignet sich gut fuer Rechtecke, Dreiecke, Beschriftungen, Markierungen und Drag-and-Drop
- React hilft, Level, Feedback und UI-Komponenten sauber zu strukturieren
- TypeScript reduziert Fehler bei Aufgabenlogik und Formeln

Moegliche Alternative:

- Reines HTML/CSS/JavaScript waere fuer einen sehr kleinen Demo-Prototyp einfacher, kann aber bei mehreren Leveln, Feedbacktypen und Interaktionen schnell unuebersichtlich werden.
- Canvas waere gut fuer freie grafische Effekte, ist aber fuer beschriftete Geometrie, Klickbereiche und Barrierefreiheit weniger angenehm als SVG.

## Grobe Produktstruktur

Startscreen:

- MathWorld-Karte
- Garten-Mission aktiv
- spaetere Bereiche wie Restaurant oder Stadt nur als Ausblick, nicht als echte Funktion

Missionsscreen:

- links oder zentral: Gartenvisualisierung
- rechts: aktuelle Aufgabe, Rechenschritte, Eingaben
- unten: Fortschritt und Navigation

Hilfesystem:

- erster Hinweis: lenkt auf die richtige Teilflaeche
- zweiter Hinweis: zeigt passende Formel
- dritter Hinweis: zeigt einen Zwischenschritt, aber nicht sofort die komplette Loesung

Feedback:

- richtig: positives Feedback und sichtbarer Fortschritt
- fast richtig: konkreter Hinweis zum fehlenden Schritt
- falsch: Diagnose, keine Bestrafung

## Projektdateien, die bald sinnvoll waeren

- `README.md`: Startanleitung, Ziel, lokales Ausfuehren
- `PROJECT_CONTEXT.md`: Zielgruppe, MVP, Entscheidungen
- `ROADMAP.md`: Meilensteine bis zur Abgabe
- `docs/didaktisches-konzept.md`: Lernziele, Aufgabentypen, Feedbacklogik
- `docs/entscheidungen.md`: Tech-Stack und Begruendungen
- `src/`: spaeter die eigentliche Anwendung

## Vorschlag fuer eure Besprechung am Montag

Entscheidungen, die ihr im Team klaeren solltet:

1. Zielgruppe: Bleibt es bei Klasse 5-7 oder soll es genauer werden?
2. Unterrichtssituation: Einzelarbeit, Partnerarbeit, Smartboard, Hausaufgabe oder Freiarbeit?
3. MVP-Erfolg: Was muss in der Demo unbedingt funktionieren?
4. Levelumfang: Lieber 3 sehr gute Level oder 6 grobe Level?
5. Spielgrad: Eher spielerische Mission mit Freischaltungen oder eher schulnahes interaktives Arbeitsblatt?
6. Feedbacktiefe: Nur richtig/falsch oder schrittweise Diagnose?
7. Technik: Hat das Team Erfahrung mit React/TypeScript oder waere ein einfacherer Einstieg sinnvoller?
8. Abgabe: Wird Code, Bericht, Praesentation, Video oder Live-Demo erwartet?
9. Evaluation: Wer testet den Prototypen und woran erkennt ihr, ob er didaktisch funktioniert?
10. Rollen: Wer uebernimmt Inhalt, UI, technische Umsetzung, Dokumentation und Tests?

## Naechster sinnvoller Schritt

Nach dem Montagstreffen sollte das Team den MVP schriftlich einfrieren:

- konkrete Zielgruppe
- 3-5 Level der Garten-Mission
- genaue Lernziele pro Level
- Interaktionen pro Level
- Feedbackregeln
- Tech-Stack
- Aufgabenverteilung

Danach kann die technische Projektstruktur angelegt werden. Der erste technische Meilenstein waere eine lokale Web-App mit einer einzigen funktionierenden Aufgabe: rechteckiger Garten, rechteckiges Beet, Eingabe der Rasenflaeche, Feedback und sichtbarer Fortschritt.
