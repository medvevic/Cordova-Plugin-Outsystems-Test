package org.apache.cordova.passportscanner;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.InputDevice;

import java.util.Set;

/**
 * Created by Hp-user on 11.05.2017.
 */

public class DeviceFinder {


    public interface EventListener {
        void onDeviceFound(DeviceWrapper device);
    }

    public DeviceFinder(final EventListener listener) {
        this.listener = listener;
    }

    public void find(final Context context, DeviceWrapper... devices) {
        if (devices == null)
            return;
        this.devices = devices;

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
    private EventListener listener;

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
