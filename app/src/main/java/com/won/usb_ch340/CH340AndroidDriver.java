package com.won.usb_ch340;

import java.io.IOException;
import java.util.HashMap;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

public class CH340AndroidDriver {
    private static final String ACTION_USB_PERMISSION = "ACTION_USB_PERMISSION";
    private UsbManager mUsbManager;
    private UsbDevice mUsbDevice;
    private UsbInterface mInterface;
    private UsbEndpoint mBulkInPoint;
    private UsbEndpoint mBulkOutPoint;
    private UsbDeviceConnection mDeviceConnection;
    private Context mContext;
    private boolean BroadcastFlag = false;
    private boolean READ_ENABLE = false;
    private ReadThread mReadThread;

    private byte[] readBuffer; /* circular buffer */
    private byte[] usbdata;
    private int writeIndex;
    private int readIndex;
    private int readcount;
    private int totalBytes;
    private final Object ReadQueueLock = new Object();
    private final Object WriteQueueLock = new Object();
    private int mBulkPacketSize;
    private final int maxnumbytes = 65536;

    private int WriteTimeOutMillis;
    private int ReadTimeOutMillis;
    private int DEFAULT_TIMEOUT = 500;


    private KeyEventListener mKeyEventListener;

    public CH340AndroidDriver(Context context) {
        super();
        readBuffer = new byte[maxnumbytes];
        usbdata = new byte[1024];
        writeIndex = 0;
        readIndex = 0;

        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mContext = context;
        WriteTimeOutMillis = 10000;
        ReadTimeOutMillis = 10000;


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        context.registerReceiver(mUsbReceiver, intentFilter);
    }

