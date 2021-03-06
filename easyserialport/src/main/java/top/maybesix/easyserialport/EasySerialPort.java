package top.maybesix.easyserialport;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;

import android_serialport_api.SerialPort;
import top.maybesix.easyserialport.util.HexStringUtils;

/**
 * @author MaybeSix
 */
public class EasySerialPort {
    private static final String TAG = "SerialPortHelper";
    private SerialPort serialPort;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ReadThread readThread;
    private SendThread sendThread;
    private boolean openState = false;
    private byte[] loopData = new byte[]{0x30};
    private int delay = 500;

    /**
     * 串口参数
     */
    private String port;
    private int baudRate;
    private OnSerialPortReceivedListener onSerialPortReceivedListener;
    private OnStatesChangeListener onStatesChangeListener;


    private EasySerialPort(String port, int baudRate, OnSerialPortReceivedListener listener) {
        this.port = port;
        this.baudRate = baudRate;
        this.onSerialPortReceivedListener = listener;
    }

    public static class Builder {
        private String port;
        private int baudRate = 9600;
        private OnSerialPortReceivedListener onSerialPortReceivedListener;
        private OnStatesChangeListener onStatesChangeListener;

        public Builder setPort(String port) {
            this.port = port;
            return this;
        }

        public Builder setBaudRate(int baudRate) {
            this.baudRate = baudRate;
            return this;
        }

        public Builder setListener(OnSerialPortReceivedListener onSerialPortReceivedListener) {
            this.onSerialPortReceivedListener = onSerialPortReceivedListener;
            return this;
        }

        public Builder setSatesListener(OnStatesChangeListener onStatesChangeListener) {
            this.onStatesChangeListener = onStatesChangeListener;
            return this;
        }

        public EasySerialPort build() throws Exception {
            if (port == null || port.isEmpty()) {
                throw new Exception("port is null or empty!");
            }
            return new EasySerialPort(port, baudRate, onSerialPortReceivedListener);
        }
    }

    public void setSerialPortReceivedListener(OnSerialPortReceivedListener onSerialPortReceivedListener) {
        this.onSerialPortReceivedListener = onSerialPortReceivedListener;
    }

    public void setSatesListener(OnStatesChangeListener onStatesChangeListener) {
        this.onStatesChangeListener = onStatesChangeListener;
    }

    /**
     * 是否开启串口
     *
     * @return
     */
    public boolean isOpen() {
        return openState;
    }

    /**
     * 是否没有开启串口
     *
     * @return
     */
    public boolean isNotOpen() {
        return !openState;
    }
    /**
     * 串口打开方法
     */
    public void open() {
        try {
            baseOpen();
            Log.i(TAG, "打开串口成功！");
            onStatesChangeListener.onOpen(true, "");
        } catch (SecurityException e) {
            Log.e(TAG, "打开串口失败:没有串口读/写权限!");
            e.printStackTrace();
            onStatesChangeListener.onOpen(false, "没有串口读/写权限!");
        } catch (IOException e) {
            Log.e(TAG, "打开串口失败:未知错误!");
            e.printStackTrace();
            onStatesChangeListener.onOpen(false, "未知错误!");
        } catch (InvalidParameterException e) {
            Log.e(TAG, "打开串口失败:参数错误!");
            e.printStackTrace();
            onStatesChangeListener.onOpen(false, "参数错误!");
        } catch (Exception e) {
            Log.e(TAG, "openComPort: 其他错误");
            e.printStackTrace();
            onStatesChangeListener.onOpen(false, "其他错误!");
        }
    }

    private void baseOpen() throws SecurityException, IOException, InvalidParameterException {
        serialPort = new SerialPort(new File(port), baudRate, 0);
        outputStream = serialPort.getOutputStream();
        inputStream = serialPort.getInputStream();
        readThread = new ReadThread();
        readThread.start();
        sendThread = new SendThread();
        sendThread.setSuspendFlag();
        sendThread.start();
        openState = true;
    }

