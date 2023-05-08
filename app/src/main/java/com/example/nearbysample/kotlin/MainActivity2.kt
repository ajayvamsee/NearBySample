package com.example.nearbysample.kotlin

import android.os.Bundle
import android.text.format.DateFormat
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.nearbysample.R
import com.example.nearbysample.java.MainActivity
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.Strategy
import java.nio.charset.StandardCharsets

open class MainActivity2 : NearbyConnections() {

    private val TAG = MainActivity::class.java.simpleName + "logs"
    private var mState = State.UNKNOWN

    private var mName: String? = null

    private var SERVICE_ID: String? = null
    private val STRATEGY = Strategy.P2P_STAR
    private var discoveredEndpoint: TextView? = null
    private var localTvEndPoint: TextView? = null
    private var btnAdvertise: Button? = null
    private var btnSendMsg: Button? = null
    private var tvDebug: TextView? = null
    private var etSendMsg: EditText? = null

    private var endpoint: Endpoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        discoveredEndpoint = findViewById(R.id.tvdis)
        localTvEndPoint = findViewById(R.id.tvlocal)
        btnAdvertise = findViewById(R.id.btnAdvertise)
        tvDebug = findViewById(R.id.debug_log)
        btnSendMsg = findViewById(R.id.btnSendData)
        etSendMsg = findViewById(R.id.etSendMsg)


        mName = "Ultra"
        //mName  = "s5e";

        //SERVICE_ID = getPackageName();
        //mName  = "s5e";

        //SERVICE_ID = getPackageName();
        SERVICE_ID = "com.dooropen.mobile"
    }

    override fun onStart(){
        super.onStart()

        btnAdvertise!!.setOnClickListener {
            Log.e(TAG, "onClick: $mState")
            if (getState() != State.SEARCHING) {
                setState(State.SEARCHING)
                updateTextView(discoveredEndpoint!!, State.SEARCHING)
            }

            /* if(btnAdvertise.getText().toString()=="Turn on"){
                         btnAdvertise.setText("Turn off");

                     }else if(btnAdvertise.getText().toString()=="Turn off"){
                         btnAdvertise.setText("Turn on");
                         rejectConnection(endpoint);
                     }
     */
        }

        btnSendMsg!!.setOnClickListener {
            if (mState == State.CONNECTED) {
                appendToLogs("sending data " + etSendMsg!!.text.toString())
                val payload = Payload.fromBytes(etSendMsg!!.text.toString().toByteArray())
                send(payload)
            } else {
                log("Disconnected")
            }
        }
    }

    override fun getName(): String? {
        return mName
    }

    override fun getServiceId(): String? {
        return SERVICE_ID
    }

    override fun getStrategy(): Strategy {
        return STRATEGY
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return if (mState == State.CONNECTED) {
            true
        } else super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        setState(State.UNKNOWN)
        super.onStop()
    }

    override fun onConnectionInitiated(
        endpoint: Endpoint?,
        connectionInfo: ConnectionInfo?
    ) {
        acceptConnection(endpoint!!)
    }

    override fun onEndpointDiscovered(endpoint: Endpoint?) {
        stopDiscovering()
        connectToEndpoint(endpoint!!)
    }

    override fun onConnectionFailed(endpoint: Endpoint?) {
        if (getState() == State.SEARCHING) {
            startDiscovering()
        }
    }

    override fun onEndpointConnected(endpoint: Endpoint?) {
        setState(State.CONNECTED)
        updateTextView(discoveredEndpoint!!, State.CONNECTED)
        appendToLogs("onEndpointConnected:" + endpoint?.id + ", Name " + endpoint?.name)
        discoveredEndpoint!!.append("Connected to " + endpoint?.name)
        this.endpoint = endpoint
    }

    override fun onEndpointDisconnected(endpoint: Endpoint?) {
        setState(State.SEARCHING)
    }


    override fun onReceive(endpoint: Endpoint?, payload: Payload?) {
        val receivedBytes = payload?.asBytes()
        appendToLogs("onReceive: " + String(receivedBytes!!, StandardCharsets.UTF_8))
    }

    private fun setState(state: State) {
        appendToLogs("setState: $state")
        if (mState == state) {
            Log.d(TAG, "setState: " + state + "But already in that state")
            return
        }
        val oldState: State = mState
        mState = state
        onStateChanged(oldState, state)
    }

    private fun getState(): State {
        return mState
    }

    private fun onStateChanged(olsState: State, newState: State) {
        appendToLogs("onStateChanged: $newState")
        when (newState) {
            State.SEARCHING -> {
                disconnectFromAllEndpoints()
                startDiscovering()
                startAdvertising()
                localTvEndPoint!!.text = "Advertising with $mName"
            }

            State.CONNECTED -> {
                stopDiscovering()
                stopAdvertising()
            }

            State.UNKNOWN -> stopAllEndpoints()
        }
    }

    private fun updateTextView(textView: TextView, state: State) {
        when (state) {
            State.SEARCHING ->                 //textView.setBackgroundResource(R.color.state_searching);
                textView.setText(R.string.status_searching)

            State.CONNECTED ->                 //textView.setBackgroundResource(R.color.black);
                textView.setText(R.string.status_connected)

            else ->                 //textView.setBackgroundResource(R.color.black);
                textView.setText(R.string.status_unknown)
        }
    }

    override fun logsMessage(msg: String?) {
        Log.d(TAG, "logsMessage: $msg")
    }

    enum class State{
        UNKNOWN,
        SEARCHING,
        CONNECTED
    }

    private fun appendToLogs(msg: CharSequence) {
        Log.d(TAG, "Logs: $msg")
        tvDebug!!.append("\n")
        tvDebug!!.append(DateFormat.format("hh:mm", System.currentTimeMillis()).toString() + ": ")
        tvDebug!!.append(msg)
        tvDebug!!.movementMethod = ScrollingMovementMethod()
    }


}