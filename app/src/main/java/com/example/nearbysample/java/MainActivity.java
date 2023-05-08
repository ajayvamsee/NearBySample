package com.example.nearbysample.java;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.nearbysample.Endpoint;
import com.example.nearbysample.R;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.StandardCharsets;

public class MainActivity extends NearbyActivity {

    private static final String TAG = MainActivity.class.getSimpleName() + "logs";
    private State mState = State.UNKNOWN;

    private String mName;

    private static String SERVICE_ID = null;
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private TextView discoveredEndpoint;
    private TextView localTvEndPoint;
    private Button btnAdvertise;
    private Button btnSendMsg;
    private TextView tvDebug;
    private EditText etSendMsg;

    private Endpoint endpoint;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ----------------------------------------------------");
        setContentView(R.layout.activity_main);

        discoveredEndpoint = findViewById(R.id.tvdis);
        localTvEndPoint = findViewById(R.id.tvlocal);
        btnAdvertise = findViewById(R.id.btnAdvertise);
        tvDebug = findViewById(R.id.debug_log);
        btnSendMsg = findViewById(R.id.btnSendData);
        etSendMsg = findViewById(R.id.etSendMsg);


        mName = "Ultra";
        //mName  = "s5e";

        //SERVICE_ID = getPackageName();
       SERVICE_ID = "com.dooropen.mobile";
       //SERVICE_ID = "com.google.apps.hellouwb";


    }

    @Override
    protected String getName() {
        return mName;
    }

    @Override
    protected String getServiceId() {
        return SERVICE_ID;
    }

    @Override
    protected Strategy getStrategy() {
        return STRATEGY;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mState == State.CONNECTED) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();

        btnAdvertise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e(TAG, "onClick: " + mState);
                if (getState() != State.SEARCHING) {
                    setState(State.SEARCHING);
                    updateTextView(discoveredEndpoint, State.SEARCHING);
                }

               /* if(btnAdvertise.getText().toString()=="Turn on"){
                    btnAdvertise.setText("Turn off");

                }else if(btnAdvertise.getText().toString()=="Turn off"){
                    btnAdvertise.setText("Turn on");
                    rejectConnection(endpoint);
                }
*/

            }
        });

        btnSendMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == State.CONNECTED) {
                    appendToLogs("sending data " + etSendMsg.getText().toString());
                    Payload payload = Payload.fromBytes(etSendMsg.getText().toString().getBytes());
                    send(payload);
                } else {
                    log("Disconnected");
                }

            }
        });


    }

    @Override
    protected void onStop() {
        setState(State.UNKNOWN);
        super.onStop();
    }

    @Override
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        acceptConnection(endpoint);
    }

    @Override
    protected void onEndpointDiscovered(Endpoint endpoint) {
        stopDiscovering();
        connectToEndpoint(endpoint);
    }


    @Override
    protected void onConnectionFailed(Endpoint endpoint) {
        if (getState() == State.SEARCHING) {
            startDiscovering();
        }
    }

    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        setState(State.CONNECTED);
        updateTextView(discoveredEndpoint, State.CONNECTED);
        appendToLogs("onEndpointConnected:" + endpoint.getId() + ", Name " + endpoint.getName());
        discoveredEndpoint.append("Connected to " + endpoint.getName());
        this.endpoint = endpoint;
    }

    @Override
    protected void onEndpointDisconnected(Endpoint endpoint) {
        setState(State.SEARCHING);
    }


    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        byte[] receivedBytes = payload.asBytes();
        appendToLogs("onReceive: " + new String(receivedBytes, StandardCharsets.UTF_8));
    }

    private void setState(State state) {
        appendToLogs("setState: " + state);
        if (mState == state) {
            Log.d(TAG, "setState: " + state + "But already in that state");
            return;
        }
        State oldState = mState;
        mState = state;
        onStateChanged(oldState, state);
    }

    private State getState() {
        return mState;
    }

    private void onStateChanged(State olsState, State newState) {
        appendToLogs("onStateChanged: " + newState);
        // Update Nearby Connections to the new state.
        switch (newState) {
            case SEARCHING:
                disconnectFromAllEndpoints();
                startDiscovering();
                startAdvertising();
                localTvEndPoint.setText("Advertising with " + mName);
                break;
            case CONNECTED:
                stopDiscovering();
                stopAdvertising();
                break;
            case UNKNOWN:
                stopAllEndpoints();
                break;
        }
    }

    private void updateTextView(TextView textView, State state) {
        switch (state) {
            case SEARCHING:
                //textView.setBackgroundResource(R.color.state_searching);
                textView.setText(R.string.status_searching);
                break;
            case CONNECTED:
                //textView.setBackgroundResource(R.color.black);
                textView.setText(R.string.status_connected);
                break;
            default:
                //textView.setBackgroundResource(R.color.black);
                textView.setText(R.string.status_unknown);
                break;
        }
    }


    @Override
    protected void logsMessage(String msg) {
        Log.d(TAG, "logsMessage: " + msg);
    }

    public enum State {
        UNKNOWN,
        SEARCHING,
        CONNECTED
    }

    private void appendToLogs(CharSequence msg) {
        Log.d(TAG, "Logs: " + msg);
        tvDebug.append("\n");
        tvDebug.append(DateFormat.format("hh:mm", System.currentTimeMillis()) + ": ");
        tvDebug.append(msg);
        tvDebug.setMovementMethod(new ScrollingMovementMethod());
    }
}