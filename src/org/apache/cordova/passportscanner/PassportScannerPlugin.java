package org.apache.cordova.passportscanner;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.InputDevice;
import android.view.View;
import android.widget.ImageView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private HashMap<Integer, ConnectedDevice> mConnections =
            new HashMap<Integer, ConnectedDevice>();
    private static int mNextConnectionId = 1;

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        for (ConnectedDevice d : mConnections.values()) {
            d.close();
        }
        mConnections.clear();
        unregisterReceiver();
    }

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
        //final JSONObject params = args.getJSONObject(ARG_INDEX_PARAMS);
        //final CordovaArgs finalArgs = args;
        //Log.d(TAG, "Action: " + action + " params: " + params);
        Log.d(TAG, "Action: " + action);
        this.openCallbackContext = callbackContext;

        try {
            //if (action.equals(ACTION_SWITCH_ON)) {
            //} else if (action.equals(ACTION_SWITCH_OFF)) {

            if ("hasUsbHostFeature".equals(action)) {
                boolean usbHostFeature = cordova.getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, usbHostFeature));
                return true;
            } else if (action.equals(ACTION_AVAILABLE)) {
            //} else if ("available".equals(action)) {
                openCallbackContext.success(1);
                return true;

                //boolean usbHostFeature = cordova.getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
                //callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, usbHostFeature));
                //return true;
            } else if (action.equals(ACTION_FIND_DEVICES)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        String result = "";
                        try {
                            result = findDevices();
                            String passportScannerStr = passportScanner == null? "passportScanner=null" : passportScanner.toString();
                            openCallbackContext.success("findDevices : " + result);
                        } catch (Exception e) {
                            openCallbackContext.error("Error. PassportScannerPlugin -> findDevices : " + e.getMessage() + " " + result);
                        }
                    }
                });
                return true;
            }

/*
            else if ("getDevices".equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            getDevices(args, params, callbackContext);
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
                return true;
            } else if ("openDevice".equals(action)) {
                this.openCallbackContext = callbackContext;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            openDevice(args, params, callbackContext);
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
                return true;
            } else if ("closeDevice".equals(action)) {
                closeDevice(args, params, callbackContext);
                return true;
            } else if ("listInterfaces".equals(action)) {
                listInterfaces(args, params, callbackContext);
                return true;
            } else if ("claimInterface".equals(action)) {
                claimInterface(args, params, callbackContext);
                return true;
            } else if ("releaseInterface".equals(action)) {
                releaseInterface(args, params, callbackContext);
                return true;
            }
*/

        } catch (UsbError e) {
            callbackContext.error(e.getMessage());
            return true;
        }
