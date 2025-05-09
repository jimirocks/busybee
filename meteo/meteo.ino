const int inputPin = 5;              // Pin for reading signal
const int ledPin = 13;               // Pin for LED diode
const int outputPins[] = {8, 9, 10, 11, 12};  // Output pins for binary encoding (reduced to 5 pins)
const int numPins = 5;               // Number of output pins
unsigned long lastTime = 0;          // For storing the last time state changed
int pulseCount = 0;                  // Pulse counter for wind speed measurement
const float pulseFactor = 2.4 / 3.6; // Conversion factor: 2.4 km/h = 0.667 m/s per pulse

void setup() {
  pinMode(inputPin, INPUT);          // Setting pin D5 as input
  pinMode(ledPin, OUTPUT);           // Setting pin D13 as output for LED
  for (int i = 0; i < numPins; i++) {
    pinMode(outputPins[i], OUTPUT);  // Setting up output pins
  }
  attachInterrupt(digitalPinToInterrupt(inputPin), countPulse, RISING);  // Interrupt on rising edge of signal
  Serial.begin(9600);                // Initialize serial communication for debugging
}

void loop() {    
  // Calculate wind speed in m/s
  float elapsedSeconds = (millis() - lastTime) / 1000.0;
  float windSpeed = pulseCount * pulseFactor;  // Convert pulse count to m/s
  
  // Print wind speed to serial monitor
  Serial.print("Wind speed: ");
  Serial.print(windSpeed);
  Serial.println(" m/s");
  
  lastTime = millis();             // Time update
  pulseCount = 0;                  // Reset pulse counter

  // Round the wind speed to an integer for binary encoding
  int windSpeedInt = round(windSpeed);
  
  // Encode wind speed into 5-bit format on output pins
  for (int i = 0; i < numPins; i++) {
    digitalWrite(outputPins[i], (windSpeedInt >> i) & 1);  // Setting each bit
  }

  // Blink LED twice
  for (int i = 0; i < 2; i++) {
    digitalWrite(ledPin, HIGH);   // Turn on LED
    delay(100);                   // Pause for 100 ms
    digitalWrite(ledPin, LOW);    // Turn off LED
    delay(100);                   // Pause for 100 ms
  }

  delay(1000);
}

// Interrupt function that is called at each pulse on pin D5
void countPulse() {
  pulseCount++;                       // Increase pulse count
}