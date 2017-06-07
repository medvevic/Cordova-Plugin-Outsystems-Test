package org.apache.cordova.passportscanner;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.hoho.android.usbserial.driver.UsbSerialDriver;

import org.apache.cordova.AuthenticationToken;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaBridge;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewEngine;
import org.apache.cordova.ExposedJsApi;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.apache.cordova.Whitelist;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PassportScannerPlugin extends CordovaPlugin {

    // The current driver that handle the serial port
    //private UsbSerialDriver driver;
    // The serial port that will be used in this plugin
    //private UsbSerialPort port;
    // Read buffer, and read params
    private static final int READ_WAIT_MILLIS = 200;
    private static final int BUFSIZ = 4096;
    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);
    // Connection info
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private int parity;
    private boolean setDTR;
    private boolean setRTS;
    private boolean sleepOnPause;

    // callback that will be used to send back data to the cordova app
    private CallbackContext readCallback;

    // I/O manager to handle new incoming serial data
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private UsbDevice device;
    private UsbDeviceConnection connection;

    //----------------------------------------------------------
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private CordovaWebView cordovaWebView;
    private CordovaPlugin activityResultCallback = null;
    private ImageView imageView;
    private View errorView;
    private PassportScanner passportScanner;
    private String resultFindDevice;

    //private TscPrinter tscPrinter;
    private String lastUrl;

    //------------------------------- New for work from Outsystems

    // Index of the 'params' object in CordovaArgs array passed in each action.
    private static final int ARG_INDEX_PARAMS = 0;
    // Index of the 'data' ArrayBuffer in CordovaArgs array passed in each action (where relevant).
    private static final int ARG_INDEX_DATA_ARRAYBUFFER = 1;

    // An endpoint address is constructed from the interface index left-shifted this many bits,
    // or-ed with the endpoint index.
    private static final int ENDPOINT_IF_SHIFT = 16;

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private BroadcastReceiver mUsbReceiver;

    // Maps connection handles to the corresponding device & connection objects.
    //private HashMap<Integer, ConnectedDevice> mConnections = new HashMap<Integer, ConnectedDevice>();
    private static int mNextConnectionId = 1;

/*
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        for (ConnectedDevice d : mConnections.values()) {
            d.close();
        }
        mConnections.clear();
        unregisterReceiver();
    }
*/
    @Override
    public void onReset() {
        unregisterReceiver();
    }

    private void unregisterReceiver() {
        if(mUsbReceiver != null) {
            webView.getContext().unregisterReceiver(mUsbReceiver);
            mUsbReceiver = null;
        }
    }

    private CallbackContext openCallbackContext;

    private static final String TAG = "PassportScannerPlugin";
    private static final String ACTION_AVAILABLE = "available";
    private static final String ACTION_FIND_DEVICES = "findDevices";
    private static final String ACTION_USB_PERMISSION = TAG + ".USB_PERMISSION";

    @Override
    public boolean execute(String action, final CordovaArgs args, final CallbackContext callbackContext)
            throws JSONException {
     //   final JSONObject params = args.getJSONObject(ARG_INDEX_PARAMS); // <- returns JSON error!!!!
    //    final CordovaArgs finalArgs = args;
    //    Log.d(TAG, "Action: " + action + " params: " + params);
          Log.d(TAG, "Action: " + action);
        this.openCallbackContext = callbackContext;

        try {
            //if (action.equals(ACTION_SWITCH_ON)) {
            //} else if (action.equals(ACTION_SWITCH_OFF)) {

            if ("hasUsbHostFeature".equals(action)) {
                boolean usbHostFeature = cordova.getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, usbHostFeature));
                return true;
            } else if (action.equals(ACTION_AVAILABLE)) { //} else if ("available".equals(action)) {
                openCallbackContext.success(1);
                return true;
            } else if (action.equals(ACTION_FIND_DEVICES)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            String passportScannerStr = passportScanner == null ? "passportScanner=null" : passportScanner.toString();
                            //openCallbackContext.success("findDevices : " + passportScannerStr + " " + result);
                            //openCallbackContext.success("findDevices(): " + findDevices() + ", args = " + args);
                            openCallbackContext.success("findDevices(): " + findDevices());
                            //openCallbackContext.success("findDevices params : " + params);
                        } catch (Exception e) {
                            openCallbackContext.error("Error. PassportScannerPlugin -> findDevices : " + e.getMessage());
                        }
                    }
                });
                return true;
            }
        } catch (Throwable e) {
            return false;
        }
        return false;
    }

    private String findDevices() {
        try {
            DeviceWrapper dw = new DeviceWrapper();
            DeviceWrapper barcodeReaderDevice = dw.forBarcodeReader();

            // Existing code
            new DeviceFinder(new DeviceFinder.EventListener() {
                @Override
                public void onDeviceFound(DeviceWrapper device) {
                    String name = device.getName();
                    resultFindDevice = resultFindDevice + ", onDeviceFound device.getName() = " + device.getName();
                    resultFindDevice = resultFindDevice + ", onDeviceFound device.toString() = " + device.toString();
                    if (DeviceWrapper.BARCODE_READER.equals(device.getName())) {
                        //addUserAgent(UrlHelper.UA_BARCODE_READER);
                    } else if (DeviceWrapper.PASSPORT_SCANNER.equals(device.getName())) {
                        passportScanner = new PassportScanner(device.getUsbDevice(), device.getUsbConnection());
                        //addUserAgent(UrlHelper.UA_PASSPORT_READER);
                    } else if (DeviceWrapper.RECEIPT_PRINTER.equals(device.getName())) {
                        // TODO: there must be separate printer for sticker
                        //escPosPrinter = new EscPosPrinter(device.getUsbDevice(), device.getUsbConnection(), 58);
                    } else if (device.getName().equals(DeviceWrapper.RECEIPT_PRINTER_BLUETOOTH_REGO_NAME)) {
                        //contextRegoPrinter = new RegoPrinter(getApplicationContext());
                        //contextRegoPrinter.setPort(DeviceWrapper.RECEIPT_PRINTER_BLUETOOTH_REGO_ADDRESS);
                    } else if (device.getName().equals(DeviceWrapper.RECEIPT_PRINTER_BLUETOOTH_TSC_NAME)) {
                        //tscPrinter = new TscPrinter();
                        //tscPrinter.setMacAddress(device.getBluetoothAddress());
                    }
                }
            }).find(this.cordova.getActivity().getApplicationContext(), barcodeReaderDevice, dw.forPassportScannerNew(),
                    dw.forPassportScanner(), dw.forReceiptPrinter(), dw.forBluetoothPrinterTSC());
        }
        catch (Throwable e) {
            return resultFindDevice + e.getMessage();
        }
        return resultFindDevice;
    }

/*
    private void addUserAgent(String value) {
        String ua = value + "; " + cordovaWebView.getSettings().getUserAgentString(); // android.webkit.
        cordovaWebView.getSettings().setUserAgentString(ua);
        WebServicesClient.getInstance().setUserAgent(ua);
    }
*/

