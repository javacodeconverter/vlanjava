package com.javas.vlan;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class JvlanVpnService extends VpnService implements Runnable {
    private static final String TAG = "JvlanVpnService";
    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private String serverIp;
    private int serverPort;
    private String netName;
    private String netPass;
    private String role;
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            serverIp = intent.getStringExtra("serverIp");
            serverPort = intent.getIntExtra("serverPort", 8888);
            netName = intent.getStringExtra("netName");
            netPass = intent.getStringExtra("netPass");
            role = intent.getStringExtra("role");
        }

        if (mThread != null && mThread.isAlive()) {
            isRunning = false;
            mThread.interrupt();
        }

        isRunning = true;
        mThread = new Thread(this, "JvlanVpnThread");
        mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (mThread != null) {
            mThread.interrupt();
        }
        closeTunnel();
        super.onDestroy();
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            String assignedIp = role.equals("create") ? "10.8.0.1" : "10.8.0.2";

            Builder builder = new Builder();
            builder.setMtu(1500);
            builder.addAddress(assignedIp, 24);
            builder.addRoute("10.8.0.0", 24);
            
            mInterface = builder.setSession("java's VLAN").establish();

            socket = new Socket(serverIp, serverPort);
            OutputStream socketOut = socket.getOutputStream();
            InputStream socketIn = socket.getInputStream();

            String handshake = (role.equals("create") ? "C" : "J") + ":" + netName + ":" + netPass + "\n";
            socketOut.write(handshake.getBytes());
            socketOut.flush();

            final FileInputStream tunIn = new FileInputStream(mInterface.getFileDescriptor());
            final Socket finalSocket = socket;
            
            Thread writerThread = new Thread(() -> {
                byte[] packetBuffer = new byte[32767];
                try {
                    OutputStream out = finalSocket.getOutputStream();
                    while (isRunning) {
                        int readBytes = tunIn.read(packetBuffer);
                        if (readBytes > 0) {
                            byte[] sizeHeader = ByteBuffer.allocate(4).putInt(readBytes).array();
                            synchronized (out) {
                                out.write(sizeHeader);
                                out.write(packetBuffer, 0, readBytes);
                                out.flush();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Write error: " + e.getMessage());
                }
            });
            writerThread.start();

            FileOutputStream tunOut = new FileOutputStream(mInterface.getFileDescriptor());
            byte[] sizeBuffer = new byte[4];

            while (isRunning) {
                int headerRead = 0;
                while (headerRead < 4) {
                    int r = socketIn.read(sizeBuffer, headerRead, 4 - headerRead);
                    if (r == -1) throw new Exception("Server closed connection");
                    headerRead += r;
                }

                int packetSize = ByteBuffer.wrap(sizeBuffer).getInt();
                if (packetSize <= 0 || packetSize > 32767) continue;

                byte[] packetData = new byte[packetSize];
                int dataRead = 0;
                while (dataRead < packetSize) {
                    int r = socketIn.read(packetData, dataRead, packetSize - dataRead);
                    if (r == -1) throw new Exception("Server closed connection mid-packet");
                    dataRead += r;
                }

                tunOut.write(packetData, 0, packetSize);
                tunOut.flush();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in Tunneling loop: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
            closeTunnel();
        }
    }

    private void closeTunnel() {
        try {
            if (mInterface != null) {
                mInterface.close();
                mInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing tunnel interface: " + e.getMessage());
        }
    }
}
