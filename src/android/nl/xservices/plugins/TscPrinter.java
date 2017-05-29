package nl.xservices.plugins;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Hp-user on 11.05.2017.
 */

public class TscPrinter {

    public static final int y_gap = 30;
    public static final int y_gapBig = 35;
    //public static final int fontBig = 12; // for font "0" Monotype CG Triumvirate Bold Condensed, font width and height is stretchable
    //public static final int fontMedium = 10; // for font "0" Monotye CG Triumvirate Bold Condensed, font width and height is stretchable
    public static final int fontBig = 3; // for font "3" 16 x 24 fixed pitch dot font
    public static final int fontMedium = 2; // for font "2" 12 x 20 fixed pitch dot font
    //public static final int fontSmall = 9;
    public static final int alignLeft = 1;
    public static final int alignCenter = 2;
    public static final int alignRight = 3;

    public class BluetoothNotAvailableException extends IOException {
        public BluetoothNotAvailableException() {
            super("Bluetooth is not available");
        }
    }

    public class ConnectPrinterException extends IOException {
        public ConnectPrinterException() {
            super("Could not connect to the printer");
        }
    }

    /*
0: not readable
1: human readable aligns to left
2: human readable aligns to center
3: human readable aligns to right
     */

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public void closePort() {
        if(this.outStream != null) {
            try {
                this.outStream.flush();
            } catch (IOException var3) {
                //
            }
        }

        try {
            this.btSocket.close();
        } catch (IOException var2) {
            //
        }
    }

    public void openPort() throws IOException {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null || !btAdapter.isEnabled()) {
            throw new BluetoothNotAvailableException();
        }
        BluetoothDevice device = btAdapter.getRemoteDevice(getMacAddress());

