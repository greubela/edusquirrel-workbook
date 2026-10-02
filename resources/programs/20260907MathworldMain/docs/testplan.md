# Manueller Regressionstestplan fuer MathWorld

Stand: 2026-06-25

Dieses Dokument beschreibt manuelle Regressionstests fuer den aktuellen MathWorld-MVP mit 8 Garten-Missionen. Die Tests pruefen bestehende Funktionen systematisch, ohne neue Funktionen einzubauen.

## Vorbereitung

| ID | Test | Voraussetzungen | Schritte | Erwartetes Ergebnis | Tatsaechliches Ergebnis | Status |
| --- | --- | --- | --- | --- | --- | --- |
| V-01 | Aktuellen Stand starten | Repository ist lokal vorhanden. | Aktuellen Stand holen, Abhaengigkeiten installieren, Entwicklungsserver starten. | Die App ist unter `http://127.0.0.1:5173/` erreichbar. | Anwendung startet erfolgreich und Mission 1 wird angezeigt. | Bestanden |
| V-02 | Build pruefen | Abhaengigkeiten sind installiert. | `npm run build` ausfuehren. | Der Build laeuft ohne Fehler durch. | `npm run build` laeuft erfolgreich durch. | Bestanden |

### Windows PowerShell

```powershell
npm.cmd install
npm.cmd run dev
```

### macOS Terminal

```bash
npm install
npm run dev
```

## Missionsreihenfolge

| Nummer | Mission | Schwerpunkt |
| --- | --- | --- |
| 1 | Zielflaeche fuellen | `1 x 1`-Quadrate, Snapping, Zielpruefung |
| 2 | Groessere Formen | Pflichtformen, feste Groessen |
| 3 | Vierecke bauen | Freie Rechteck-Eingabe, Drag-Erzeugung, Resize |
| 4 | Radieschen-Beet | Feste `1 x 1`-Formen, Radieschen-Belohnung |
| 5 | Karotten-Streifen | Feste Streifenwerkzeuge, Karotten-Belohnung |
| 6 | Gemuese-Mix-Beet | Pflichtformen `2 x 2`, `1 x 3`, `3 x 1` |
| 7 | Blumen-Ecke | Freie Rechtecke, mehrere Zerlegungen |
| 8 | Freies Gartenbeet | Freie Rechtecke, mehrere Loesungen, Abschlussmeldung |

## Missionstests

