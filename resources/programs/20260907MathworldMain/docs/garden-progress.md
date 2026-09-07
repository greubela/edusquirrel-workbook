# Garten-Fortschritt und Belohnungskonzept

Stand: 2026-06-23

Dieses Dokument beschreibt ein kindgerechtes Fortschritts- und Belohnungssystem fuer MathWorld. Es ist ein fachliches Konzept und fuehrt noch keine App-Code-Aenderung ein.

Ergaenzend beschreibt `docs/season-mission-concept.md` eine groessere Saison-Struktur, in der eine Mission einer Gartenwoche entspricht und Tagesaufgaben ueber einen Sonnenzyklus sichtbar gemacht werden.

## Ziel des Fortschrittssystems

Das Fortschrittssystem soll Kindern zeigen, dass sie den MathWorld-Garten Schritt fuer Schritt bepflanzen. Jede geloeste Mission macht einen sichtbaren Teil des Gartens lebendiger. Der Fokus liegt auf Motivation, Orientierung und Freude am Weiterprobieren.

Wichtig:

- Fortschritt funktioniert ohne Strafe.
- Es gibt keinen Zeitdruck.
- Fehler sind Teil des Ausprobierens.
- Zunaechst gibt es kein kompliziertes Punktesystem.
- Belohnungen sollen sichtbar, freundlich und leicht verstaendlich sein.

## Was Kinder nach einem gelösten Level sehen

Nach einem geloesten Level sollen Kinder direkt sehen, dass ihre Arbeit erfolgreich war:

- Die korrekt belegten Felder zeigen passende Pflanzen.
- Eine kurze Erfolgsmeldung bestaetigt die Loesung.
- Das naechste Level wird erreichbar.
- Ein Bereich im Garten gilt als bepflanzt oder freigeschaltet.

Die Belohnung soll nicht nur als Text erscheinen, sondern im Garten sichtbar werden. Dadurch entsteht das Gefuehl: `Ich habe wirklich etwas gebaut.`

## Gartenkarte mit freischaltbaren Bereichen

Langfristig kann es eine einfache Gartenkarte geben. Jeder Gartenbereich steht fuer eine Mission oder eine kleine Levelgruppe.

Beispiele fuer Bereiche:

- Blumenbeet
- Gemueseecke
- Radieschen-Beet
- Karottenreihe
- Freies Gartenbeet

Nach jeder geloesten Mission wird ein Bereich sichtbar freigeschaltet oder als bepflanzt markiert. Gesperrte Bereiche sollen freundlich wirken, zum Beispiel als leere Erde oder noch geschlossener Gartenzaun, nicht als harte Fehlermeldung.

## Sterne-System

Ein einfaches Sterne-System kann spaeter zusaetzliche Motivation geben. Fuer den ersten Ausbau reicht aber eine sehr einfache Variante:

- 1 Stern: Mission geloest.
- 2 Sterne: Mission ohne viele Korrekturen geloest.
- 3 Sterne: besonders schoene oder passende Zerlegung.

Fuer den naechsten kleinen Schritt ist noch kein Sterne-System noetig. Es sollte zuerst nur dokumentiert bleiben, damit die App nicht zu frueh kompliziert wird.

## Erntekorb oder Pflanzensammlung

Ein Erntekorb oder eine Pflanzensammlung kann zeigen, welche Pflanzen Kinder schon freigeschaltet haben.

Moegliche Eintraege:

- Blumen
- Radieschen
- Karotten
- spaeter weitere Gemuese- oder Blumenarten

Die Sammlung soll kein Wettbewerbssystem sein. Sie zeigt ruhig und positiv: `Diese Pflanzen hast du schon geschafft.`

## Belohnungen pro Mission

| Mission | Belohnung |
| --- | --- |
| Mission 1: Zielflaeche fuellen | Blumen |
| Mission 2: Groessere Formen | Blumen |
| Mission 3: Vierecke bauen | Blumen |
| Radieschen-Beet | Radieschen |
| Karotten-Streifen | Karotten |

Mission 1 bis 3 nutzen Blumen als neutrale erste Belohnung. Die spaeteren Garten-Level bekommen passendere Pflanzenarten.

## Beispielhafter Fortschritt für Mission 1 bis 5

| Schritt | Zustand im Garten |
| --- | --- |
| Vor Mission 1 | Der Garten ist leer, einige Beete sind noch Erde. |
| Nach Mission 1 | Das erste Blumenbeet blueht. |
| Nach Mission 2 | Ein weiteres Blumenbeet ist bepflanzt. |
| Nach Mission 3 | Die Blumen-Ecke oder ein freies Beet ist sichtbar fertig. |
| Nach Radieschen-Beet | Radieschen erscheinen im Gemuese-Bereich. |
| Nach Karotten-Streifen | Eine Karottenreihe wird sichtbar bepflanzt. |

Ein moeglicher Fortschrittstext waere:

```text
3 von 5 Gartenbeeten bepflanzt
```

## Kindgerechte Texte

Moegliche Texte nach Erfolg:

- `Super, dein Beet ist bepflanzt!`
- `Die Blumen bluehen.`
- `Die Radieschen sind fertig gepflanzt.`
- `Die Karottenreihe sieht prima aus.`
- `Du hast wieder ein Stueck Garten geschafft.`

Moegliche Texte fuer Fortschritt:

- `2 von 5 Gartenbeeten bepflanzt`
- `Noch ein Beet wartet auf dich.`
- `Dein Garten waechst weiter.`

Die Texte sollen freundlich, kurz und konkret sein.

## Minimaler erster UI-Schritt

Der kleinste spaetere Code-Schritt waere eine einfache Fortschrittsanzeige, zum Beispiel:

```text
3 von 5 Gartenbeeten bepflanzt
```

Diese Anzeige koennte auf der bestehenden Anzahl geloester Missionen basieren. Sie braucht noch keine Gartenkarte, keine Animation und kein Punktesystem. Sie wuerde nur sichtbar machen, wie viele Missionen bereits geschafft wurden.

## Spätere Erweiterungen

Spaeter koennten ergaenzt werden:

- eine kleine Gartenkarte mit freigeschalteten Bereichen;
- eine Pflanzensammlung;
- einfache Sterne pro Mission;
- unterschiedliche Belohnungen fuer weitere Level;
- ein Abschlussbild, wenn alle Beete bepflanzt sind;
- optionale Hinweise, welche Pflanzen als naechstes freigeschaltet werden.

## Offene Fragen

- Soll der Fortschritt pro Browser-Sitzung gespeichert werden oder nur waehrend des aktuellen Spiels gelten?
- Soll ein geloestes Level dauerhaft als bepflanzt gelten, auch wenn es spaeter neu gestartet wird?
- Soll die Gartenkarte sofort sichtbar sein oder erst nach mehreren Missionen?
- Sollen Sterne nur angezeigt werden oder spaeter echte Zusatzkriterien haben?
- Wie viele Gartenbereiche soll die erste Version der Fortschrittsanzeige zeigen?
