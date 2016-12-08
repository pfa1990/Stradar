#include "mbed.h"
#include "MPU6050.h"

// FUNCTIONS
void blinkLed();
void calculateDistance();
void rx_interrupt();
void scan();
void sendDistanceData();
void sendImuData();
void setCarInNeutralPosition();
void updateImuValues();

// GENERAL VARIABLES
DigitalOut myled(LED1);
Serial pc(SERIAL_TX, SERIAL_RX);
bool carActive;
int rawDirection;
int rawSpeed;
float direction;
float speed;
int scanSet;
int speedLimitedSet;
int count = 0;

// HC-06's VARIABLE
Serial bluetooth(D8, D2);

// HC-SR04's VARIABLES
DigitalIn echo(D6);
DigitalOut trigger(D7);
Timer ultrasonicPulseTimer;
int distance;

// SG90's VARIABLES
PwmOut servo(D3); // Working range [0.0007f - 0.0023f]
float servoPosition;
int pos;

// GY-521's VARIABLES
MPU6050 imu(D14, D15);
float temperature;
float gyroscope[3];
float accelerometer[3];

// RC CAR's VARIABLES
PwmOut carServo(D10); // Working range [0.0009f - 0.0019f]
PwmOut carMotor(D11); // Working range [0.0009f - 0.0019f]

// CONNECTION STATE's VARIABLES
Timer connectionLostTimer;
int state = 0;
volatile int rx_in = 0;
const int buffer_size = 255;
char rx_buffer[buffer_size + 1];
char value;

int main() {
    bluetooth.attach(&rx_interrupt, Serial::RxIrq);
    
    carActive = true;
    setCarInNeutralPosition();
    servo.pulsewidth(0.0015f);
    //pc.printf("---CAR ACTIVE---\r\n");
    
    while (1) {
        if (state == 2) {
            if (rx_buffer[4] == '}') {
                if (carActive == false) {
                    //pc.printf("\r\n---CAR ACTIVE---\r\n");
                    carActive = true;
                } else if (connectionLostTimer.read() > 0.0f) {
                    connectionLostTimer.stop();
                    connectionLostTimer.reset();
                }
                
                //pc.printf("\r\n---PROCESSING DATA---\r\n");
                
                rawDirection = (int) rx_buffer[0];
                rawSpeed = (int) rx_buffer[1];
                scanSet = (int) rx_buffer[2];
                speedLimitedSet = (int) rx_buffer[3];
                
                memset(rx_buffer, 0, 5);
            
                //pc.printf("Direction: %d | Speed: %d | Scan: %d | Speed Limited: %d\r\n", rawDirection, rawSpeed, scanSet, speedLimitedSet);
                
                if (scanSet == 1) {
                    //pc.printf("\r\n---SCANNING---\r\n");
                    setCarInNeutralPosition();
                    scan();
                } else {
                    direction = 0.0019f - (0.00005f * rawDirection);
                    speed = 0.0009f + (0.00005f * rawSpeed);
                    
                    //pc.printf("\r\n---MOVING---\r\n");
                    if (speedLimitedSet == 1) {
                        if (speed > 0.00155f) {
                            //pc.printf("Speed: %f\r\n", 0.00155f);
                            carMotor.pulsewidth(0.00155f);
                        } else if (speed < 0.00125f) {
                            //pc.printf("Speed: %f\r\n", 0.00125f);
                            carMotor.pulsewidth(0.00125f);
                        } else {
                            carMotor.pulsewidth(speed);
                        }
                    } else {
                        //pc.printf("Speed: %f\r\n", speed);
                        carMotor.pulsewidth(speed);
                    }
                    
                    //pc.printf("Direction: %f\r\n", direction);
                    carServo.pulsewidth(direction);
                }
                
                //pc.printf("\r\n---UPDATING IMU VALUES---\r\n");
                updateImuValues();
                sendImuData();
            }
            state = 0;
        } else if (carActive == true) {
            if (connectionLostTimer.read() > 0.0f) {
                if (connectionLostTimer.read() >= 0.1f) {
                    //pc.printf("\r\n---CAR INACTIVE---\r\n");
                    setCarInNeutralPosition();
                    connectionLostTimer.stop();
                    connectionLostTimer.reset();
                    carActive = false;
                }
            } else {
                connectionLostTimer.start();
                state = 0;
            }
        }
        
        if (count == 1000000) {
            blinkLed();
            count = -1;
        }
        count++;
    }
}

