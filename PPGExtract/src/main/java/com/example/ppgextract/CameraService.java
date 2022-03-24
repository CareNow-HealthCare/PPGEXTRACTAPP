package com.example.ppgextract;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.greenrobot.eventbus.EventBus;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static org.opencv.imgproc.Imgproc.cvtColor;

public class CameraService extends Service
{

    private static String URL = "https://sdk-dev.carenow.healthcare/vitals/add-scan";
    protected static final int CAMERA_CALIBRATION_DELAY = 500;
    protected static final String TAG = "myLog";
    protected static final int CAMERACHOICE = CameraCharacteristics.LENS_FACING_BACK;
    protected static long cameraCaptureStartTime;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;
    private final IBinder binder = new LocalBinder();
    private  long timeDiff;
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;

    private boolean scanStopped=false;
    private ArrayList<JSONObject> rawIntensity = new ArrayList<>();
    private ArrayList<Double> ppgTime = new ArrayList<>();
    private static boolean allow_scan;
    private String status;

    public class LocalBinder extends Binder {
        public CameraService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CameraService.this;
        }
    }



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
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
            CameraService.this.session = session;
            try {
                if(!scanStopped){
                session.setRepeatingRequest(createCaptureRequest(), null, backgroundHandler);
                cameraCaptureStartTime = System.currentTimeMillis ();
                status="CALIBERATION";
                EventBus.getDefault().post(new MessageEvent(status));}
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
        int frameCount=0;
        long frameTime=0;
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            Object[] objects=initFPSNew("Measure fps is-->",frameTime,frameCount);
            frameTime=(long)objects[0];
            frameCount=(int)objects[1];
            Image img = reader.acquireLatestImage();
            if (img != null) {

                if (System.currentTimeMillis () > cameraCaptureStartTime + CAMERA_CALIBRATION_DELAY) {
                    timeDiff  = System.currentTimeMillis()-cameraCaptureStartTime;
                    if(timeDiff>=20000d&&!status.equals("SCANNING")){
                        status="SCANNING";
                        EventBus.getDefault().post(new MessageEvent(status));}
                    if(timeDiff>=60000d||rawIntensity.size()==1800){EventBus.getDefault().post(new MessageEvent("APICALL"));
                        callAPI();
                    }
                   processImage(img);



                }
                img.close();
            }
        }
    };
    public static Object[] initFPSNew(String message,long startTime,int counter){

        Object[] mObjectTime=new Object[2];
        if(startTime==0){

            startTime=System.currentTimeMillis();
            mObjectTime[0]=startTime;
            counter+=1;
            mObjectTime[1]=counter;
        }else{
            long difference=System.currentTimeMillis()-startTime;
            //We wil check count only after 1 second laps
            double seconds = difference / 1000.0;

            if(seconds>=1)
            {
                Log.d("TAGFPS",message+ counter);
                counter=0;
                mObjectTime[0]=System.currentTimeMillis();
                mObjectTime[1]=counter;

            }else{
                counter++;
                mObjectTime[0]=startTime;
                mObjectTime[1]=counter;
            }

        }
        return mObjectTime;
    }

    private void callAPI() {
        SharedPreferences pref = getApplicationContext().getSharedPreferences("PPGAPP",0);
        String scanToken = pref.getString("scanToken","");
        String apiKey = pref.getString("apiKey","");
        String empId = pref.getString("empId","");
        String measured_height = pref.getString("height", "");
        String measured_weight = pref.getString("weight", "");
        String posture = pref.getString("posture", "");
        String URL = "https://sdk-dev.carenow.healthcare/vitals/add-scan";
        scanStopped=true;
        CameraService.this.session.close();
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
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


                try {
                    if(response.get("statusCode").equals(200)){
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("response", String.valueOf(response));
                        editor.commit();
                        EventBus.getDefault().post(new MessageEvent("SCANSUCCESS"));
                        stopBackgroundThread();
                        stopSelf();

                    }else{
                        EventBus.getDefault().post(new MessageEvent(response.getString("message")));
                        stopSelf();
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
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("PPGAPP",0);
        SharedPreferences.Editor editor = preferences.edit();
        String URL="https://sdk-dev.carenow.healthcare/vitals/check-token";
        String scanToken = preferences.getString("scanToken","");
        String apiKey = preferences.getString("apiKey","");
        String empId = preferences.getString("empId","");
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
        JSONObject reqBody =new JSONObject();
        try {
            reqBody.put("api_key",apiKey);
            reqBody.put("scan_token",scanToken);
            reqBody.put("employee_id",empId);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, reqBody, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if(response.get("statusCode").equals(200)){
                        allow_scan=true;
                        Log.d("SDK--","Token Verified");
                        readyCamera();

                        }else{
                            EventBus.getDefault().post(new MessageEvent((String) response.get("message")));
                            stopSelf();
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

    public void readyCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String pickedCamera = getCamera(manager);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(pickedCamera, cameraStateCallback,null );
            imageReader = ImageReader.newInstance(160, 120, ImageFormat.YUV_420_888, 4 /* images buffered */);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            Log.d(TAG, "imageReader created");
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }

    public String getCamera(CameraManager manager){
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                //fps = (Range[])characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                //Log.d("CHARACT--", String.valueOf());
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flags " + flags + " startId " + startId);

        readyCamera();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        Log.d(TAG,"onCreate service");
        super.onCreate();
        startBackgroundThread();
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("PPGAPP",0);
        //SharedPreferences.Editor editor = preferences.edit();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
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
    private void stopBackgroundThread(){
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread=null;
            backgroundHandler=null;

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void actOnReadyCameraDevice()
    {
        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, backgroundHandler);
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        try {
            session.abortCaptures();
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
        session.close();
        stopBackgroundThread();
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
            ppgTime.add(Double.valueOf(timeDiff));
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
            //builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,);
            builder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_TORCH);

            return builder.build();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }}
