const int inputPin = 3;              // Pin for reading signal
const int ledPin = 13;               // Pin for LED diode
const int outputPins[] = {7, 8, 9, 10, 11, 12};  // Output pins for binary encoding (pins 7-12)
const int numPins = 6;               // Number of output pins
const int extraPin = 6;              // Extra digital output pin (always set to LOW)
unsigned long lastTime = 0;          // For storing the last time state changed
volatile int pulseCount = 0;                  // Pulse counter for wind speed measurement
volatile unsigned long lastInterruptTime = 0;
const float pulseFactor = 2.4; // Conversion factor: 2.4 km/h per pulse

void setup() {
  pinMode(inputPin, INPUT_PULLUP);          // Setting pin D5 as input
  pinMode(ledPin, OUTPUT);           // Setting pin D13 as output for LED
  pinMode(extraPin, OUTPUT);         // Setting pin D6 as output
  digitalWrite(extraPin, LOW);       // Ensure pin D6 is set to LOW
  
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
  // Calculate and output wind speed every 5 seconds
  unsigned long currentMillis = millis();
  if (currentMillis - lastTime >= 5000) {
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
    digitalWrite(extraPin, LOW);
    

    // Blink LED twice
    for (int i = 0; i < 2; i++) {
      digitalWrite(ledPin, HIGH);   // Turn on LED
      delay(100);                   // Pause for 100 ms
      digitalWrite(ledPin, LOW);    // Turn off LED
      delay(100);                   // Pause for 100 ms
    }    
  }
}