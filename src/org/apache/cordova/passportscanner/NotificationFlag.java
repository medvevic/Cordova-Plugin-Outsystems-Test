package org.apache.cordova.passportscanner;

/**
 * Created by Hp-user on 11.05.2017.
 */

public class NotificationFlag {
    private boolean value;

    public NotificationFlag() {
        value = false;
    }

    public boolean isSet() {
        return value;
    }

    public void set() {
        this.value = true;
    }

    public void reset() {
        this.value = false;
    }

    @Override
    public String toString() {
        return "NotificationFlag:" + (isSet() ? "ON" : "OFF");
    }

}
