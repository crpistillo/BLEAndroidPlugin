package com.carolinapistillo.ble_plugin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BLEPlugin {
    public interface UnityCallback {
        public void sendMessage(String message);
    }

    List<BluetoothGattCharacteristic> chars = new ArrayList<>();

    private static volatile BLEPlugin _instance;;
    private static final String TAG = "BLEPlugin";
    private static final int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private static final String DEVICE_ADDRESS = "94:B5:55:2C:C9:C2";
    private static final String SERVICE_UUID = "f020f474-36c6-4f9f-9fa5-9736ee68a8f9";
    private static final String CHAR_UUID = "eb359b0e-fd69-4cfb-ad0d-9b4d4c3f83db";

    private static final long SCAN_PERIOD = 10000;

    private Activity unityActivity;
    private boolean setUpFinished = false;
    private boolean scanning;
    private Handler handler = new Handler();
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(DEVICE_ADDRESS).build();
    private List<ScanFilter> filters = Arrays.asList(filter);
    private ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0)
            .build();
    private BluetoothGatt connectedGatt;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic characteristicForRead;

    private UnityCallback onCharRead;

    public static BLEPlugin getInstance(Activity activity)
    {
        if (_instance == null ) {
            synchronized (BLEPlugin.class) {
                if (_instance == null) {
                    Log.d(TAG, "Creation of instance");
                    _instance = new BLEPlugin(activity);
                }
            }
        }
        return _instance;
    }

    public void connectUnityCallbacks(UnityCallback onCharRead) {
        this.onCharRead = onCharRead;
    }

    private BLEPlugin(Activity activity) {
        Log.d(TAG, "saving unityActivity in private var");
        unityActivity = activity;
    }

    public void prepareAndStartBleScan() {
        final List<String> wantedPermissions = Build.VERSION.SDK_INT >= 31 ?
                Arrays.asList(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN}) : Collections.emptyList();

        if (readyToScan()) {
            safeStartBleScan();
        } else {
            Log.d(TAG, "Not ready to scan");
            unityActivity.finish();
        }
    }

    @SuppressLint("MissingPermission")
    private boolean readyToScan() {
        final List<String> wantedPermissions = Build.VERSION.SDK_INT >= 31 ?
                Arrays.asList(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN}) : Collections.emptyList();

        if(wantedPermissions.size() != 0 && !hasPermissions(wantedPermissions)) {
            return false;
        }

        bluetoothManager = unityActivity.getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            unityActivity.startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
        }

        return true;
    }

    private boolean hasPermissions(List<String> wantedPermissions) {
        for(String permission : wantedPermissions) {
            if(unityActivity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private void safeStartBleScan() {
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {
                @SuppressLint("MissingPermission")
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    Log.d(TAG, String.valueOf(result.getDevice()));
                    if (scanning) {
                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                    connect(result.getDevice());
                }
            };

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        device.connectGatt(unityActivity, true, gattCallback);
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            String deviceAddress = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to " + deviceAddress);

                    (new Handler(Looper.getMainLooper())).post((Runnable)(new Runnable() {
                        @SuppressLint("MissingPermission")
                        public final void run() {
                            gatt.discoverServices();
                        }
                    }));
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconneted from " + deviceAddress);
                    connectedGatt = (BluetoothGatt)null;
                    characteristicForRead = (BluetoothGattCharacteristic)null;
                    gatt.close();
                }
            } else {
                gatt.close();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered: " + gatt.getServices().size());

            if (status == 129 /*GATT_INTERNAL_ERROR*/) {
                Log.d(TAG, "ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting");
                gatt.disconnect();
                return;
            }

            service = gatt.getService(UUID.fromString(SERVICE_UUID));
            if(service == null) {
                Log.d(TAG, "ERROR: Service not found " + SERVICE_UUID + ", disconnecting");
                gatt.disconnect();
                return;
            }

            connectedGatt = gatt;
            characteristicForRead = service.getCharacteristic(UUID.fromString(CHAR_UUID));

            readCharacteristic();

            setUpFinished = true;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (characteristic.getUuid().equals(UUID.fromString(CHAR_UUID))) {
                String strValue = characteristic.getStringValue(0);
                Log.d(TAG, "CHAR: " + strValue);
                onCharRead.sendMessage(strValue);
            }
            else {
                Log.d(TAG, "Unknown characteristic");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "changed");
        }
    };

    @SuppressLint("MissingPermission")
    public void readCharacteristic() {
        connectedGatt.readCharacteristic(characteristicForRead);
    }

    public boolean setUpFinished() {
        return setUpFinished;
    }
}
