package com.example.myapplication;



import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
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

import com.example.ppgextract.CameraModule;
import com.example.ppgextract.MyListener;


import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private Button openCamera;
    private CameraManager cameraManager;
    private Handler handler;
    //private CameraStateCallback cameraStateCallback;
    private Size videoSize;
    private String st4;
    //CameraService cameraService;
    boolean mBound = false;
    private static final String APIKEY="SUNLIFE";
    private static final String EMPID="8240328816";
    private static String URL = "https://sdk-dev.carenow.healthcare/vitals/create-token";
    private String height;
    private String weight;
    private EditText Height;
    private EditText Weight;
    private TextView Status;
    private Button Token;
    private boolean previewReady=false;
    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener= new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            previewReady=true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };
    //private EditText resBody;
    private Button reset;
    private static final int requestCode = 100;
    private boolean isBind = false;
    //private AutoFitTextureView autoFitTextureView;

    CameraModule cameraModule;
    private TextView heartrate;
    private TextView bpsys;
    private TextView bpdia;
    private TextView bpressys;
    private TextView Percent;
    private TextView bpresdia;


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

        cameraModule = new CameraModule(getApplicationContext(),myListener);
        SharedPreferences preferences = getSharedPreferences("PPGAPP",0);
        SharedPreferences.Editor editor = preferences.edit();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        handler = new Handler();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, requestCode);
        }


        Percent = findViewById(R.id.status2);

        cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        Weight = findViewById(R.id.weight);
        Height = findViewById(R.id.height);
        heartrate = findViewById(R.id.heart);
        bpsys = findViewById(R.id.bpsys);
        bpdia = findViewById(R.id.bpdia);
        bpresdia=findViewById(R.id.bpresdia);
        bpressys = findViewById(R.id.bpresysys);
        Status = findViewById(R.id.status);
        Token = findViewById(R.id.button2);
        openCamera = findViewById(R.id.button);
        reset = findViewById(R.id.button3);
        reset.setVisibility(View.INVISIBLE);
        //autoFitTextureView = findViewById(R.id.textureView);
        openCamera.setVisibility(View.INVISIBLE);
        //resBody = findViewById(R.id.resBody);
        //resBody.setVisibility(View.INVISIBLE);
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
                if(previewReady) {
                cameraModule = new CameraModule(getApplicationContext(),myListener);
                    textureView.setVisibility(View.VISIBLE);
                    cameraModule.checkToken(textureView);
                    reset.setVisibility(View.VISIBLE);
                    openCamera.setVisibility(View.INVISIBLE);
                }
            }
        });

    }
    MyListener myListener = new MyListener() {
        @Override
        public void onStatusChange(String result) {
            Status.setText(result);
        }

        @Override
        public void onScanResult(JSONObject result) {
            String HR="",BPSYS="",BPDIA="",BPSYSRES="",BPDIARES="";
            try {

                Log.d("RESULT--", String.valueOf(result));

                HR= String.valueOf((int) result.get("heart_rate"));
                BPSYS= String.valueOf((int) result.get("bp_sys"));
                BPDIA= String.valueOf((int) result.get("bp_dia"));
                BPSYSRES= String.valueOf((int) result.get("bp_res_sys"));
                BPDIARES= String.valueOf((int) result.get("bp_res_dia"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Log.d("VALUE--", String.valueOf(HR));

            heartrate.setText(String
                    .valueOf( HR));
            bpsys.setText(BPSYS);
            bpdia.setText((BPDIA));
            bpressys.setText((BPSYSRES));
            bpresdia.setText((BPDIARES));
            reset.setVisibility(View.VISIBLE);


        }

        @Override
        public void onScanProgressed(int progress) {
            String p = progress+"%";
            Percent.setText(p);
        }
    };



    private void resetState() {
        textureView.setVisibility(View.INVISIBLE);
        height =null;
        weight = null;
        Height.setText("");
        Weight.setText("");
        Percent.setText("");
        heartrate.setText("--");
        bpsys.setText("--");
        bpdia.setText("--");
        bpressys.setText("--");
        bpresdia.setText("--");
        Token.setVisibility(View.VISIBLE);
        openCamera.setVisibility(View.INVISIBLE);
        //resBody.setText("");
        //resBody.setVisibility(View.INVISIBLE);
        Height.setVisibility(View.VISIBLE);
        Weight.setVisibility(View.VISIBLE);
        //Intent intent = new Intent(MainActivity.this,CameraService.class);
        //stopService(intent);
        reset.setVisibility(View.INVISIBLE);
        Status.setText("Status");
        //cameraService = null;

        //unbindService(connection);


    }

    private void StartScan() {
        if (Height.getText().equals(null)||Weight.getText().equals(null)) {
            Toast.makeText(this,"Enter Height and Weight",Toast.LENGTH_LONG).show();
            return;
        }
        Height.setVisibility(View.INVISIBLE);
        Weight.setVisibility(View.INVISIBLE);

        //cameraModule.checkToken();

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
                            Log.d("REQ-BODY--", String.valueOf(reqBody));
                            Toast.makeText(MainActivity.this, String.valueOf(response.get("message")), Toast.LENGTH_SHORT).show();
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

    }

    @Override
    protected void onStart() {
        super.onStart();


    }

}