void blinkLed() {
    myled = !myled;
}

void calculateDistance() {
    int sample = 0;
    int validSamples = 0;
    int minimum = 99999;
    int maximum = 0;
    int total = 0;
    
    for (sample = 0; sample < 10; sample++) {
        ultrasonicPulseTimer.reset();
        
        trigger = 0;
        wait_us(2);
        trigger = 1;
        wait_us(10);
        trigger = 0;
        
        while (!echo);
        ultrasonicPulseTimer.start();
        while (echo);
        ultrasonicPulseTimer.stop();
        
        distance = ultrasonicPulseTimer.read_us() / 58;
        //pc.printf("Distance: %d\r\n", distance);
        
        if (distance >= 0 && distance <= 350) {
            if (distance < minimum)
                minimum = distance;
            if (distance > maximum)
                maximum = distance;
        
            total += distance;
            validSamples++;
        }
        
        wait(0.03);
    }
    
    distance = (total - maximum - minimum) / (validSamples - 2);
}

void rx_interrupt() {
    value = bluetooth.getc();
    
    switch (state) {
    case 0:
        if (value == '{') {
            state = 1;
        }
        break;
    case 1:
        if (value != '}') {
            rx_buffer[rx_in] = value;
            rx_in++;
        } else {
            rx_buffer[rx_in] = '}';
            rx_in = 0;
            state = 2;
        }
        break;
    default:
        break;
    }
}

void scan() {
    pos = -1;
    for (servoPosition = 0.0007f; servoPosition <= 0.0023f; servoPosition += 0.00005f) {
        servo.pulsewidth(servoPosition);
        pos++;
        //pc.printf("Servo Angle: %1.2f\r\n", pos * 5.625);
        wait(0.2);
        calculateDistance();
        sendDistanceData();
    }

    servo.pulsewidth(0.0015f);
}

void sendDistanceData() {
    // Debug
    //pc.printf("d{%d:%d:%d}\r\n", pos, (distance / 10), (distance % 10));
    bluetooth.printf("d{%c%c%c}", pos, (distance / 10), (distance % 10));
}

void sendImuData() {
    // Debug
    //pc.printf("i{%d:%d:%d}\r\n", (int) (accelerometer[0] + 9), (int) (accelerometer[1] + 9), (int) (accelerometer[2] + 9));
    bluetooth.printf("i{%c%c%c}", (int) (accelerometer[0] + 9), (int) (accelerometer[1] + 9), (int) (accelerometer[2] + 9));
}

void setCarInNeutralPosition() {
    carServo.pulsewidth(0.0014f);
    
    if (speed > 0.0014f) {
        carMotor.pulsewidth(0.0009f);
        wait(1.0);
    } else if (speed < 0.0014f) {
        carMotor.pulsewidth(0.0019f);
        wait(0.1);
    }
    
    carMotor.pulsewidth(0.0014f);
}

void updateImuValues() {
    temperature = imu.getTemp();
    //pc.printf("Temprature: %.2f C\r\n", temperature);

    imu.getGyro(gyroscope);
    //pc.printf("Gyroscope: x = %.2f y = %.2f z = %.2f\r\n", gyroscope[0], gyroscope[1], gyroscope[2]);

    imu.getAccelero(accelerometer);
    //pc.printf("Accelerometer: x = %.2f y = %.2f z = %.2f\r\n", accelerometer[0], accelerometer[1], accelerometer[2]);
}
