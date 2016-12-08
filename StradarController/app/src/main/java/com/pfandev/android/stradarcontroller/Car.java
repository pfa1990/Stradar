package com.pfandev.android.stradarcontroller;

public class Car {

    private byte mDirection;
    private byte mSpeed;
    private boolean mScanning;
    private boolean mSpeedLimited;
    private boolean mActive;

    public Car() {
        setCarInDefaultStatus();
    }

    public byte getDirection() {
        return mDirection;
    }

    public void setDirection(byte direction) {
        if (direction < 0) {
            mDirection = 0;
        } else if (direction > 20) {
            mDirection = 20;
        } else {
            mDirection = direction;
        }
    }

    public byte getSpeed() {
        return mSpeed;
    }

    public void setSpeed(byte speed) {
        if (speed < 0) {
            mSpeed = 0;
        } else if (speed > 20) {
            mSpeed = 20;
        } else {
            mSpeed = speed;
        }
    }

    public boolean isScanning() {
        return mScanning;
    }

    public void setScanning(boolean scanning) {
        mScanning = scanning;
    }

    public boolean hasSpeedLimited() {
        return mSpeedLimited;
    }

    public void setSpeedLimited(boolean speedLimited) {
        mSpeedLimited = speedLimited;
    }

    public boolean isActive() {
        return mActive;
    }

    public void setActive(boolean active) {
        mActive = active;
    }

    public void setCarInNeutralPosition() {
        mDirection = 10;
        mSpeed = 10;
    }

    public void setCarInDefaultStatus() {
        setCarInNeutralPosition();
        mScanning = false;
        mSpeedLimited = false;
    }
}
