package nl.xservices.plugins;


import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

}
