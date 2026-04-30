/*
 * Girls Day - Automatische Pflanzen-Bewässerung
 * Kompletter Arduino Sketch (aus dem Workbook generiert)
 */

const int SENSOR_PIN = A0;  // Analog Pin 0 zu DO
const int SENSOR_POWER_PIN = 2; // Pin, damit der Sensor nicht so schnell rostet
const int PUMP_PIN = 8; // Pin fürs relais für die pumpe
const int feuchtigkeitsGrenze = 400; // Feuchtigkeitsgrenze für die Messwerte vom Sensor

void setup() {
  Serial.begin(9600);

  pinMode(PUMP_PIN, OUTPUT);
  pinMode(SENSOR_POWER_PIN, OUTPUT);

  digitalWrite(PUMP_PIN, LOW);
  digitalWrite(SENSOR_POWER_PIN, LOW);
}

void loop() {
  digitalWrite(SENSOR_POWER_PIN, HIGH);
  delay(10);
  int messwert = analogRead(SENSOR_PIN);
  digitalWrite(SENSOR_POWER_PIN, LOW);
  Serial.print("Analoger Wert: " );
  Serial.println(messwert);
  if (messwert < feuchtigkeitsGrenze) {
    digitalWrite(PUMP_PIN, HIGH);
    delay(2000);
    digitalWrite(PUMP_PIN, LOW);
  } else {
    Serial.println("Boden feucht - keine Bewässerung nötig");
  }
  delay(10000);
}
