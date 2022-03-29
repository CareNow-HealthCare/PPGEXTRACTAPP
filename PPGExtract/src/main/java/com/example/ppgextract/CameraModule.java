package com.example.ppgextract;

import android.Manifest;
import android.content.Context;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;

import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.CAMERA_SERVICE;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class CameraModule {
    private Context context;

    private static String URL = "https://sdk-dev.carenow.healthcare/vitals/add-scan";
    protected static final int CAMERA_CALIBRATION_DELAY = 500;
    protected static final String TAG = "myLog";
    protected static final int CAMERACHOICE = CameraCharacteristics.LENS_FACING_BACK;
    protected static long cameraCaptureStartTime;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private static boolean allow_scan;
    private  long timeDiff;
    private boolean scanStopped=false;
    private ArrayList<JSONObject> rawIntensity = new ArrayList<>();
    private ArrayList<Long> ppgTime = new ArrayList<>();
    private  int progress;
    private double percent=21000d;
    private TextureView preview;
    private String status="STANDBY";

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(context) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    private MyListener ml;
    protected CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {

            Log.d(TAG, "CameraDevice.StateCallback onOpened");
            cameraDevice = camera;
            actOnReadyCameraDevice();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "CameraDevice.StateCallback onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.StateCallback onError " + error);
        }
    };
    protected CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onReady(CameraCaptureSession session) {
            CameraModule.this.session = session;
            try {
                if(!scanStopped){
                    ml.onStatusChange(status);
                    session.setRepeatingRequest(createCaptureRequest(), null, null);
                    cameraCaptureStartTime = System.currentTimeMillis ();
                    status="CALIBRATING";
                    progress=0;
                    percent=21000d;
                    ml.onStatusChange(status);
                    //EventBus.getDefault().post(new MessageEvent("Caliberating"));
                }
            } catch (CameraAccessException e) {
                Log.e(TAG, e.getMessage());
            }
        }


        @Override
        public void onConfigured(CameraCaptureSession session) {

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };
    protected ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            Image img = reader.acquireLatestImage();
            if (img != null) {

                if (System.currentTimeMillis () > cameraCaptureStartTime + CAMERA_CALIBRATION_DELAY) {

                    timeDiff  = System.currentTimeMillis()-cameraCaptureStartTime;
                    if(timeDiff>=20000d){

                        if(!status.equals("SCANNING")){
                            status="SCANNING";
                            ml.onStatusChange(status);}
                        if(timeDiff>=percent){

                            //Log.d("PERCENT", String.valueOf(percent));
                            progress=progress+2;
                            percent=percent+1000d;
                            scanProgressed();
                        }

                    }
                    if(timeDiff>=60000d){
                        status="APICALL";
                        percent=80;
                        scanProgressed();
                        ml.onStatusChange(status);
                        scanStopped = true;
                        img.close();
                        reader.close();
                        try {
                            session.abortCaptures();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        callAPI();

                        return;
                    }
                }
                processImage(img);



            }


            img.close();

        }
    };

    private void callAPI() {
        SharedPreferences pref = context.getSharedPreferences("PPGAPP",0);
        String scanToken = pref.getString("scanToken","");
        String apiKey = pref.getString("apiKey","");
        String empId = pref.getString("empId","");
        String measured_height = pref.getString("height", "");
        String measured_weight = pref.getString("weight", "");
        String posture = pref.getString("posture", "");
        String URL = "https://sdk-dev.carenow.healthcare/vitals/add-scan";
        scanStopped=true;
        CameraModule.this.session.close();
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        JSONObject reqBody = new JSONObject();
        try {
            reqBody.put("heart_rate", "0");
            reqBody.put("oxy_sat_prcnt", "1");
            reqBody.put("posture", posture);
            reqBody.put("resp_rate", "16");
            reqBody.put("lat", "");
            reqBody.put("long", "");
            reqBody.put("scan_token", scanToken);
            reqBody.put("api_key", apiKey);
            reqBody.put("employee_id", empId);
            reqBody.put("ref_id","");
            String updateables[] =new String[3];
            updateables[0] = "heart_rate";
            updateables[1] = "oxy_sat_prcnt";
            updateables[2] = "resp_rate";
            JSONArray update = new JSONArray(updateables);
            reqBody.put("updateables",update);

            JSONObject metareqBody = new JSONObject();

            JSONObject physioreqBody = new JSONObject();
            physioreqBody.put("height", Integer.valueOf(measured_height));
            physioreqBody.put("weight", Integer.valueOf(measured_weight));
            metareqBody.put("physiological_scores", physioreqBody);


            JSONArray raw= new JSONArray(rawIntensity);
            metareqBody.put("raw_intensity", raw);
            JSONArray pparray = new JSONArray(ppgTime);
            metareqBody.put("ppg_time", pparray);
            Log.d("PPG--", String.valueOf(metareqBody));
            metareqBody.put("fps", rawIntensity.size()/40);
            metareqBody.put("device", "RPPG_CAREPLIX_FINGER_ANDROID");
            reqBody.put("platform","android");
            reqBody.put("app_version","5.0.0");





            reqBody.put("metadata", metareqBody);
            Log.d("RAWINTE", String.valueOf(physioreqBody));
            Log.d("REQBODY--", String.valueOf(reqBody));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        final String requestBody = reqBody.toString();

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, reqBody, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

                Log.d("SDKRES--", String.valueOf(response));

                try {
                    if(response.get("statusCode").equals(200)){
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("response", String.valueOf(response));
                        editor.commit();
                        JSONObject resultObject = response.getJSONObject("vitals");
                        ml.onScanResult(resultObject);
                        progress=99;
                        scanProgressed();
                        //resultObject.put("result",response.get("vitals"));
                        ml.onStatusChange("SCAN-COMPLETE");
                        //EventBus.getDefault().post(new MessageEvent("SCANSUCCESS"));
                        //stopBackgroundThread();
                        stopSDK();

                    }else{
                        //EventBus.getDefault().post(new MessageEvent(response.getString("message")));
                        //stopSelf();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(0,DefaultRetryPolicy.DEFAULT_MAX_RETRIES,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(jsonObjectRequest);


    }
    public void checkToken(){
        SharedPreferences preferences = context.getSharedPreferences("PPGAPP",0);
        SharedPreferences.Editor editor = preferences.edit();
        String URL="https://sdk-dev.carenow.healthcare/vitals/check-token";
        String scanToken = preferences.getString("scanToken","");
        String apiKey = preferences.getString("apiKey","");
        String empId = preferences.getString("empId","");
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        JSONObject reqBody =new JSONObject();
        try {
            reqBody.put("api_key",apiKey);
            reqBody.put("scan_token",scanToken);
            reqBody.put("employee_id",empId);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, reqBody, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d("TOKEN--", String.valueOf(response));
                    try {
                        if(response.get("statusCode").equals(200)){
                            allow_scan=true;
                            Log.d("SDK--","Token Verified");
                            readyCamera();

                        }else{
                            //EventBus.getDefault().post(new MessageEvent((String) response.get("message")));
                            ml.onStatusChange("SCAN-FAILED");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {

                }
            });
            requestQueue.add(jsonObjectRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    public void stopSDK()  {

        //stopBackgroundThread();
//        try {
//            session.abortCaptures();
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }



        onImageAvailableListener=null;
        session.close();
        cameraDevice.close();

        cameraDevice=null;


    }

    public void readyCamera() {
        scanStopped=false;
        //stopBackgroundThread();
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        try {
            String pickedCamera = getCamera(manager);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            manager.openCamera(pickedCamera, cameraStateCallback, null);
            imageReader = ImageReader.newInstance(160, 120, ImageFormat.YUV_420_888, 4 /* images buffered */);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
            Log.d(TAG, "imageReader created");
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }
    private void scanProgressed(){
        Log.d("pROGRESS--", String.valueOf(progress));
        ml.onScanProgressed(progress);

    }

    public String getCamera(CameraManager manager){
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CAMERACHOICE) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
        return null;
    }
    public CameraModule(Context context,MyListener ml){
        this.ml = ml;
        this.context=context;
        startBackgroundThread();
        SharedPreferences preferences = context.getSharedPreferences("PPGAPP",0);
        //SharedPreferences.Editor editor = preferences.edit();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, context, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("WorkerThread");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }
    public void stopBackgroundThread(){

        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread.quitSafely();
            backgroundHandlerThread=null;
            backgroundHandler=null;

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void actOnReadyCameraDevice()
    {

        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, null);
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }
    private void processImage(Image image){
        if (image != null) {

            byte[] nv21;
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            nv21 = new byte[ySize + uSize + vSize];

            //U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            Mat mRGB = getYUV2Mat(nv21,image);

            Scalar avgVal = Core.mean(mRGB);
            int avgR = (int) avgVal.val[0];
            int avgG = (int) avgVal.val[1];
            int avgB = (int) avgVal.val[2];
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("r",avgR);
                jsonObject.put("g",avgG);
                jsonObject.put("b",avgB);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            rawIntensity.add(jsonObject);
            ppgTime.add(timeDiff);
            Log.d("RAW--",String.valueOf(rawIntensity.size()));
            Log.d("PPG--",String.valueOf(ppgTime.size()));
            Log.d("START--", String.valueOf((cameraCaptureStartTime)));
            Log.d("DIFF--",String.valueOf(timeDiff));
            //EventBus.getDefault().post(new MessageEvent("Starting Calib"));

        }
    }
    public Mat getYUV2Mat(byte[] data, Image image) {
        Mat mYuv = new Mat(new Integer(image.getHeight() + image.getHeight() / 2),new Integer( image.getWidth()), CvType.CV_8UC1);
        mYuv.put(0, 0, data);
        Mat mRGB = new Mat();
        cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3);
        return mRGB;
    }
    protected CaptureRequest createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_TORCH);

            return builder.build();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }
}