//        catch (JSONException e) {
//            callbackContext.error(e.getMessage());
//            return true;
//        }

        return false;
    }

    private String findDevices() {
        try {
            DeviceWrapper dw = new DeviceWrapper();
            DeviceWrapper barcodeReaderDevice = dw.forBarcodeReader();
            //resultFindDevice = barcodeReaderDevice.getName();
            new DeviceFinder(new DeviceFinder.EventListener() {
                @Override
                public void onDeviceFound(DeviceWrapper device) {
                    String name = device.getName();
                    resultFindDevice = "device.getName() = " + device.getName();
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
            // DeviceWrapper.forBluetoothPrinterREGO(), <- removed to make contextRegoPrinter == null and don't use Rego printer
        }
        catch (Throwable e) {
            return e.getMessage();
        }
        return resultFindDevice;
    }







//--------------------------------------------------------------------------------------------------

    private boolean filterDevice(UsbDevice device, JSONArray filters) throws JSONException {
        if (filters == null) {
            return true;
        }
        Log.d(TAG, "filtering " + filters);
        for (int filterIdx = 0; filterIdx < filters.length(); filterIdx++) {
            JSONObject filter = filters.getJSONObject(filterIdx);
            int vendorId = filter.optInt("vendorId", -1);
            if (vendorId != -1) {
                if (device.getVendorId() != vendorId) {
                    continue;
                }
            }
            int productId = filter.optInt("productId", -1);
            if (productId != -1) {
                if (device.getProductId() != productId) {
                    continue;
                }
            }
            int interfaceClass = filter.optInt("interfaceClass", -1);
            int interfaceSubclass = filter.optInt("interfaceSubclass", -1);
            int interfaceProtocol = filter.optInt("interfaceProtocol", -1);
            if (interfaceClass == -1 && interfaceSubclass == -1 && interfaceProtocol == -1) {
                return true;
            }
            int interfaceCount = device.getInterfaceCount();
            for (int interfaceIdx = 0; interfaceIdx < interfaceCount; interfaceIdx++) {
                UsbInterface usbInterface = device.getInterface(interfaceIdx);
                if (interfaceClass != -1) {
                    if (interfaceClass != usbInterface.getInterfaceClass()) {
                        continue;
                    }
                }
                if (interfaceSubclass != -1) {
                    if (interfaceSubclass != usbInterface.getInterfaceSubclass()) {
                        continue;
                    }
                }
                if (interfaceProtocol != -1) {
                    if (interfaceProtocol != usbInterface.getInterfaceProtocol()) {
                        continue;
                    }
                }
                return true;
            }
        }
        return false;
    }

    // Used when instantiated via reflection by PluginManager
    public PassportScannerPlugin() {
    }

    // Encapsulates the Android UsbDevice and UsbDeviceConnection classes, and provides wrappers
    // around the UsbInterface and UsbEndpoint methods to allow for mocking.
    private static abstract class ConnectedDevice {
        abstract int getInterfaceCount();
        abstract int getEndpointCount(int interfaceNumber);
        abstract void describeInterface(int interfaceNumber, JSONObject result)
                throws JSONException;
        abstract void describeEndpoint(int interfaceNumber, int endpointNumber, JSONObject result)
                throws JSONException;
        abstract boolean claimInterface(int interfaceNumber);
        abstract boolean releaseInterface(int interfaceNumber);
        abstract int controlTransfer(int requestType, int request, int value, int index,
                                     byte[] transferBuffer, byte[] receiveBuffer, int timeout);
        abstract int bulkTransfer(int interfaceNumber, int endpointNumber, int direction,
                                  byte[] buffer, int timeout) throws UsbError;
        abstract int interruptTransfer(int interfaceNumber, int endpointNumber, int direction,
                                       byte[] buffer, int timeout) throws UsbError;
        abstract void close();
    };


    private void getDevices(CordovaArgs args, JSONObject params,
                            final CallbackContext callbackContext) throws JSONException, UsbError {
        HashMap<String, UsbDevice> devices = mUsbManager.getDeviceList();
        JSONArray filters = params.optJSONArray("filters");
        JSONArray result = new JSONArray();
        for (UsbDevice device: devices.values()) {
            if (filterDevice(device, filters)) {
                addDeviceToArray(result, device.getDeviceId(), device.getVendorId(),
                        device.getProductId());
            }
        }
        if (params.optBoolean("appendFakeDevice", false)) {
            addDeviceToArray(result, FakeDevice.ID, FakeDevice.VID, FakeDevice.PID);
        }
        callbackContext.success(result);
    }
    private static void addDeviceToArray(JSONArray result, int deviceId, int vendorId,
                                         int productId) throws JSONException {
        JSONObject jsonDev = new JSONObject();
        jsonDev.put("device", deviceId);
        jsonDev.put("vendorId", vendorId);
        jsonDev.put("productId", productId);
        result.put(jsonDev);
    }

    private void openDevice(CordovaArgs args, JSONObject params, final CallbackContext callbackContext) throws JSONException, UsbError {
        // First recover the device object from Id.
    }
    private void closeDevice(CordovaArgs args, JSONObject params,
                             final CallbackContext callbackContext) throws JSONException, UsbError {
        int handle = params.getInt("handle");
        ConnectedDevice d = mConnections.remove(handle);
        if (d != null) {
            d.close();
        }
        callbackContext.success();
    }
    private void listInterfaces(CordovaArgs args, JSONObject params,
                                final CallbackContext callbackContext) throws JSONException, UsbError {
        ConnectedDevice dev = getDevice(params);
        JSONArray jsonInterfaces = new JSONArray();
        int interfaceCount = dev.getInterfaceCount();
        for (int i = 0; i < interfaceCount; i++) {
            JSONArray jsonEndpoints = new JSONArray();
            int endpointCount = dev.getEndpointCount(i);
            for (int j = 0; j < endpointCount; j++) {
                JSONObject jsonEp = new JSONObject();
                dev.describeEndpoint(i, j, jsonEp);
                jsonEp.put("address", i << ENDPOINT_IF_SHIFT | j);
                if (!jsonEp.getString("type").startsWith("i")) {
                    // Only interrupt and isochronous endpoints have pollingInterval.
                    jsonEp.remove("pollingInterval");
                }
                jsonEp.put("extra_data", new JSONObject());
                jsonEndpoints.put(jsonEp);
            }
            JSONObject jsonIf = new JSONObject();
            dev.describeInterface(i, jsonIf);
            jsonIf.put("interfaceNumber", i);
            jsonIf.put("endpoints", jsonEndpoints);
            jsonInterfaces.put(jsonIf);
        }
        callbackContext.success(jsonInterfaces);
    }
    private void claimInterface(CordovaArgs args, JSONObject params,
                                final CallbackContext callbackContext) throws JSONException, UsbError {
        ConnectedDevice dev = getDevice(params);
        int interfaceNumber = getInterfaceNumber(params, dev);
        if (!dev.claimInterface(interfaceNumber)) {
            throw new UsbError("claimInterface returned false for i/f: " + interfaceNumber);
        }
        callbackContext.success();
    }
    private void releaseInterface(CordovaArgs args, JSONObject params,
                                  final CallbackContext callbackContext) throws JSONException, UsbError {
        ConnectedDevice dev = getDevice(params);
        int interfaceNumber = getInterfaceNumber(params, dev);
        if (!dev.releaseInterface(interfaceNumber)) {
            throw new UsbError("releaseInterface returned false for i/f: " + interfaceNumber);
        }
        callbackContext.success();
    }
    private void controlTransfer(CordovaArgs args, JSONObject params,
                                 final CallbackContext callbackContext) throws JSONException, UsbError {
        ConnectedDevice dev = getDevice(params);

        int direction = directionFromName(params.getString("direction"));
        int requestType = controlRequestTypeFromName(params.getString("requestType"));
        int recipient = recipientFromName(params.getString("recipient"));

        byte[] transferBuffer = getByteBufferForTransfer(args, params, UsbConstants.USB_DIR_OUT);
        byte[] receiveBuffer = getByteBufferForTransfer(args, params, UsbConstants.USB_DIR_IN);

        int ret = dev.controlTransfer(
                direction | requestType | recipient,
                params.getInt("request"),
                params.getInt("value"),
                params.getInt("index"),
                transferBuffer,
                receiveBuffer,
                params.getInt("timeout"));
        if (ret < 0) {
            throw new UsbError("Control transfer returned " + ret);
        }

        /* control transfer is bidirectional, buffer should alway be passed */
        callbackContext.success(Arrays.copyOf(receiveBuffer, receiveBuffer.length));
    }
    private void bulkTransfer(CordovaArgs args, JSONObject params,
                              final CallbackContext callbackContext) throws JSONException, UsbError {
        ConnectedDevice dev = getDevice(params);
        int endpointAddress = params.getInt("endpoint");
        int interfaceNumber = endpointAddress >> ENDPOINT_IF_SHIFT;
        int endpointNumber = endpointAddress & ((1 << ENDPOINT_IF_SHIFT) - 1);
        if (interfaceNumber >= dev.getInterfaceCount() ||
                endpointNumber >= dev.getEndpointCount(interfaceNumber)) {
            throw new UsbError("Enpoint not found: " + endpointAddress);
        }
        int direction = directionFromName(params.getString("direction"));
        byte[] buffer = getByteBufferForTransfer(args, params, direction);

        int ret = dev.bulkTransfer(interfaceNumber, endpointNumber, direction, buffer,
                params.getInt("timeout"));
        if (ret < 0) {
            throw new UsbError("Bulk transfer returned " + ret);
        }
        if (direction == UsbConstants.USB_DIR_IN) {
            callbackContext.success(Arrays.copyOf(buffer, ret));
        } else {
            callbackContext.success();
        }
    }
    private void interruptTransfer(CordovaArgs args, JSONObject params,
                                   final CallbackContext callbackContext) throws JSONException, UsbError {
        ConnectedDevice dev = getDevice(params);
        int endpointAddress = params.getInt("endpoint");
        int interfaceNumber = endpointAddress >> ENDPOINT_IF_SHIFT;
        int endpointNumber = endpointAddress & ((1 << ENDPOINT_IF_SHIFT) - 1);
        if (interfaceNumber >= dev.getInterfaceCount() ||
                endpointNumber >= dev.getEndpointCount(interfaceNumber)) {
            throw new UsbError("Enpoint not found: " + endpointAddress);
        }

        int direction = directionFromName(params.getString("direction"));
        byte[] buffer = getByteBufferForTransfer(args, params, direction);

        int ret = dev.interruptTransfer(interfaceNumber, endpointNumber, direction, buffer,
                params.getInt("timeout"));
        if (ret < 0) {
            throw new UsbError("Interrupt transfer returned " + ret);
        }
        if (direction == UsbConstants.USB_DIR_IN) {
            callbackContext.success(Arrays.copyOf(buffer, ret));
        } else {
            callbackContext.success();
        }
    }
    private ConnectedDevice getDevice(JSONObject params) throws JSONException, UsbError {
        int handle = params.getInt("handle");
        ConnectedDevice d = mConnections.get(handle);
        if (d == null) {
            throw new UsbError("Unknown connection handle: " + handle);
        }
        return d;
    }
    private int getInterfaceNumber(JSONObject params, ConnectedDevice device)
            throws JSONException, UsbError {
        int interfaceNumber = params.getInt("interfaceNumber");
        if (interfaceNumber >= device.getInterfaceCount()) {
            throw new UsbError("interface number " + interfaceNumber + " out of range 0.."
                    + device.getInterfaceCount());
        }
        return interfaceNumber;
    }

    // Internal exception type used to simplify the action dispatcher error paths.
    private static class UsbError extends RuntimeException {
        UsbError(String msg) {
            super(msg);
        }
    }

    // Concrete subclass of ConnectedDevice that routes calls through to the real Android APIs.
    // The implementation of this class is by design very minimalist: if the methods are kept free
    // of logic/control statements as the test strategy (see FakeDevice) does not cover this class.
    private static class RealDevice extends ConnectedDevice {
        RealDevice(UsbDevice device, UsbDeviceConnection connection) {
            mDevice = device;
            mConnection = connection;
        }

        private final UsbDevice mDevice;
        private final UsbDeviceConnection mConnection;

        int getInterfaceCount() {
            return mDevice.getInterfaceCount();
        }
        int getEndpointCount(int interfaceNumber) {
            return mDevice.getInterface(interfaceNumber).getEndpointCount();
        }
        void describeInterface(int interfaceNumber, JSONObject res) throws JSONException {
            UsbInterface i = mDevice.getInterface(interfaceNumber);
            res.put("alternateSetting", 0);  // TODO: In LOLLIPOP use i.getAlternateSetting());
            res.put("interfaceClass", i.getInterfaceClass());
            res.put("interfaceProtocol", i.getInterfaceProtocol());
            res.put("interfaceSubclass", i.getInterfaceSubclass());
            byte[] rawDescriptors = mConnection.getRawDescriptors();
            byte[] extraDescriptor = null;
            int idx = 0;
            while (idx < rawDescriptors.length) {
                byte length = rawDescriptors[idx];
                byte descriptorType = rawDescriptors[idx + 1];
                if (descriptorType == 0x04) {
                    // interface descriptor
                    int descriptorInterfaceNumber = rawDescriptors[idx + 2];
                    if (interfaceNumber == descriptorInterfaceNumber) {
                        Log.d(TAG, "Interface descriptor found: " + interfaceNumber);
                        idx += length;
                        if (idx < rawDescriptors.length) {
                            length = rawDescriptors[idx];
                            descriptorType = rawDescriptors[idx + 1];
                            if (descriptorType == 0x01 || descriptorType == 0x02 || descriptorType == 0x04 || descriptorType == 0x05) {
                                break;
                            }
                            extraDescriptor = new byte[length];
                            System.arraycopy(rawDescriptors, idx, extraDescriptor, 0, length);
                            break;
                        }
                        break;
                    }
                }
                idx += length;
            }
            if (null == extraDescriptor) {
                extraDescriptor = new byte[0];
            }
            res.put("extra_data", Base64.encodeToString(extraDescriptor, Base64.NO_WRAP));
        }
        void describeEndpoint(int interfaceNumber, int endpointNumber, JSONObject res)
                throws JSONException {
            UsbEndpoint ep = mDevice.getInterface(interfaceNumber).getEndpoint(endpointNumber);
            res.put("direction", directionName(ep.getDirection()));
            res.put("maximumPacketSize", ep.getMaxPacketSize());
            res.put("pollingInterval", ep.getInterval());
            res.put("type", endpointTypeName(ep.getType()));
        }
        boolean claimInterface(int interfaceNumber) {
            return mConnection.claimInterface(mDevice.getInterface(interfaceNumber), true);
        }
        boolean releaseInterface(int interfaceNumber) {
            return mConnection.releaseInterface(mDevice.getInterface(interfaceNumber));
        }
        private static String getResultString(byte[] in) {
            String ret = "";

            for(int n = 0; n < in.length; n++) {
                String s = "0" + Integer.toHexString(in[n]);
                ret += s.substring(s.length() - 2) + " ";
            }

            return ret;
        }
        int controlTransfer(int requestType, int request, int value, int index,
                            byte[] transferBuffer, byte[] receiveBuffer, int timeout) {
            UsbEndpoint ep = mDevice.getInterface(0).getEndpoint(0);
            int result = -1;

            if(ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT &&
                    ep.getDirection() == UsbConstants.USB_DIR_IN) {
                ByteBuffer bb = ByteBuffer.wrap(receiveBuffer);
                UsbRequest ur = new UsbRequest();

                ur.initialize(mConnection, ep);

                ur.queue(bb, receiveBuffer.length);

                result = mConnection.controlTransfer(requestType, request, value, index,
                        transferBuffer, transferBuffer.length, timeout);

                if(result >= 0) {
                    if (mConnection.requestWait() != ur) {
                        Log.e(TAG, "[controlTransfer] requestWait failed");

                        return -1;
                    }
                } else {
                    Log.e(TAG, "[controlTransfer] Transfer failed");
                }
            } else {
                result = mConnection.controlTransfer(requestType, request, value, index,
                        transferBuffer, transferBuffer.length, timeout);

                receiveBuffer = transferBuffer.clone();
            }

            return result;
        }
        int bulkTransfer(int interfaceNumber, int endpointNumber, int direction,
                         byte[] buffer, int timeout)
                throws UsbError {
            UsbEndpoint ep = mDevice.getInterface(interfaceNumber).getEndpoint(endpointNumber);
            if (ep.getDirection() != direction) {
                throw new UsbError("Endpoint has direction: " + directionName(ep.getDirection()));
            }
            return mConnection.bulkTransfer(ep, buffer, buffer.length, timeout);
        }
        int interruptTransfer(int interfaceNumber, int endpointNumber, int direction,
                              byte[] buffer, int timeout)
                throws UsbError {
            UsbEndpoint ep = mDevice.getInterface(interfaceNumber).getEndpoint(endpointNumber);
            if (ep.getDirection() != direction) {
                throw new UsbError("Endpoint has direction: " + directionName(ep.getDirection()));
            }
            return mConnection.bulkTransfer(ep, buffer, buffer.length, timeout);
        }
        void close() {
            mConnection.close();
        }
    };

    // Fake device, used in test code.
    public static class FakeDevice extends ConnectedDevice {
        static final int ID = -1000000;
        static final int VID = 0x18d1;  // Google VID.
        static final int PID = 0x2001;  // Reserved for non-production uses.

        private byte[] echoBytes = null;

        int getInterfaceCount() {
            return 1;
        }
        int getEndpointCount(int interfaceNumber) {
            return 2;
        }
        void describeInterface(int interfaceNumber, JSONObject res) throws JSONException {
            res.put("alternateSetting", 0);
            res.put("interfaceClass", 255);
            res.put("interfaceProtocol", 255);
            res.put("interfaceSubclass", 255);
            res.put("extra_data", new JSONObject());
        }
        void describeEndpoint(int interfaceNumber, int endpointNumber, JSONObject res)
                throws JSONException {
            res.put("direction", directionName(endpointNumber == 0 ?
                    UsbConstants.USB_DIR_IN : UsbConstants.USB_DIR_OUT));
            res.put("maximumPacketSize", 64);
            res.put("pollingInterval", 0);
            res.put("type", endpointTypeName(UsbConstants.USB_ENDPOINT_XFER_BULK));
        }
        boolean claimInterface(int interfaceNumber) {
            return true;
        }
        boolean releaseInterface(int interfaceNumber) {
            return true;
        }
        int controlTransfer(int requestType, int request, int value, int index,
                            byte[] transferBuffer, byte[] receiveBuffer, int timeout) {
            if ((requestType & UsbConstants.USB_ENDPOINT_DIR_MASK) == UsbConstants.USB_DIR_IN) {
                // For an 'IN' transfer, reflect params into the response data.
                receiveBuffer[0] = (byte)request;
                receiveBuffer[1] = (byte)value;
                receiveBuffer[2] = (byte)index;
                return 3;
            }
            return transferBuffer.length;
        }
        int bulkTransfer(int interfaceNumber, int endpointNumber, int direction,
                         byte[] buffer, int timeout)
                throws UsbError {
            if (direction == UsbConstants.USB_DIR_OUT) {
                echoBytes = buffer;
                return echoBytes.length;
            }
            // IN transfer.
            if (echoBytes == null) {
                return 0;
            }
            int len = Math.min(echoBytes.length, buffer.length);
            System.arraycopy(echoBytes, 0, buffer, 0, len);
            echoBytes = null;
            return len;
        }
        int interruptTransfer(int interfaceNumber, int endpointNumber, int direction,
                              byte[] buffer, int timeout)
                throws UsbError {
            if (direction == UsbConstants.USB_DIR_OUT) {
                echoBytes = buffer;
                return echoBytes.length;
            }
            // IN transfer.
            if (echoBytes == null) {
                return 0;
            }
            int len = Math.min(echoBytes.length, buffer.length);
            System.arraycopy(echoBytes, 0, buffer, 0, len);
            echoBytes = null;
            return len;
        }
        void close() {
        }
    };

    static String directionName(int direction) {
        switch (direction) {
            case UsbConstants.USB_DIR_IN: return "in";
            case UsbConstants.USB_DIR_OUT: return "out";
            default: return "ERR:" + direction;
        }
    }

    static String endpointTypeName(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return "bulk";
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL: return "control";
            case UsbConstants.USB_ENDPOINT_XFER_INT: return "interrupt";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return "isochronous";
            default: return "ERR:" + type;
        }
    }

    private static int controlRequestTypeFromName(String requestType) throws UsbError{
        requestType = requestType.toLowerCase();
        if ("standard".equals(requestType)) {
            return UsbConstants.USB_TYPE_STANDARD;  /* 0x00 */
        } else if ("class".equals(requestType)) {
            return UsbConstants.USB_TYPE_CLASS;     /* 0x20 */
        } else if ("vendor".equals(requestType)) {
            return UsbConstants.USB_TYPE_VENDOR;    /* 0x40 */
        } else if ("reserved".equals(requestType)) {
            return UsbConstants.USB_TYPE_RESERVED;  /* 0x60 */
        } else {
            throw new UsbError("Unknown transfer requestType: " + requestType);
        }
    }

    private static int recipientFromName(String recipient) throws UsbError {
        /* recipient value from pyUSB */
        recipient = recipient.toLowerCase();

        if("device".equals(recipient)) {
            return 0;
        } else if("interface".equals(recipient)) {
            return 1;
        } else if("endpoint".equals(recipient)) {
            return 2;
        } else if("other".equals(recipient)) {
            return 3;
        } else {
            throw new UsbError("Unknown recipient: " + recipient);
        }
    }

    private static int directionFromName(String direction) throws UsbError {
        direction = direction.toLowerCase();
        if ("out".equals(direction)) {
            return UsbConstants.USB_DIR_OUT; /* 0x00 */
        } else if ("in".equals(direction)) {
            return UsbConstants.USB_DIR_IN; /* 0x80 */
        } else {
            throw new UsbError("Unknown transfer direction: " + direction);
        }
    }

    private static byte[] getByteBufferForTransfer(CordovaArgs args, JSONObject params,
                                                   int direction) throws JSONException {
        if (direction == UsbConstants.USB_DIR_OUT) {
            // OUT transfer requires data positional argument.
            return args.getArrayBuffer(ARG_INDEX_DATA_ARRAYBUFFER);
        } else {
            // IN transfer requires client to pass the length to receive.
            return new byte[params.optInt("length")];
        }
    }


//--------------------------------------------------------------------------------------------------
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

    public interface EventListener {
        void onDeviceFound(DeviceWrapper device);
    }

    public DeviceFinder(final DeviceFinder.EventListener listener) {
        this.listener = listener;
    }

    public void find(final Context context, DeviceWrapper... devices) {
        if (devices == null)
            return;
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
                            listener.onDeviceFound(dev);
                        }
                    }
                }
            }
        } catch (Throwable e) {
        }
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

                currentPacket = new PassportScanner.ScannerPacket(data[0], data[1] * 256 + data[2]);
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

        /** 5 data bits. */
        public static final int DATABITS_5 = 5;

        /** 6 data bits. */
        public static final int DATABITS_6 = 6;

        /** 7 data bits. */
        public static final int DATABITS_7 = 7;

        /** 8 data bits. */
        public static final int DATABITS_8 = 8;

        /** No flow control. */
        public static final int FLOWCONTROL_NONE = 0;

        /** RTS/CTS input flow control. */
        public static final int FLOWCONTROL_RTSCTS_IN = 1;

        /** RTS/CTS output flow control. */
        public static final int FLOWCONTROL_RTSCTS_OUT = 2;

        /** XON/XOFF input flow control. */
        public static final int FLOWCONTROL_XONXOFF_IN = 4;

        /** XON/XOFF output flow control. */
        public static final int FLOWCONTROL_XONXOFF_OUT = 8;

        /** No parity. */
        public static final int PARITY_NONE = 0;

        /** Odd parity. */
        public static final int PARITY_ODD = 1;

        /** Even parity. */
        public static final int PARITY_EVEN = 2;

        /** Mark parity. */
        public static final int PARITY_MARK = 3;

        /** Space parity. */
        public static final int PARITY_SPACE = 4;

        /** 1 stop bit. */
        public static final int STOPBITS_1 = 1;

        /** 1.5 stop bits. */
        public static final int STOPBITS_1_5 = 3;

        /** 2 stop bits. */
        public static final int STOPBITS_2 = 2;

        public UsbSerialDriver getDriver();

        /**
         * Port number within driver.
         */
        public int getPortNumber();

        /**
         * The serial number of the underlying UsbDeviceConnection, or {@code null}.
         */
        public String getSerial();

        /**
         * Opens and initializes the port. Upon success, caller must ensure that
         * {@link #close()} is eventually called.
         *
         * @param connection an open device connection, acquired with
         *            {@link UsbManager#openDevice(android.hardware.usb.UsbDevice)}
         * @throws IOException on error opening or initializing the port.
         */
        public void open(UsbDeviceConnection connection) throws IOException;

        /**
         * Closes the port.
         *
         * @throws IOException on error closing the port.
         */
        public void close() throws IOException;

        /**
         * Reads as many bytes as possible into the destination buffer.
         *
         * @param dest the destination byte buffer
         * @param timeoutMillis the timeout for reading
         * @return the actual number of bytes read
         * @throws IOException if an error occurred during reading
         */
        public int read(final byte[] dest, final int timeoutMillis) throws IOException;

        /**
         * Writes as many bytes as possible from the source buffer.
         *
         * @param src the source byte buffer
         * @param timeoutMillis the timeout for writing
         * @return the actual number of bytes written
         * @throws IOException if an error occurred during writing
         */
        public int write(final byte[] src, final int timeoutMillis) throws IOException;

        /**
         * Sets various serial port parameters.
         *
         * @param baudRate baud rate as an integer, for example {@code 115200}.
         * @param dataBits one of {@link #DATABITS_5}, {@link #DATABITS_6},
         *            {@link #DATABITS_7}, or {@link #DATABITS_8}.
         * @param stopBits one of {@link #STOPBITS_1}, {@link #STOPBITS_1_5}, or
         *            {@link #STOPBITS_2}.
         * @param parity one of {@link #PARITY_NONE}, {@link #PARITY_ODD},
         *            {@link #PARITY_EVEN}, {@link #PARITY_MARK}, or
         *            {@link #PARITY_SPACE}.
         * @throws IOException on error setting the port parameters
         */
        public void setParameters(
                int baudRate, int dataBits, int stopBits, int parity) throws IOException;

        /**
         * Gets the CD (Carrier Detect) bit from the underlying UART.
         *
         * @return the current state, or {@code false} if not supported.
         * @throws IOException if an error occurred during reading
         */
        public boolean getCD() throws IOException;

        /**
         * Gets the CTS (Clear To Send) bit from the underlying UART.
         *
         * @return the current state, or {@code false} if not supported.
         * @throws IOException if an error occurred during reading
         */
        public boolean getCTS() throws IOException;

        /**
         * Gets the DSR (Data Set Ready) bit from the underlying UART.
         *
         * @return the current state, or {@code false} if not supported.
         * @throws IOException if an error occurred during reading
         */
        public boolean getDSR() throws IOException;

        /**
         * Gets the DTR (Data Terminal Ready) bit from the underlying UART.
         *
         * @return the current state, or {@code false} if not supported.
         * @throws IOException if an error occurred during reading
         */
        public boolean getDTR() throws IOException;

        /**
         * Sets the DTR (Data Terminal Ready) bit on the underlying UART, if
         * supported.
         *
         * @param value the value to set
         * @throws IOException if an error occurred during writing
         */
        public void setDTR(boolean value) throws IOException;

        /**
         * Gets the RI (Ring Indicator) bit from the underlying UART.
         *
         * @return the current state, or {@code false} if not supported.
         * @throws IOException if an error occurred during reading
         */
        public boolean getRI() throws IOException;

        /**
         * Gets the RTS (Request To Send) bit from the underlying UART.
         *
         * @return the current state, or {@code false} if not supported.
         * @throws IOException if an error occurred during reading
         */
        public boolean getRTS() throws IOException;

        /**
         * Sets the RTS (Request To Send) bit on the underlying UART, if
         * supported.
         *
         * @param value the value to set
         * @throws IOException if an error occurred during writing
         */
        public void setRTS(boolean value) throws IOException;

        /**
         * Flush non-transmitted output data and / or non-read input data
         * @param flushRX {@code true} to flush non-transmitted output data
         * @param flushTX {@code true} to flush non-read input data
         * @return {@code true} if the operation was successful, or
         * {@code false} if the operation is not supported by the driver or device
         * @throws IOException if an error occurred during flush
         */
        public boolean purgeHwBuffers(boolean flushRX, boolean flushTX) throws IOException;

    }

    public interface UsbSerialDriver {

        /**
         * Returns the raw {@link UsbDevice} backing this port.
         *
         * @return the device
         */
        public UsbDevice getDevice();

        /**
         * Returns all available ports for this device. This list must have at least
         * one entry.
         *
         * @return the ports
         */
        public List<UsbSerialPort> getPorts();
    }

    public static class SerialInputOutputManager implements Runnable {

        private final String TAG = SerialInputOutputManager.class.getSimpleName();
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
            /**
             * Called when new incoming data is available.
             */
            public void onNewData(byte[] data);

            /**
             * Called when {@link SerialInputOutputManager#run()} aborts due to an
             * error.
             */
            public void onRunError(Exception e);
        }

        /**
         * Creates a new instance with no listener.
         */
        public SerialInputOutputManager(UsbSerialPort driver) {
            this(driver, null);
        }

        /**
         * Creates a new instance with the provided listener.
         */
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
                Log.i(TAG, "Stop requested");
                mState = State.STOPPING;
            }
        }

        private synchronized State getState() {
            return mState;
        }

        /**
         * Continuously services the read and write buffers until {@link #stop()} is
         * called, or until a driver exception is raised.
         *
         * NOTE(mikey): Uses inefficient read/write-with-timeout.
         * TODO(mikey): Read asynchronously with {@link UsbRequest#queue(ByteBuffer, int)}
         */
        @Override
        public void run() {
            synchronized (this) {
                if (getState() != State.STOPPED) {
                    throw new IllegalStateException("Already running.");
                }
                mState = State.RUNNING;
            }

            Log.i(TAG, "Running ..");
            try {
                while (true) {
                    if (getState() != State.RUNNING) {
                        Log.i(TAG, "Stopping mState=" + getState());
                        break;
                    }
                    step();
                }
            } catch (Exception e) {
                Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
                final Listener listener = getListener();
                if (listener != null) {
                    listener.onRunError(e);
                }
            } finally {
                synchronized (this) {
                    mState = State.STOPPED;
                    Log.i(TAG, "Stopped.");
                }
            }
        }

        private void step() throws IOException {
            // Handle incoming data.
            int len = mDriver.read(mReadBuffer.array(), READ_WAIT_MILLIS);
            if (len > 0) {
                if (DEBUG) Log.d(TAG, "Read data len=" + len);
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
                if (DEBUG) {
                    Log.d(TAG, "Writing data len=" + len);
                }
                mDriver.write(outBuff, READ_WAIT_MILLIS);
            }
        }

    } // class SerialInputOutputManager


    abstract class CommonUsbSerialPort implements UsbSerialPort {

        public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
        public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;

        protected final UsbDevice mDevice;
        protected final int mPortNumber;

        // non-null when open()
        protected UsbDeviceConnection mConnection = null;

        protected final Object mReadBufferLock = new Object();
        protected final Object mWriteBufferLock = new Object();

        /** Internal read buffer.  Guarded by {@link #mReadBufferLock}. */
        protected byte[] mReadBuffer;

        /** Internal write buffer.  Guarded by {@link #mWriteBufferLock}. */
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

        /**
         * Returns the currently-bound USB device.
         *
         * @return the device
         */
        public final UsbDevice getDevice() {
            return mDevice;
        }

        @Override
        public int getPortNumber() {
            return mPortNumber;
        }

        /**
         * Returns the device serial number
         *  @return serial number
         */
        @Override
        public String getSerial() {
            return mConnection.getSerial();
        }

        /**
         * Sets the size of the internal buffer used to exchange data with the USB
         * stack for read operations.  Most users should not need to change this.
         *
         * @param bufferSize the size in bytes
         */
        public final void setReadBufferSize(int bufferSize) {
            synchronized (mReadBufferLock) {
                if (bufferSize == mReadBuffer.length) {
                    return;
                }
                mReadBuffer = new byte[bufferSize];
            }
        }

        /**
         * Sets the size of the internal buffer used to exchange data with the USB
         * stack for write operations.  Most users should not need to change this.
         *
         * @param bufferSize the size in bytes
         */
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

    } // abstract class CommonUsbSerialPort



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
                        // We *should* use UsbRequest, except it has a bug/api oversight
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
                /* Closing the port (at least for passport reader) makes it unusable until restart;
                 * even when it's called only from finalize (it looks like finalize gets called
                 * after some time of inactivity, but the object itself continue existing...) */
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


}
