const int inputPin = 5;              // Pin for reading signal
const int ledPin = 13;               // Pin for LED diode
const int outputPins[] = {7, 8, 9, 10, 11, 12};  // Output pins for binary encoding (pins 7-12)
const int numPins = 6;               // Number of output pins
const int extraPin = 6;              // Extra digital output pin (always set to LOW)
unsigned long lastTime = 0;          // For storing the last time state changed
int pulseCount = 0;                  // Pulse counter for wind speed measurement
const float pulseFactor = 2.4 / 3.6; // Conversion factor: 2.4 km/h = 0.667 m/s per pulse

int lastPinState = LOW;              // Track the previous state of the input pin

void setup() {
  pinMode(inputPin, INPUT);          // Setting pin D5 as input
  pinMode(ledPin, OUTPUT);           // Setting pin D13 as output for LED
  pinMode(extraPin, OUTPUT);         // Setting pin D6 as output
  digitalWrite(extraPin, LOW);       // Ensure pin D6 is set to LOW
  
  for (int i = 0; i < numPins; i++) {
    pinMode(outputPins[i], OUTPUT);  // Setting up output pins
  }
  Serial.begin(9600);                // Initialize serial communication for debugging
}

void loop() {    
  // Read the current state of the pin
  int currentPinState = digitalRead(inputPin);
  
  // Check if we detect a rising edge (transition from LOW to HIGH)
  if (currentPinState == HIGH && lastPinState == LOW) {
    pulseCount++;  // Increment the pulse counter ONLY on rising edge
    
    Serial.println("Pulse detected");
  }
  
  // Update the last state for the next comparison
  lastPinState = currentPinState;
  delay(10);

  // Calculate and output wind speed every 5 seconds
  unsigned long currentMillis = millis();
  if (currentMillis - lastTime >= 5000) {
    // Calculate wind speed in m/s
    float elapsedSeconds = (currentMillis - lastTime) / 1000.0;
    
    Serial.print("Elapsed seconds: ");
    Serial.println(elapsedSeconds);

    Serial.print("Pulse count: ");
    Serial.println(pulseCount);

    float pulseRate = pulseCount / elapsedSeconds;  // Pulses per second
    float windSpeed = pulseRate * pulseFactor;  // Convert pulse rate to m/s
    
    // Print wind speed to serial monitor
    Serial.print("Wind speed: ");
    Serial.print(windSpeed);
    Serial.print(" m/s, ");
    Serial.print(windSpeed * 3.6);
    Serial.println(" km/h");
    
    lastTime = currentMillis;        // Time update
    pulseCount = 0;                  // Reset pulse counter

    // Round the wind speed to an integer for binary encoding
    int windSpeedInt = round(windSpeed);
    
    // Encode wind speed into binary format on output pins
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

    // Reset pin 6 to LOW
    digitalWrite(extraPin, LOW);
  }
}