    private void OpenUsbDevice(UsbDevice device) {
        UsbInterface usbInterface = getUsbInterface(device);
        if (usbInterface != null) {
            UsbDeviceConnection usbDeviceConnection = this.mUsbManager.openDevice(device);
            if (usbDeviceConnection != null) {
                if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                    this.mUsbDevice = device;
                    this.mDeviceConnection = usbDeviceConnection;
                    this.mInterface = usbInterface;
                    if (enumerateEndPoint(usbInterface)) {
                        if (!READ_ENABLE) {
                            READ_ENABLE = true;
                            mReadThread = new ReadThread(mBulkInPoint,
                                    mDeviceConnection);
                            mReadThread.start();
                        }
                        if (uartInit()) {
                            setConfig(9600, (byte) 8, (byte) 1, (byte) 0, (byte) 1);
                        }

                    }
                }
            }
        }

    }

    /**
     * 关闭小键盘监听
     */
    public void close() {
        try {
            Thread.sleep(10);
        } catch (Exception ignored) {
        }

        if (this.mDeviceConnection != null) {
            if (this.mInterface != null) {
                this.mDeviceConnection.releaseInterface(this.mInterface);
                this.mInterface = null;
            }

            this.mDeviceConnection.close();
        }

        if (this.mUsbDevice != null) {
            this.mUsbDevice = null;
        }

        if (READ_ENABLE) {
            READ_ENABLE = false;
        }

        /*
         * No need unregisterReceiver
         */
        if (BroadcastFlag) {
            this.mContext.unregisterReceiver(mUsbReceiver);
            BroadcastFlag = false;
        }

        // System.exit(0);
    }


    /**
     * 开启小键盘监听
     */
    public void start() {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == 6790 && device.getProductId() == 29987) {
                requestPermission(mContext, device);
                break;
            }
        }
    }

    private void requestPermission(Context context, UsbDevice usbDevice) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mUsbManager.requestPermission(usbDevice, pendingIntent);
    }


    public boolean isConnected() {
        return (this.mUsbDevice != null) && (this.mInterface != null)
                && (this.mDeviceConnection != null);
    }

    /**
     * Performs a control transaction on endpoint zero for this device. The
     * direction of the transfer is determined by the request type. If
     * requestType & {@link UsbConstants#USB_ENDPOINT_DIR_MASK} is
     * {@link UsbConstants#USB_DIR_OUT}, then the transfer is a write, and if it
     * is {@link UsbConstants#USB_DIR_IN}, then the transfer is a read.
     *
     * @return length of data transferred (or zero) for success, or negative
     * value for failure
     * <p>
     * public int controlTransfer(int requestType, int request, int
     * value, int index, byte[] buffer, int length, int timeout)
     */

    private int Uart_Control_Out(int request, int value, int index) {
        int retval = 0;
        retval = mDeviceConnection.controlTransfer(UsbType.USB_TYPE_VENDOR, request,
                value, index, null, 0, DEFAULT_TIMEOUT);

        return retval;
    }

    private int Uart_Control_In(int request, int value, int index,
                                byte[] buffer, int length) {
        int retval = 0;
        retval = mDeviceConnection.controlTransfer(UsbType.USB_TYPE_VENDOR
                        | UsbType.USB_DIR_IN, request,
                value, index, buffer, length, DEFAULT_TIMEOUT);
        return retval;
    }

    private int Uart_Set_Handshake(int control) {
        return Uart_Control_Out(UartCmd.VENDOR_MODEM_OUT, ~control, 0);
    }

    private int Uart_Tiocmset(int set, int clear) {
        int control = 0;
        if ((set & UartModem.TIOCM_RTS) == UartModem.TIOCM_RTS)
            control |= UartIoBits.UART_BIT_RTS;
        if ((set & UartModem.TIOCM_DTR) == UartModem.TIOCM_DTR)
            control |= UartIoBits.UART_BIT_DTR;
        if ((clear & UartModem.TIOCM_RTS) == UartModem.TIOCM_RTS)
            control &= ~UartIoBits.UART_BIT_RTS;
        if ((clear & UartModem.TIOCM_DTR) == UartModem.TIOCM_DTR)
            control &= ~UartIoBits.UART_BIT_DTR;

        return Uart_Set_Handshake(control);
    }

    public boolean uartInit() {
        int ret;
        int size = 8;
        byte[] buffer = new byte[size];
        Uart_Control_Out(UartCmd.VENDOR_SERIAL_INIT, 0x0000, 0x0000);
        ret = Uart_Control_In(UartCmd.VENDOR_VERSION, 0x0000, 0x0000, buffer, 2);
        if (ret < 0)
            return false;
        Uart_Control_Out(UartCmd.VENDOR_WRITE, 0x1312, 0xD982);
        Uart_Control_Out(UartCmd.VENDOR_WRITE, 0x0f2c, 0x0004);
        ret = Uart_Control_In(UartCmd.VENDOR_READ, 0x2518, 0x0000, buffer, 2);
        if (ret < 0)
            return false;
        Uart_Control_Out(UartCmd.VENDOR_WRITE, 0x2727, 0x0000);
        Uart_Control_Out(UartCmd.VENDOR_MODEM_OUT, 0x00ff, 0x0000);
        return true;
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    private boolean setConfig(int baudRate, byte dataBit, byte stopBit,
                              byte parity, byte flowControl) {
        int value = 0;
        int index = 0;
        char valueHigh = 0, valueLow = 0, indexHigh = 0, indexLow = 0;
        switch (parity) {
            case 0: /* NONE */
                valueHigh = 0x00;
                break;
            case 1: /* ODD */
                valueHigh |= 0x08;
                break;
            case 2: /* Even */
                valueHigh |= 0x18;
                break;
            case 3: /* Mark */
                valueHigh |= 0x28;
                break;
            case 4: /* Space */
                valueHigh |= 0x38;
                break;
            default: /* None */
                valueHigh = 0x00;
                break;
        }

        if (stopBit == 2) {
            valueHigh |= 0x04;
        }

        switch (dataBit) {
            case 5:
                valueHigh |= 0x00;
                break;
            case 6:
                valueHigh |= 0x01;
                break;
            case 7:
                valueHigh |= 0x02;
                break;
            case 8:
                valueHigh |= 0x03;
                break;
            default:
                valueHigh |= 0x03;
                break;
        }

        valueHigh |= 0xc0;
        valueLow = 0x9c;

        value |= valueLow;
        value |= (int) (valueHigh << 8);

        switch (baudRate) {
            case 50:
                indexLow = 0;
                indexHigh = 0x16;
                break;
            case 75:
                indexLow = 0;
                indexHigh = 0x64;
                break;
            case 110:
                indexLow = 0;
                indexHigh = 0x96;
                break;
            case 135:
                indexLow = 0;
                indexHigh = 0xa9;
                break;
            case 150:
                indexLow = 0;
                indexHigh = 0xb2;
                break;
            case 300:
                indexLow = 0;
                indexHigh = 0xd9;
                break;
            case 600:
                indexLow = 1;
                indexHigh = 0x64;
                break;
            case 1200:
                indexLow = 1;
                indexHigh = 0xb2;
                break;
            case 1800:
                indexLow = 1;
                indexHigh = 0xcc;
                break;
            case 2400:
                indexLow = 1;
                indexHigh = 0xd9;
                break;
            case 4800:
                indexLow = 2;
                indexHigh = 0x64;
                break;
            case 9600:
                indexLow = 2;
                indexHigh = 0xb2;
                break;
            case 19200:
                indexLow = 2;
                indexHigh = 0xd9;
                break;
            case 38400:
                indexLow = 3;
                indexHigh = 0x64;
                break;
            case 57600:
                indexLow = 3;
                indexHigh = 0x98;
                break;
            case 115200:
                indexLow = 3;
                indexHigh = 0xcc;
                break;
            case 230400:
                indexLow = 3;
                indexHigh = 0xe6;
                break;
            case 460800:
                indexLow = 3;
                indexHigh = 0xf3;
                break;
            case 500000:
                indexLow = 3;
                indexHigh = 0xf4;
                break;
            case 921600:
                indexLow = 7;
                indexHigh = 0xf3;
                break;
            case 1000000:
                indexLow = 3;
                indexHigh = 0xfa;
                break;
            case 2000000:
                indexLow = 3;
                indexHigh = 0xfd;
                break;
            case 3000000:
                indexLow = 3;
                indexHigh = 0xfe;
                break;
            default: // default baudRate "9600"
                indexLow = 2;
                indexHigh = 0xb2;
                break;
        }

        index |= 0x88 | indexLow;
        index |= (int) (indexHigh << 8);

        Uart_Control_Out(UartCmd.VENDOR_SERIAL_INIT, value, index);
        if (flowControl == 1) {
            Uart_Tiocmset(UartModem.TIOCM_DTR | UartModem.TIOCM_RTS, 0x00);
        }
        return true;
    }

    public int ReadData(byte[] data, int length) {
        int mLen;

        /* should be at least one byte to read */
        if ((length < 1) || (totalBytes == 0)) {
            mLen = 0;
            return mLen;
        }

        /* check for max limit */
        if (length > totalBytes)
            length = totalBytes;

        /* update the number of bytes available */
        totalBytes -= length;

        mLen = length;

        /* copy to the user buffer */
        for (int count = 0; count < length; count++) {
            data[count] = readBuffer[readIndex];
            readIndex++;
            /*
             * shouldnt read more than what is there in the buffer, so no need
             * to check the overflow
             */
            readIndex %= maxnumbytes;
        }
        return mLen;
    }

    public int ReadData(char[] data, int length) {
        int mLen;

        /* should be at least one byte to read */
        if ((length < 1) || (totalBytes == 0)) {
            mLen = 0;
            return mLen;
        }

        /* check for max limit */
        if (length > totalBytes)
            length = totalBytes;

        /* update the number of bytes available */
        totalBytes -= length;

        mLen = length;

        /* copy to the user buffer */
        for (int count = 0; count < length; count++) {
            data[count] = (char) readBuffer[readIndex];
            readIndex++;
            /*
             * shouldnt read more than what is there in the buffer, so no need
             * to check the overflow
             */
            readIndex %= maxnumbytes;
        }
        return mLen;
    }

    /**
     * Performs a bulk transaction on the given endpoint. The direction of the
     * transfer is determined by the direction of the endpoint
     *
     * @return length of data transferred (or zero) for success, or negative
     * value for failure
     * <p>
     * public int bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int
     * length, int timeout)
     */

    public int WriteData(byte[] buf) {
//        synchronized (this) {
            return WriteData(buf, buf.length, this.WriteTimeOutMillis);
//        }
    }

    public int WriteData(byte[] buf, int length, int timeoutMillis) {
        int offset = 0;
        int HasWritten = 0;
        int odd_len = length;
        if (this.mBulkOutPoint == null)
            return -1;
        while (offset < length) {
            synchronized (this.WriteQueueLock) {
                int mLen = Math.min(odd_len, this.mBulkPacketSize);
                byte[] arrayOfByte = new byte[mLen];
                if (offset == 0) {
                    System.arraycopy(buf, 0, arrayOfByte, 0, mLen);
                } else {
                    System.arraycopy(buf, offset, arrayOfByte, 0, mLen);
                }
                HasWritten = this.mDeviceConnection.bulkTransfer(
                        this.mBulkOutPoint, arrayOfByte, mLen, timeoutMillis);
                if (HasWritten < 0) {
                    return -2;
                } else {
                    offset += HasWritten;
                    odd_len -= HasWritten;
                    // Log.d(TAG, "offset " + offset + " odd_len " + odd_len);
                }
            }
        }
        return offset;
    }

    private boolean enumerateEndPoint(UsbInterface sInterface) {
        if (sInterface == null)
            return false;
        for (int i = 0; i < sInterface.getEndpointCount(); ++i) {
            UsbEndpoint endPoint = sInterface.getEndpoint(i);
            if (endPoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endPoint.getMaxPacketSize() == 0x20) {
                if (endPoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    mBulkInPoint = endPoint;
                } else {
                    mBulkOutPoint = endPoint;
                }
                this.mBulkPacketSize = endPoint.getMaxPacketSize();
            }
        }
        return true;
    }

    private UsbInterface getUsbInterface(UsbDevice paramUsbDevice) {
        if (this.mDeviceConnection != null) {
            if (this.mInterface != null) {
                this.mDeviceConnection.releaseInterface(this.mInterface);
                this.mInterface = null;
            }
            this.mDeviceConnection.close();
            this.mUsbDevice = null;
            this.mInterface = null;
        }
        if (paramUsbDevice == null)
            return null;

        for (int i = 0; i < paramUsbDevice.getInterfaceCount(); i++) {
            UsbInterface intf = paramUsbDevice.getInterface(i);
            if (intf.getInterfaceClass() == 0xff
                    && intf.getInterfaceSubclass() == 0x01
                    && intf.getInterfaceProtocol() == 0x02) {
                return intf;
            }
        }
        return null;
    }

    /*********** USB broadcast receiver *******************************************/
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice usbDevice = intent.getParcelableExtra("device");
                if (intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    OpenUsbDevice(usbDevice);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice usbDevice = intent.getParcelableExtra("device");
                if (mUsbDevice != null && usbDevice.getDeviceId() == mUsbDevice.getDeviceId()) {
                    close();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                start();
            }
        }
    };

    public void setKeyEventListener(KeyEventListener keyEventListener) {
        mKeyEventListener = keyEventListener;
    }

    /* usb input data handler */
    private class ReadThread extends Thread {
        UsbEndpoint endpoint;
        UsbDeviceConnection mConn;

        ReadThread(UsbEndpoint point, UsbDeviceConnection con) {
            endpoint = point;
            mConn = con;
            this.setPriority(Thread.MAX_PRIORITY);
        }

        public void run() {
            while (READ_ENABLE) {
//                while (totalBytes > (maxnumbytes - 63)) {
//                    try {
//                        Thread.sleep(5);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }

                synchronized (CH340AndroidDriver.this) {//
                    if (endpoint != null) {
                        readcount = mConn.bulkTransfer(endpoint, usbdata, 128, ReadTimeOutMillis);

                        if (readcount > 0) {
                            byte[] b = new byte[readcount];
                            System.arraycopy(usbdata, 0, b, 0, readcount);
                            if (mKeyEventListener != null) {
                                mKeyEventListener.onKeyEvent(b);
                            }
//                        Log.i("Test", "readBuffer:" + toHexString(b, readcount));


//                            if (writeIndex >= readIndex)
//                                totalBytes = writeIndex - readIndex;
//                            else
//                                totalBytes = (maxnumbytes - readIndex)
//                                        + writeIndex;

                        }
                    }
                }
            }
        }
    }


    private String toHexString(byte[] arg, int length) {
        String result = new String();
        if (arg != null) {
            for (int i = 0; i < length; i++) {
                result = result
                        + (Integer.toHexString(
                        arg[i] < 0 ? arg[i] + 256 : arg[i]).length() == 1 ? "0"
                        + Integer.toHexString(arg[i] < 0 ? arg[i] + 256
                        : arg[i])
                        : Integer.toHexString(arg[i] < 0 ? arg[i] + 256
                        : arg[i])) + " ";
            }
            return result;
        }
        return "";
    }


    public final class UartModem {
        public static final int TIOCM_LE = 0x001;
        public static final int TIOCM_DTR = 0x002;
        public static final int TIOCM_RTS = 0x004;
        public static final int TIOCM_ST = 0x008;
        public static final int TIOCM_SR = 0x010;
        public static final int TIOCM_CTS = 0x020;
        public static final int TIOCM_CAR = 0x040;
        public static final int TIOCM_RNG = 0x080;
        public static final int TIOCM_DSR = 0x100;
        public static final int TIOCM_CD = TIOCM_CAR;
        public static final int TIOCM_RI = TIOCM_RNG;
        public static final int TIOCM_OUT1 = 0x2000;
        public static final int TIOCM_OUT2 = 0x4000;
        public static final int TIOCM_LOOP = 0x8000;
    }

    public final class UsbType {
        public static final int USB_TYPE_VENDOR = (0x02 << 5);
        public static final int USB_RECIP_DEVICE = 0x00;
        public static final int USB_DIR_OUT = 0x00; /* to device */
        public static final int USB_DIR_IN = 0x80; /* to host */
    }

    public final class UartCmd {
        public static final int VENDOR_WRITE_TYPE = 0x40;
        public static final int VENDOR_READ_TYPE = 0xC0;
        public static final int VENDOR_READ = 0x95;
        public static final int VENDOR_WRITE = 0x9A;
        public static final int VENDOR_SERIAL_INIT = 0xA1;
        public static final int VENDOR_MODEM_OUT = 0xA4;
        public static final int VENDOR_VERSION = 0x5F;
    }

    public final class UartState {
        public static final int UART_STATE = 0x00;
        public static final int UART_OVERRUN_ERROR = 0x01;
        public static final int UART_PARITY_ERROR = 0x02;
        public static final int UART_FRAME_ERROR = 0x06;
        public static final int UART_RECV_ERROR = 0x02;
        public static final int UART_STATE_TRANSIENT_MASK = 0x07;
    }

    public final class UartIoBits {
        public static final int UART_BIT_RTS = (1 << 6);
        public static final int UART_BIT_DTR = (1 << 5);
    }
}
