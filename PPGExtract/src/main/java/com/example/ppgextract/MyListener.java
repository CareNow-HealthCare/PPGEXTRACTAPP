package com.example.ppgextract;

import org.json.JSONObject;

public interface MyListener {
    // you can define any parameter as per your requirement
    public void onStatusChange( String result);
    public void onScanResult(JSONObject result);
    public void onScanProgressed(int progress);
}