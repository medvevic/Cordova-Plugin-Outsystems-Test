package org.apache.cordova.passportscanner;

import android.os.Build;

/**
 * Created by Hp-user on 11.05.2017.
 */

public class EnvironmentHelper {


    public static EnvironmentHelper getInstance() {
        if (_instance == null) {
            _instance = new EnvironmentHelper();
        }
        return _instance;
    }


    public String getApplicationHosted() {
        return applicationHosted;
    }

    public void setApplicationHosted(String applicationHosted) {
        this.applicationHosted = applicationHosted;
    }

    public boolean isJSFApplicationServer() {
        return isJSFApplicationServer;
    }

    public void setJSFApplicationServer(boolean isJSFApplicationServer) {
        this.isJSFApplicationServer = isJSFApplicationServer;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }


    public boolean isCustomer() {
        return isCustomer;
    }

    public void setIsCustomer(boolean isCustomer) {
        this.isCustomer = isCustomer;
    }

    public boolean isTaxFree() {
        return isTaxFree;
    }

    public void setTaxFree(boolean taxFree) {
        isTaxFree = taxFree;
    }

    public boolean isMerchantDevice() {
        if (!isMerchantDeviceChecked) {
            isMerchantDeviceChecked = true;
            isMerchantDevice = "EposMiniTF1".equals(Build.MANUFACTURER) || "DENQIN".equals(Build.MANUFACTURER) && "MW802_MT501".equals(Build.MODEL);
        }
        return isMerchantDevice;
    }

    public String getDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    private static EnvironmentHelper _instance;
    private boolean isCustomer;
    private boolean isTaxFree;
    private String applicationHosted = null;

    // The application Server - default value .aspx
    private boolean isJSFApplicationServer = false;

    private String deviceId = "";
    private boolean isMerchantDevice;
    private boolean isMerchantDeviceChecked;

}
