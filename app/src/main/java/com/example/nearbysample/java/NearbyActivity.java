package com.example.nearbysample.java;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nearbysample.Endpoint;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class NearbyActivity extends AppCompatActivity {

    public static final String TAG = "NearbyActivity";
    private final int PERMISSION_REQUEST_CODE = 1234;

    private static final List<String> PERMISSION_REQUIRED_BEFORE_T = Arrays.asList(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE

    );

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static final String[] PERMISSION_REQUIRED_T = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static final String[] PERMISSION_ABOVE_T = {
            Manifest.permission.NEARBY_WIFI_DEVICES,
    };

    private static String[] PERMISSION_REQUIRED = PERMISSION_REQUIRED_BEFORE_T.toArray(new String[0]);

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<String> permissionList = new ArrayList<>(PERMISSION_REQUIRED_BEFORE_T);
            permissionList.addAll(Arrays.asList(PERMISSION_REQUIRED_T));
            permissionList.addAll(Arrays.asList(PERMISSION_ABOVE_T)); // need to fix to nearby device
            PERMISSION_REQUIRED = permissionList.toArray(new String[0]);
        }
    }

    private ConnectionsClient connectionsClient;

    private final Map<String, Endpoint> mDiscoveredEndpoints = new HashMap<>();

    private final Map<String, Endpoint> mPendingConnections = new HashMap<>();

    private final Map<String, Endpoint> mEstablishedConnections = new HashMap<>();

    private boolean mIsConnecting = false;

    /**
     * True if we are discovering.
     */
    private boolean mIsDiscovering = false;

    /**
     * True if we are advertising.
     */
    private boolean mIsAdvertising = false;

    // callbacks for connections to other devices
    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {

           log(endpointId+" Connection Info Name "+connectionInfo.getEndpointName()+
                    " Connection getAuthenticationDigits "+connectionInfo.getAuthenticationDigits()+
                    " Connection getAuthenticationToken "+connectionInfo.getAuthenticationToken());

            Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
            mPendingConnections.put(endpointId, endpoint);
            NearbyActivity.this.onConnectionInitiated(endpoint, connectionInfo);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            log( "onConnectionResult: "+endpointId+" Status "+result.getStatus());
            mIsConnecting = false;

            if (!result.getStatus().isSuccess()) {
                onConnectionFailed(mPendingConnections.remove(endpointId));
                return;
            }
            connectedToEndpoint(mPendingConnections.remove(endpointId));
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            log( "onDisconnected: "+endpointId);
            if (!mEstablishedConnections.containsKey(endpointId)) {
                return;
            }
            disconnectedFromEndpoint(mEstablishedConnections.get(endpointId));


        }
    };

    // callbacks for payloads sent from another device to us

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointID, @NonNull Payload payload) {
            //log("onPayloadReceived: "+payload.getType());
            switch (payload.getType()){
                case Payload.Type.BYTES:
                    Log.d(TAG, "onPayloadReceived: "+ payload.asBytes());
                    break;
                case Payload.Type.FILE:
                    Log.d(TAG, "onPayloadReceived: "+payload.asFile());
                    break;
                case Payload.Type.STREAM:
                    Log.d(TAG, "onPayloadReceived: "+payload.asStream());
                    break;
            }
            onReceive(mEstablishedConnections.get(endpointID), payload);
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointID, @NonNull PayloadTransferUpdate payloadTransferUpdate) {
        }
    };


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectionsClient = Nearby.getConnectionsClient(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        requestOurPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            int i = 0;
            for (int grantResult : grantResults) {

                Log.d(TAG, "Result permission Granted " + permissions[i]);
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "Result permission DENIED " + permissions[i]);
                    Log.d(TAG, "Failed to request the permission " + permissions[i]);
                    Toast.makeText(this, "error_missing_permissions", Toast.LENGTH_LONG).show();
                    //finish();
                    return;
                }
                i++;
            }
            recreate();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // Advertising
    protected void startAdvertising() {
        log("startAdvertising: "+getName());

        mIsAdvertising = true;
        final String localEndpointName = getName();
        final String SERVICE_ID = getServiceId();

        AdvertisingOptions.Builder advertisingOptions = new AdvertisingOptions.Builder();
        advertisingOptions.setStrategy(getStrategy());

        connectionsClient.startAdvertising(
                localEndpointName,
                SERVICE_ID,
                connectionLifecycleCallback,
                advertisingOptions.build()
        ).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                onAdvertisingStarted();
            }
        }).addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        onAdvertisingFailed();
                    }
                }
        );

    }

    protected void stopAdvertising() {
        log("stopAdvertising: ");
        mIsAdvertising = false;
        connectionsClient.stopAdvertising();
    }

    protected boolean isAdvertising() {
        return mIsAdvertising;
    }

    protected void onAdvertisingStarted() {
    }

    protected void onAdvertisingFailed() {
    }

    protected void logsMessage(String msg){

    }


    // Pending connection with a remote endpoint
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
    }

    protected void acceptConnection(final Endpoint endpoint) {
        log("acceptConnection: End Point ID:-"+endpoint.getId()+ " End Point Name:- "+endpoint.getName());
        connectionsClient.acceptConnection(endpoint.getId(), payloadCallback)
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                log("onFailure: " + e);
                            }
                        }
                );
    }

    protected void rejectConnection(Endpoint endpoint) {
        log("rejectConnection: End Point ID:-"+endpoint.getId()+ " End Point Name:- "+endpoint.getName());
        connectionsClient
                .rejectConnection(endpoint.getId())
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                log( "onFailure: rejectConnection() failed."+e);
                            }
                        });
    }


    // Discover
    protected void startDiscovering() {
        log("startDiscovering: ");
        mIsDiscovering = true;
        mDiscoveredEndpoints.clear();

        DiscoveryOptions.Builder discoverOptions = new DiscoveryOptions.Builder();
        discoverOptions.setStrategy(getStrategy());

        connectionsClient.startDiscovery(
                        getServiceId(),
                        new EndpointDiscoveryCallback() {
                            @Override
                            public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                                Log.d(TAG, "onEndpointFound: " + String.format("(endpointId=%s, serviceId=%s, endpointName=%s)",
                                        endpointId, info.getServiceId(), info.getEndpointName()));
                                log("onEndpointFound: Service ID: "+getServiceId()+" Discovered Info Id "+info.getServiceId()+ " Discovered Endpoint Name "+info.getEndpointName());

                                if (getServiceId().equals(info.getServiceId())) {
                                     Endpoint endpoint = new Endpoint(endpointId, info.getEndpointName());
                                    mDiscoveredEndpoints.put(endpointId, endpoint);
                                    onEndpointDiscovered(endpoint);
                                }
                            }

                            @Override
                            public void onEndpointLost(@NonNull String endpointId) {
                                log(String.format("onEndpointLost(endpointId=%s)", endpointId));
                            }
                        },
                        discoverOptions.build()
                )
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        onDiscoveryStarted();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        onDiscoveryFailed();
                    }
                });
    }

    protected void stopDiscovering() {
        log("stopDiscovering: ");
        mIsDiscovering = false;
        connectionsClient.stopDiscovery();
    }

    protected boolean isDiscovering() {
        return mIsDiscovering;
    }


    // Called when discovery successfully starts
    protected void onDiscoveryStarted() {
    }

    protected void onDiscoveryFailed() {
    }

    protected void onEndpointDiscovered(Endpoint endpoint) {
    }

    protected void disconnect(Endpoint endpoint) {
        connectionsClient.disconnectFromEndpoint(endpoint.getId());
        mEstablishedConnections.remove(endpoint.getId());
    }

    protected void disconnectFromAllEndpoints() {
        log("disconnectFromAllEndpoints: ");
        for (Endpoint endpoint : mEstablishedConnections.values()) {
            connectionsClient.disconnectFromEndpoint(endpoint.getId());
        }
        mEstablishedConnections.clear();
    }

    protected void stopAllEndpoints() {
        log( "stopAllEndpoints: ");
        connectionsClient.stopAllEndpoints();
        mIsAdvertising = false;
        mIsDiscovering = false;
        mIsConnecting = false;
        mDiscoveredEndpoints.clear();
        mPendingConnections.clear();
        mEstablishedConnections.clear();
    }


    //  connection request to the endpoint

    protected void connectToEndpoint(final Endpoint endpoint) {
        log("connectToEndpoint: Sending a connection request to endpoint " + endpoint);
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true;

        // Ask to connect
        connectionsClient
                .requestConnection(getName(), endpoint.getId(), connectionLifecycleCallback)
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                log("requestConnection() failed."+e);
                                mIsConnecting = false;
                                onConnectionFailed(endpoint);
                            }
                        });
    }

    protected final boolean isConnecting() {
        return mIsConnecting;
    }

    private void connectedToEndpoint(Endpoint endpoint) {
        log(String.format("connectedToEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.put(endpoint.getId(), endpoint);
        onEndpointConnected(endpoint);
    }

    private void disconnectedFromEndpoint(Endpoint endpoint) {
        log(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.remove(endpoint.getId());
        onEndpointDisconnected(endpoint);
    }


    // connection with this endpoint has failed
    protected void onConnectionFailed(Endpoint endpoint) {
    }

    protected void onEndpointConnected(Endpoint endpoint) {
    }

    protected void onEndpointDisconnected(Endpoint endpoint) {
    }


    // currently connected endpoints.
    protected Set<Endpoint> getDiscoveredEndpoints() {
        return new HashSet<>(mDiscoveredEndpoints.values());
    }

    // Returns a list of currently connected endpoints.
    protected Set<Endpoint> getConnectedEndpoints() {
        return new HashSet<>(mEstablishedConnections.values());
    }

    protected void send(Payload payload){
        send(payload,mEstablishedConnections.keySet());
    }

    private void send(Payload payload, Set<String> endpoints) {
        connectionsClient.sendPayload(new ArrayList<>(endpoints), payload)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "onFailure: " + e);
                    }
                });
    }

    protected void onReceive(Endpoint endpoint, Payload payload) {
    }


    private void requestOurPermissions() {
        if (!arePermissionsGranted()) {
            requestPermissions(PERMISSION_REQUIRED, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean arePermissionsGranted() {
        for (String permission : PERMISSION_REQUIRED) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    protected abstract String getName();

    protected abstract String getServiceId();

    protected abstract Strategy getStrategy();

    private static String toString(Status status) {
        return String.format(
                Locale.US,
                "[%d]%s",
                status.getStatusCode(),
                status.getStatusMessage() != null
                        ? status.getStatusMessage()
                        : ConnectionsStatusCodes.getStatusCodeString(status.getStatusCode()));
    }

    public void log(String message) {
        Log.d(TAG, message);
        // You can add additional functionality here, such as writing to a log file
        logsMessage(message);
    }



}


