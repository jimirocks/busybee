const int inputPin = 3;              // Pin for reading signal
const int ledPin = 13;               // Pin for LED diode
const int outputPins[] = {7, 8, 9, 10, 11, 12};  // Output pins for binary encoding (pins 7-12)
const int numPins = 6;               // Number of output pins
const int modePin = 6;              // Extra digital output pin (always set to LOW)
const int directionInputPin = A0;
unsigned long lastTime = 0;          // For storing the last time state changed
volatile int pulseCount = 0;                  // Pulse counter for wind speed measurement
volatile unsigned long lastInterruptTime = 0;
const float pulseFactor = 2.4; // Conversion factor: 2.4 km/h per pulse
const float VCC = 5.0;

void setup() {
  pinMode(inputPin, INPUT_PULLUP);          // Setting pin D5 as input
  pinMode(ledPin, OUTPUT);           // Setting pin D13 as output for LED
  pinMode(modePin, OUTPUT);         // Setting pin D6 as output
  digitalWrite(modePin, LOW);       // Ensure pin D6 is set to LOW
  
  for (int i = 0; i < numPins; i++) {
    pinMode(outputPins[i], OUTPUT);  // Setting up output pins
  }
  Serial.begin(9600);                // Initialize serial communication for debugging

  attachInterrupt(digitalPinToInterrupt(inputPin), countPulse, FALLING);
}

void countPulse() {
  unsigned long now = millis();
  if (now - lastInterruptTime > 5) {   // debounce 5ms
    pulseCount++;
    lastInterruptTime = now;
  }
}

void loop() {    
  // Calculate and output wind speed every 1 second
  unsigned long currentMillis = millis();
  if (currentMillis - lastTime >= 1000) {
    // Calculate wind speed in m/s
    float elapsedSeconds = (currentMillis - lastTime) / 1000.0;
    
    Serial.print("Elapsed seconds: ");
    Serial.println(elapsedSeconds);

    Serial.print("Pulse count: ");
    Serial.println(pulseCount);

    noInterrupts();
    float pulseRate = pulseCount / elapsedSeconds;  // Pulses per second
    pulseCount = 0;                  // Reset pulse counter
    interrupts();

    float windSpeed = pulseRate * pulseFactor;  // Convert pulse rate to m/s
    
    // Print wind speed to serial monitor
    Serial.print("Wind speed: ");
    Serial.print(windSpeed);
    Serial.print(" km/h, ");
    
    lastTime = currentMillis;        // Time update    

    // Round the wind speed to an integer for binary encoding
    int windSpeedInt = round(windSpeed);
    // sanitize overflow
    if (windSpeedInt > 63) {
      windSpeedInt = 63;
    }
    Serial.print("Setting windspeed on output ");
    Serial.print(windSpeedInt);
    Serial.println(" km/h");
    
    // Encode wind speed into binary format on output pins    
    for (int i = 0; i < numPins; i++) {
      digitalWrite(outputPins[i], (windSpeedInt >> i) & 1);  // Setting each bit
    }
    // set mode to wind speed
    digitalWrite(modePin, LOW);

    delay(200);

    // measure wind direction
    int raw = analogRead(directionInputPin);
    float voltage = (raw / 1023.0) * VCC;

    int windDirection = 0;

    if      (voltage >= 4.62) windDirection = 13; // W
    else if (voltage >= 4.33) windDirection = 15; // NW
    else if (voltage >= 4.04) windDirection = 14; // NWW
    else if (voltage >= 3.84) windDirection = 1;  // N
    else if (voltage >= 3.43) windDirection = 16; // NNW
    else if (voltage >= 3.08) windDirection = 11; // SW
    else if (voltage >= 2.93) windDirection = 12; // SWW
    else if (voltage >= 2.25) windDirection = 3;  // NE
    else if (voltage >= 1.98) windDirection = 2;  // NNE
    else if (voltage >= 1.40) windDirection = 9;  // S
    else if (voltage >= 1.19) windDirection = 10; // SSW      
    else if (voltage >= 0.90) windDirection = 7;  // SE
    else if (voltage >= 0.62) windDirection = 8;  // SSE  
    else if (voltage >= 0.45) windDirection = 5;  // E
    else if (voltage >= 0.41) windDirection = 4;  // NEE 
    else if (voltage >= 0.32) windDirection = 6;  // SEE
    else windDirection = 0; // unknown

    // Print wind direction to serial monitor
    Serial.print("Wind direction: ");
    Serial.println(windDirection);

    // Encode wind diretcion into binary format on output pins    
    for (int i = 0; i < numPins; i++) {
      digitalWrite(outputPins[i], (windDirection >> i) & 1);  // Setting each bit
    }
    // set mode to wind direction
    digitalWrite(modePin, HIGH);
  }
}