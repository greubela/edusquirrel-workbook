# Setup fuer Mitarbeitende

Diese Anleitung richtet das Projekt lokal auf einem Windows-Rechner ein. Das Repository enthaelt inzwischen ein erstes React/Vite/TypeScript-Grundgeruest fuer die Web-App. Es gibt keine Python-Umgebung und keine Ruff-Konfiguration.

## 1. Benoetigte Programme

| Programm | Rolle im Projekt | Jetzt noetig? |
| --- | --- | --- |
| Git for Windows | Versionsverwaltung: `clone`, `pull`, `commit`, `push` | Ja |
| OpenSSH Client | GitLab-Zugriff per SSH-Key | Ja, wenn SSH genutzt wird |
| PowerShell | Befehle aus dieser Anleitung ausfuehren | Ja auf Windows |
| Webbrowser | GitLab-Login, SSH-Key eintragen, Merge Requests | Ja |
| Code-Editor | Dateien bearbeiten, empfohlen: Visual Studio Code | Ja |
| Node.js und npm | Web-App installieren, starten und bauen | Ja |
| Python | Spaeter eventuell fuer Hilfsskripte oder Backend | Noch nicht |
| Ruff | Python-Linter/Formatter fuer Code-Checks | Noch nicht |
| ChatGPT/Codex | KI-Unterstuetzung mit `KI_BASISPROMPT.md` | Optional |

Kurz gesagt: Fuer den Start der Web-App braucht ihr Git, Node.js/npm, einen Browser und einen Editor.

Aktuell geprueft auf Stephans Rechner: Git `2.52.0.windows.1`, OpenSSH `9.5p2`, Node.js `24.15.0`, npm `11.12.1`. Python und Ruff sind aktuell nicht installiert und im Repository nicht konfiguriert.

Nach dem Klonen bitte auch `CONTRIBUTING.md` lesen. Dort stehen die gemeinsamen Arbeitsregeln, insbesondere die Sprachregeln fuer Code, Dokumentation und KI-Chats.

## 2. Git einmalig einrichten

PowerShell oeffnen und pruefen, ob Git installiert ist:

```powershell
git --version
ssh -V
```

Wenn `git` nicht gefunden wird, Git for Windows installieren:

```text
https://git-scm.com/download/win
```

Danach die eigene Commit-Identitaet setzen:

```powershell
git config --global user.name "Vorname Nachname"
git config --global user.email "deine.email@example.de"
git config --global init.defaultBranch main
```

Die E-Mail sollte am besten zur GitLab-/Uni-Adresse passen.

## 3. Variante A: Repository per HTTPS klonen

Das ist der einfachste Weg, wenn GitLab HTTPS bei euch ohne Probleme akzeptiert.

```powershell
cd $HOME\Desktop
git clone https://scm.cms.hu-berlin.de/schrecks/mathworld.git
cd mathworld
git status
```

Wenn Git nach Zugangsdaten fragt, mit dem HU-GitLab-Account anmelden. Je nach GitLab-Konfiguration kann statt des normalen Passworts ein Personal Access Token noetig sein.

Wenn HTTPS mit einer ungewoehnlichen Fehlermeldung scheitert, zum Beispiel wegen Login-, Cookie- oder Schutzseiten, nutzt Variante B mit SSH.

## 4. Variante B: Repository per SSH klonen

SSH ist meistens die stabilere Variante fuer regelmaessige Arbeit mit GitLab.

Zuerst einen SSH-Key erzeugen:

```powershell
ssh-keygen -t ed25519 -C "deine.email@example.de"
```

Bei der Frage nach dem Speicherort einfach `Enter` druecken. Bei der Passphrase kann man ebenfalls `Enter` druecken oder eine eigene Passphrase setzen.

Dann den Public Key in die Zwischenablage kopieren:

```powershell
Get-Content "$env:USERPROFILE\.ssh\id_ed25519.pub" | Set-Clipboard
```

In GitLab eintragen:

1. `https://scm.cms.hu-berlin.de` oeffnen
2. Oben rechts auf Avatar / Profil klicken
3. **Edit profile** oder **Preferences** oeffnen
4. Links zu **Access > SSH keys**
5. **Add new key**
6. Public Key einfuegen
7. Titel setzen, zum Beispiel `MathWorld Windows Laptop`
8. Speichern

SSH-Verbindung testen:

```powershell
ssh -T git@scm.cms.hu-berlin.de
```

Erwartete Ausgabe:

```text
Welcome to GitLab, @dein-benutzername!
```

Danach klonen:

```powershell
cd $HOME\Desktop
git clone git@scm.cms.hu-berlin.de:schrecks/mathworld.git
cd mathworld
git status
```

## 5. Von HTTPS auf SSH umstellen

Falls das Repository schon per HTTPS geklont wurde, kann die Remote-URL spaeter auf SSH umgestellt werden:

```powershell
git remote -v
git remote set-url origin git@scm.cms.hu-berlin.de:schrecks/mathworld.git
git remote -v
```

## 6. Normaler Arbeitsablauf

Vor jeder Arbeit:

```powershell
git status
git pull
```

Neue Aenderung in einem eigenen Branch vorbereiten:

```powershell
git checkout -b feature/kurzer-name
```

Aenderungen speichern:

```powershell
git status
git add .
git commit -m "Kurze Beschreibung der Aenderung"
git push -u origin feature/kurzer-name
```

Danach in GitLab einen Merge Request erstellen.

## 7. Haeufige Probleme

`Permission denied (publickey).`

Der SSH-Key ist noch nicht im GitLab-Account hinterlegt oder Git nutzt den falschen Key.

`Updates were rejected because the remote contains work that you do not have locally.`

Vor dem Push zuerst `git pull` ausfuehren. Kein `force push` verwenden, ausser das Team hat das ausdruecklich abgesprochen.

`dubious ownership in repository`

Das lokale Repository gehoert einem anderen Windows-Benutzer. Das hat nichts mit dem GitLab-Account zu tun. In diesem Fall nicht einfach blind Befehle kopieren, sondern kurz im Team fragen.

`python` oder `ruff` wird nicht gefunden.

Das ist im aktuellen Projektstand in Ordnung. Diese Tools sind noch keine Pflicht.

`npm` wird in PowerShell wegen der Ausfuehrungsrichtlinie blockiert.

Unter Windows kann statt `npm` der Befehl `npm.cmd` genutzt werden:

```powershell
npm.cmd install
npm.cmd run dev
npm.cmd run build
```

## 8. Web-App lokal starten

Im Projektordner:

```powershell
npm.cmd install
npm.cmd run dev
```

Der Entwicklungsserver zeigt danach eine lokale URL an, normalerweise:

```text
http://127.0.0.1:5173/
```

Vor einem Merge sollte mindestens der Build laufen:

```powershell
npm.cmd run build
```
