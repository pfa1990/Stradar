package com.pfandev.android.stradarcontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemClick;
import butterknife.OnTouch;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // GENERAL VARIABLES
    private Car mCar = new Car();
    private byte[] mDataOut = new byte[6];
    private int state = 0;
    private byte[] distanceData = new byte[255];
    private int distanceIn = 0;
    private boolean readingDistance = false;
    private boolean distanceReady;
    private byte[] tiltData = new byte[255];
    private int tiltIn = 0;
    private boolean readingTilt = false;
    private boolean tiltReady;
    private boolean mControlledByAccelerometer = false;

    // VIEWS's VARIABLES
    @BindView(R.id.scan_button) Button scanButton;
    @BindView(R.id.speed_limited_button) Button speedLimitedButton;
    @BindView(R.id.speed_text_view) TextView speedTextView;
    @BindView(R.id.direction_text_view) TextView directionTextView;
    SeekBar throttleBrakeSeekBar;
    @BindView(R.id.bluetooth_image_button) ImageButton bluetoothImageButton;
    SeekBar leftRightSeekBar;
    @BindView(R.id.control_mode_button) Button controlModeButton;
    @BindView(R.id.car_active_button) Button carActiveButton;
    @BindView(R.id.front_car_image_view) ImageView frontCarImageView;
    @BindView(R.id.side_car_image_view) ImageView sideCarImageView;
    @BindView(R.id.devices_list_text_view) TextView devicesListTextView;
    @BindView(R.id.devices_list_view) ListView devicesListView;
    DrawingFrameView mDrawingFrameView;
    @BindView(R.id.navigation_image_view) ImageView navigationImageView;

    // BLUETOOTH's VARIABLES
    BluetoothAdapter mBluetoothAdapter;
    //private Set<BluetoothDevice> pairedDevices;
    ArrayAdapter<String> mArrayAdapter;
    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mDevices.add(device);
            }
        }
    };
    List<BluetoothDevice> mDevices;
    ConnectedThread mConnectedThread;
    Handler mSendingHandler = new Handler();
    Runnable mSendingRunnable = new Runnable() {
        @Override
        public void run() {
            updateDataOut();
            mConnectedThread.write(mDataOut);
            mSendingHandler.postDelayed(mSendingRunnable, 20);
        }
    };

    // DRAWING FRAME's VARIABLES
    List<Point> mPoints = new ArrayList<>();
    Handler mHandler = new Handler();
    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {

            if (state == 3) {
                if (distanceReady) {
                    if (distanceData[3] == '}') {
                        int pos = distanceData[0];
                        int distance = (distanceData[1] * 10) + distanceData[2];
                        int x;
                        int y;
                        double angle = 5.625 * pos;

                        //Log.i("Distance Info - ", "Data Received: angle = " + angle + ", distance = " +
                        //        distance);

                        if (pos == 0) {
                            mPoints.clear();
                        }

                        if (distance <= 350 && distance >= 0) {
                            x = (int) (Math.cos(Math.toRadians(angle)) * distance);
                            y = (int) (Math.sin(Math.toRadians(angle)) * distance);
                            mPoints.add(pos, new Point(x, y));
                        } else {
                            mPoints.add(pos, new Point(0, 0));
                        }

                        mDrawingFrameView.setPoints(mPoints);

                        if (pos == 32) {
                            Toast.makeText(getApplicationContext(), "Scan completed",
                                    Toast.LENGTH_SHORT).show();
                        }

                        //Log.i("Point Info - ", mPoints.get(listIndex).toString());

                        distanceReady = false;
                    }
                } else if (tiltReady) {
                    if (tiltData[3] == '}') {
                        int xTilt = tiltData[0] * 10;
                        int yTilt = tiltData[1] * 10;
                        int zTilt = tiltData[2];
                        int offset = 90;

                        //Log.i("Tilt Info - ", "Data Received: x = " + tiltData[0] + ", y = "
                        //        + tiltData[1] + ", z = " + tiltData[2]);

                        if (zTilt >= 9) {
                            frontCarImageView.setRotation(xTilt - offset);
                            sideCarImageView.setRotation(-yTilt + offset);
                        } else {
                            frontCarImageView.setRotation(-xTilt - offset);
                            sideCarImageView.setRotation(yTilt + offset);
                        }

                        tiltReady = false;
                    }
                }
                state = 0;
            }
            mHandler.postDelayed(mRunnable, 20);
        }
    };

    // ACCELEROMETER's VARIABLES
    SensorManager mSensorManager;
    Sensor mSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
            }
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getApplicationContext(), "Device does not support Bluetooth",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        ButterKnife.bind(this);

        mDataOut[0] = 123; // { - Start trace char
        mDataOut[5] = 125; // } - End of trace char

        mArrayAdapter = new ArrayAdapter<>(this, android.R.layout.
                simple_expandable_list_item_1);
        devicesListView.setAdapter(mArrayAdapter);
        mDevices = new ArrayList<>();

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        leftRightSeekBar = (SeekBar) findViewById(R.id.left_right_seek_bar);
        leftRightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isControlledByAccelerometer() && mCar.isActive()) {
                    mCar.setDirection((byte) progress);
                    updateTextViews();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                leftRightSeekBar.setProgress(10);
                mCar.setDirection((byte) 10);
                updateTextViews();
            }
        });

        throttleBrakeSeekBar = (SeekBar) findViewById(R.id.throttle_brake_seek_bar);
        throttleBrakeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isControlledByAccelerometer() && mCar.isActive()) {
                    mCar.setSpeed((byte) progress);
                    updateTextViews();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                throttleBrakeSeekBar.setProgress(10);
                mCar.setSpeed((byte) 10);
                updateTextViews();
            }
        });

        mDrawingFrameView = (DrawingFrameView) findViewById(R.id.drawing_frame_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        setWindowProperties();
        updateTextViews();
        mCar.setActive(false);
        carActiveButton.setText(R.string.off);
        carActiveButton.setEnabled(false);
        disableButtons();
        disableSeekBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if (mBluetoothAdapter.isEnabled()) {
            if (mConnectedThread != null && mConnectedThread.mmSocket.isConnected()) {
                if (mCar.isActive()) {
                    mCar.setActive(false);
                    disableButtons();
                    if (!isControlledByAccelerometer()) {
                        disableSeekBars();
                    }
                    mSendingHandler.removeCallbacks(mSendingRunnable);
                    mHandler.removeCallbacks(mRunnable);
                }
                mDrawingFrameView.setClear(true);
                navigationImageView.setVisibility(View.INVISIBLE);
                mCar.setCarInDefaultStatus();
                updateTextViews();
                mConnectedThread.cancel();
            }
            bluetoothImageButton.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_bluetooth_disabled_black_24dp));
            mBluetoothAdapter.disable();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isControlledByAccelerometer() && mCar.isActive()) {
            event.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
            mCar.setDirection((byte) (event.values[1] * 2.5 + 10));
            mCar.setSpeed((byte) (-event.values[0] * 3.5 + 30));
            updateTextViews();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ENABLE_BT) {
                Toast.makeText(getApplicationContext(), "Bluetooth connected",
                        Toast.LENGTH_SHORT).show();
                bluetoothImageButton.setImageDrawable(
                        ContextCompat.getDrawable(this, R.drawable.ic_bluetooth_black_24dp));
                mArrayAdapter.clear();
                navigationImageView.setVisibility(View.INVISIBLE);
                devicesListTextView.setVisibility(View.VISIBLE);
                devicesListView.setVisibility(View.VISIBLE);
                mBluetoothAdapter.startDiscovery();
            }
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(getApplicationContext(), "Connect Bluetooth canceled",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @OnItemClick(R.id.devices_list_view)
    public void connectToSelectedDevice(int position) {
        short trial = 0;
        ConnectThread thread = new ConnectThread(mDevices.get(position));
        thread.start();

        while(!thread.mmSocket.isConnected() && trial++ < 10){
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (thread.mmSocket.isConnected()) {
            Toast.makeText(getApplicationContext(), "Connection established with " +
                    thread.mmDevice.getName(), Toast.LENGTH_SHORT).show();
            bluetoothImageButton.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_bluetooth_connected_black_24dp));
            navigationImageView.setVisibility(View.VISIBLE);
            devicesListTextView.setVisibility(View.INVISIBLE);
            devicesListView.setVisibility(View.INVISIBLE);
            carActiveButton.setEnabled(true);
            mConnectedThread = new ConnectedThread(thread.mmSocket, this);
            mConnectedThread.start();
        } else {
            Toast.makeText(getApplicationContext(), "Cannot connect with " +
                    thread.mmDevice.getName(), Toast.LENGTH_SHORT).show();
            thread.cancel();
        }
    }

    public void disableButtons() {
        controlModeButton.setEnabled(false);
        scanButton.setEnabled(false);
        scanButton.setText(R.string.scan);
        speedLimitedButton.setEnabled(false);
        speedLimitedButton.setText(R.string.no);
    }

    public void disableSeekBars() {
        leftRightSeekBar.setProgress(10);
        leftRightSeekBar.setEnabled(false);
        throttleBrakeSeekBar.setProgress(10);
        throttleBrakeSeekBar.setEnabled(false);
    }

    public void enableSeekBars() {
        leftRightSeekBar.setEnabled(true);
        throttleBrakeSeekBar.setEnabled(true);
    }

    public boolean isControlledByAccelerometer() {
        return mControlledByAccelerometer;
    }

    public void setControlledByAccelerometer(boolean controlledByAccelerometer) {
        mControlledByAccelerometer = controlledByAccelerometer;
    }

    public void setWindowProperties() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @OnClick(R.id.bluetooth_image_button)
    public void switchBluetoothStatus() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (mConnectedThread != null && mConnectedThread.mmSocket.isConnected()) {
                if (mCar.isActive()) {
                    mCar.setActive(false);
                    disableButtons();
                    if (!isControlledByAccelerometer()) {
                        disableSeekBars();
                    }
                    carActiveButton.setText(R.string.off);
                    mSendingHandler.removeCallbacks(mSendingRunnable);
                    mHandler.removeCallbacks(mRunnable);
                }
                mDrawingFrameView.setClear(true);
                carActiveButton.setEnabled(false);
                mCar.setCarInDefaultStatus();
                updateTextViews();
                mConnectedThread.cancel();
            }
            bluetoothImageButton.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_bluetooth_disabled_black_24dp));
            navigationImageView.setVisibility(View.INVISIBLE);
            devicesListTextView.setVisibility(View.INVISIBLE);
            devicesListView.setVisibility(View.INVISIBLE);
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "Bluetooth disconnected",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @OnClick(R.id.car_active_button)
    public void switchCarActive() {
        if (mCar.isActive()) {
            mCar.setCarInDefaultStatus();
            updateTextViews();
            mCar.setActive(false);
            disableButtons();
            if (!isControlledByAccelerometer()) {
                disableSeekBars();
            }
            carActiveButton.setText(R.string.off);
            mSendingHandler.removeCallbacks(mSendingRunnable);
            mHandler.removeCallbacks(mRunnable);
        } else {
            mCar.setActive(true);
            controlModeButton.setEnabled(true);
            scanButton.setEnabled(true);
            speedLimitedButton.setEnabled(true);
            if (!isControlledByAccelerometer()) {
                enableSeekBars();
            }
            carActiveButton.setText(R.string.on);
            mSendingHandler.postDelayed(mSendingRunnable, 20);
            mHandler.postDelayed(mRunnable, 20);
        }
    }

    @OnClick(R.id.control_mode_button)
    public void switchControlMode() {
        mCar.setCarInNeutralPosition();
        updateTextViews();
        if (isControlledByAccelerometer()) {
            setControlledByAccelerometer(false);
            controlModeButton.setText(R.string.bars);
            enableSeekBars();
        } else {
            setControlledByAccelerometer(true);
            controlModeButton.setText(R.string.accelerometer);
            disableSeekBars();
        }
    }

    @OnTouch(R.id.scan_button)
    public boolean switchScan(MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            mCar.setScanning(true);
            Toast.makeText(getApplicationContext(), "Scanning", Toast.LENGTH_SHORT).show();
        } else {
            mCar.setScanning(false);
        }
        return false;
    }

    @OnClick(R.id.speed_limited_button)
    public void switchSpeedLimited() {
        if (mCar.hasSpeedLimited()) {
            mCar.setSpeedLimited(false);
            speedLimitedButton.setText(R.string.no);
        } else {
            mCar.setSpeedLimited(true);
            speedLimitedButton.setText(R.string.yes);
        }
    }

    public void updateDataOut() {
        mDataOut[1] = mCar.getDirection();
        mDataOut[2] = mCar.getSpeed();
        mDataOut[3] = (byte) (mCar.isScanning() ? 1 : 0);
        mDataOut[4] = (byte) (mCar.hasSpeedLimited() ? 1 : 0);
    }

    public void updateTextViews() {
        String newDirection = getResources().getString(R.string.direction) + (mCar.getDirection() - 10);
        String newSpeed = getResources().getString(R.string.speed) + (mCar.getSpeed() - 10);
        directionTextView.setText(newDirection);
        speedTextView.setText(newSpeed);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            }

            // Do work to manage the connection (in a separate thread)
            //manageConnectedSocket(mmSocket);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final AppCompatActivity mmContext;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, AppCompatActivity context) {
            mmSocket = socket;
            mmContext = context;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            int actualByte;

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    actualByte = 0;

                    if (bytes > 0) {
                        while (actualByte < bytes) {
                            switch (state) {
                                case 0:
                                    if (buffer[actualByte] == 'd') {
                                        readingDistance = true;
                                        state = 1;
                                    } else if (buffer[actualByte] == 'i') {
                                        readingTilt = true;
                                        state = 1;
                                    }
                                    break;
                                case 1:
                                    if (readingDistance) {
                                        if (buffer[actualByte] == '{') {
                                            state = 2;
                                        }
                                    } else if (readingTilt) {
                                        if (buffer[actualByte] == '{') {
                                            state = 2;
                                        }
                                    }
                                    break;
                                case 2:
                                    if (readingDistance) {
                                        if (buffer[actualByte] != '}') {
                                            distanceData[distanceIn] = buffer[actualByte];
                                            distanceIn++;
                                        } else {
                                            distanceData[distanceIn] = buffer[actualByte];
                                            distanceIn = 0;
                                            distanceReady = true;
                                            readingDistance = false;
                                            state = 3;
                                        }
                                    } else if (readingTilt) {
                                        if (buffer[actualByte] != '}') {
                                            tiltData[tiltIn] = buffer[actualByte];
                                            tiltIn++;
                                        } else {
                                            tiltData[tiltIn] = buffer[actualByte];
                                            tiltIn = 0;
                                            tiltReady = true;
                                            readingTilt = false;
                                            state = 3;
                                        }
                                    }
                                    break;
                                default:
                                    break;
                            }
                            actualByte++;
                        }
                    }
                } catch (IOException e) {
                    mmContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mmContext, "Connection lost", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