    /**
     * 串口关闭方法
     */
    public void close() {
        if (readThread != null) {
            readThread.interrupt();
        }
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        openState = false;
        onStatesChangeListener.onClose();
    }

    /**
     * 执行发送程序，若未开启，则会先开启，然后再发送
     *
     * @param bOutArray
     */
    private void send(byte[] bOutArray) {
        try {
            if (openState) {
                outputStream.write(bOutArray);
            } else {
                open();
                outputStream.write(bOutArray);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送十六进制字符串
     *
     * @param hexString
     */
    public void sendHex(String hexString) {
        byte[] bOutArray = HexStringUtils.hexString2ByteArray(hexString);
        send(bOutArray);
    }

    /**
     * 发送文本
     *
     * @param txtString
     */
    public void sendTxtString(String txtString) {
        byte[] bOutArray = txtString.getBytes();
        send(bOutArray);
    }

    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                try {
                    if (inputStream == null) {
                        return;
                    }
                    byte[] buffer = new byte[512];
                    int size = inputStream.read(buffer);
                    if (size > 0) {
                        ComPortData comPortData = new ComPortData(port, buffer, size);
                        onSerialPortReceivedListener.onSerialPortDataReceived(comPortData);
                    }

                } catch (Throwable e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    private class SendThread extends Thread {
        /**
         * 线程运行标志
         */
        boolean runFlag = true;

        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                synchronized (this) {
                    while (runFlag) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                send(getLoopData());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 线程暂停
         */
        private void setSuspendFlag() {
            this.runFlag = true;
        }

        /**
         * 唤醒线程
         */
        private synchronized void setResume() {
            this.runFlag = false;
            notify();
        }
    }

    public int getBaudRate() {
        return baudRate;
    }

    public boolean setBaudRate(int iBaud) {
        if (openState) {
            return false;
        } else {
            baudRate = iBaud;
            return true;
        }
    }

    public boolean setBaudRate(String sBaud) {
        int iBaud = Integer.parseInt(sBaud);
        return setBaudRate(iBaud);
    }

    public String getPort() {
        return port;
    }

    public boolean setPort(String sPort) {
        if (openState) {
            return false;
        } else {
            this.port = sPort;
            return true;
        }
    }


    public byte[] getLoopData() {
        return loopData;
    }

    /**
     * 设置循环发送的数据
     *
     * @param loopData byte数据
     */
    public void setLoopData(byte[] loopData) {
        this.loopData = loopData;
    }

    /**
     * 设置循环发送的数据
     *
     * @param str         传入的字符串
     * @param isHexString 是否为16进制字符串
     */
    public void setLoopData(String str, boolean isHexString) {
        if (isHexString) {
            this.loopData = str.getBytes();
        } else {
            this.loopData = HexStringUtils.hexString2ByteArray(str);
        }
    }

    /**
     * 获取延迟
     *
     * @return 时间（毫秒）
     */
    public int getDelay() {
        return delay;
    }

    /**
     * 设置延时（毫秒）
     *
     * @param delay
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * 开启循环发送
     */
    public void startSend() {
        if (sendThread != null) {
            sendThread.setResume();
        }
    }

    /**
     * 停止循环发送
     */
    public void stopSend() {
        if (sendThread != null) {
            sendThread.setSuspendFlag();
        }
    }

    /**
     * 实现串口数据的接收监听
     */
    public interface OnSerialPortReceivedListener {
        /**
         * 串口接收到数据后的回调
         *
         * @param comPortData 接收到的数据类
         */
        void onSerialPortDataReceived(ComPortData comPortData);
    }

    /**
     * 实现串口状态改变
     */
    public interface OnStatesChangeListener {
        /**
         * 打开时的回调
         *
         * @param isSuccess 是否成功
         * @param reason    原因
         */
        void onOpen(boolean isSuccess, String reason);

        /**
         * 关闭时的回调
         */
        void onClose();
    }
}