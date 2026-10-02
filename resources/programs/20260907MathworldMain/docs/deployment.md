# Deployment-Optionen fuer eine statische Demo

Stand: 2026-06-09

Dieses Dokument sammelt moegliche Wege, um den aktuellen MathWorld-Prototypen als statische Demo zu zeigen. Es beschreibt nur Optionen und trifft noch keine finale Entscheidung. Es werden keine Deployment-Konfigurationen angelegt.

## Ausgangspunkt

MathWorld ist aktuell eine React/Vite/TypeScript-Web-App ohne Backend. Der Prototyp laeuft lokal im Browser und kann mit Vite als statische Anwendung gebaut werden.

Wichtige Befehle:

```bash
npm install
npm run dev
npm run build
npm run preview
```

Unter Windows PowerShell koennen stattdessen die `npm.cmd`-Befehle genutzt werden:

```powershell
npm.cmd install
npm.cmd run dev
npm.cmd run build
npm.cmd run preview
```

## Option 1: Lokaler Vite-Preview

Der lokale Vite-Preview ist die einfachste Option fuer eine Demo auf einem einzelnen Rechner.

Ablauf:

1. Abhaengigkeiten installieren.
2. Statischen Build erzeugen.
3. Build lokal mit Vite Preview anzeigen.

Beispiel macOS:

```bash
npm install
npm run build
npm run preview
```

Beispiel Windows PowerShell:

```powershell
npm.cmd install
npm.cmd run build
npm.cmd run preview
```

Vorteile:

- sehr schnell fuer lokale Vorfuehrungen
- keine Server- oder GitLab-Pages-Konfiguration noetig
- gut geeignet fuer Tests vor einer Praesentation

Nachteile:

- Demo laeuft nur auf dem eigenen Rechner
- Rechner muss waehrend der Vorfuehrung vorbereitet sein
- nicht als Link fuer andere verfuegbar

## Option 2: GitLab Pages

GitLab Pages koennte spaeter genutzt werden, um die statische Demo als Weblink bereitzustellen. Dafuer waere eine GitLab-CI-Konfiguration noetig, die den Vite-Build erzeugt und den erzeugten `dist`-Ordner als Pages-Artefakt veroeffentlicht.

Vorteile:

- Demo ist als Link erreichbar
- gut fuer Review, Abgabe oder Praesentation
- passt zum bestehenden GitLab-Repository

Nachteile:

- benoetigt eine spaetere CI-/Pages-Konfiguration
- kann je nach GitLab-Umgebung der Uni zusaetzliche Rechte oder Einstellungen brauchen
- Pfad-/Base-URL-Fragen muessen fuer Vite sauber geprueft werden

Offene Pruefpunkte:

- Ist GitLab Pages im HU-GitLab-Projekt aktiviert?
- Welche URL wuerde GitLab Pages fuer das Projekt erzeugen?
- Muss in Vite ein spezieller `base`-Pfad gesetzt werden?
- Soll die Pages-Konfiguration erst nach Review des MVP angelegt werden?

## Option 3: Manuelles Build-Artefakt

Eine weitere einfache Option ist ein manuell erzeugter Build. Dabei wird lokal `npm run build` ausgefuehrt und der erzeugte `dist`-Ordner als Artefakt weitergegeben, zum Beispiel als ZIP-Datei.

Vorteile:

- keine CI-Konfiguration noetig
- gut fuer schnelle Weitergabe an Teammitglieder oder Lehrende
- funktioniert unabhaengig von GitLab Pages

Nachteile:

- manuelle Schritte sind fehleranfaelliger
- Artefakt kann veralten, wenn der Code weiterentwickelt wird
- Empfaenger brauchen trotzdem eine Moeglichkeit, die statischen Dateien korrekt auszuliefern

Hinweis:

Die Dateien aus `dist` sollten nicht einfach dauerhaft ins Repository committed werden, solange das Team nicht ausdruecklich entscheidet, Build-Artefakte versioniert abzulegen.

## Vorlaeufige Empfehlung

Fuer die naechste Demo ist der lokale Vite-Preview wahrscheinlich ausreichend. GitLab Pages ist die sinnvollste spaetere Option, wenn das Team einen stabilen Link fuer Review oder Abgabe braucht. Ein manuelles Build-Artefakt kann als einfache Zwischenloesung genutzt werden, sollte aber nicht zur dauerhaften Hauptstrategie werden.

## Lokaler Test am 2026-06-09

Der statische Build und die lokale Preview wurden am 2026-06-09 erfolgreich getestet.

Getestete Befehle:

```bash
npm run build
npm run preview
```

Ergebnis:

- Der Build wurde erfolgreich erzeugt.
- Die statische Preview startet lokal.
- Die App ist im Browser erreichbar.

## Offene Entscheidung

Das Team sollte klaeren:

- Reicht eine lokale Demo fuer die naechste Praesentation?
- Soll ein oeffentlich oder intern erreichbarer Link bereitgestellt werden?
- Wer prueft, ob GitLab Pages im HU-GitLab-Projekt verfuegbar ist?
- Wann soll eine echte Deployment-Konfiguration ins Repository aufgenommen werden?
