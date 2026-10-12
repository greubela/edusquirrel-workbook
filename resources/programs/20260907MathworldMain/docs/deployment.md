# MathWorld bereitstellen

## EduSquirrel GitHub Pages

Diese Kopie wird zusammen mit EduSquirrel veröffentlicht. Der Workflow [scala.yml](../../../../.github/workflows/scala.yml) baut auf Pushes nach `main` den Scala.js-Client und -Worker sowie MathWorld:

```sh
cd resources/programs/20260907MathworldMain
npm ci
npm run build -- --base=./
```

Diese Befehle werden vom EduSquirrel-Repository aus begonnen. Der relative Vite-Basispfad ist erforderlich, weil MathWorld unter `resources/programs/20260907MathworldMain/dist/` ausgeliefert wird. Anschließend führt der Workflow im Repository-Hauptverzeichnis `node tools/dev/assemble-pages.mjs` aus und veröffentlicht `_site/`. Das Assembly-Skript benötigt bereits erzeugte Client-/Worker-Artefakte und MathWorlds `dist/index.html`.

Ein eigener GitLab-Pages-Workflow wird für diese Kopie nicht verwendet. Die GitLab-Adressen in den ursprünglichen Setup- und Teamdokumenten beziehen sich auf das Herkunftsprojekt.

## Lokale Vorschau

Im MathWorld-Verzeichnis:

```sh
npm ci
npm run build -- --base=./
npm run preview
```

Unter Windows kann `npm.cmd` verwendet werden. Die Vorschau-URL wird von Vite ausgegeben. Die Anwendung benötigt kein Backend; Fortschritt wird im jeweiligen Browser gespeichert. Ein erfolgreicher Build ersetzt keinen manuellen Durchlauf der Missionen.

Für die gesamte EduSquirrel-Deployment-Struktur müssen danach beide Scala.js-Artefakte und der MathWorld-Build vorhanden sein. Im Repository-Hauptverzeichnis:

```sh
node tools/dev/assemble-pages.mjs
npm run preview
```

Das Assembly-Skript ersetzt `_site/`. Build-Artefakte werden aus Quellen erzeugt und nicht als Quelldateien bearbeitet.
