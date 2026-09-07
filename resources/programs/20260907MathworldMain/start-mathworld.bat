@echo off
setlocal

cd /d "%~dp0"

echo MathWorld wird gestartet...

where npm.cmd >nul 2>nul
if errorlevel 1 (
  echo.
  echo Fehler: npm.cmd wurde nicht gefunden.
  echo Bitte Node.js installieren und danach diese Datei erneut starten.
  echo.
  pause
  exit /b 1
)

if not exist "node_modules\" (
  echo.
  echo Abhaengigkeiten werden installiert...
  npm.cmd install
  if errorlevel 1 (
    echo.
    echo Installation fehlgeschlagen.
    echo.
    pause
    exit /b 1
  )
)

echo.
echo Entwicklungsserver startet. Das Browserfenster oeffnet sich automatisch.
echo Dieses Fenster geoeffnet lassen, solange MathWorld laufen soll.
echo Beenden mit Strg+C.
echo.

npm.cmd run dev -- --open

if errorlevel 1 (
  echo.
  echo MathWorld konnte nicht gestartet werden.
  echo.
  pause
  exit /b 1
)

echo.
echo MathWorld wurde beendet.
echo.
pause