        this.btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);

        btAdapter.cancelDiscovery();

        this.btSocket.connect();
        try {
            Thread.sleep(1000L); // demands some time to finish
        } catch (InterruptedException e) {
            //
        }
        this.outStream = this.btSocket.getOutputStream();
        this.inStream = this.btSocket.getInputStream();
        if (this.outStream == null || this.inStream == null) {
            throw new ConnectPrinterException();
        }
    }

    public void sendCommand(String message) throws IOException {
        this.outStream.write(message.getBytes());
    }

    public void sendCommand(byte[] message) throws IOException {
        this.outStream.write(message);
    }

    public String status() throws IOException {
        byte[] message = new byte[]{(byte)27, (byte)33, (byte)83};

        this.outStream.write(message);

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException var3) {
            //
        }

        byte[] readBuf = null;
        int tim;
        while(this.inStream.available() > 0) {
            readBuf = new byte[1024];
            tim = this.inStream.read(readBuf);
        }

        String printerStatus = "";

        if (readBuf != null && readBuf[0] == 2 && readBuf[5] == 3) {
            for(tim = 0; tim <= 7; ++tim) {
                if(readBuf[tim] == 2 && readBuf[tim + 1] == 64 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Ready";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 96 && readBuf[tim + 5] == 3) {
                    printerStatus = "Head Open";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 64 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 96 && readBuf[tim + 5] == 3) {
                    printerStatus = "Head Open";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 72 && readBuf[tim + 5] == 3) {
                    printerStatus = "Ribbon Jam";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 68 && readBuf[tim + 5] == 3) {
                    printerStatus = "Ribbon Empty";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 65 && readBuf[tim + 5] == 3) {
                    printerStatus = "No Paper";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 66 && readBuf[tim + 5] == 3) {
                    printerStatus = "Paper Jam";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 65 && readBuf[tim + 5] == 3) {
                    printerStatus = "Paper Empty";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 67 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Cutting";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 75 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Waiting to Press Print Key";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 76 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Waiting to Take Label";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 80 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Printing Batch";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 96 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Pause";
                    break;
                }

                if(readBuf[tim] == 2 && readBuf[tim + 1] == 69 && readBuf[tim + 2] == 64 && readBuf[tim + 3] == 64 && readBuf[tim + 4] == 64 && readBuf[tim + 5] == 3) {
                    printerStatus = "Pause";
                    break;
                }
            }
        }

        return printerStatus;
    }

    public String batch() throws IOException {
        String printbatch = "";
        String stringbatch = "";
        String message = "~HS";
        byte[] batcharray = new byte[8];
        byte[] msgBuffer = message.getBytes();

        this.outStream.write(msgBuffer);

        try {
            Thread.sleep(100L);
        } catch (InterruptedException var8) {
            //
        }

        byte[] readBuf = null;
        while(this.inStream.available() > 0) {
            readBuf = new byte[1024];
            int e = this.inStream.read(readBuf);
        }

        if(readBuf != null && readBuf[0] == 2) {
            System.arraycopy(readBuf, 55, batcharray, 0, 8);
            stringbatch = new String(batcharray);
            int printvalue1 = Integer.parseInt(stringbatch);
            printbatch = Integer.toString(printvalue1);
        }

        return printbatch;
    }

    public void setup(int width, int height, int speed, int density, int sensor, int sensor_distance, int sensor_offset) throws IOException {
        String message = "";
        String size = "SIZE " + width + " mm" + ", " + height + " mm";
        String speed_value = "SPEED " + speed;
        String density_value = "DENSITY " + density;
        String sensor_value = "";
        if(sensor == 0) {
            sensor_value = "GAP " + sensor_distance + " mm" + ", " + sensor_offset + " mm";
        } else if(sensor == 1) {
            sensor_value = "BLINE " + sensor_distance + " mm" + ", " + sensor_offset + " mm";
        }

        message = size + "\n" + speed_value + "\n" + density_value + "\n" + sensor_value + "\n";

        this.outStream.write(message.getBytes());
    }

    public void clearBuffer() throws IOException {
        this.outStream.write("CLS\n".getBytes());
    }

    public void barcode(int x, int y, String type, int height, int human_readable, int rotation, int narrow, int wide, String string) throws IOException {
        String message = "";
        String barcode = "BARCODE ";
        String position = x + "," + y;
        String mode = "\"" + type + "\"";
        String height_value = "" + height;
        String human_value = "" + human_readable;
        String rota = "" + rotation;
        String narrow_value = "" + narrow;
        String wide_value = "" + wide;
        String string_value = "\"" + string + "\"";
        message = barcode + position + " ," + mode + " ," + height_value + " ," + human_value + " ," + rota + " ," + narrow_value + " ," + wide_value + " ," + string_value + "\n";
        byte[] msgBuffer = message.getBytes();

        this.outStream.write(msgBuffer);
    }

    public void font(int x, int y, String size, int rotation, int x_multiplication, int y_multiplication, String string) throws IOException {
        String message = "";
        String text = "TEXT ";
        String position = x + "," + y;
        String size_value = "\"" + size + "\"";
        String rota = "" + rotation;
        String x_value = "" + x_multiplication;
        String y_value = "" + y_multiplication;
        String string_value = "\"" + string + "\"";
        message = text + position + " ," + size_value + " ," + rota + " ," + x_value + " ," + y_value + " ," + string_value + "\n";
        this.outStream.write(message.getBytes());
    }

    public void label(int quantity, int copy) {
        String message = "";
        message = "PRINT " + quantity + ", " + copy + "\n";
        byte[] msgBuffer = message.getBytes();

        try {
            this.outStream.write(msgBuffer);
        } catch (IOException var6) {
            var6.printStackTrace();
        }

    }

    public void formFeed() throws IOException {
        this.outStream.write("FORMFEED\n".getBytes());
    }

    public void noBackFeed() throws IOException {
        this.outStream.write("SET TEAR OFF\n".getBytes());
    }

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private String macAddress;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;
    private InputStream inStream = null;

}
