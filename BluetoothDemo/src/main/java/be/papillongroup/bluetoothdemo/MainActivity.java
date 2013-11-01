package be.papillongroup.bluetoothdemo;

import android.bluetooth.*;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String TAG = "BluetoothDemo";

    Button led_btn;
    TextView arduino_txt;

    private BluetoothAdapter bt_adapter = null;
    private BluetoothSocket bt_socket = null;
    private StringBuilder sb = new StringBuilder();
    Handler h;
    private ConnectedThread m_connected_thread = null;

    private final UUID MY_UUID = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB");
    // TODO Change when you know the correct MAC address!!!
    private static String mac_address_arduino = "00:18:A1:12:16:BD";

    final int RECEIVE_MESSAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        led_btn = (Button) findViewById(R.id.button);
        arduino_txt = (TextView) findViewById(R.id.textView2);

        // Setup the handler
        h = new Handler() {
          public void handleMessage(android.os.Message msg) {
             switch (msg.what) {
                 case RECEIVE_MESSAGE:
                     byte[] read_buffer = (byte[]) msg.obj;
                     String incoming = new String(read_buffer, 0, msg.arg1);
                     sb.append(incoming);
                     int eol_index = sb.indexOf("\n");
                     if (eol_index > 0) {
                         String sb_print = sb.substring(0, eol_index);
                         sb.delete(0, sb.length());
                         arduino_txt.setText("Text from Arduino:\n" + sb_print);

                     }
                     break;
             }


          }
        };

        bt_adapter = BluetoothAdapter.getDefaultAdapter();
        check_bt_state();

        led_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (led_btn.getText() == "Led is Off") {
                    // Turn the led on!
                    m_connected_thread.write("1");
                    led_btn.setText("Led is On");
                    Toast.makeText(getBaseContext(), "Led will be turned on", 2);
                } else {
                    m_connected_thread.write("2");
                    led_btn.setText("Led is Off");
                    Toast.makeText(getBaseContext(), "Led will be turned off", 2);
                }
            }
        });

    }

    private BluetoothSocket create_bluetooth_socket(BluetoothDevice bt_device) throws IOException {
        if (Build.VERSION.SDK_INT >= 10) {
            try {
                final Method m = bt_device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] {UUID.class});
                return (BluetoothSocket) m.invoke(bt_device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG,"Failed to create insecure comms.");
            }
        }
        return bt_device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    private void check_bt_state() {

        if (bt_adapter == null) {
            errorExit("Fatal Error","Bluetooth not supported!");
        } else {
            if (bt_adapter.isEnabled()) {
                Log.d(TAG, " Bluetooth is on!");
            } else {
                Intent enable_bluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enable_bluetooth, 1);
                Log.d(TAG, "Bluetooth should be turning on...");
            }

        }
    }


    private void errorExit(String title, String msg) {
        Toast.makeText(getBaseContext(), title + " - " + msg, Toast.LENGTH_LONG).show();
        finish();
    }


    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "Resuming, trying to connect to the bluetooth module");

        BluetoothDevice device = bt_adapter.getRemoteDevice(mac_address_arduino);

        try {
            bt_socket = create_bluetooth_socket(device);
        } catch (IOException e) {
            errorExit("Fatal Error","Failed to create socket!! " + e.getMessage());
        }

        bt_adapter.cancelDiscovery();

        Log.d(TAG,"Connecting...");
        try {
            bt_socket.connect();
            Log.d(TAG, "Connection ok");
        } catch (IOException e) {
            Log.d(TAG, "Connection not ok!!");
            try {
                bt_socket.close();
            } catch (IOException f) {
                errorExit("Fatal Error","close socket failed!!! " + f.getMessage());
            }
        }

        m_connected_thread = new ConnectedThread(bt_socket);
        m_connected_thread.start();
    }

    @Override
    public void onPause(){
        super.onPause();

        Log.d(TAG,"Shutting down while in pause mode");
        try {
            bt_socket.close();
        } catch (IOException e) {
            errorExit("Fatal Error","Failed to close the socket!!! " + e.getMessage());
        }
    }

    private class ConnectedThread extends Thread {

        private final InputStream input_stream;
        private final OutputStream output_stream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmp_in = null;
            OutputStream tmp_out = null;

            try {
                tmp_in = socket.getInputStream();
                tmp_out = socket.getOutputStream();
            } catch (IOException e) {

            }

            input_stream = tmp_in;
            output_stream = tmp_out;
        }

        public void run() {

            byte[] buffer = new byte[256];
            int bytes;

            while (true) {

                try {

                    bytes = input_stream.read(buffer);
                    h.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget();

                } catch (IOException e) { break; }

            }


        }

        public void write(String message) {
            Log.d(TAG, "Writing data to bluetooth module");
            byte[] msgbuffer = message.getBytes();
            try {
                output_stream.write(msgbuffer);
            } catch (IOException e) {
                Log.d(TAG, "Failed to write data to bluetooth stack! " + e.getMessage());
            }
        }


    }
}
