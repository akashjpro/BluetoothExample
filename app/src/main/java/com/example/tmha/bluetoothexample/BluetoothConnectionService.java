package com.example.tmha.bluetoothexample;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Created by tmha on 8/18/2017.
 */

public class BluetoothConnectionService {

    private static final String TAG = "BleConnectionService";
    private static final String APP_NAME = "APP_NAME";
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter mAdapter;
    private Context mContext;

    private AcceptThread mInsecureAcceptThread;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private BluetoothDevice mDevice;
    private UUID deviceUUID;
    private ProgressDialog mProgressDialog;


    public BluetoothConnectionService(Context mContext) {
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mContext = mContext;
    }

    public class AcceptThread extends Thread{
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            APP_NAME, MY_UUID_INSECURE);
                Log.d(TAG, "AcceptThread: "+ MY_UUID_INSECURE);
            } catch (IOException e) {

            }
            mmServerSocket = tmp;
        }


        public void run() {
            Log.d(TAG, "run: Accept Thread running.");

            BluetoothSocket socket = null;

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                socket = mmServerSocket.accept();
                Log.d(TAG, "run: Server socket aceptted connection");
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread:  IOException" + e.getMessage());
            }

            // If a connection was accepted
            if (socket != null) {
                Connected(socket, mDevice);
            }
        }

        public void cancel() {
            Log.d(TAG, "cancel: Cancel AcceptThread" );
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() of server failed", e);
            }
        }

    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectThread extends Thread {
        private  BluetoothSocket mSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "create ConnectedThread: ");
            mDevice = device;
            deviceUUID = uuid;
        }

        public void run() {
            BluetoothSocket tmp = null;
            Log.d(TAG, "run: ConnectedThread");

            try {
                Log.d(TAG, "run: ConnectedThread createRfcommSocketToServiceRecord");
                tmp = mDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG, "run: ConnectedThread IOException");
            }

            mSocket = tmp;
            mAdapter.cancelDiscovery();

            try {
                mSocket.connect();
                Log.d(TAG, "run: connect");
            } catch (IOException e) {
                try {
                    mSocket.close();
                    Log.d(TAG, "run: close socket");
                } catch (IOException e1) {
                    Log.e(TAG, "run: IOException connect"+ e1.getMessage() );
                }

                Log.d(TAG, "run: ConnectedThread could not to UUID" + MY_UUID_INSECURE);
            }

            Connected(mSocket, mDevice);
        }

        public void cancel() {
            Log.d(TAG, "cancel: Cancel AcceptThread" );
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() of server failed", e);
            }
        }


    }



    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }

    }

    public void startClient(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startClient: ");

        mProgressDialog = ProgressDialog.show(mContext, "Connect Bluetooth", "Please wait...", true);
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            mProgressDialog.dismiss();
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    String inComingMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity
                    Log.d(TAG, "run: InputStream"+ inComingMessage);

                    Intent inComingMessageIntent = new Intent("inComingMessage");
                    inComingMessageIntent.putExtra("theMessage", inComingMessage);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(inComingMessageIntent);
                } catch (IOException e) {
                    Log.d(TAG, "run: IOException ");
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {

            String text = new String(buffer, Charset.defaultCharset());
            Log.d(TAG, "write: ");
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


    private void Connected(BluetoothSocket mSocket, BluetoothDevice mDevice) {
        Log.d(TAG, "Connected: ");
        mConnectedThread = new ConnectedThread(mSocket);
        mConnectedThread.start();
    }

    public void write(byte[] out){
        ConnectedThread r;
        Log.d(TAG, "write: Write called");
        mConnectedThread.write(out);

    }
    
}

