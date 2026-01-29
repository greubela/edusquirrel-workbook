/*
 * Girls Day - Automatische Pflanzen-Bewässerung
 * Kompletter Arduino Code
 * 
 * Dieser Code zeigt, wie das fertige Programm aussehen sollte,
 * wenn alle Module im Workshop durchlaufen wurden.
 */

const int SENSOR_PIN = A0;  // Analog Pin 0 zu DO
const int SENSOR_POWER_PIN = 2; // Pin, damit der Sensor nicht so schnell rostet
const int PUMP_PIN = 8; // Pin fürs relais für die pumpe

int feuchtigkeitsGrenze = 100;


void setup() {
  Serial.begin(9600);

  pinMode(PUMP_PIN, OUTPUT);
  pinMode(SENSOR_POWER_PIN, OUTPUT);
  
  digitalWrite(PUMP_PIN, HIGH);
  digitalWrite(SENSOR_POWER_PIN, LOW);
}

void loop() {

  digitalWrite(SENSOR_POWER_PIN, HIGH);
  delay(10); // Kurz warten zum messen

  int messwert = analogRead(SENSOR_PIN);

  digitalWrite(SENSOR_POWER_PIN, LOW);
  
  Serial.print("Analoger Wert: ");
  Serial.print(messwert);
  
  // Interpretation des Signals
  if (messwert < feuchtigkeitsGrenze) {
    Serial.println(" -> trocken, Relais an!\n");
    Serial.println(" Pumpe an.\n");
    digitalWrite(PUMP_PIN, LOW);
    delay(2000); // Giesszeit hier einstellbar
    Serial.println(" Fertig gegossen.\n");
    digitalWrite(PUMP_PIN, HIGH);
  }
  
  delay(10000); // zum Testen 10 Sekunden
  //delay(600000) // 10 Minuten bei echter Anwendung
}