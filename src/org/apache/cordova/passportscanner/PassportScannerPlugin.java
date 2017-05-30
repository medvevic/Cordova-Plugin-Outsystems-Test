package org.apache.cordova.passportscanner;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.ConfigXmlParser;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.apache.cordova.Whitelist;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
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
import android.view.InputDevice;
import android.view.View;
import android.webkit.DownloadListener;
import android.widget.ImageView;

/*
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
*/
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

public class PassportScannerPlugin extends CordovaPlugin {

    private static final String TAG = "PassportScannerPlugin";

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
    //private PassportScanner passportScanner;
    //private TscPrinter tscPrinter;
    private String lastUrl;

    //------------------------------- New for work from Outsystems
    private static final String ACTION_USB_PERMISSION = TAG + ".USB_PERMISSION";

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

    @Override
    public boolean execute(String action, final CordovaArgs args, final CallbackContext callbackContext)
            throws JSONException {
        final JSONObject params = args.getJSONObject(ARG_INDEX_PARAMS);
        final CordovaArgs finalArgs = args;
        Log.d(TAG, "Action: " + action + " params: " + params);


        try {

            if ("hasUsbHostFeature".equals(action)) {
                boolean usbHostFeature = cordova.getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, usbHostFeature));
                return true;
            } else if ("findDevices".equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            findDevices();
                            callbackContext.success("findDevices");
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
                return true;
            } else if ("getDevices".equals(action)) {
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


        } catch (UsbError e) {
            callbackContext.error(e.getMessage());
            return true;
        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
            return true;
        }

        return false;
    }

    private void findDevices() {
        DeviceWrapper dw = new DeviceWrapper();
        DeviceWrapper barcodeReaderDevice = dw.forBarcodeReader();
        new DeviceFinder(new DeviceFinder.EventListener() {
            @Override
            public void onDeviceFound(DeviceWrapper device) {
                String name = device.getName();

                if (DeviceWrapper.BARCODE_READER.equals(device.getName())) {
                    //addUserAgent(UrlHelper.UA_BARCODE_READER);
                } else if (DeviceWrapper.PASSPORT_SCANNER.equals(device.getName())) {
                    //passportScanner = new PassportScanner(device.getUsbDevice(), device.getUsbConnection());
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

}

//--------------------------------------------------------------------------------------------------
/*
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

    private BlockingQueue<PassportScanner.ScannerPacket> bq = (BlockingQueue<PassportScanner.ScannerPacket>) new ArrayBlockingQueue<PassportScanner.ScannerPacket>(16);
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


}
*/


}
