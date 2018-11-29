package com.example.franciszeeek.electrocardiogramapplication;


import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @OnClick(R.id.button3)
    void OnClick_F3() {
        Disconnect();
    }


    @BindView(R.id.txtView1)
    TextView txtView1;

    @BindView(R.id.txtView2)
    TextView txtView2;

    @BindView(R.id.graph1)
    GraphView graph1;

    @BindView(R.id.graph2)
    GraphView graph2;

    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter btAdapter = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "MY_APP_DEBUG_TAG";

    Handler bluetoothIn;
    final int handlerState = 0;
    private StringBuilder recDataString = new StringBuilder();
    private ConnectedThread mConnectedThread;

    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private double graphLastXValue = 5d;
    private LineGraphSeries<DataPoint> mSeries1;
    private LineGraphSeries<DataPoint> mSeries2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        final double[] signal = {0};
        final int[] bpm = {0};


        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                String[] splitData;
                if (msg.what == handlerState) {
                    String readMessage = (String) msg.obj;
                    int endOfLineIndex = readMessage.indexOf("~");
                    if (endOfLineIndex > 0) {
                        String dataInPrint = readMessage.substring(0, endOfLineIndex);
                        if (dataInPrint.charAt(0) == '#')								//if it starts with # we know it is what we are looking for
                        {
                            final String data = dataInPrint.substring(1, endOfLineIndex);
                            splitData = data.split(":");

                            //txtView1.setText(splitData[0]);
                            txtView2.setText(splitData[1]);

                            signal[0] = Double.parseDouble(splitData[0]);
                            bpm[0] = Integer.parseInt(splitData[1]);

                        }

                    }
                }
            }
        };

        mTimer = new Runnable() {
            @Override
            public void run() {
                graphLastXValue += 1d;
                mSeries1.appendData(new DataPoint(graphLastXValue, signal[0]), true, 100);
                mSeries2.appendData(new DataPoint(graphLastXValue, bpm[0]), true, 600);
                mHandler.postDelayed(this, 1);
            }
        };
        mHandler.postDelayed(mTimer, 1);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
        initGraph1(graph1);
        initGraph2(graph2);
    }

    public void initGraph1(GraphView graph) {
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(100);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(5);

        graph.getGridLabelRenderer().setLabelVerticalWidth(80);
        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getGridLabelRenderer().setGridColor(Color.RED);


        mSeries1 = new LineGraphSeries<>();
        mSeries1.setDrawDataPoints(false);
        mSeries1.setDrawBackground(false);
        mSeries1.setColor(Color.RED);


        graph.addSeries(mSeries1);
    }

    public void initGraph2(GraphView graph) {
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(600);

        graph.getGridLabelRenderer().setLabelVerticalWidth(50);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getGridLabelRenderer().setVerticalLabelsColor(Color.RED);
        graph.getGridLabelRenderer().setGridColor(Color.RED);

        mSeries2 = new LineGraphSeries<>();
        mSeries2.setDrawDataPoints(false);
        mSeries2.setDrawBackground(true);
        mSeries2.setColor(Color.RED);

        graph.addSeries(mSeries2);
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return  device.createRfcommSocketToServiceRecord(myUUID);
    }

    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        address = intent.getStringExtra(DeviceList.EXTRA_ADDRESS);
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
        }
        try
        {
            btSocket.connect();
        } catch (IOException e) {
            try
            {
                btSocket.close();
            } catch (IOException e2) {
                Log.e(TAG, "Error", e2);            }
        }

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
        mConnectedThread.write("x");



    }

    private void checkBTState() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[128];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(String input) {
            byte[] msgBuffer = input.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        try {
            btSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error", e);

        }
        mHandler.removeCallbacks(mTimer);
    }


    private void Disconnect() {
        if (btSocket != null)
        {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error", e);
            }
        }
        finish();
    }

}