//--------------------------------------------------------------------------------------------------
    public class DeviceWrapper {


    public static final String BARCODE_READER = "BarcodeReader";
    public static final String PASSPORT_SCANNER = "PassportScanner";
    public static final String RECEIPT_PRINTER = "ReceiptPrinter";
    public static final String RECEIPT_PRINTER_BLUETOOTH_REGO_NAME = "RG-MLP58A";
    public static final String RECEIPT_PRINTER_BLUETOOTH_REGO_ADDRESS = "00:02:5B:B3:D8:21";
    //public static final String RECEIPT_PRINTER_BLUETOOTH_TSC_NAME = "Alpha-3R";
    public static final String RECEIPT_PRINTER_BLUETOOTH_TSC_NAME = "BT-SPP";

    public DeviceWrapper forBarcodeReader() {
        return new DeviceWrapper(BARCODE_READER, 0x05E0, 0x1200); // 1504, 4608
    }

    public DeviceWrapper forPassportScanner() {
        return new DeviceWrapper(PASSPORT_SCANNER, 0xFFFF, 5);
    }

    public DeviceWrapper forPassportScannerNew() {
        return new DeviceWrapper(PASSPORT_SCANNER, 0x2B78, 5);
    }

    public DeviceWrapper forReceiptPrinter() {
        return new DeviceWrapper(RECEIPT_PRINTER, 1003, 8204);
    }

    public DeviceWrapper forBluetoothPrinterREGO() {
        return new DeviceWrapper(RECEIPT_PRINTER_BLUETOOTH_REGO_NAME);
    }

    public DeviceWrapper forBluetoothPrinterTSC() {
        return new DeviceWrapper(RECEIPT_PRINTER_BLUETOOTH_TSC_NAME);
    }

    public DeviceWrapper(String name, int vendorId, int productId) {
        setName(name);
        setVendorId(vendorId);
        setProductId(productId);
    }

    public DeviceWrapper(String name) {
        setName(name);
    }

    public DeviceWrapper() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVendorId() {
        return vendorId;
    }

    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }

    public int getProductId() {
        return productId;
    }

    public String getBluetoothAddress() {
        return btAddress;
    }

    public void setBluetoothAddress(String btAddress) {
        this.btAddress = btAddress;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public InputDevice getInputDevice() {
        return inputDevice;
    }

    public void setInputDevice(InputDevice inputDevice) {
        this.inputDevice = inputDevice;
    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    public void setUsbDevice(UsbDevice usbDevice) {
        this.usbDevice = usbDevice;
    }

    public UsbDeviceConnection getUsbConnection() {
        return usbConnection;
    }

    public void setUsbConnection(UsbDeviceConnection usbConnection) {
        this.usbConnection = usbConnection;
    }

    public boolean hasDevice() {
        return inputDevice != null || usbDevice != null || bluetoothDevice != null;
    }

    public void closeUsbConnection() {
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        closeUsbConnection();
        super.finalize();
    }

    private String name;
    private int vendorId;
    private int productId;
    private InputDevice inputDevice;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private BluetoothDevice bluetoothDevice;
    private String btAddress;

}

//--------------------------------------------------------------------------------------------------
    public static class DeviceFinder {

    String deviceName = "";
    public interface EventListener {
        void onDeviceFound(DeviceWrapper device);
    }

    public DeviceFinder(final DeviceFinder.EventListener listener) {
        this.listener = listener;
    }

    public String find(final Context context, DeviceWrapper... devices) {

        if (devices == null)
            return "devices == null";
        this.devices = devices;
/*
        if (!EnvironmentHelper.getInstance().isMerchantDevice()) {
            // Find bluetooth devices
            try {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                // Phone does not support Bluetooth
                if (btAdapter != null) {
                    Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        for (BluetoothDevice device : pairedDevices) {
                            DeviceWrapper dev = findDevice(device);
                            if (dev != null) {
                                listener.onDeviceFound(dev);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
            }
        }
*/
        // Find USB devices
        try {
            usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if(usbManager != null) {
                context.registerReceiver(receiver, new IntentFilter(ACTION_USB_PERMISSION));
                PendingIntent intent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                for (final UsbDevice device : usbManager.getDeviceList().values()) {
                    DeviceWrapper dev = findDevice(device);
                    if (dev != null) {
                        deviceName = "USB";
                        usbManager.requestPermission(device, intent);
                    }
                }
            }
        } catch (Throwable e) {
        }

        // Find HID devices, which are among inputs
        try {
            InputManager inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (inputManager != null) {
                for (int id : inputManager.getInputDeviceIds()) {
                    if (id > 0) {
                        DeviceWrapper dev = findDevice(inputManager.getInputDevice(id));
                        if (dev != null) {
                            deviceName = "HID";
                            listener.onDeviceFound(dev);
                        }
                    }
                }
            }
        } catch (Throwable e) {
        }

        return "device " + deviceName + " found!";
    }

    private static final String ACTION_USB_PERMISSION = "net.etaxfree.refund.helpers.USB_PERMISSION";

    private UsbManager usbManager;
    private DeviceWrapper[] devices;
    private DeviceFinder.EventListener listener;

    final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        DeviceWrapper device = findDevice((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                        if (device != null) {
                            device.setUsbConnection(usbManager.openDevice(device.getUsbDevice()));
                            listener.onDeviceFound(device);
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                DeviceWrapper device = findDevice((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                if (device != null && device.getUsbConnection() != null) {
                    device.getUsbConnection().close();
                    device.setUsbConnection(null);
                }
            }
        }
    };

    private DeviceWrapper findDevice(UsbDevice device) {
        if (device == null) {
            return null;
        }
        DeviceWrapper res = findDevice(device.getVendorId(), device.getProductId());
        if (res != null) {
            res.setUsbDevice(device);
        }
        return res;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private DeviceWrapper findDevice(InputDevice device) {
        if (device == null) {
            return null;
        }
        DeviceWrapper res = findDevice(device.getVendorId(), device.getProductId());
        if (res != null) {
            res.setInputDevice(device);
        }
        return res;
    }

    private DeviceWrapper findDevice(int vendorId, int productId) {
        if (devices == null || vendorId <= 0 || productId <= 0) {
            return null;
        }
        for(DeviceWrapper dev : this.devices) {
            if (dev.getVendorId() == vendorId && dev.getProductId() == productId) {
                return dev;
            }
        }
        return null;
    }
/*
    private DeviceWrapper findDevice(BluetoothDevice device) {
        if (device == null || device.getName() == null) {
            return null;
        }

        for(DeviceWrapper dev : this.devices) {
            if (device.getName().equals(dev.getName())) {
                dev.setBluetoothDevice(device);
                dev.setBluetoothAddress(device.getAddress());
                return dev;
            }
        }
        return null;
    }
*/
} // class DeviceFinder

//--------------------------------------------------------------------------------------------------
    public class PassportScanner extends UsbSerialDevice {

        public PassportScanner(UsbDevice device, UsbDeviceConnection connection) {
            super(device, connection);
        }

        public String getVersion() throws IOException
        {
            if (mScannerVersion == null || mScannerVersion.isEmpty())
            {
                //Get Version
                send((byte)'V', null, null);
            }

            return mScannerVersion;
        }

        public synchronized String[] waitMRZ(NotificationFlag stopFlag) throws IOException
        {
            mMRZ=null;

            // Get MRZ

            // Force reset of document detection, to detect document already in the slot
            send((byte) 'C', new byte[]{0x00}, stopFlag);

            send((byte) 'C', new byte[]{0x01}, stopFlag);

            return mMRZ;
        }

        private static final String TAG = "PassportScanner";

        private String mScannerVersion;

        private String[] mMRZ = null;

        private class ScannerPacket {

            private byte mCode;
            private int mLength;

            private byte [] mData;
            private int mDataPos = 0;

            public ScannerPacket(byte code, int length) {
                mCode = code;
                mLength = length;

                mData = new byte[mLength];
                mDataPos = 0;
            }

            public ScannerPacket(byte code, byte [] data) {
                mCode = code;
                mLength = data.length;

                mData = data;
                mDataPos = mLength;
            }

            public int addData(byte [] data, int offset, int length) {
                int toCopy = Math.min(mLength-mDataPos, length);

                System.arraycopy(data, offset, mData, mDataPos, toCopy);
                mDataPos += toCopy;
                return toCopy;
            }

            public boolean isComplete() {
                return mDataPos == mLength;
            }

            public byte getCode() {
                return mCode;
            }

            public byte[] getData() {
                return mData;
            }
        }

        private BlockingQueue<ScannerPacket> bq = (BlockingQueue<PassportScanner.ScannerPacket>) new ArrayBlockingQueue<ScannerPacket>(16);
        PassportScanner.ScannerPacket currentPacket = null;

        byte[] tmpBuffer = new byte[256];
        int tmpPos=0;

        private void appendBuffer(byte [] data, int offset, int length) throws IOException {

            if(256-tmpPos<length)
                throw new IOException("Buffer size is too small.");

            System.arraycopy(data, offset, tmpBuffer, tmpPos, length);
            tmpPos += length;
        }

        private void resetResponse() {
            currentPacket = null;
        }

        private PassportScanner.ScannerPacket unpackResponse(byte[] data) throws IOException {
            int tmpLen = 0;
            int usedLen = 0;

            if(data==null)
            {
                if(tmpPos==0) // Padd with existing buffer
                {
                    // Nothing to do
                    return null;
                } else {
                    data = tmpBuffer;
                    tmpLen = tmpPos;
                }
            }
            else if(tmpPos>0) // Padd with existing buffer
            {
                appendBuffer(data, 0, data.length);
                data = tmpBuffer;
                tmpLen = tmpPos;
            }
            else
            {
                tmpLen = data.length;
            }

            if(currentPacket==null) {
                if (tmpLen<3)	{
                    //throw new IOException("Incomplete header response.");
                    return null;
                }

                currentPacket = new ScannerPacket(data[0], data[1] * 256 + data[2]);
                usedLen = 3 + currentPacket.addData(data, 3, tmpLen-3);
            }
            else
            {
                usedLen = currentPacket.addData(data, 0, tmpLen);
            }

            // There is a rest
            if(usedLen<tmpLen) {
                // Reset buffer origin
                tmpPos = 0;
                appendBuffer(data, usedLen, tmpLen-usedLen);
            }

            return currentPacket;
        }

        @Override
        protected synchronized void updateReceivedData(byte[] data) {
            super.updateReceivedData(data);
            //final String message = "Read " + data.length + " bytes: \n"
            //        + HexDump.dumpHexString(data) + "\n\n";

            boolean next = true;
            PassportScanner.ScannerPacket packet = null;

            try {
                packet = unpackResponse(data);
                do {
                    if(packet!=null && packet.isComplete()) {
                        bq.add(packet);
                        notifyAll();
                        resetResponse();
                        packet = unpackResponse(null);
                    } else {
                        next = false;
                    }
                }while(next);

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        private synchronized int send(byte cmd_code, byte [] data, NotificationFlag stopFlag) throws IOException {
            if (getPort() == null)
                throw new IOException("No scanner device available");

            // If driver wasn't available during first loading
            if (!isOpen()) {
                resume();
            }

            if (!bq.isEmpty())
                bq.clear();

            byte[] cmd;

            if (data != null) {
                cmd = new byte[3 + data.length];
                cmd[0] = cmd_code;
                cmd[1] = (byte) (data.length >> 8);
                cmd[2] = (byte) (data.length & 0xFF);
                System.arraycopy(data, 0, cmd, 3, data.length);
            } else {
                cmd = new byte[]{cmd_code, 0, 0};
            }

            int written = getPort().write(cmd, 3000);

            // If continuous reading mode is enable expected response is Inquire
            if (cmd_code == 'C' && data.length > 0 && data[0] == 1)
                cmd_code = 'I';

            boolean gotResponse = false;

            try {
                while (bq.isEmpty() && !gotResponse) {
                    if (stopFlag != null && stopFlag.isSet()) {
                        break;
                    }
                    wait(1000);

                    //bq.poll(timeout, TimeUnit.MILLISECONDS);

                    while (!bq.isEmpty()) {
                        if (stopFlag != null && stopFlag.isSet()) {
                            break;
                        }
                        PassportScanner.ScannerPacket packet = bq.poll();
                        if (cmd_code == packet.getCode())
                            gotResponse = true;

                        switch (packet.getCode()) {
                            case 'V':
                                mScannerVersion = new String(packet.getData(), "ASCII");
                                break;

                            case 'I':
                                mMRZ = new String(packet.getData(), "ASCII").split("\r");
                                break;
                            default:
                                break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            return written;
        }

    } // class PassportScanner

//--------------------------------------------------------------------------------------------------
    public final class UsbId {

    public static final int VENDOR_FTDI = 0x0403;
    public static final int FTDI_FT232R = 0x6001;
    public static final int FTDI_FT231X = 0x6015;

    public static final int VENDOR_ATMEL = 0x03EB;
    public static final int ATMEL_LUFA_CDC_DEMO_APP = 0x2044;

    public static final int VENDOR_ARDUINO = 0x2341;
    public static final int ARDUINO_UNO = 0x0001;
    public static final int ARDUINO_MEGA_2560 = 0x0010;
    public static final int ARDUINO_SERIAL_ADAPTER = 0x003b;
    public static final int ARDUINO_MEGA_ADK = 0x003f;
    public static final int ARDUINO_MEGA_2560_R3 = 0x0042;
    public static final int ARDUINO_UNO_R3 = 0x0043;
    public static final int ARDUINO_MEGA_ADK_R3 = 0x0044;
    public static final int ARDUINO_SERIAL_ADAPTER_R3 = 0x0044;
    public static final int ARDUINO_LEONARDO = 0x8036;
    public static final int ARDUINO_MICRO = 0x8037;

    public static final int VENDOR_VAN_OOIJEN_TECH = 0x16c0;
    public static final int VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL = 0x0483;

    public static final int VENDOR_LEAFLABS = 0x1eaf;
    public static final int LEAFLABS_MAPLE = 0x0004;

    public static final int VENDOR_SILABS = 0x10c4;
    public static final int SILABS_CP2102 = 0xea60;
    public static final int SILABS_CP2105 = 0xea70;
    public static final int SILABS_CP2108 = 0xea71;
    public static final int SILABS_CP2110 = 0xea80;

    public static final int VENDOR_PROLIFIC = 0x067b;
    public static final int PROLIFIC_PL2303 = 0x2303;

    public static final int VENDOR_QINHENG = 0x1a86;
    public static final int QINHENG_HL340 = 0x7523;

    private UsbId() {
        throw new IllegalAccessError("Non-instantiable class.");
    }

}



    public interface UsbSerialPort {

        // 5 data bits.
        public static final int DATABITS_5 = 5;

        // 6 data bits.
        public static final int DATABITS_6 = 6;

        // 7 data bits.
        public static final int DATABITS_7 = 7;

        // 8 data bits.
        public static final int DATABITS_8 = 8;

        // No flow control.
        public static final int FLOWCONTROL_NONE = 0;

        // RTS/CTS input flow control.
        public static final int FLOWCONTROL_RTSCTS_IN = 1;

        // RTS/CTS output flow control.
        public static final int FLOWCONTROL_RTSCTS_OUT = 2;

        // XON/XOFF input flow control.
        public static final int FLOWCONTROL_XONXOFF_IN = 4;

        // XON/XOFF output flow control.
        public static final int FLOWCONTROL_XONXOFF_OUT = 8;

        // No parity.
        public static final int PARITY_NONE = 0;

        // Odd parity.
        public static final int PARITY_ODD = 1;

        // Even parity.
        public static final int PARITY_EVEN = 2;

        // Mark parity.
        public static final int PARITY_MARK = 3;

        // Space parity.
        public static final int PARITY_SPACE = 4;

        // 1 stop bit.
        public static final int STOPBITS_1 = 1;

        // 1.5 stop bits.
        public static final int STOPBITS_1_5 = 3;

        // 2 stop bits.
        public static final int STOPBITS_2 = 2;

        public UsbSerialDriver getDriver();

        //
        // Port number within driver.
        ///
        public int getPortNumber();

        //
        // The serial number of the underlying UsbDeviceConnection, or {@code null}.
        ///
        public String getSerial();

        //
        // Opens and initializes the port. Upon success, caller must ensure that
        // {@link #close()} is eventually called.
        //
        // @param connection an open device connection, acquired with
        //            {@link UsbManager#openDevice(android.hardware.usb.UsbDevice)}
        // @throws IOException on error opening or initializing the port.
        ///
        public void open(UsbDeviceConnection connection) throws IOException;

        //
        // Closes the port.
        //
        // @throws IOException on error closing the port.
        ///
        public void close() throws IOException;

        //
        // Reads as many bytes as possible into the destination buffer.
        //
        // @param dest the destination byte buffer
        // @param timeoutMillis the timeout for reading
        // @return the actual number of bytes read
        // @throws IOException if an error occurred during reading
        ///
        public int read(final byte[] dest, final int timeoutMillis) throws IOException;

        //
        // Writes as many bytes as possible from the source buffer.
        //
        // @param src the source byte buffer
        // @param timeoutMillis the timeout for writing
        // @return the actual number of bytes written
        // @throws IOException if an error occurred during writing
        ///
        public int write(final byte[] src, final int timeoutMillis) throws IOException;

        //
        // Sets various serial port parameters.
        //
        // @param baudRate baud rate as an integer, for example {@code 115200}.
        // @param dataBits one of {@link #DATABITS_5}, {@link #DATABITS_6},
        //            {@link #DATABITS_7}, or {@link #DATABITS_8}.
        // @param stopBits one of {@link #STOPBITS_1}, {@link #STOPBITS_1_5}, or
        //            {@link #STOPBITS_2}.
        // @param parity one of {@link #PARITY_NONE}, {@link #PARITY_ODD},
        //            {@link #PARITY_EVEN}, {@link #PARITY_MARK}, or
        //            {@link #PARITY_SPACE}.
        // @throws IOException on error setting the port parameters
        ///
        public void setParameters(
                int baudRate, int dataBits, int stopBits, int parity) throws IOException;

        //
        // Gets the CD (Carrier Detect) bit from the underlying UART.
        //
        // @return the current state, or {@code false} if not supported.
        // @throws IOException if an error occurred during reading
        ///
        public boolean getCD() throws IOException;

        //
        // Gets the CTS (Clear To Send) bit from the underlying UART.
        //
        // @return the current state, or {@code false} if not supported.
        // @throws IOException if an error occurred during reading
        ///
        public boolean getCTS() throws IOException;

        //
        // Gets the DSR (Data Set Ready) bit from the underlying UART.
        //
        // @return the current state, or {@code false} if not supported.
        // @throws IOException if an error occurred during reading
        ///
        public boolean getDSR() throws IOException;

        //
        // Gets the DTR (Data Terminal Ready) bit from the underlying UART.
        //
        // @return the current state, or {@code false} if not supported.
        // @throws IOException if an error occurred during reading
        ///
        public boolean getDTR() throws IOException;

        //
        // Sets the DTR (Data Terminal Ready) bit on the underlying UART, if
        // supported.
        //
        // @param value the value to set
        // @throws IOException if an error occurred during writing
        ///
        public void setDTR(boolean value) throws IOException;

        //
        // Gets the RI (Ring Indicator) bit from the underlying UART.
        //
        // @return the current state, or {@code false} if not supported.
        // @throws IOException if an error occurred during reading
        ///
        public boolean getRI() throws IOException;

        //
        // Gets the RTS (Request To Send) bit from the underlying UART.
        //
        // @return the current state, or {@code false} if not supported.
        // @throws IOException if an error occurred during reading
        ///
        public boolean getRTS() throws IOException;

        //
        // Sets the RTS (Request To Send) bit on the underlying UART, if
        // supported.
        //
        // @param value the value to set
        // @throws IOException if an error occurred during writing
        ///
        public void setRTS(boolean value) throws IOException;

        //
        // Flush non-transmitted output data and / or non-read input data
        // @param flushRX {@code true} to flush non-transmitted output data
        // @param flushTX {@code true} to flush non-read input data
        // @return {@code true} if the operation was successful, or
        // {@code false} if the operation is not supported by the driver or device
        // @throws IOException if an error occurred during flush
        ///
        public boolean purgeHwBuffers(boolean flushRX, boolean flushTX) throws IOException;

    }

    public interface UsbSerialDriver {

        //
        // Returns the raw {@link UsbDevice} backing this port.
        //
        // @return the device

        public UsbDevice getDevice();

        //
        // Returns all available ports for this device. This list must have at least
        // one entry.
        //
        // @return the ports
        ///
        public List<UsbSerialPort> getPorts();
    }

    public static class SerialInputOutputManager implements Runnable {

        private static final String TAG = SerialInputOutputManager.class.getSimpleName();
        private static final boolean DEBUG = true;

        private static final int READ_WAIT_MILLIS = 200;
        private static final int BUFSIZ = 4096;

        private final UsbSerialPort mDriver;

        private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);

        // Synchronized by 'mWriteBuffer'
        private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(BUFSIZ);

        private enum State {
            STOPPED,
            RUNNING,
            STOPPING
        }

        // Synchronized by 'this'
        private State mState = State.STOPPED;

        // Synchronized by 'this'
        private Listener mListener;

        public interface Listener {
            //
            // Called when new incoming data is available.
            ///
            public void onNewData(byte[] data);

            //
            // Called when {@link SerialInputOutputManager#run()} aborts due to an
            // error.
            ///
            public void onRunError(Exception e);
        }

        //
        // Creates a new instance with no listener.
        ///
        public SerialInputOutputManager(UsbSerialPort driver) {
            this(driver, null);
        }

        //
        // Creates a new instance with the provided listener.
        ///
        public SerialInputOutputManager(UsbSerialPort driver, Listener listener) {
            mDriver = driver;
            mListener = listener;
        }

        public synchronized void setListener(Listener listener) {
            mListener = listener;
        }

        public synchronized Listener getListener() {
            return mListener;
        }

        public void writeAsync(byte[] data) {
            synchronized (mWriteBuffer) {
                mWriteBuffer.put(data);
            }
        }

        public synchronized void stop() {
            if (getState() == State.RUNNING) {
                mState = State.STOPPING;
            }
        }

        private synchronized State getState() {
            return mState;
        }

        //
        // Continuously services the read and write buffers until {@link #stop()} is
        // called, or until a driver exception is raised.
        //
        // NOTE(mikey): Uses inefficient read/write-with-timeout.
        // TODO(mikey): Read asynchronously with {@link UsbRequest#queue(ByteBuffer, int)}
        ///
        @Override
        public void run() {
            synchronized (this) {
                if (getState() != State.STOPPED) {
                    throw new IllegalStateException("Already running.");
                }
                mState = State.RUNNING;
            }

            try {
                while (true) {
                    if (getState() != State.RUNNING) {
                        break;
                    }
                    step();
                }
            } catch (Exception e) {
                final Listener listener = getListener();
                if (listener != null) {
                    listener.onRunError(e);
                }
            } finally {
                synchronized (this) {
                    mState = State.STOPPED;
                }
            }
        }

        private void step() throws IOException {
            // Handle incoming data.
            int len = mDriver.read(mReadBuffer.array(), READ_WAIT_MILLIS);
            if (len > 0) {
                final Listener listener = getListener();
                if (listener != null) {
                    final byte[] data = new byte[len];
                    mReadBuffer.get(data, 0, len);
                    listener.onNewData(data);
                }
                mReadBuffer.clear();
            }

            // Handle outgoing data.
            byte[] outBuff = null;
            synchronized (mWriteBuffer) {
                len = mWriteBuffer.position();
                if (len > 0) {
                    outBuff = new byte[len];
                    mWriteBuffer.rewind();
                    mWriteBuffer.get(outBuff, 0, len);
                    mWriteBuffer.clear();
                }
            }
            if (outBuff != null) {
                mDriver.write(outBuff, READ_WAIT_MILLIS);
            }
        }

    }

    abstract class CommonUsbSerialPort implements UsbSerialPort {

        public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
        public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;

        protected final UsbDevice mDevice;
        protected final int mPortNumber;

        // non-null when open()
        protected UsbDeviceConnection mConnection = null;

        protected final Object mReadBufferLock = new Object();
        protected final Object mWriteBufferLock = new Object();

        // Internal read buffer.  Guarded by {@link #mReadBufferLock}.
        protected byte[] mReadBuffer;

        // Internal write buffer.  Guarded by {@link #mWriteBufferLock}.
        protected byte[] mWriteBuffer;

        public CommonUsbSerialPort(UsbDevice device, int portNumber) {
            mDevice = device;
            mPortNumber = portNumber;

            mReadBuffer = new byte[DEFAULT_READ_BUFFER_SIZE];
            mWriteBuffer = new byte[DEFAULT_WRITE_BUFFER_SIZE];
        }

        @Override
        public String toString() {
            return String.format("<%s device_name=%s device_id=%s port_number=%s>",
                    getClass().getSimpleName(), mDevice.getDeviceName(),
                    mDevice.getDeviceId(), mPortNumber);
        }

        //
        // Returns the currently-bound USB device.
        //
        // @return the device

        public final UsbDevice getDevice() {
            return mDevice;
        }

        @Override
        public int getPortNumber() {
            return mPortNumber;
        }

        //
        // Returns the device serial number
        //  @return serial number

        @Override
        public String getSerial() {
            return mConnection.getSerial();
        }

        //
        // Sets the size of the internal buffer used to exchange data with the USB
        // stack for read operations.  Most users should not need to change this.
        //
        // @param bufferSize the size in bytes

        public final void setReadBufferSize(int bufferSize) {
            synchronized (mReadBufferLock) {
                if (bufferSize == mReadBuffer.length) {
                    return;
                }
                mReadBuffer = new byte[bufferSize];
            }
        }

        //
        // Sets the size of the internal buffer used to exchange data with the USB
        // stack for write operations.  Most users should not need to change this.
        //
        // @param bufferSize the size in bytes

        public final void setWriteBufferSize(int bufferSize) {
            synchronized (mWriteBufferLock) {
                if (bufferSize == mWriteBuffer.length) {
                    return;
                }
                mWriteBuffer = new byte[bufferSize];
            }
        }

        @Override
        public abstract void open(UsbDeviceConnection connection) throws IOException;

        @Override
        public abstract void close() throws IOException;

        @Override
        public abstract int read(final byte[] dest, final int timeoutMillis) throws IOException;

        @Override
        public abstract int write(final byte[] src, final int timeoutMillis) throws IOException;

        @Override
        public abstract void setParameters(
                int baudRate, int dataBits, int stopBits, int parity) throws IOException;

        @Override
        public abstract boolean getCD() throws IOException;

        @Override
        public abstract boolean getCTS() throws IOException;

        @Override
        public abstract boolean getDSR() throws IOException;

        @Override
        public abstract boolean getDTR() throws IOException;

        @Override
        public abstract void setDTR(boolean value) throws IOException;

        @Override
        public abstract boolean getRI() throws IOException;

        @Override
        public abstract boolean getRTS() throws IOException;

        @Override
        public abstract void setRTS(boolean value) throws IOException;

        @Override
        public boolean purgeHwBuffers(boolean flushReadBuffers, boolean flushWriteBuffers) throws IOException {
            return !flushReadBuffers && !flushWriteBuffers;
        }

    }

    public class CdcAcmSerialDriver implements UsbSerialDriver {

        private final String TAG = CdcAcmSerialDriver.class.getSimpleName();

        private final UsbDevice mDevice;
        private final UsbSerialPort mPort;

        public CdcAcmSerialDriver(UsbDevice device) {
            mDevice = device;
            mPort = new CdcAcmSerialPort(device, 0);
        }

        @Override
        public UsbDevice getDevice() {
            return mDevice;
        }

        @Override
        public List<UsbSerialPort> getPorts() {
            return Collections.singletonList(mPort);
        }

        class CdcAcmSerialPort extends CommonUsbSerialPort {

            private final boolean mEnableAsyncReads;
            private UsbInterface mControlInterface;
            private UsbInterface mDataInterface;

            private UsbEndpoint mControlEndpoint;
            private UsbEndpoint mReadEndpoint;
            private UsbEndpoint mWriteEndpoint;

            private boolean mRts = false;
            private boolean mDtr = false;

            private static final int USB_RECIP_INTERFACE = 0x01;
            private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;

            private static final int SET_LINE_CODING = 0x20;  // USB CDC 1.1 section 6.2
            private static final int GET_LINE_CODING = 0x21;
            private static final int SET_CONTROL_LINE_STATE = 0x22;
            private static final int SEND_BREAK = 0x23;

            public CdcAcmSerialPort(UsbDevice device, int portNumber) {
                super(device, portNumber);
                mEnableAsyncReads = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);
            }

            @Override
            public UsbSerialDriver getDriver() {
                return CdcAcmSerialDriver.this;
            }

            @Override
            public void open(UsbDeviceConnection connection) throws IOException {
                if (mConnection != null) {
                    throw new IOException("Already open");
                }

                mConnection = connection;
                boolean opened = false;
                try {

                    if (1 == mDevice.getInterfaceCount()) {
                        Log.d(TAG,"device might be castrated ACM device, trying single interface logic");
                        openSingleInterface();
                    } else {
                        Log.d(TAG,"trying default interface logic");
                        openInterface();
                    }

                    if (mEnableAsyncReads) {
                        Log.d(TAG, "Async reads enabled");
                    } else {
                        Log.d(TAG, "Async reads disabled.");
                    }


                    opened = true;
                } finally {
                    if (!opened) {
                        mConnection = null;
                        // just to be on the save side
                        mControlEndpoint = null;
                        mReadEndpoint = null;
                        mWriteEndpoint = null;
                    }
                }
            }

            private void openSingleInterface() throws IOException {
                // the following code is inspired by the cdc-acm driver
                // in the linux kernel

                mControlInterface = mDevice.getInterface(0);
                Log.d(TAG, "Control iface=" + mControlInterface);

                mDataInterface = mDevice.getInterface(0);
                Log.d(TAG, "data iface=" + mDataInterface);

                if (!mConnection.claimInterface(mControlInterface, true)) {
                    throw new IOException("Could not claim shared control/data interface.");
                }

                int endCount = mControlInterface.getEndpointCount();

                if (endCount < 3) {
                    Log.d(TAG,"not enough endpoints - need 3. count=" + mControlInterface.getEndpointCount());
                    throw new IOException("Insufficient number of endpoints(" + mControlInterface.getEndpointCount() + ")");
                }

                // Analyse endpoints for their properties
                mControlEndpoint = null;
                mReadEndpoint = null;
                mWriteEndpoint = null;
                for (int i = 0; i < endCount; ++i) {
                    UsbEndpoint ep = mControlInterface.getEndpoint(i);
                    if ((ep.getDirection() == UsbConstants.USB_DIR_IN) &&
                            (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT)) {
                        Log.d(TAG,"Found controlling endpoint");
                        mControlEndpoint = ep;
                    } else if ((ep.getDirection() == UsbConstants.USB_DIR_IN) &&
                            (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        Log.d(TAG,"Found reading endpoint");
                        mReadEndpoint = ep;
                    } else if ((ep.getDirection() == UsbConstants.USB_DIR_OUT) &&
                            (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        Log.d(TAG,"Found writing endpoint");
                        mWriteEndpoint = ep;
                    }


                    if ((mControlEndpoint != null) &&
                            (mReadEndpoint != null) &&
                            (mWriteEndpoint != null)) {
                        Log.d(TAG,"Found all required endpoints");
                        break;
                    }
                }

                if ((mControlEndpoint == null) ||
                        (mReadEndpoint == null) ||
                        (mWriteEndpoint == null)) {
                    Log.d(TAG,"Could not establish all endpoints");
                    throw new IOException("Could not establish all endpoints");
                }
            }

            private void openInterface() throws IOException {
                Log.d(TAG, "claiming interfaces, count=" + mDevice.getInterfaceCount());

                mControlInterface = mDevice.getInterface(0);
                Log.d(TAG, "Control iface=" + mControlInterface);
                // class should be USB_CLASS_COMM

                if (!mConnection.claimInterface(mControlInterface, true)) {
                    throw new IOException("Could not claim control interface.");
                }

                mControlEndpoint = mControlInterface.getEndpoint(0);
                Log.d(TAG, "Control endpoint direction: " + mControlEndpoint.getDirection());

                Log.d(TAG, "Claiming data interface.");
                mDataInterface = mDevice.getInterface(1);
                Log.d(TAG, "data iface=" + mDataInterface);
                // class should be USB_CLASS_CDC_DATA

                if (!mConnection.claimInterface(mDataInterface, true)) {
                    throw new IOException("Could not claim data interface.");
                }
                mReadEndpoint = mDataInterface.getEndpoint(1);
                Log.d(TAG, "Read endpoint direction: " + mReadEndpoint.getDirection());
                mWriteEndpoint = mDataInterface.getEndpoint(0);
                Log.d(TAG, "Write endpoint direction: " + mWriteEndpoint.getDirection());
            }

            private int sendAcmControlMessage(int request, int value, byte[] buf) {
                return mConnection.controlTransfer(
                        USB_RT_ACM, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
            }

            @Override
            public void close() throws IOException {
                if (mConnection == null) {
                    throw new IOException("Already closed");
                }
                mConnection.close();
                mConnection = null;
            }

            @Override
            public int read(byte[] dest, int timeoutMillis) throws IOException {
                if (mEnableAsyncReads) {
                    final UsbRequest request = new UsbRequest();
                    try {
                        request.initialize(mConnection, mReadEndpoint);
                        final ByteBuffer buf = ByteBuffer.wrap(dest);
                        if (!request.queue(buf, dest.length)) {
                            throw new IOException("Error queueing request.");
                        }

                        final UsbRequest response = mConnection.requestWait();
                        if (response == null) {
                            throw new IOException("Null response");
                        }

                        final int nread = buf.position();
                        if (nread > 0) {
                            //Log.d(TAG, HexDump.dumpHexString(dest, 0, Math.min(32, dest.length)));
                            return nread;
                        } else {
                            return 0;
                        }
                    } finally {
                        request.close();
                    }
                }

                final int numBytesRead;
                synchronized (mReadBufferLock) {
                    int readAmt = Math.min(dest.length, mReadBuffer.length);
                    numBytesRead = mConnection.bulkTransfer(mReadEndpoint, mReadBuffer, readAmt,
                            timeoutMillis);
                    if (numBytesRead < 0) {
                        // This sucks: we get -1 on timeout, not 0 as preferred.
                        // We *should//use UsbRequest, except it has a bug/api oversight
                        // where there is no way to determine the number of bytes read
                        // in response :\ -- http://b.android.com/28023
                        if (timeoutMillis == Integer.MAX_VALUE) {
                            // Hack: Special case "~infinite timeout" as an error.
                            return -1;
                        }
                        return 0;
                    }
                    System.arraycopy(mReadBuffer, 0, dest, 0, numBytesRead);
                }
                return numBytesRead;
            }

            @Override
            public int write(byte[] src, int timeoutMillis) throws IOException {
                // TODO(mikey): Nearly identical to FtdiSerial write. Refactor.
                int offset = 0;

                while (offset < src.length) {
                    final int writeLength;
                    final int amtWritten;

                    synchronized (mWriteBufferLock) {
                        final byte[] writeBuffer;

                        writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                        if (offset == 0) {
                            writeBuffer = src;
                        } else {
                            // bulkTransfer does not support offsets, make a copy.
                            System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                            writeBuffer = mWriteBuffer;
                        }

                        amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                                timeoutMillis);
                    }
                    if (amtWritten <= 0) {
                        throw new IOException("Error writing " + writeLength
                                + " bytes at offset " + offset + " length=" + src.length);
                    }

                    Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
                    offset += amtWritten;
                }
                return offset;
            }

            @Override
            public void setParameters(int baudRate, int dataBits, int stopBits, int parity) {
                byte stopBitsByte;
                switch (stopBits) {
                    case STOPBITS_1: stopBitsByte = 0; break;
                    case STOPBITS_1_5: stopBitsByte = 1; break;
                    case STOPBITS_2: stopBitsByte = 2; break;
                    default: throw new IllegalArgumentException("Bad value for stopBits: " + stopBits);
                }

                byte parityBitesByte;
                switch (parity) {
                    case PARITY_NONE: parityBitesByte = 0; break;
                    case PARITY_ODD: parityBitesByte = 1; break;
                    case PARITY_EVEN: parityBitesByte = 2; break;
                    case PARITY_MARK: parityBitesByte = 3; break;
                    case PARITY_SPACE: parityBitesByte = 4; break;
                    default: throw new IllegalArgumentException("Bad value for parity: " + parity);
                }

                byte[] msg = {
                        (byte) ( baudRate & 0xff),
                        (byte) ((baudRate >> 8 ) & 0xff),
                        (byte) ((baudRate >> 16) & 0xff),
                        (byte) ((baudRate >> 24) & 0xff),
                        stopBitsByte,
                        parityBitesByte,
                        (byte) dataBits};
                sendAcmControlMessage(SET_LINE_CODING, 0, msg);
            }

            @Override
            public boolean getCD() throws IOException {
                return false;  // TODO
            }

            @Override
            public boolean getCTS() throws IOException {
                return false;  // TODO
            }

            @Override
            public boolean getDSR() throws IOException {
                return false;  // TODO
            }

            @Override
            public boolean getDTR() throws IOException {
                return mDtr;
            }

            @Override
            public void setDTR(boolean value) throws IOException {
                mDtr = value;
                setDtrRts();
            }

            @Override
            public boolean getRI() throws IOException {
                return false;  // TODO
            }

            @Override
            public boolean getRTS() throws IOException {
                return mRts;
            }

            @Override
            public void setRTS(boolean value) throws IOException {
                mRts = value;
                setDtrRts();
            }

            private void setDtrRts() {
                int value = (mRts ? 0x2 : 0) | (mDtr ? 0x1 : 0);
                sendAcmControlMessage(SET_CONTROL_LINE_STATE, value, null);
            }

        }

        public Map<Integer, int[]> getSupportedDevices() {
            final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
            supportedDevices.put(Integer.valueOf(UsbId.VENDOR_ARDUINO),
                    new int[] {
                            UsbId.ARDUINO_UNO,
                            UsbId.ARDUINO_UNO_R3,
                            UsbId.ARDUINO_MEGA_2560,
                            UsbId.ARDUINO_MEGA_2560_R3,
                            UsbId.ARDUINO_SERIAL_ADAPTER,
                            UsbId.ARDUINO_SERIAL_ADAPTER_R3,
                            UsbId.ARDUINO_MEGA_ADK,
                            UsbId.ARDUINO_MEGA_ADK_R3,
                            UsbId.ARDUINO_LEONARDO,
                            UsbId.ARDUINO_MICRO,
                    });
            supportedDevices.put(Integer.valueOf(UsbId.VENDOR_VAN_OOIJEN_TECH),
                    new int[] {
                            UsbId.VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL,
                    });
            supportedDevices.put(Integer.valueOf(UsbId.VENDOR_ATMEL),
                    new int[] {
                            UsbId.ATMEL_LUFA_CDC_DEMO_APP,
                    });
            supportedDevices.put(Integer.valueOf(UsbId.VENDOR_LEAFLABS),
                    new int[] {
                            UsbId.LEAFLABS_MAPLE,
                    });
            return supportedDevices;
        }

    } // class CdcAcmSerialDriver


    public class UsbSerialDevice {

        public UsbSerialDevice(UsbDevice device, UsbDeviceConnection connection) {
            if (connection != null) {
                usbSerialDriver = new CdcAcmSerialDriver(device);
                this.connection = connection;
            }
        }

        public UsbSerialDevice(UsbSerialDriver driver, UsbDeviceConnection connection) {
            this.usbSerialDriver = driver;
            this.connection = connection;
        }

        public boolean hasConnection() {
            return getPort() != null;
        }

        public void finalize() throws Throwable {
            pause();
            super.finalize();
        }

        public void pause() {
            synchronized (this) {
                try {
                    isOpen = false;
                    stopIoManager();
                ///Closing the port (at least for passport reader) makes it unusable until restart;
                 //even when it's called only from finalize (it looks like finalize gets called
                 //after some time of inactivity, but the object itself continue existing...) */
                /*if (closePort && getPort() != null) {
                    getPort().close();
                }*/
                } catch (Throwable e) {
                    // Ignore.
                    //Logger.getInstance().write(e);
                }
            }
        }

        public void resume() throws IOException {
            synchronized (this) {
                if (getPort() == null) {
                    throw new IOException("Device unavailable.");
                }
                stopIoManager();
                try {
                    getPort().open(connection);
                } catch (IOException e) {
                    // open method throws exception if the port is already open!
                    if (e.getMessage() == null || !e.getMessage().contains("Already open")) {
                        throw e;
                    }
                }
                startIoManager();
                isOpen = true;
            }
        }

        public boolean isOpen() {
            return isOpen;
        }

        protected UsbSerialPort getPort() {
            if (port == null) {
                if (usbSerialDriver == null || usbSerialDriver.getPorts() == null || usbSerialDriver.getPorts().isEmpty()) {
                    return null;
                }
                port = usbSerialDriver.getPorts().get(0);
            }
            return port;
        }

        protected synchronized void updateReceivedData(byte[] data) {
        }

        private UsbSerialDriver usbSerialDriver = null;
        private boolean isOpen = false;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private SerialInputOutputManager serialIoManager;
        private UsbDeviceConnection connection;
        private UsbSerialPort port;

        private final SerialInputOutputManager.Listener listener =
                new SerialInputOutputManager.Listener() {

                    @Override
                    public void onRunError(Exception e) {
                    }

                    @Override
                    public void onNewData(final byte[] data) {
                        updateReceivedData(data);
                    }
                };

        private void stopIoManager() {
            if (serialIoManager != null) {
                serialIoManager.stop();
                serialIoManager = null;
            }
        }

        private void startIoManager() {
            if (usbSerialDriver != null) {
                serialIoManager = new SerialInputOutputManager(usbSerialDriver.getPorts().get(0), listener);
                executor.submit(serialIoManager);
            }
        }

    } // class UsbSerialDevice



/*
    public interface CordovaInterface {

         // Launch an activity for which you would like a result when it finished. When this activity exits,
         // your onActivityResult() method will be called.
         //
         // @param command     The command object
         // @param intent      The intent to start
         // @param requestCode   The request code that is passed to callback to identify the activity
        abstract public void startActivityForResult(CordovaPlugin command, Intent intent, int requestCode);

         // Set the plugin to be called when a sub-activity exits.
         //
         // @param plugin      The plugin on which onActivityResult is to be called
        abstract public void setActivityResultCallback(CordovaPlugin plugin);

         // Get the Android activity.
        public abstract Activity getActivity();


         // Called when a message is sent to plugin.
         // @param id            The message id
         // @param data          The message data
         // @return              Object or null
        public Object onMessage(String id, Object data);

         // Returns a shared thread pool that can be used for background tasks.
        public ExecutorService getThreadPool();
    }

    public class CordovaWebView extends WebView {


        public static final String TAG = "CordovaWebView";
        public static final String CORDOVA_VERSION = "3.6.3";

        private HashSet<Integer> boundKeyCodes = new HashSet<Integer>();

        public PluginManager pluginManager;
        private boolean paused;

        private BroadcastReceiver receiver;


        /// Activities and other important classes 
        private CordovaInterface cordova;
        CordovaWebViewClient viewClient;
        private CordovaChromeClient chromeClient;

        // Flag to track that a loadUrl timeout occurred
        int loadUrlTimeout = 0;

        private long lastMenuEventTime = 0;

        CordovaBridge bridge;

        // custom view created by the browser (a video player for example) 
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;

        private CordovaResourceApi resourceApi;
        private Whitelist internalWhitelist;
        private Whitelist externalWhitelist;

        // The URL passed to loadUrl(), not necessarily the URL of the current page.
        String loadedUrl;
        private CordovaPreferences preferences;

        class ActivityResult {

            int request;
            int result;
            Intent incoming;

            public ActivityResult(int req, int res, Intent intent) {
                request = req;
                result = res;
                incoming = intent;
            }


        }

        final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);

        public CordovaWebView(Context context) {
            this(context, null);
        }

        public CordovaWebView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Deprecated
        public CordovaWebView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @TargetApi(11)
        @Deprecated
        public CordovaWebView(Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
            super(context, attrs, defStyle, privateBrowsing);
        }

        // Use two-phase init so that the control will work with XML layouts.
        public void init(CordovaInterface cordova, CordovaWebViewClient webViewClient, CordovaChromeClient webChromeClient,
                         List<PluginEntry> pluginEntries, Whitelist internalWhitelist, Whitelist externalWhitelist,
                         CordovaPreferences preferences) {
            if (this.cordova != null) {
                throw new IllegalStateException();
            }
            this.cordova = cordova;
            this.viewClient = webViewClient;
            this.chromeClient = webChromeClient;
            this.internalWhitelist = internalWhitelist;
            this.externalWhitelist = externalWhitelist;
            this.preferences = preferences;
            super.setWebChromeClient(webChromeClient);
            super.setWebViewClient(webViewClient);

            pluginManager = new PluginManager((org.apache.cordova.CordovaWebView) this, (org.apache.cordova.CordovaInterface) this.cordova, pluginEntries);
            bridge = new CordovaBridge(pluginManager, new NativeToJsMessageQueue(this, cordova));
            resourceApi = new CordovaResourceApi(this.getContext(), pluginManager);

            pluginManager.addService("App", "org.apache.cordova.App");
            initWebViewSettings();
            exposeJsInterface();
        }

        @SuppressWarnings("deprecation")
        private void initIfNecessary() {
            if (pluginManager == null) {
                Log.w(TAG, "CordovaWebView.init() was not called. This will soon be required.");
                // Before the refactor to a two-phase init, the Context needed to implement CordovaInterface.
                CordovaInterface cdv = (CordovaInterface)getContext();
                if (!Config.isInitialized()) {
                    Config.init(cdv.getActivity());
                }
                init(cdv, makeWebViewClient(cdv), makeWebChromeClient(cdv), Config.getPluginEntries(), Config.getWhitelist(), Config.getExternalWhitelist(), Config.getPreferences());
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        @SuppressWarnings("deprecation")
        private void initWebViewSettings() {
            this.setInitialScale(0);
            this.setVerticalScrollBarEnabled(false);
            // TODO: The Activity is the one that should call requestFocus().
            if (shouldRequestFocusOnInit()) {
                this.requestFocusFromTouch();
            }
            // Enable JavaScript
            WebSettings settings = this.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

            // Set the nav dump for HTC 2.x devices (disabling for ICS, deprecated entirely for Jellybean 4.2)
            try {
                Method gingerbread_getMethod =  WebSettings.class.getMethod("setNavDump", new Class[] { boolean.class });

                String manufacturer = android.os.Build.MANUFACTURER;
                Log.d(TAG, "CordovaWebView is running on device made by: " + manufacturer);
                if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB &&
                        android.os.Build.MANUFACTURER.contains("HTC"))
                {
                    gingerbread_getMethod.invoke(settings, true);
                }
            } catch (NoSuchMethodException e) {
                Log.d(TAG, "We are on a modern version of Android, we will deprecate HTC 2.3 devices in 2.8");
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Doing the NavDump failed with bad arguments");
            } catch (IllegalAccessException e) {
                Log.d(TAG, "This should never happen: IllegalAccessException means this isn't Android anymore");
            } catch (InvocationTargetException e) {
                Log.d(TAG, "This should never happen: InvocationTargetException means this isn't Android anymore.");
            }

            //We don't save any form data in the application
            settings.setSaveFormData(false);
            settings.setSavePassword(false);

            // Jellybean rightfully tried to lock this down. Too bad they didn't give us a whitelist
            // while we do this
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                Level16Apis.enableUniversalAccess(settings);
            // Enable database
            // We keep this disabled because we use or shim to get around DOM_EXCEPTION_ERROR_16
            String databasePath = getContext().getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
            settings.setDatabaseEnabled(true);
            settings.setDatabasePath(databasePath);


            //Determine whether we're in debug or release mode, and turn on Debugging!
            ApplicationInfo appInfo = getContext().getApplicationContext().getApplicationInfo();
            if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                enableRemoteDebugging();
            }

            settings.setGeolocationDatabasePath(databasePath);

            // Enable DOM storage
            settings.setDomStorageEnabled(true);

            // Enable built-in geolocation
            settings.setGeolocationEnabled(true);

            // Enable AppCache
            // Fix for CB-2282
            settings.setAppCacheMaxSize(5 //1048576);
            settings.setAppCachePath(databasePath);
            settings.setAppCacheEnabled(true);

            // Fix for CB-1405
            // Google issue 4641
            settings.getUserAgentString();

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            if (this.receiver == null) {
                this.receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        getSettings().getUserAgentString();
                    }
                };
                getContext().registerReceiver(this.receiver, intentFilter);
            }
            // end CB-1405
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        private void enableRemoteDebugging() {
            try {
                WebView.setWebContentsDebuggingEnabled(true);
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "You have one job! To turn on Remote Web Debugging! YOU HAVE FAILED! ");
                e.printStackTrace();
            }
        }

        public CordovaChromeClient makeWebChromeClient(CordovaInterface cordova) {
            return new CordovaChromeClient(cordova, this);
        }

        public CordovaWebViewClient makeWebViewClient(CordovaInterface cordova) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                return new CordovaWebViewClient(cordova, this);
            }
            return new IceCreamCordovaWebViewClient(cordova, this);
        }

         // Override this method to decide whether or not you need to request the
         // focus when your application start
         // @return true unless this method is overriden to return a different value
        protected boolean shouldRequestFocusOnInit() {
            return true;
        }

        private void exposeJsInterface() {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)) {
                Log.i(TAG, "Disabled addJavascriptInterface() bridge since Android version is old.");
                // Bug being that Java Strings do not get converted to JS strings automatically.
                // This isn't hard to work-around on the JS side, but it's easier to just
                // use the prompt bridge instead.
                return;
            }
            this.addJavascriptInterface(new ExposedJsApi(bridge), "_cordovaNative");
        }

        @Override
        public void setWebViewClient(WebViewClient client) {
            this.viewClient = (CordovaWebViewClient)client;
            super.setWebViewClient(client);
        }

        @Override
        public void setWebChromeClient(WebChromeClient client) {
            this.chromeClient = (CordovaChromeClient)client;
            super.setWebChromeClient(client);
        }

        public CordovaChromeClient getWebChromeClient() {
            return this.chromeClient;
        }


        public Whitelist getWhitelist() {
            return this.internalWhitelist;
        }

        public Whitelist getExternalWhitelist() {
            return this.externalWhitelist;
        }

         // Load the url into the webview.
        @Override
        public void loadUrl(String url) {
            if (url.equals("about:blank") || url.startsWith("javascript:")) {
                this.loadUrlNow(url);
            }
            else {
                this.loadUrlIntoView(url);
            }
        }

         // Load the url into the webview after waiting for period of time.
         // This is used to display the splashscreen for certain amount of time.
         // @param url
         // @param time              The number of ms to wait before loading webview
        @Deprecated
        public void loadUrl(final String url, int time) {
            if(url == null)
            {
                this.loadUrlIntoView(Config.getStartUrl());
            }
            else
            {
                this.loadUrlIntoView(url);
            }
        }

        public void loadUrlIntoView(final String url) {
            loadUrlIntoView(url, true);
        }

         // Load the url into the webview.
        public void loadUrlIntoView(final String url, boolean recreatePlugins) {
            LOG.d(TAG, ">>> loadUrl(" + url + ")");

            initIfNecessary();

            if (recreatePlugins) {
                this.loadedUrl = url;
                this.pluginManager.init();
            }

            // Create a timeout timer for loadUrl
            final org.apache.cordova.CordovaWebView me = this;
            final int currentLoadUrlTimeout = me.loadUrlTimeout;
            final int loadUrlTimeoutValue = Integer.parseInt(this.getProperty("LoadUrlTimeoutValue", "20000"));

            // Timeout error method
            final Runnable loadError = new Runnable() {
                public void run() {
                    me.stopLoading();
                    LOG.e(TAG, "CordovaWebView: TIMEOUT ERROR!");
                    if (viewClient != null) {
                        viewClient.onReceivedError(me, -6, "The connection to the server was unsuccessful.", url);
                    }
                }
            };

            // Timeout timer method
            final Runnable timeoutCheck = new Runnable() {
                public void run() {
                    try {
                        synchronized (this) {
                            wait(loadUrlTimeoutValue);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // If timeout, then stop loading and handle error
                    if (me.loadUrlTimeout == currentLoadUrlTimeout) {
                        me.cordova.getActivity().runOnUiThread(loadError);
                    }
                }
            };

            // Load url
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    cordova.getThreadPool().execute(timeoutCheck);
                    me.loadUrlNow(url);
                }
            });
        }

         // Load URL in webview.
        void loadUrlNow(String url) {
            if (LOG.isLoggable(LOG.DEBUG) && !url.startsWith("javascript:")) {
                LOG.d(TAG, ">>> loadUrlNow()");
            }
            if (url.startsWith("file://") || url.startsWith("javascript:") || internalWhitelist.isUrlWhiteListed(url)) {
                super.loadUrl(url);
            }
        }

         //Load the url into the webview after waiting for period of time.
         //This is used to display the splashscreen for certain amount of time.
         //@param url
         //@param time              The number of ms to wait before loading webview
        public void loadUrlIntoView(final String url, final int time) {

            // If not first page of app, then load immediately
            // Add support for browser history if we use it.
            if ((url.startsWith("javascript:")) || this.canGoBack()) {
            }

            // If first page, then show splashscreen
            else {

                LOG.d(TAG, "loadUrlIntoView(%s, %d)", url, time);

                // Send message to show splashscreen now if desired
                this.postMessage("splashscreen", "show");
            }

            // Load url
            this.loadUrlIntoView(url);
        }

        @Override
        public void stopLoading() {
            viewClient.isCurrentlyLoading = false;
            super.stopLoading();
        }

        public void onScrollChanged(int l, int t, int oldl, int oldt)
        {
            super.onScrollChanged(l, t, oldl, oldt);
            //We should post a message that the scroll changed
            ScrollEvent myEvent = new ScrollEvent(l, t, oldl, oldt, this);
            this.postMessage("onScrollChanged", myEvent);
        }

        
         //Send JavaScript statement back to JavaScript.
         //Deprecated (https://issues.apache.org/jira/browse/CB-6851)
         //Instead of executing snippets of JS, you should use the exec bridge
         //to create a Java->JS communication channel.
         //To do this:
         //1. Within plugin.xml (to have your JS run before deviceready):
         //   <js-module><runs/></js-module>
         //2. Within your .js (call exec on start-up):
         //   require('cordova/channel').onCordovaReady.subscribe(function() {
         //     require('cordova/exec')(win, null, 'Plugin', 'method', []);
         //     function win(message) {
         //       ... process message from java here ...
         //     }
         //   });
         //3. Within your .java:
         //   PluginResult dataResult = new PluginResult(PluginResult.Status.OK, CODE);
         //   dataResult.setKeepCallback(true);
         //   savedCallbackContext.sendPluginResult(dataResult);
        @Deprecated
        public void sendJavascript(String statement) {
            this.bridge.getMessageQueue().addJavaScript(statement);
        }

        
         //Send a plugin result back to JavaScript.
         //(This is a convenience method)
         //@param result
         //@param callbackId
        public void sendPluginResult(PluginResult result, String callbackId) {
            this.bridge.getMessageQueue().addPluginResult(result, callbackId);
        }

        
         //Send a message to all plugins.
         //@param id            The message id
         //@param data          The message data
        public void postMessage(String id, Object data) {
            if (this.pluginManager != null) {
                this.pluginManager.postMessage(id, data);
            }
        }


        
         //Go to previous page in history.  (We manage our own history)
         //@return true if we went back, false if we are already at top
        public boolean backHistory() {
            // Check webview first to see if there is a history
            // This is needed to support curPage#diffLink, since they are added to appView's history, but not our history url array (JQMobile behavior)
            if (super.canGoBack()) {
                super.goBack();
                return true;
            }
            return false;
        }


        
         //Load the specified URL in the Cordova webview or a new browser instance.
         //NOTE: If openExternal is false, only URLs listed in whitelist can be loaded.
         //@param url           The url to load.
         //@param openExternal  Load url in browser instead of Cordova webview.
         //@param clearHistory  Clear the history stack, so new page becomes top of history
         //@param params        Parameters for new app
        public void showWebPage(String url, boolean openExternal, boolean clearHistory, HashMap<String, Object> params) {
            LOG.d(TAG, "showWebPage(%s, %b, %b, HashMap", url, openExternal, clearHistory);

            // If clearing history
            if (clearHistory) {
                this.clearHistory();
            }

            // If loading into our webview
            if (!openExternal) {

                // Make sure url is in whitelist
                if (url.startsWith("file://") || internalWhitelist.isUrlWhiteListed(url)) {
                    // TODO: What about params?
                    // Load new URL
                    this.loadUrl(url);
                    return;
                }
                // Load in default viewer if not
                LOG.w(TAG, "showWebPage: Cannot load URL into webview since it is not in white list.  Loading into browser instead. (URL=" + url + ")");
            }
            try {
                // Omitting the MIME type for file: URLs causes "No Activity found to handle Intent".
                // Adding the MIME type to http: URLs causes them to not be handled by the downloader.
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.parse(url);
                if ("file".equals(uri.getScheme())) {
                    intent.setDataAndType(uri, resourceApi.getMimeType(uri));
                } else {
                    intent.setData(uri);
                }
                cordova.getActivity().startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                LOG.e(TAG, "Error loading url " + url, e);
            }
        }

        
         //Get string property for activity.
         //@param name
         //@param defaultValue
         //@return the String value for the named property
        public String getProperty(String name, String defaultValue) {
            Bundle bundle = this.cordova.getActivity().getIntent().getExtras();
            if (bundle == null) {
                return defaultValue;
            }
            name = name.toLowerCase(Locale.getDefault());
            Object p = bundle.get(name);
            if (p == null) {
                return defaultValue;
            }
            return p.toString();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event)
        {
            if(boundKeyCodes.contains(keyCode))
            {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    this.loadUrl("javascript:cordova.fireDocumentEvent('volumedownbutton');");
                    return true;
                }
                // If volumeup key
                else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    this.loadUrl("javascript:cordova.fireDocumentEvent('volumeupbutton');");
                    return true;
                }
                else
                {
                    return super.onKeyDown(keyCode, event);
                }
            }
            else if(keyCode == KeyEvent.KEYCODE_BACK)
            {
                return !(this.startOfHistory()) || isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK);
            }
            else if(keyCode == KeyEvent.KEYCODE_MENU)
            {
                //How did we get here?  Is there a childView?
                View childView = this.getFocusedChild();
                if(childView != null)
                {
                    //Make sure we close the keyboard if it's present
                    InputMethodManager imm = (InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(childView.getWindowToken(), 0);
                    cordova.getActivity().openOptionsMenu();
                    return true;
                } else {
                    return super.onKeyDown(keyCode, event);
                }
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event)
        {
            // If back key
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // A custom view is currently displayed  (e.g. playing a video)
                if(mCustomView != null) {
                    this.hideCustomView();
                    return true;
                } else {
                    // The webview is currently displayed
                    // If back key is bound, then send event to JavaScript
                    if (isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK)) {
                        this.loadUrl("javascript:cordova.fireDocumentEvent('backbutton');");
                        return true;
                    } else {
                        // If not bound
                        // Go to previous page in webview if it is possible to go back
                        if (this.backHistory()) {
                            return true;
                        }
                        // If not, then invoke default behavior
                    }
                }
            }
            // Legacy
            else if (keyCode == KeyEvent.KEYCODE_MENU) {
                if (this.lastMenuEventTime < event.getEventTime()) {
                    this.loadUrl("javascript:cordova.fireDocumentEvent('menubutton');");
                }
                this.lastMenuEventTime = event.getEventTime();
                return super.onKeyUp(keyCode, event);
            }
            // If search key
            else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
                this.loadUrl("javascript:cordova.fireDocumentEvent('searchbutton');");
                return true;
            }

            //Does webkit change this behavior?
            return super.onKeyUp(keyCode, event);
        }

        public void setButtonPlumbedToJs(int keyCode, boolean override) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_BACK:
                    // TODO: Why are search and menu buttons handled separately?
                    if (override) {
                        boundKeyCodes.add(keyCode);
                    } else {
                        boundKeyCodes.remove(keyCode);
                    }
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported keycode: " + keyCode);
            }
        }

        @Deprecated // Use setButtonPlumbedToJs() instead.
        public void bindButton(boolean override)
        {
            setButtonPlumbedToJs(KeyEvent.KEYCODE_BACK, override);
        }

        @Deprecated // Use setButtonPlumbedToJs() instead.
        public void bindButton(String button, boolean override) {
            if (button.compareTo("volumeup")==0) {
                setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_UP, override);
            }
            else if (button.compareTo("volumedown")==0) {
                setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_DOWN, override);
            }
        }

        @Deprecated // Use setButtonPlumbedToJs() instead.
        public void bindButton(int keyCode, boolean keyDown, boolean override) {
            setButtonPlumbedToJs(keyCode, override);
        }

        @Deprecated // Use isButtonPlumbedToJs
        public boolean isBackButtonBound()
        {
            return isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK);
        }

        public boolean isButtonPlumbedToJs(int keyCode)
        {
            return boundKeyCodes.contains(keyCode);
        }

        public void handlePause(boolean keepRunning)
        {
            LOG.d(TAG, "Handle the pause");
            // Send pause event to JavaScript
            this.loadUrl("javascript:try{cordova.fireDocumentEvent('pause');}catch(e){console.log('exception firing pause event from native');};");

            // Forward to plugins
            if (this.pluginManager != null) {
                this.pluginManager.onPause(keepRunning);
            }

            // If app doesn't want to run in background
            if (!keepRunning) {
                // Pause JavaScript timers (including setInterval)
                this.pauseTimers();
            }
            paused = true;

        }

        public void handleResume(boolean keepRunning, boolean activityResultKeepRunning)
        {

            this.loadUrl("javascript:try{cordova.fireDocumentEvent('resume');}catch(e){console.log('exception firing resume event from native');};");

            // Forward to plugins
            if (this.pluginManager != null) {
                this.pluginManager.onResume(keepRunning);
            }

            // Resume JavaScript timers (including setInterval)
            this.resumeTimers();
            paused = false;
        }

        public void handleDestroy()
        {
            // Send destroy event to JavaScript
            this.loadUrl("javascript:try{cordova.require('cordova/channel').onDestroy.fire();}catch(e){console.log('exception firing destroy event from native');};");

            // Load blank page so that JavaScript onunload is called
            this.loadUrl("about:blank");

            // Forward to plugins
            if (this.pluginManager != null) {
                this.pluginManager.onDestroy();
            }

            // unregister the receiver
            if (this.receiver != null) {
                try {
                    getContext().unregisterReceiver(this.receiver);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering configuration receiver: " + e.getMessage(), e);
                }
            }
        }

        public void onNewIntent(Intent intent)
        {
            //Forward to plugins
            if (this.pluginManager != null) {
                this.pluginManager.onNewIntent(intent);
            }
        }

        public boolean isPaused()
        {
            return paused;
        }

        @Deprecated // This never did anything.
        public boolean hadKeyEvent() {
            return false;
        }

        // Wrapping these functions in their own class prevents warnings in adb like:
        // VFY: unable to resolve virtual method 285: Landroid/webkit/WebSettings;.setAllowUniversalAccessFromFileURLs
        @TargetApi(16)
        private class Level16Apis {
            void enableUniversalAccess(WebSettings settings) {
                settings.setAllowUniversalAccessFromFileURLs(true);
            }
        }

        public void printBackForwardList() {
            WebBackForwardList currentList = this.copyBackForwardList();
            int currentSize = currentList.getSize();
            for(int i = 0; i < currentSize; ++i)
            {
                WebHistoryItem item = currentList.getItemAtIndex(i);
                String url = item.getUrl();
                LOG.d(TAG, "The URL at index: " + Integer.toString(i) + " is " + url );
            }
        }


        //Can Go Back is BROKEN!
        public boolean startOfHistory()
        {
            WebBackForwardList currentList = this.copyBackForwardList();
            WebHistoryItem item = currentList.getItemAtIndex(0);
            if( item!=null){	// Null-fence in case they haven't called loadUrl yet (CB-2458)
                String url = item.getUrl();
                String currentUrl = this.getUrl();
                LOG.d(TAG, "The current URL is: " + currentUrl);
                LOG.d(TAG, "The URL at item 0 is: " + url);
                return currentUrl.equals(url);
            }
            return false;
        }

        public void showCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            // This code is adapted from the original Android Browser code, licensed under the Apache License, Version 2.0
            Log.d(TAG, "showing Custom View");
            // if a view already exists then immediately terminate the new one
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }

            // Store the view and its callback for later (to kill it properly)
            mCustomView = view;
            mCustomViewCallback = callback;

            // Add the custom view to its container.
            ViewGroup parent = (ViewGroup) this.getParent();
            parent.addView(view, COVER_SCREEN_GRAVITY_CENTER);

            // Hide the content view.
            this.setVisibility(View.GONE);

            // Finally show the custom view container.
            parent.setVisibility(View.VISIBLE);
            parent.bringToFront();
        }

        public void hideCustomView() {
            // This code is adapted from the original Android Browser code, licensed under the Apache License, Version 2.0
            Log.d(TAG, "Hiding Custom View");
            if (mCustomView == null) return;

            // Hide the custom view.
            mCustomView.setVisibility(View.GONE);

            // Remove the custom view from its container.
            ViewGroup parent = (ViewGroup) this.getParent();
            parent.removeView(mCustomView);
            mCustomView = null;
            mCustomViewCallback.onCustomViewHidden();

            // Show the content view.
            this.setVisibility(View.VISIBLE);
        }

        
         //if the video overlay is showing then we need to know
         //as it effects back button handling
         //@return true if custom view is showing
        public boolean isCustomViewShowing() {
            return mCustomView != null;
        }

        public WebBackForwardList restoreState(Bundle savedInstanceState)
        {
            WebBackForwardList myList = super.restoreState(savedInstanceState);
            Log.d(TAG, "WebView restoration crew now restoring!");
            //Initialize the plugin manager once more
            this.pluginManager.init();
            return myList;
        }

        @Deprecated // This never did anything
        public void storeResult(int requestCode, int resultCode, Intent intent) {
        }

        public CordovaResourceApi getResourceApi() {
            return resourceApi;
        }

        public CordovaPreferences getPreferences() {
            return preferences;
        }
    }  // class CordovaWebView

    public class CordovaWebViewClient extends WebViewClient {

        private static final String TAG = "CordovaWebViewClient";
        CordovaInterface cordova;
        org.apache.cordova.CordovaWebView appView;
        CordovaUriHelper helper;
        private boolean doClearHistory = false;
        boolean isCurrentlyLoading;

        ///The authorization tokens.
        private Hashtable<String, AuthenticationToken> authenticationTokens = new Hashtable<String, AuthenticationToken>();

        @Deprecated
        public CordovaWebViewClient(CordovaInterface cordova) {
            this.cordova = cordova;
        }

        
         //Constructor.
         //@param cordova
         //@param view
        public CordovaWebViewClient(CordovaInterface cordova, org.apache.cordova.CordovaWebView view) {
            this.cordova = cordova;
            this.appView = view;
            helper = new CordovaUriHelper(cordova, view);
        }

        
         //Constructor.
         //@param view
        @Deprecated
        public void setWebView(org.apache.cordova.CordovaWebView view) {
            this.appView = view;
            helper = new CordovaUriHelper(cordova, view);
        }

        
         //Give the host application a chance to take over the control when a new url
         //is about to be loaded in the current WebView.
         //@param view          The WebView that is initiating the callback.
         //@param url           The url to be loaded.
         //@return              true to override, false for default behavior
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return helper.shouldOverrideUrlLoading(view, url);
        }

        
         //On received http auth request.
         //The method reacts on all registered authentication tokens. There is one and only one authentication token for any host + realm combination
         //@param view
         //@param handler
         //@param host
         //@param realm
        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

            // Get the authentication token
            AuthenticationToken token = this.getAuthenticationToken(host, realm);
            if (token != null) {
                handler.proceed(token.getUserName(), token.getPassword());
            }
            else {
                // Handle 401 like we'd normally do!
                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }
        }

        
         //Notify the host application that a page has started loading.
         //This method is called once for each main frame load so a page with iframes or framesets will call onPageStarted
         //one time for the main frame. This also means that onPageStarted will not be called when the contents of an
         //embedded frame changes, i.e. clicking a link whose target is an iframe.
         //@param view          The webview initiating the callback.
         //@param url           The url of the page.
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            isCurrentlyLoading = true;
            LOG.d(TAG, "onPageStarted(" + url + ")");
            // Flush stale messages.
            this.appView.bridge.reset(url);

            // Broadcast message that page has loaded
            this.appView.postMessage("onPageStarted", url);

            // Notify all plugins of the navigation, so they can clean up if necessary.
            if (this.appView.pluginManager != null) {
                this.appView.pluginManager.onReset();
            }
        }

        
         //Notify the host application that a page has finished loading.
         //This method is called only for main frame. When onPageFinished() is called, the rendering picture may not be updated yet.
         //@param view          The webview initiating the callback.
         //@param url           The url of the page.
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Ignore excessive calls.
            if (!isCurrentlyLoading) {
                return;
            }
            isCurrentlyLoading = false;
            LOG.d(TAG, "onPageFinished(" + url + ")");

            
             //Because of a timing issue we need to clear this history in onPageFinished as well as
             //onPageStarted. However we only want to do this if the doClearHistory boolean is set to
             //true. You see when you load a url with a # in it which is common in jQuery applications
             //onPageStared is not called. Clearing the history at that point would break jQuery apps.
            if (this.doClearHistory) {
                view.clearHistory();
                this.doClearHistory = false;
            }

            // Clear timeout flag
            this.appView.loadUrlTimeout++;

            // Broadcast message that page has loaded
            this.appView.postMessage("onPageFinished", url);

            // Make app visible after 2 sec in case there was a JS error and Cordova JS never initialized correctly
            if (this.appView.getVisibility() == View.INVISIBLE) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(2000);
                            cordova.getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    appView.postMessage("spinner", "stop");
                                }
                            });
                        } catch (InterruptedException e) {
                        }
                    }
                });
                t.start();
            }

            // Shutdown if blank loaded
            if (url.equals("about:blank")) {
                appView.postMessage("exit", null);
            }
        }

        
         //Report an error to the host application. These errors are unrecoverable (i.e. the main resource is unavailable).
         //The errorCode parameter corresponds to one of the ERROR_//constants.
         //@param view          The WebView that is initiating the callback.
         //@param errorCode     The error code corresponding to an ERROR_//value.
         //@param description   A String describing the error.
         //@param failingUrl    The url that failed to load.
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            // Ignore error due to stopLoading().
            if (!isCurrentlyLoading) {
                return;
            }
            LOG.d(TAG, "CordovaWebViewClient.onReceivedError: Error code=%s Description=%s URL=%s", errorCode, description, failingUrl);

            // Clear timeout flag
            this.appView.loadUrlTimeout++;

            // If this is a "Protocol Not Supported" error, then revert to the previous
            // page. If there was no previous page, then punt. The application's config
            // is likely incorrect (start page set to sms: or something like that)
            if (errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
                if (view.canGoBack()) {
                    view.goBack();
                    return;
                } else {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                }
            }

            // Handle other errors by passing them to the webview in JS
            JSONObject data = new JSONObject();
            try {
                data.put("errorCode", errorCode);
                data.put("description", description);
                data.put("url", failingUrl);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.appView.postMessage("onReceivedError", data);
        }

        
         //Notify the host application that an SSL error occurred while loading a resource.
         //The host application must call either handler.cancel() or handler.proceed().
         //Note that the decision may be retained for use in response to future SSL errors.
         //The default behavior is to cancel the load.
         //@param view          The WebView that is initiating the callback.
         //@param handler       An SslErrorHandler object that will handle the user's response.
         //@param error         The SSL error object.
       @TargetApi(8)
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {

            final String packageName = this.cordova.getActivity().getPackageName();
            final PackageManager pm = this.cordova.getActivity().getPackageManager();

            ApplicationInfo appInfo;
            try {
                appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    // debug = true
                    handler.proceed();
                    return;
                } else {
                    // debug = false
                    super.onReceivedSslError(view, handler, error);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // When it doubt, lock it out!
                super.onReceivedSslError(view, handler, error);
            }
        }


        
         //Sets the authentication token.
         //@param authenticationToken
         //@param host
         //@param realm
        public void setAuthenticationToken(AuthenticationToken authenticationToken, String host, String realm) {
            if (host == null) {
                host = "";
            }
            if (realm == null) {
                realm = "";
            }
            this.authenticationTokens.put(host.concat(realm), authenticationToken);
        }

        
         //Removes the authentication token.
         //@param host
         //@param realm
         //@return the authentication token or null if did not exist
        public AuthenticationToken removeAuthenticationToken(String host, String realm) {
            return this.authenticationTokens.remove(host.concat(realm));
        }

        
         //Gets the authentication token.
         //In order it tries:
         //1- host + realm
         //2- host
         //3- realm
         //4- no host, no realm
         //@param host
         //@param realm
         //@return the authentication token
        public AuthenticationToken getAuthenticationToken(String host, String realm) {
            AuthenticationToken token = null;
            token = this.authenticationTokens.get(host.concat(realm));

            if (token == null) {
                // try with just the host
                token = this.authenticationTokens.get(host);

                // Try the realm
                if (token == null) {
                    token = this.authenticationTokens.get(realm);
                }

                // if no host found, just query for default
                if (token == null) {
                    token = this.authenticationTokens.get("");
                }
            }

            return token;
        }

        
         //Clear all authentication tokens.
        public void clearAuthenticationTokens() {
            this.authenticationTokens.clear();
        }

    }   // class CordovaWebViewClient

    class CordovaUriHelper {

        private static final String TAG = "CordovaUriHelper";

        private org.apache.cordova.CordovaWebView appView;
        private CordovaInterface cordova;

        CordovaUriHelper(CordovaInterface cdv, org.apache.cordova.CordovaWebView webView)
        {
            appView = webView;
            cordova = cdv;
        }

        
         //Give the host application a chance to take over the control when a new url
         //is about to be loaded in the current WebView.
         //@param view          The WebView that is initiating the callback.
         //@param url           The url to be loaded.
         //@return              true to override, false for default behavior
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
        boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Give plugins the chance to handle the url
            if (this.appView.pluginManager.onOverrideUrlLoading(url)) {
                // Do nothing other than what the plugins wanted.
                // If any returned true, then the request was handled.
                return true;
            }
            else if(url.startsWith("file://") | url.startsWith("data:"))
            {
                //This directory on WebKit/Blink based webviews contains SQLite databases!
                //DON'T CHANGE THIS UNLESS YOU KNOW WHAT YOU'RE DOING!
                return url.contains("app_webview");
            }
            else if (appView.getWhitelist().isUrlWhiteListed(url)) {
                // Allow internal navigation
                return false;
            }
            else if (appView.getExternalWhitelist().isUrlWhiteListed(url))
            {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setComponent(null);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                        intent.setSelector(null);
                    }
                    this.cordova.getActivity().startActivity(intent);
                    return true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(TAG, "Error loading url " + url, e);
                }
            }
            // Intercept the request and do nothing with it -- block it
            return true;
        }
    }  // class CordovaUriHelper

    public class CordovaChromeClient extends WebChromeClient {

        public static final int FILECHOOSER_RESULTCODE = 5173;
        private String TAG = "CordovaLog";
        private long MAX_QUOTA = 100 //1024 //1024;
        protected CordovaInterface cordova;
        protected org.apache.cordova.CordovaWebView appView;

        // the video progress view
        private View mVideoProgressView;

        // File Chooser
        public ValueCallback<Uri> mUploadMessage;

        @Deprecated
        public CordovaChromeClient(CordovaInterface cordova) {
            this.cordova = cordova;
        }

        public CordovaChromeClient(CordovaInterface ctx, org.apache.cordova.CordovaWebView app) {
            this.cordova = ctx;
            this.appView = app;
        }

        @Deprecated
        public void setWebView(org.apache.cordova.CordovaWebView view) {
            this.appView = view;
        }

         //Tell the client to display a javascript alert dialog.
         //@param view
         //@param url
         //@param message
         //@param result
         //@see Other implementation in the Dialogs plugin.
        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            AlertDialog.Builder dlg = new AlertDialog.Builder(this.cordova.getActivity());
            dlg.setMessage(message);
            dlg.setTitle("Alert");
            //Don't let alerts break the back button
            dlg.setCancelable(true);
            dlg.setPositiveButton(android.R.string.ok,
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    });
            dlg.setOnCancelListener(
                    new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    });
            dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
                //DO NOTHING
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK)
                    {
                        result.confirm();
                        return false;
                    }
                    else
                        return true;
                }
            });
            dlg.show();
            return true;
        }

         //Tell the client to display a confirm dialog to the user.
         //@param view
         //@param url
         //@param message
         //@param result
         //@see Other implementation in the Dialogs plugin.
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            AlertDialog.Builder dlg = new AlertDialog.Builder(this.cordova.getActivity());
            dlg.setMessage(message);
            dlg.setTitle("Confirm");
            dlg.setCancelable(true);
            dlg.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    });
            dlg.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    });
            dlg.setOnCancelListener(
                    new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    });
            dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
                //DO NOTHING
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK)
                    {
                        result.cancel();
                        return false;
                    }
                    else
                        return true;
                }
            });
            dlg.show();
            return true;
        }

         //Tell the client to display a prompt dialog to the user.
         //If the client returns true, WebView will assume that the client will
         //handle the prompt dialog and call the appropriate JsPromptResult method.
         //Since we are hacking prompts for our own purposes, we should not be using them for
         //this purpose, perhaps we should hack console.log to do this instead!
         //@see Other implementation in the Dialogs plugin.
        @Override
        public boolean onJsPrompt(WebView view, String origin, String message, String defaultValue, JsPromptResult result) {
            // Unlike the @JavascriptInterface bridge, this method is always called on the UI thread.
            String handledRet = appView.bridge.promptOnJsPrompt(origin, message, defaultValue);
            if (handledRet != null) {
                result.confirm(handledRet);
            } else {
                // Returning false would also show a dialog, but the default one shows the origin (ugly).
                final JsPromptResult res = result;
                AlertDialog.Builder dlg = new AlertDialog.Builder(this.cordova.getActivity());
                dlg.setMessage(message);
                final EditText input = new EditText(this.cordova.getActivity());
                if (defaultValue != null) {
                    input.setText(defaultValue);
                }
                dlg.setView(input);
                dlg.setCancelable(false);
                dlg.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String usertext = input.getText().toString();
                                res.confirm(usertext);
                            }
                        });
                dlg.setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                res.cancel();
                            }
                        });
                dlg.show();
            }
            return true;
        }

         //Handle database quota exceeded notification.
        @Override
        public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
                                            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater)
        {
            LOG.d(TAG, "onExceededDatabaseQuota estimatedSize: %d  currentQuota: %d  totalUsedQuota: %d", estimatedSize, currentQuota, totalUsedQuota);
            quotaUpdater.updateQuota(MAX_QUOTA);
        }

        // console.log in api level 7: http://developer.android.com/guide/developing/debug-tasks.html
        // Expect this to not compile in a future Android release!
        @SuppressWarnings("deprecation")
        @Override
        public void onConsoleMessage(String message, int lineNumber, String sourceID)
        {
            //This is only for Android 2.1
            if(android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.ECLAIR_MR1)
            {
                LOG.d(TAG, "%s: Line %d : %s", sourceID, lineNumber, message);
                super.onConsoleMessage(message, lineNumber, sourceID);
            }
        }

        @TargetApi(8)
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage)
        {
            if (consoleMessage.message() != null)
                LOG.d(TAG, "%s: Line %d : %s" , consoleMessage.sourceId() , consoleMessage.lineNumber(), consoleMessage.message());
            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        
         //Instructs the client to show a prompt to ask the user to set the Geolocation permission state for the specified origin.
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            super.onGeolocationPermissionsShowPrompt(origin, callback);
            callback.invoke(origin, true, false);
        }

        // API level 7 is required for this, see if we could lower this using something else
        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            this.appView.showCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            this.appView.hideCustomView();
        }

        @Override
        
         //Ask the host application for a custom progress view to show while
         //a <video> is loading.
         //@return View The progress view.
        public View getVideoLoadingProgressView() {

            if (mVideoProgressView == null) {
                // Create a new Loading view programmatically.

                // create the linear layout
                LinearLayout layout = new LinearLayout(this.appView.getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                layout.setLayoutParams(layoutParams);
                // the proress bar
                ProgressBar bar = new ProgressBar(this.appView.getContext());
                LinearLayout.LayoutParams barLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                barLayoutParams.gravity = Gravity.CENTER;
                bar.setLayoutParams(barLayoutParams);
                layout.addView(bar);

                mVideoProgressView = layout;
            }
            return mVideoProgressView;
        }

        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            this.openFileChooser(uploadMsg, "*");
        }

        public void openFileChooser( ValueCallback<Uri> uploadMsg, String acceptType ) {
            this.openFileChooser(uploadMsg, acceptType, null);
        }

        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture)
        {
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*");
            this.cordova.getActivity().startActivityForResult(Intent.createChooser(i, "File Browser"),
                    FILECHOOSER_RESULTCODE);
        }

        public ValueCallback<Uri> getValueCallback() {
            return this.mUploadMessage;
        }
    }  // class CordovaChromeClient
*/


}
