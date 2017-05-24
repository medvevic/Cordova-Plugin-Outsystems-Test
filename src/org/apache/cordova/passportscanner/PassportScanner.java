package org.apache.cordova.passportscanner;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Hp-user on 11.05.2017.
 */

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

    private BlockingQueue<ScannerPacket> bq = (BlockingQueue<ScannerPacket>) new ArrayBlockingQueue<ScannerPacket>(16);
    ScannerPacket currentPacket = null;

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

    private ScannerPacket unpackResponse(byte[] data) throws IOException {
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
        ScannerPacket packet = null;

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
                    ScannerPacket packet = bq.poll();
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
