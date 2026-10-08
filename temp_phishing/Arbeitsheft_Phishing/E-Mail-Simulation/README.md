# E-Mail Simulator (Python-Original)

Diese Anleitung gehört zur mitgelieferten Python-Version. Der neue digitale Simulator läuft im Browser ohne Python oder Mailversand; siehe [Email-Simulator-Dokumentation](../../../docs/email-simulator.md). Die Originaldateien bleiben als Unterrichtsmaterial und Datenquelle erhalten.

Simulation eines E-Mail-Postfach, in dem die Erkennung von Phishing-Nachrichten geübt werden kann. Bestandteil des Arbeitsheftes "Phishing entlarven!"

## Voraussetzungen
- Python 3.10 oder neuer installiert (https://www.python.org/downloads/)

## Installation & Start
1. Terminal im Projektordner öffnen
2. Virtuelle Umgebung anlegen und aktivieren:

   macOS/Linux:
     python3 -m venv venv
     source venv/bin/activate

   Windows:
     py -3 -m venv venv
     venv\Scripts\activate.bat

3. Abhängigkeiten installieren:
     pip install -r requirements.txt

4. Programm starten:
     python E-Mail-Postfach.py