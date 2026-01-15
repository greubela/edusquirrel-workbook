/*
 * Girls Day - Automatische Pflanzen-Bewässerung
 * Kompletter Arduino Sketch (Referenz)
 * 
 * Dieser Code zeigt, wie das fertige Programm aussehen sollte,
 * wenn alle Module im Workshop durchlaufen wurden.
 */

// Pin-Definitionen
#define MOISTURE_PIN 8     // Digitaler Pin für Feuchtigkeitssensor (DO)
#define PUMP_PIN 7         // Digitaler Pin für Relais (Pumpe)

// Timing-Parameter
#define PUMP_DURATION 2000     // Pumpe läuft 2 Sekunden
#define CHECK_INTERVAL 10000   // Alle 10 Sekunden prüfen (zum Testen)

void setup() {
  // Serial Monitor für Debugging
  Serial.begin(9600);
  
  // Pin-Modi setzen
  pinMode(MOISTURE_PIN, INPUT);
  pinMode(PUMP_PIN, OUTPUT);
  digitalWrite(PUMP_PIN, LOW);  // Pumpe initial aus
  
  Serial.println("=== Automatische Bewässerung gestartet ===");
  Serial.println("Sensor: DO-Pin (HIGH = trocken, LOW = feucht)");
  Serial.println();
}

void loop() {
  // --- MODUL 2: Feuchtigkeit prüfen ---
  int sensorValue = digitalRead(MOISTURE_PIN);
  
  // Ausgabe auf Serial Monitor
  Serial.print("Sensor-Wert: ");
  if (sensorValue == HIGH) {
    Serial.println("HIGH (Boden ist TROCKEN)");
  } else {
    Serial.println("LOW (Boden ist FEUCHT)");
  }
  
  // --- MODUL 4: Entscheidung treffen ---
  if (sensorValue == HIGH) {
    // Trocken! Bewässerung starten
    Serial.println(">>> BODEN TROCKEN! Bewässerung wird gestartet...");
    
    // --- MODUL 3: Pumpe steuern ---
    digitalWrite(PUMP_PIN, HIGH);  // Pumpe EIN
    delay(PUMP_DURATION);           // Pumpen-Dauer
    digitalWrite(PUMP_PIN, LOW);   // Pumpe AUS
    
    Serial.println(">>> Bewässerung abgeschlossen!");
  } else {
    Serial.println(">>> Boden feucht genug - keine Bewässerung nötig");
  }
  
  Serial.println();
  
  // Warten bis zur nächsten Messung
  delay(CHECK_INTERVAL);
}

/*
 * BONUS-AUFGABEN (Erweiterte Version)
 */

// --- BONUS 1: LED blinken während Pumpen ---
/*
#define LED_PIN 13

void setup() {
  // ... wie oben ...
  pinMode(LED_PIN, OUTPUT);
}

void pumpWithLED() {
  Serial.println(">>> Pumpe läuft - LED blinkt...");
  unsigned long startTime = millis();
  while (millis() - startTime < PUMP_DURATION) {
    digitalWrite(PUMP_PIN, HIGH);
    digitalWrite(LED_PIN, HIGH);
    delay(250);
    digitalWrite(LED_PIN, LOW);
    delay(250);
  }
  digitalWrite(PUMP_PIN, LOW);
}
*/

// --- BONUS 2: Wassertank-Füllstand prüfen ---
/*
#define WATER_LEVEL_PIN 9  // Digitaler Pin für Füllstand-Sensor

bool isWaterTankEmpty() {
  int level = digitalRead(WATER_LEVEL_PIN);
  return level == LOW;  // LOW = Tank leer
}

// Im loop() vor dem Pumpen:
if (sensorValue == HIGH) {
  if (isWaterTankEmpty()) {
    Serial.println("⚠️ WARNUNG: Wassertank ist leer!");
  } else {
    // Pumpen...
  }
}
*/

// --- BONUS 3: Mehrfarbige LED-Anzeige ---
/*
#define LED_RED 9
#define LED_GREEN 10

void showMoistureStatus(int sensorValue) {
  // Alle LEDs aus
  digitalWrite(LED_RED, LOW);
  digitalWrite(LED_GREEN, LOW);
  
  // Je nach Feuchtigkeit LED anschalten
  if (sensorValue == HIGH) {
    digitalWrite(LED_RED, HIGH);     // Rot: trocken
  } else {
    digitalWrite(LED_GREEN, HIGH);   // Grün: feucht
  }
}

// Im loop() aufrufen:
showMoistureStatus(sensorValue);
*/

/*
 * HINWEISE FÜR LEHRKRÄFTE:
 * 
 * 1. Sensor-Typ:
 *    - Dieser Code verwendet einen DIGITALEN Feuchtigkeitssensor (DO-Ausgang)
 *    - DO = Digital Output: HIGH wenn trocken, LOW wenn feucht
 *    - Anschlüsse: VCC (3.3V), GND, DO (Pin 8)
 * 
 * 2. Sicherheit:
 *    - Relais immer mit separatem Netzteil betreiben
 *    - Niemals Arduino-Pins direkt an Pumpe anschließen
 *    - Darauf achten, dass Wasser nicht auf elektronische Bauteile gelangt
 * 
 * 3. Debugging:
 *    - Serial Monitor öffnen (9600 Baud)
 *    - Messwerte live beobachten (HIGH/LOW)
 *    - Bei Tests CHECK_INTERVAL kürzer setzen (z.B. 5000 = 5 Sekunden)
 * 
 * 4. Anpassungen:
 *    - PUMP_DURATION: Je nach Pumpenleistung und Pflanzengröße anpassen
 *    - CHECK_INTERVAL: Für Tests kurz (5-10 Sekunden), später länger (1-5 Minuten)
 *    - Manche Sensoren haben einen einstellbaren Potentiometer für die Schwelle
 */
