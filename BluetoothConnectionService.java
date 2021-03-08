package com.lenovo.farzinapp.bluetooth4;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {
    private static final String TAG = "BluetoothConnectionServ";
    private static final String appName = "MyApp";
    private static final UUID My_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mInsecureAcceptThread;

    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;

    private ConnectedThread mConnectedThread;

    public BluetoothConnectionService(Context mContext) {
        this.mContext = mContext;
        mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        start();
    }

    private class AcceptThread extends Thread{
        //the local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            //Create a new Listening server socket
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName,My_UUID_INSECURE);
                Log.d(TAG,"AcceptThread: Setting up Server using: "+My_UUID_INSECURE);
            } catch (IOException e) {
                Log.e(TAG,"AcceptThread: IOException : "+e.getMessage());
            }

            mmServerSocket = tmp;

        }

        public void run(){
            Log.d(TAG,"run:AcceptThread Running.");
            BluetoothSocket socket = null;

            try {
                Log.d(TAG,"run: RFCOM Server Socket Start .....");
                socket = mmServerSocket.accept();
                Log.d(TAG,"run: RFCOM server socket accepted connection. ");
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread: IOException: "+ e.getMessage());
            }

            if (socket != null){
                connected(socket,mmDevice);
            }

            Log.i(TAG,"END mAcceptedThread ");
        }
        public void cancel(){
            Log.d(TAG, "cancel: Canceling AcceptThread. ");
            try{
                mmServerSocket.close();
            }catch (IOException e){
                Log.e(TAG,"cancel: Close of AcceptThread ServerSocket failed. "+e.getMessage());
            }
        }
    }


    private class ConnectThread extends Thread{
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device , UUID uuid) {
            Log.d(TAG,"ConnectThread: started. ");
            mmDevice = device;
            deviceUUID = uuid;
        }

        public void run(){
            BluetoothSocket tmp = null;
            Log.i(TAG, "RUN mConnectThread ");

            //Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d(TAG,"ConnectThread: Trying to create InsecureRFcommSocket using UUID: "+My_UUID_INSECURE);
                tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG,"ConnectThread: Could not create InsecureRFcommSocket "+ e.getMessage());
            }

            mmSocket = tmp;

            //Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            //Make a connection to the BluetoothSocket

            try {
            //This is a blocking call and will only return on a
            //successfull connection or an exception
                mmSocket.connect();

                Log.d(TAG,"run: ConnectThread connected.");
            } catch (IOException e) {
                //Close the socket
                try {
                    mmSocket.close();
                    Log.d(TAG,"run: Closed Socket.");
                } catch (IOException ex) {
                    Log.e(TAG,"mConnectThread: run: Unable to close connection in socket "+ex.getMessage());
                }
                Log.d(TAG,"run: ConnectThread: Could not connect to UUID: "+My_UUID_INSECURE);
            }

            connected(mmSocket,mmDevice);
        }
        public void cancel(){
            try {
                Log.d(TAG,"cancel: Clothing Client Socket.");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG,"cancel: close() of mmSocket in Connectthread failed. " + e.getMessage());
            }
        }
    }

    public synchronized void start(){
        Log.d(TAG,"start");

        //Cancel any thread attempting to make a connection
        if (mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mInsecureAcceptThread == null){
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    public void startClient(BluetoothDevice device, UUID uuid){
        Log.d(TAG,"startClient: Started.");

        //initprogress dialog
        mProgressDialog = ProgressDialog.show(mContext,"Connecting Bluetooth","Please wait ... ",true);

        mConnectThread = new ConnectThread(device,uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket){
            Log.d(TAG, "ConnectedThread: Starting.");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //dismiss the progressdialog when connection is established
            try {
                mProgressDialog.dismiss();
            }catch (NullPointerException e){
                e.printStackTrace();
            }

            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];  //buffer store for the stream

            int bytes; // bytes returned from read()

            //keep listening to the Inputstream untill an exception occurs
            while (true){

                try {
                    //Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    String incomingMessage = new String(buffer,0 ,bytes);
                    Log.d(TAG,"inputStream: "+ incomingMessage);
                } catch (IOException e) {
                    Log.e(TAG, "write:Error reading inputstream . "+ e.getMessage());
                    break;
                }
            }
        }
        //Cal this from the main activity to send data to the remote device
        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG,"write: Writing to outputstream: "+ text);
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG,"write: Error writting to outputstream. "+e.getMessage());
            }
        }

        //callthis from the main activity to shutdown the connection
        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }

    }

    private void connected(BluetoothSocket mmSocket, BluetoothDevice mmDevice){
        Log.d(TAG , "connected: Starting.");

        // Start the thread to manage the connection and preform transmissions
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
    }

    //write to the ConnectedThread in an unsynchronized manner
    public void write(byte[] out){
        //Create temporary object
        ConnectedThread r;

        //Synchronize a copy of the ConnectedThread
        Log.d(TAG, "write: Write Clled. ");
        //perform the write
        mConnectedThread.write(out);

    }
}