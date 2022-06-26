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
    private static final String FLEX_SENSOR_SERVICE_UUID = "f020f474-36c6-4f9f-9fa5-9736ee68a8f9";
    private static final String MPU_SERVICE_UUID = "87c26127-73d1-4eb2-9531-f202d75bc7ba";

    private static final String THUMB_FINGER_UUID = "ffd6cbd5-28fc-4fc7-8760-b74e93f1a73d";
    private static final String INDEX_FINGER_UUID = "eb359b0e-fd69-4cfb-ad0d-9b4d4c3f83db";
    private static final String MIDDLE_FINGER_UUID = "fe3dae39-d576-4cd7-86e2-05b374258f20";
    private static final String RING_FINGER_UUID = "c18ca83e-8cba-4a57-811e-a5de911ffd41";
    private static final String PINKY_FINGER_UUID = "b713cb87-4234-4ac8-af57-86cf72fdbc1a";

    private static final String ACCEL_X_UUID = "fd27e73f-e235-472a-9c4d-6cf85665a9af";
    private static final String ACCEL_Y_UUID = "f81cb7d1-26bd-4954-b3ef-685ca4cc8f03";
    private static final String ACCEL_Z_UUID = "9ac1c4cc-67a2-487d-ad04-ba7aeabf89b3";
    private static final String GYRO_X_UUID = "b67f8d22-3085-4455-83cf-124c5054dd02";
    private static final String GYRO_Y_UUID = "b6f85fdc-d3a9-4e85-bdac-d88cd6ce3c7d";
    private static final String GYRO_Z_UUID = "796dc4e0-d89c-4bae-b4d9-475505a5d72d";

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
    private BluetoothGattService flexSensorService;
    private BluetoothGattService mpuService;

    private UnityCallback onThumbRead;
    private UnityCallback onIndexRead;
    private UnityCallback onMiddleRead;
    private UnityCallback onRingRead;
    private UnityCallback onPinkyRead;

    private UnityCallback onAccelXRead;
    private UnityCallback onAccelYRead;
    private UnityCallback onAccelZRead;
    private UnityCallback onGyroXRead;
    private UnityCallback onGyroYRead;
    private UnityCallback onGyroZRead;

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

    public void connectUnityCallbacks(UnityCallback onThumbRead,
                                      UnityCallback onIndexRead,
                                      UnityCallback onMiddleRead,
                                      UnityCallback onRingRead,
                                      UnityCallback onPinkyRead,
                                      UnityCallback onAccelXRead,
                                      UnityCallback onAccelYRead,
                                      UnityCallback onAccelZRead,
                                      UnityCallback onGyroXRead,
                                      UnityCallback onGyroYRead,
                                      UnityCallback onGyroZRead) {
        this.onThumbRead = onThumbRead;
        this.onIndexRead = onIndexRead;
        this.onMiddleRead = onMiddleRead;
        this.onRingRead = onRingRead;
        this.onPinkyRead = onPinkyRead;

        this.onAccelXRead = onAccelXRead;
        this.onAccelYRead = onAccelYRead;
        this.onAccelZRead = onAccelZRead;
        this.onGyroXRead = onGyroXRead;
        this.onGyroYRead = onGyroYRead;
        this.onGyroZRead = onGyroZRead;
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

            flexSensorService = gatt.getService(UUID.fromString(FLEX_SENSOR_SERVICE_UUID));
            if(flexSensorService == null) {
                Log.d(TAG, "ERROR: Service not found " + FLEX_SENSOR_SERVICE_UUID + ", disconnecting");
                gatt.disconnect();
                return;
            }

            chars.addAll(flexSensorService.getCharacteristics());

            mpuService = gatt.getService(UUID.fromString(MPU_SERVICE_UUID));
            if(mpuService == null) {
                Log.d(TAG, "ERROR: Service not found " + MPU_SERVICE_UUID + ", disconnecting");
                gatt.disconnect();
                return;
            }

            chars.addAll(mpuService.getCharacteristics());

            connectedGatt = gatt;
            requestCharacteristics();

            setUpFinished = true;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (characteristic.getUuid().equals(UUID.fromString(THUMB_FINGER_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onThumbRead.sendMessage(strValue);
                Log.d(TAG, "THUMB: " + strValue);
            } else if (characteristic.getUuid().equals(UUID.fromString(INDEX_FINGER_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onIndexRead.sendMessage(strValue);
                Log.d(TAG, "INDEX: " + strValue);
            } else if (characteristic.getUuid().equals(UUID.fromString(MIDDLE_FINGER_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onMiddleRead.sendMessage(strValue);
                Log.d(TAG, "MIDDLE: " + strValue);
            } else if (characteristic.getUuid().equals(UUID.fromString(RING_FINGER_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onRingRead.sendMessage(strValue);
                Log.d(TAG, "RING: " + strValue);
            } else if (characteristic.getUuid().equals(UUID.fromString(PINKY_FINGER_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onPinkyRead.sendMessage(strValue);
                Log.d(TAG, "PINKY: " + strValue);
            }
            else if (characteristic.getUuid().equals(UUID.fromString(ACCEL_X_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onAccelXRead.sendMessage(strValue);
                Log.d(TAG, "ACCEL_X: " + strValue);
            }
            else if (characteristic.getUuid().equals(UUID.fromString(ACCEL_Y_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onAccelYRead.sendMessage(strValue);
                Log.d(TAG, "ACCEL_Y: " + strValue);
            }
            else if (characteristic.getUuid().equals(UUID.fromString(ACCEL_Z_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onAccelZRead.sendMessage(strValue);
                Log.d(TAG, "ACCEL_Z: " + strValue);
            }
            else if (characteristic.getUuid().equals(UUID.fromString(GYRO_X_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onGyroXRead.sendMessage(strValue);
                Log.d(TAG, "GYRO_X: " + strValue);
            }
            else if (characteristic.getUuid().equals(UUID.fromString(GYRO_Y_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onGyroYRead.sendMessage(strValue);
                Log.d(TAG, "GYRO_Y: " + strValue);
            }
            else if (characteristic.getUuid().equals(UUID.fromString(GYRO_Z_UUID))) {
                String strValue = characteristic.getStringValue(0);
                onGyroZRead.sendMessage(strValue);
                Log.d(TAG, "GYRO_Z: " + strValue);
            }
            else {
                Log.d(TAG, "Unknown characteristic");
            }

            chars.remove(chars.get(chars.size() - 1));

            requestCharacteristics();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "changed");
        }
    };

    @SuppressLint("MissingPermission")
    public void requestCharacteristics() {
        if (chars.size() == 0) {
            chars.addAll(flexSensorService.getCharacteristics());
            chars.addAll(mpuService.getCharacteristics());
        }
        if(chars.size() > 0) {
            connectedGatt.readCharacteristic(chars.get(chars.size() - 1));
        }
    }

    public boolean setUpFinished() {
        return setUpFinished;
    }
}