| ID | Test | Voraussetzungen | Schritte | Erwartetes Ergebnis | Tatsaechliches Ergebnis | Status |
| --- | --- | --- | --- | --- | --- | --- |
| M1-01 | Mission 1: `1 x 1`-Quadrate | Mission 1 ist aktiv. | `1 x 1`-Quadrate erzeugen, in die Zielflaeche ziehen und loslassen. | Formen snappen ins Raster und behalten feste Groesse. | Das 1-x-1-Werkzeug funktioniert und rastet korrekt im Raster ein. | Bestanden |
| M1-02 | Mission 1: Zielpruefung | Mission 1 ist aktiv. | Zielflaeche zunaechst unvollstaendig lassen, danach exakt fuellen. | Unvollstaendige Flaeche schliesst nicht ab; exakt gefuellte Flaeche schliesst ab. | Die Zielflaeche laesst sich vollstaendig fuellen und die Mission wird erfolgreich abgeschlossen. | Bestanden |
| M1-03 | Mission 1: feste Groesse und Blumen-Belohnung | Mission 1 ist abgeschlossen. | Vorgegebene `1 x 1`-Form auswaehlen und Erfolgszustand ansehen. | Die Form ist nicht resizebar und Blumen erscheinen in der Zielflaeche. | Die vorgegebene 1-x-1-Form ist nicht resizebar und die Blumen-Belohnung erscheint nach Abschluss. | Bestanden |
| M2-01 | Mission 2: Pflichtformen | Mission 2 ist freigeschaltet. | `2 x 2`, `1 x 3` und `3 x 2` passend platzieren. | Alle drei Pflichtformen werden erkannt und die Mission kann abgeschlossen werden. | Alle drei Pflichtformen wurden erkannt und die Mission wurde erfolgreich abgeschlossen. | Bestanden |
| M2-02 | Mission 2: Fehlerfaelle | Mission 2 ist aktiv. | Eine Pflichtform weglassen, Formen ueberlappen lassen und eine Form ausserhalb platzieren. | Fehlende Pflichtform, Ueberlappung und Form ausserhalb werden erkannt. | Fehlende Pflichtform, Ueberlappung und Form ausserhalb wurden korrekt erkannt. | Bestanden |
| M2-03 | Mission 2: feste Groessen | Eine Form aus Mission 2 ist ausgewaehlt. | Pruefen, ob Plus-/Minus-Resize fuer die Form angeboten wird. | Vorgegebene Werkzeugformen sind nicht resizebar. | Vorgegebene Werkzeugformen waren nicht resizebar. | Bestanden |
| M3-01 | Mission 3: freie Eingabe | Mission 3 ist freigeschaltet. | Werte fuer `Seite a` und `Seite b` eingeben und die Vorschau anklicken. | Ein passendes freies Rechteck wird erzeugt. | Freie Rechtecke konnten per Eingabe erzeugt werden. | Bestanden |
| M3-02 | Mission 3: Drag-Erzeugung | Mission 3 ist aktiv. | Direkt in der Arbeitsflaeche ein Rechteck per Drag erzeugen. | Das Rechteck entsteht mit ganzzahligen Seitenlaengen und snappt ins Raster. | Rechtecke konnten per Drag mit ganzzahligen Seitenlaengen erzeugt werden. | Bestanden |
| M3-03 | Mission 3: Resize erlaubt | Eine freie Form aus Mission 3 ist ausgewaehlt. | Plus-/Minus-Steuerung fuer `Seite a` und `Seite b` nutzen. | Freie Formen sind resizebar und bleiben innerhalb der erlaubten Grenzen. | Freie Formen waren resizebar und blieben innerhalb der erlaubten Grenzen. | Bestanden |
| M3-04 | Mission 3: verschiedene gueltige Zerlegungen | Mission 3 ist aktiv. | Die Zielflaeche mit unterschiedlichen passenden Rechteck-Zerlegungen fuellen. | Gueltige Zerlegungen werden akzeptiert, wenn die Zielflaeche exakt gefuellt ist. | Unterschiedliche gueltige Zerlegungen wurden bei exakt gefuellter Zielflaeche akzeptiert. | Bestanden |
| M4-01 | Mission 4: Radieschen-Beet | Mission 4 ist freigeschaltet. | Das `2 x 3`-Beet mit festen `1 x 1`-Formen fuellen. | Die Mission wird abgeschlossen; die Formen sind nicht resizebar. | Das Radieschen-Beet wurde mit festen `1 x 1`-Formen abgeschlossen. | Bestanden |
| M4-02 | Mission 4: Radieschen-Belohnung | Mission 4 ist abgeschlossen. | Erfolgszustand ansehen. | Radieschen erscheinen in der Zielflaeche. | Nach Abschluss erschienen Radieschen in der Zielflaeche. | Bestanden |
| M5-01 | Mission 5: Karotten-Streifen | Mission 5 ist freigeschaltet. | Den Streifen mit `1 x 4` plus `1 x 2` fuellen. | Die Mission wird abgeschlossen; die Werkzeuge bleiben fest. | Der Streifen wurde mit `1 x 4` plus `1 x 2` abgeschlossen; die Werkzeuge blieben fest. | Bestanden |
| M5-02 | Mission 5: Karotten-Belohnung | Mission 5 ist abgeschlossen. | Erfolgszustand ansehen. | Karotten erscheinen in der Zielflaeche. | Nach Abschluss erschienen Karotten in der Zielflaeche. | Bestanden |
| M6-01 | Mission 6: Gemuese-Mix-Beet | Mission 6 ist freigeschaltet. | `2 x 2`, `1 x 3` und `3 x 1` passend platzieren. | Alle Pflichtformen fuellen gemeinsam die Zielflaeche und die Mission schliesst ab. | Alle Pflichtformen fuellten gemeinsam die Zielflaeche und die Mission schloss ab. | Bestanden |
| M6-02 | Mission 6: Blumen-Belohnung | Mission 6 ist abgeschlossen. | Erfolgszustand ansehen. | Blumen erscheinen als Default-Belohnung. | Nach Abschluss erschienen Blumen als Default-Belohnung. | Bestanden |
| M7-01 | Mission 7: Blumen-Ecke | Mission 7 ist freigeschaltet. | Die L-Form mit freien Rechtecken fuellen. | Die Mission wird bei exakt gefuellter L-Form abgeschlossen. | Die L-Form wurde mit freien Rechtecken exakt gefuellt und abgeschlossen. | Bestanden |
| M7-02 | Mission 7: mindestens zwei Zerlegungen | Mission 7 ist aktiv. | Eine Loesung mit `4 x 2` und `2 x 3` testen, danach eine alternative Zerlegung testen. | Mindestens zwei verschiedene gueltige Zerlegungen funktionieren. | Eine Loesung mit `4 x 2` und `2 x 3` sowie eine alternative Zerlegung funktionierten. | Bestanden |
| M8-01 | Mission 8: Freies Gartenbeet | Mission 8 ist freigeschaltet. | Die Zielflaeche mit freien Rechtecken fuellen. | Die Mission wird bei exakt gefuellter Zielflaeche abgeschlossen. | Das freie Gartenbeet wurde mit freien Rechtecken exakt gefuellt und abgeschlossen. | Bestanden |
| M8-02 | Mission 8: mehrere Loesungen | Mission 8 ist aktiv. | Mindestens zwei unterschiedliche gueltige Zerlegungen testen. | Mehrere Loesungen werden akzeptiert. | Mindestens zwei unterschiedliche gueltige Zerlegungen wurden akzeptiert. | Bestanden |
| M8-03 | Mission 8: Abschlussmeldung | Mission 8 ist abgeschlossen. | Fortschritt und TopBar ansehen. | Fortschritt zeigt `8 von 8`; die Meldung `Alle Gartenbeete sind bepflanzt! Dein Garten ist fertig.` erscheint. | Fortschritt zeigte `8 von 8` und die Abschlussmeldung erschien. | Bestanden |

