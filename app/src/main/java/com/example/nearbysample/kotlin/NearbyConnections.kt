package com.example.nearbysample.kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.nearbysample.java.NearbyActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.Locale

abstract class NearbyConnections : AppCompatActivity() {

    private var connectionsClient: ConnectionsClient? = null

    private var mDiscoveredEndpoints: MutableMap<String, Endpoint> = hashMapOf()

    private var mPendingConnections: MutableMap<String, Endpoint> = hashMapOf()

    private var mEstablishedConnections: MutableMap<String, Endpoint> = hashMapOf()

    private var mIsConnecting = false

    private var mIsDiscovering = false

    private var mIsAdvertising = false

    // callbacks for connections to other devices
    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                log(
                    endpointId + " Connection Info Name " + connectionInfo.endpointName +
                            " Connection getAuthenticationDigits " + connectionInfo.authenticationDigits
                )
                val endpoint = Endpoint(endpointId, connectionInfo.endpointName)
                mPendingConnections[endpointId] = endpoint
                this@NearbyConnections.onConnectionInitiated(endpoint = endpoint,connectionInfo)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                log("onConnectionResult: " + endpointId + " Status " + result.status)
                mIsConnecting = false
                if (!result.status.isSuccess) {
                    onConnectionFailed(mPendingConnections.remove(endpointId))
                    return
                }
                connectedToEndpoint(mPendingConnections.remove(endpointId)!!)
            }

            override fun onDisconnected(endpointId: String) {
                log("onDisconnected: $endpointId")
                if (!mEstablishedConnections.containsKey(endpointId)) {
                    return
                }
                disconnectedFromEndpoint(mEstablishedConnections[endpointId]!!)
            }
        }

    // callbacks for payloads sent from another device to us
    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointID: String, payload: Payload) {
            //log("onPayloadReceived: "+payload.getType());
            when (payload.type) {
                Payload.Type.BYTES -> Log.d(
                    NearbyActivity.TAG,
                    "onPayloadReceived: " + payload.asBytes()
                )

                Payload.Type.FILE -> Log.d(
                    NearbyActivity.TAG,
                    "onPayloadReceived: " + payload.asFile()
                )

                Payload.Type.STREAM -> Log.d(
                    NearbyActivity.TAG,
                    "onPayloadReceived: " + payload.asStream()
                )
            }
            onReceive(mEstablishedConnections[endpointID], payload)
        }

        override fun onPayloadTransferUpdate(
            endpointID: String,
            payloadTransferUpdate: PayloadTransferUpdate
        ) {
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionsClient = Nearby.getConnectionsClient(this)

    }

    override fun onStart() {
        super.onStart()

        requestOurPermissions()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for ((i, result) in grantResults.withIndex()) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    requestOurPermissions()
                } else {
                    Log.d(TAG, "onRequestPermissionsResult: " + permissions[i])
                    return
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }




    companion object {
        const val PERMISSION_REQUEST_CODE: Int = 1234

        val TAG: String = NearbyConnections::class.java.simpleName

        private val PERMISSION_REQUIRED_BEFORE_S = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )


        @RequiresApi(Build.VERSION_CODES.S)
        val PERMISSION_REQUIRES_S = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )


        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        val PERMISSION_REQUIRES_T = arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES
        )


        @SuppressLint("ObsoleteSdkInt")
        val PERMISSION_REQUIRED = PERMISSION_REQUIRED_BEFORE_S.toMutableList()
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addAll(PERMISSION_REQUIRES_S)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    addAll(PERMISSION_REQUIRES_T)
                }
            }.toTypedArray()


    }



    data class Endpoint(var id: String,var name: String)

    // call backs

    // Advertising
    protected open fun startAdvertising() {
        log("startAdvertising: " + getName())
        mIsAdvertising = true
        val localEndpointName = getName()
        val SERVICE_ID = getServiceId()
        val advertisingOptions = AdvertisingOptions.Builder()
        advertisingOptions.setStrategy(getStrategy()!!)
        connectionsClient!!.startAdvertising(
            localEndpointName!!,
            SERVICE_ID!!,
            connectionLifecycleCallback,
            advertisingOptions.build()
        ).addOnSuccessListener { onAdvertisingStarted() }
            .addOnFailureListener { onAdvertisingFailed() }
    }

    protected open fun stopAdvertising() {
        log("stopAdvertising: ")
        mIsAdvertising = false
        connectionsClient!!.stopAdvertising()
    }

    protected open fun isAdvertising(): Boolean {
        return mIsAdvertising
    }

    protected open fun onAdvertisingStarted() {}

    protected open fun onAdvertisingFailed() {}

    protected open fun logsMessage(msg: String?) {}


    // Pending connection with a remote endpoint
    protected open fun onConnectionInitiated(
        endpoint: Endpoint?,
        connectionInfo: ConnectionInfo?
    ) {
    }


    protected open fun acceptConnection(endpoint: Endpoint) {
        log("acceptConnection: End Point ID:-" + endpoint.id + " End Point Name:- " + endpoint.name)
        connectionsClient!!.acceptConnection(endpoint.id, payloadCallback)
            .addOnFailureListener { e -> log("onFailure: $e") }
    }

    protected open fun rejectConnection(endpoint: Endpoint) {
        log("rejectConnection: End Point ID:-" + endpoint.id + " End Point Name:- " + endpoint.name)
        connectionsClient
            ?.rejectConnection(endpoint.id)
            ?.addOnFailureListener { e -> log("onFailure: rejectConnection() failed.$e") }
    }

    // Discover
    protected open fun startDiscovering() {
        log("startDiscovering: ")
        mIsDiscovering = true
        mDiscoveredEndpoints.clear()
        val discoverOptions = DiscoveryOptions.Builder()
        discoverOptions.setStrategy(getStrategy()!!)
        connectionsClient!!.startDiscovery(
            getServiceId()!!,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d(
                        NearbyActivity.TAG, "onEndpointFound: " + String.format(
                            "(endpointId=%s, serviceId=%s, endpointName=%s)",
                            endpointId, info.serviceId, info.endpointName
                        )
                    )
                    log("onEndpointFound: Service ID: " + getServiceId() + " Discovered Info Id " + info.serviceId + " Discovered Endpoint Name " + info.endpointName)
                    if (getServiceId() == info.serviceId) {
                        val endpoint = Endpoint(endpointId, info.endpointName)
                        mDiscoveredEndpoints[endpointId] = endpoint
                        onEndpointDiscovered(endpoint)
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    log(String.format("onEndpointLost(endpointId=%s)", endpointId))
                }
            },
            discoverOptions.build()
        )
            .addOnSuccessListener { onDiscoveryStarted() }
            .addOnFailureListener { onDiscoveryFailed() }
    }

    protected open fun stopDiscovering() {
        log("stopDiscovering: ")
        mIsDiscovering = false
        connectionsClient!!.stopDiscovery()
    }

    protected open fun isDiscovering(): Boolean {
        return mIsDiscovering
    }

    // Called when discovery successfully starts
    protected open fun onDiscoveryStarted() {}

    protected open fun onDiscoveryFailed() {}

    protected open fun onEndpointDiscovered(endpoint: Endpoint?) {}

    protected open fun disconnect(endpoint: Endpoint) {
        connectionsClient!!.disconnectFromEndpoint(endpoint.id)
        mEstablishedConnections.remove(endpoint.id)
    }

    protected open fun disconnectFromAllEndpoints() {
        log("disconnectFromAllEndpoints: ")
        for (endpoint in mEstablishedConnections.values) {
            connectionsClient!!.disconnectFromEndpoint(endpoint.id)
        }
        mEstablishedConnections.clear()
    }

    protected open fun stopAllEndpoints() {
        log("stopAllEndpoints: ")
        connectionsClient!!.stopAllEndpoints()
        mIsAdvertising = false
        mIsDiscovering = false
        mIsConnecting = false
        mDiscoveredEndpoints.clear()
        mPendingConnections.clear()
        mEstablishedConnections.clear()
    }


    //  connection request to the endpoint
    protected open fun connectToEndpoint(endpoint: Endpoint) {
        log("connectToEndpoint: Sending a connection request to endpoint $endpoint")
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true

        // Ask to connect
        connectionsClient
            ?.requestConnection(getName()!!, endpoint.id, connectionLifecycleCallback)
            ?.addOnFailureListener { e ->
                log("requestConnection() failed.$e")
                mIsConnecting = false
                onConnectionFailed(endpoint)
            }
    }

    protected fun isConnecting(): Boolean {
        return mIsConnecting
    }

    fun connectedToEndpoint(endpoint: Endpoint) {
        log(String.format("connectedToEndpoint(endpoint=%s)", endpoint))
        mEstablishedConnections.put(endpoint.id, endpoint)
        onEndpointConnected(endpoint)
    }

    fun disconnectedFromEndpoint(endpoint: Endpoint) {
        log(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint))
        mEstablishedConnections.remove(endpoint.id)
        onEndpointDisconnected(endpoint)
    }

    // connection with this endpoint has failed
    protected open fun onConnectionFailed(endpoint: Endpoint?) {}

    protected open fun onEndpointConnected(endpoint: Endpoint?) {}

    protected open fun onEndpointDisconnected(endpoint: Endpoint?) {    }

    // currently connected endpoints.
    protected open fun getDiscoveredEndpoints(): Set<Endpoint?>? {
        return HashSet<Endpoint>(mDiscoveredEndpoints.values)
    }

    // Returns a list of currently connected endpoints.
    protected open fun getConnectedEndpoints(): Set<Endpoint?>? {
        return HashSet<Endpoint>(mEstablishedConnections.values)
    }

    protected open fun send(payload: Payload) {
        send(payload, mEstablishedConnections.keys)
    }

    private fun send(payload: Payload, endpoints: Set<String>) {
        connectionsClient!!.sendPayload(ArrayList(endpoints), payload)
            .addOnFailureListener { e -> Log.d(NearbyActivity.TAG, "onFailure: $e") }
    }

    protected open fun onReceive(endpoint: Endpoint?, payload: Payload?) {}


    private fun requestOurPermissions() {
        if (!arePermissionsGranted()) {
            requestPermissions(PERMISSION_REQUIRED, PERMISSION_REQUEST_CODE)
        }
    }

    private fun arePermissionsGranted(): Boolean {
        for (permission in PERMISSION_REQUIRED) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    protected abstract fun getName(): String?

    protected abstract fun getServiceId(): String?

    protected abstract fun getStrategy(): Strategy?

    fun toString(status: Status): String? {
        return String.format(
            Locale.US,
            "[%d]%s",
            status.statusCode,
            if (status.statusMessage != null) status.statusMessage else ConnectionsStatusCodes.getStatusCodeString(
                status.statusCode
            )
        )
    }

    open fun log(message: String?) {
        Log.d(TAG, message!!)
        // You can add additional functionality here, such as writing to a log file
        logsMessage(message)
    }
}