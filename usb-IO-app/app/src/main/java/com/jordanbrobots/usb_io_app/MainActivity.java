package com.jordanbrobots.usb_io_app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private static final String TAG = "usb_io_app";
    private PendingIntent mPermissionIntent = null;
    private UsbDevice teensyDevice = null;
    private boolean teensyConnected = false;

    /* Run on creation of the application */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Register the USB Permission receiver */
        UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        /* Instantiate the timer for scheduling a task */
        myTimer = new Timer();
    }

    /* Receive acceptance or denial of USB Permissions */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            Log.d(TAG, "Permission to communicate with Teensy");
                            teensyConnected = true;
                            //Start the blink test
                            new Thread(myUSBThread).start();
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    /* Scan for the teensy device */
    public void scanForTeensy(View view) {
        teensyDevice = null;
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()) {
            UsbDevice temp = deviceIterator.next();
            Log.d(TAG, "Found Device name: "+temp.getDeviceName()+" PID: "+temp.getProductId()+" VID: "+temp.getVendorId());
            if (temp.getVendorId() == TeensyConstants.VENDOR_ID && temp.getProductId() == TeensyConstants.PRODUCT_ID) {
                Log.d(TAG, "Found Teensy");
                teensyDevice = temp;
                manager.requestPermission(teensyDevice, mPermissionIntent);
            }
        }
    }



    byte[] bytes = new byte[TeensyConstants.OUT_SIZE];
    UsbInterface intf;
    UsbEndpoint outendpoint;
    UsbDeviceConnection connection;
    Timer myTimer;
    Runnable myUSBThread = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Number of Interfaces: "+teensyDevice.getInterfaceCount());
            boolean forceClaim = true;
            int timeout = 2000; //msec

            intf = teensyDevice.getInterface(0);
            Log.d(TAG, "Number of Endpoints: "+intf.getEndpointCount());
            outendpoint = intf.getEndpoint(1);
            connection = ((UsbManager) getSystemService(Context.USB_SERVICE)).openDevice(teensyDevice);
            connection.claimInterface(intf, forceClaim);

            /* Clear all of the data in the buffer */
            for (int i=0; i<bytes.length; i++) {
                bytes[i] = 0;
            }

            /* Blink the LED using the Timer scheduler */
            currCount = 0;
            myTimer.scheduleAtFixedRate(new myBlinkTask(), 0, 20);

            /* Blink the LED 20 times with spin waits */
            /*for (int i=0; i<20; i++) {
                bytes[0] = 1;
                connection.bulkTransfer(outendpoint, bytes, bytes.length, timeout);
                myWait(5);
                Log.d(TAG, "Blink");
                bytes[0] = 0;
                connection.bulkTransfer(outendpoint, bytes, bytes.length, timeout);
                myWait(5);
            }*/
        }
    };

    /* Spin wait */
    public void myWait(long msec) {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis()-start) < msec) { }
    }


    public final int maxCount = 40;  //Number of times to blink x2
    public int currCount;
    /* Timer task for scheduling a blink  */
    public class myBlinkTask extends TimerTask {

        @Override
        public void run() {
          if (currCount < maxCount) {
              if (currCount%2 == 1) {
                  bytes[0] = 0;
              } else {
                  bytes[0] = 1;
              }
              connection.bulkTransfer(outendpoint, bytes, bytes.length, 100);
              currCount++;
          } else {
              Log.d(TAG, "Done Blinking");
              this.cancel();
          }
        }
    };
}
