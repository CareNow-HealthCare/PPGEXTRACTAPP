package com.example.myapplication;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
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
    private static final String APIKEY="CNCP7WP9kBDssPVtu61e";
    private static final String EMPID="BIRLA000006";
    private static String URL = "https://sdk-dev.carenow.healthcare/vitals/create-token";
    private String height;
    private String weight;
    private EditText Height;
    private EditText Weight;
    private TextView Status;
    private Button Token;
    private EditText resBody;
    private Button reset;
    private static final int requestCode = 100;
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        if(event.message.equals("SCANSUCCESS")){
            SharedPreferences preferences = getSharedPreferences("PPGAPP",0);
            String response = preferences.getString("response","");
            resBody.setText(response);
            resBody.setVisibility(View.VISIBLE);
            reset.setVisibility(View.VISIBLE);
        }
        Status.setText(event.message);
        Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == requestCode) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SharedPreferences preferences = getSharedPreferences("PPGAPP",0);
        SharedPreferences.Editor editor = preferences.edit();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, requestCode);
        }


        cameraStateCallback = new CameraStateCallback();
        cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        Weight = findViewById(R.id.weight);
        Height = findViewById(R.id.height);
        Status = findViewById(R.id.status);
        Token = findViewById(R.id.button2);
        openCamera = findViewById(R.id.button);
        reset = findViewById(R.id.button3);
        reset.setVisibility(View.INVISIBLE);

        openCamera.setVisibility(View.INVISIBLE);
        resBody = findViewById(R.id.resBody);
        resBody.setVisibility(View.INVISIBLE);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetState();
            }
        });
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

    private void resetState() {
        height =null;
        weight = null;
        Height.setText("");
        Weight.setText("");
        Token.setVisibility(View.VISIBLE);
        openCamera.setVisibility(View.INVISIBLE);
        resBody.setText("");
        resBody.setVisibility(View.INVISIBLE);
        Height.setVisibility(View.VISIBLE);
        Weight.setVisibility(View.VISIBLE);
        Intent intent = new Intent(MainActivity.this,CameraService.class);
        reset.setVisibility(View.INVISIBLE);
        Status.setText("Status");


    }

    private void StartScan() {
        if (Height.getText().equals(null)||Weight.getText().equals(null)) {
            Toast.makeText(this,"Enter Height and Weight",Toast.LENGTH_LONG).show();
            return;
        }
        Height.setVisibility(View.INVISIBLE);
        Weight.setVisibility(View.INVISIBLE);

        Intent intent = new Intent(MainActivity.this, CameraService.class);
       bindService(intent,connection,Context.BIND_AUTO_CREATE);

        openCamera.setVisibility(View.INVISIBLE);
    }

    private void generateToken() {
        height = String.valueOf(Height.getText());
        weight = String.valueOf(Weight.getText());
        SharedPreferences preferences = getSharedPreferences("PPGAPP",0);
        SharedPreferences.Editor editor = preferences.edit();
        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            JSONObject reqBody = new JSONObject();
            reqBody.put("api_key",APIKEY);
            reqBody.put("employee_id",EMPID);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, reqBody, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if(response.get("statusCode").equals(200)){

                            editor.putString("scanToken", String.valueOf(response.get("scan_token")));
                            editor.putString("apiKey",APIKEY);
                            editor.putString("empId",EMPID);
                            editor.putString("height",height);
                            editor.putString("weight",weight);
                            editor.putString("posture","Resting");
                            editor.commit();
                            Toast.makeText(MainActivity.this, "Token Generated", Toast.LENGTH_SHORT).show();
                            openCamera.setVisibility(View.VISIBLE);
                            Token.setVisibility(View.INVISIBLE);

                        }else{
                            Log.d("REQ-BODY--", String.valueOf(response));
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
                    final  Map<String, String>  params = new HashMap<>();

                    params.put("Authorization", "aOfzaucVmyf37wXJ9ASsAqVlUkaEXpqqMjWqUQlF");
                    return  params;
                }
            };
            jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(0,DefaultRetryPolicy.DEFAULT_MAX_RETRIES,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonObjectRequest);
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
            cameraService.checkToken();

            Log.d("TAG--",cameraService.getCamera(cameraManager));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
                Log.d("SERVICEE--","DISCONNETED");
        }
    };
}