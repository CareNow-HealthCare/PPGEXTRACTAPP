package com.example.myapplication;

import android.hardware.camera2.CameraDevice;
import android.util.Log;

import androidx.annotation.NonNull;

public class CameraStateCallback extends CameraDevice.StateCallback {
    public CameraStateCallback() {
    }

    @Override
    public void onOpened(@NonNull CameraDevice cameraDevice) {
        Log.d("CAMCALLBACK","CAMERA OPEN--"+cameraDevice.getId());
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice cameraDevice) {

    }

    @Override
    public void onError(@NonNull CameraDevice cameraDevice, int i) {

    }
}
