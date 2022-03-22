package com.example.myapplication;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.ppgextract.CameraService;
import com.example.ppgextract.MessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private Button openCamera;
    private CameraManager cameraManager;
    private Handler handler;
    private CameraStateCallback cameraStateCallback;
    private Size videoSize;
    private String st4;
    CameraService cameraService;
    boolean mBound = false;
    private static final String APIKEY="SUNLIGHT";
    private static final String EMPID="8240328816";
    private static String URL = "https://sdk-dev.carenow.healthcare/vitals/create-token";
    private String height;
    private String weight;
    private EditText Height;
    private EditText Weight;
    private TextView Status;
    private Button Token;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show();
    }
    SharedPreferences preferences = getSharedPreferences("PPGAPP",0);
    SharedPreferences.Editor editor = preferences.edit();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler();




        cameraStateCallback = new CameraStateCallback();
        cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        Weight = findViewById(R.id.weight);
        Height = findViewById(R.id.height);
        Status = findViewById(R.id.status);
        Token = findViewById(R.id.button2);
        openCamera = findViewById(R.id.button);
        openCamera.setVisibility(View.INVISIBLE);
        Token.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                generateToken();
            }
        });
        openCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StartScan();

            }
        });
    }

    private void StartScan() {
        if (Height.getText().equals(null)) {
        }
        Intent intent = new Intent(MainActivity.this, CameraService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void generateToken() {
        height = String.valueOf(Height.getText());
        weight = String.valueOf(Weight.getText());
        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            JSONObject reqBody = new JSONObject();
            reqBody.put("api_key",APIKEY);
            reqBody.put("employee_id",EMPID);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, reqBody, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if(response.get("stausCode").equals(200)){

                            editor.putString("scanToken", String.valueOf(response.get("scan_token")));
                            editor.putString("apiKey",APIKEY);
                            editor.putString("empId",EMPID);
                            editor.putString("height",height);
                            editor.putString("weight",weight);
                            editor.commit();
                            openCamera.setVisibility(View.VISIBLE);

                        }else{
                            Log.d("RES--", String.valueOf(response.get("message")));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {

                }
            }){
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String>  params = new HashMap<String, String>();
                    params.put("Content-Type", "application/json");
                    params.put("Authorization", "aOfzaucVmyf37wXJ9ASsAqVlUkaEXpqqMjWqUQlF");
                    return  params;
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);

    }
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            CameraService.LocalBinder binder = (CameraService.LocalBinder) iBinder;
            cameraService = binder.getService();
            cameraService.readyCamera();
            Log.d("TAG--",cameraService.getCamera(cameraManager));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };
}