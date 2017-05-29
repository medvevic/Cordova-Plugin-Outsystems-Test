package nl.xservices.plugins;

import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.view.InputDevice;

/**
 * Created by Hp-user on 11.05.2017.
 */

public class DeviceWrapper {


    public static final String BARCODE_READER = "BarcodeReader";
    public static final String PASSPORT_SCANNER = "PassportScanner";
    public static final String RECEIPT_PRINTER = "ReceiptPrinter";
    public static final String RECEIPT_PRINTER_BLUETOOTH_REGO_NAME = "RG-MLP58A";
    public static final String RECEIPT_PRINTER_BLUETOOTH_REGO_ADDRESS = "00:02:5B:B3:D8:21";
    //public static final String RECEIPT_PRINTER_BLUETOOTH_TSC_NAME = "Alpha-3R";
    public static final String RECEIPT_PRINTER_BLUETOOTH_TSC_NAME = "BT-SPP";

    public static DeviceWrapper forBarcodeReader() {
        return new DeviceWrapper(BARCODE_READER, 0x05E0, 0x1200); // 1504, 4608
    }

    public static DeviceWrapper forPassportScanner() {
        return new DeviceWrapper(PASSPORT_SCANNER, 0xFFFF, 5);
    }

    public static DeviceWrapper forPassportScannerNew() {
        return new DeviceWrapper(PASSPORT_SCANNER, 0x2B78, 5);
    }

    public static DeviceWrapper forReceiptPrinter() {
        return new DeviceWrapper(RECEIPT_PRINTER, 1003, 8204);
    }

    public static DeviceWrapper forBluetoothPrinterREGO() {
        return new DeviceWrapper(RECEIPT_PRINTER_BLUETOOTH_REGO_NAME);
    }

    public static DeviceWrapper forBluetoothPrinterTSC() {
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