## Gemeinsame Regressionstests

| ID | Test | Voraussetzungen | Schritte | Erwartetes Ergebnis | Tatsaechliches Ergebnis | Status |
| --- | --- | --- | --- | --- | --- | --- |
| R-01 | Freischaltung in Reihenfolge | Neuer App-Start oder Neustart der Seite. | Missionen nacheinander pruefen, bevor die vorherige Mission abgeschlossen ist. | Zukuenftige Missionen bleiben gesperrt, bis die vorherige Mission abgeschlossen wurde. | Missionen wurden in Reihenfolge freigeschaltet; zukuenftige Missionen blieben gesperrt. | Bestanden |
| R-02 | Fortschrittsanzeige | Mehrere Missionen werden nacheinander abgeschlossen. | Nach jedem Abschluss die TopBar pruefen. | Der Fortschritt steigt schrittweise bis `8 von 8 Gartenbeeten bepflanzt`. | Die Fortschrittsanzeige stieg schrittweise bis `8 von 8 Gartenbeeten bepflanzt`. | Bestanden |
| R-03 | Badge `bepflanzt` | Mindestens eine Mission wurde abgeschlossen. | Missionsliste ansehen. | Abgeschlossene Missionen zeigen den Badge `bepflanzt`. | Abgeschlossene Missionen zeigten den Badge `bepflanzt`. | Bestanden |
| R-04 | Erneutes Oeffnen abgeschlossener Missionen | Mindestens eine Mission wurde abgeschlossen. | Eine abgeschlossene Mission in der Missionsliste anklicken. | Die Mission oeffnet sich erneut, bleibt mit `bepflanzt` markiert und ist spielbar. | Abgeschlossene Missionen liessen sich erneut oeffnen und blieben spielbar markiert. | Bestanden |
| R-05 | Replay mit leerem Workspace | Eine abgeschlossene Mission wird erneut geoeffnet. | Arbeitsflaeche und Feedback pruefen. | Der Workspace startet leer; der Hinweis `Dieses Beet ist schon bepflanzt. Du kannst es hier noch einmal ausprobieren.` erscheint. | Beim Replay startete der Workspace leer und der Hinweis wurde angezeigt. | Bestanden |
| R-06 | Feste versus freie Groessenaenderung | Eine feste Werkzeugform und eine freie Form sind jeweils ausgewaehlt. | Resize-Steuerung bei beiden Formtypen pruefen. | Feste Werkzeugformen sind nicht resizebar; freie Formen sind resizebar. | Feste Werkzeugformen waren nicht resizebar; freie Formen waren resizebar. | Bestanden |
| R-07 | Pflanzen-Belohnungen | Missionen 1 bis 8 werden abgeschlossen. | Belohnung nach jeder Mission ansehen. | Mission 4 zeigt Radieschen, Mission 5 Karotten, alle anderen Missionen Blumen. | Mission 4 zeigte Radieschen, Mission 5 Karotten und alle anderen Missionen Blumen. | Bestanden |
| R-08 | Abschlussmeldung bei `8 von 8` | Alle 8 Missionen sind abgeschlossen. | TopBar ansehen und abgeschlossene Mission erneut oeffnen. | Die Abschlussmeldung bleibt sichtbar. | Die Abschlussmeldung blieb bei `8 von 8` sichtbar. | Bestanden |
| R-09 | Neustarten | Eine Mission ist teilweise bearbeitet. | `Neustarten` ausloesen. | Das aktuelle Level wird geleert; bei Varianten-Missionen wird eine neue Variante moeglich. | `Neustarten` leerte das aktuelle Level; Varianten-Missionen konnten neu starten. | Bestanden |
| R-10 | Formen ausserhalb der Zielflaeche | Eine Mission ist aktiv. | Eine Form ausserhalb oder teilweise ausserhalb der Zielflaeche platzieren. | Die Mission schliesst nicht ab und zeigt passendes Feedback. | Formen ausserhalb der Zielflaeche wurden abgelehnt und passend gemeldet. | Bestanden |
| R-11 | Ueberlappung | Eine Mission ist aktiv. | Zwei Formen auf dasselbe Ziel-Feld legen. | Die Mission schliesst nicht ab und meldet eine Ueberlappung. | Ueberlappungen verhinderten den Abschluss und wurden gemeldet. | Bestanden |
| R-12 | Freie Zielzellen | Eine Mission ist aktiv. | Mindestens eine Zielzelle leer lassen. | Die Mission schliesst nicht ab und zeigt den unvollstaendigen Fortschritt. | Freie Zielzellen verhinderten den Abschluss und der Fortschritt blieb unvollstaendig. | Bestanden |

## Ergebnisprotokoll

| Datum | Bereich | Beobachtung | Erwartetes Verhalten | Status |
| --- | --- | --- | --- | --- |
| 2026-06-08 | Mission 1 | `1 x 1`-Quadrate, Snapping, Zielpruefung, Feedback und `Naechstes Level` funktionieren. | Mission 1 laesst sich erfolgreich abschliessen. | Bestanden |
| 2026-06-08 | Mission 2 | Pflichtformen `2 x 2`, `1 x 3` und `3 x 2`, Snapping, Zielpruefung, Feedback und `Naechstes Level` funktionieren. | Mission 2 laesst sich erfolgreich abschliessen. | Bestanden |
| 2026-06-08 | Mission 3 | Eigene Rechtecke, freie Erzeugung, Snapping, Zielpruefung und Feedback funktionieren. | Mission 3 laesst sich bei exakt gefuellter Zielflaeche erfolgreich abschliessen. | Bestanden |
| 2026-06-08 | Mission 3 Negativtest | Ein zu grosses Rechteck ausserhalb der Zielflaeche erzeugt korrekt die Meldung `Eine Form liegt ausserhalb der Zielflaeche`. | Fehlerhafte Platzierung wird erkannt und passend gemeldet. | Bestanden |
