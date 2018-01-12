/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.Device;
import com.vuforia.EyewearDevice;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.ViewerParameters;
import com.vuforia.ViewerParametersList;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleApplicationControl;
import com.vuforia.samples.SampleApplication.SampleApplicationException;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.samples.SampleApplication.utils.Texture;
import com.vuforia.samples.VuforiaSamples.R;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenuInterface;


public class ImageTargets extends Activity implements SampleApplicationControl,
        SampleAppMenuInterface, SensorEventListener {
    private static final String LOGTAG = "ImageTargets";

    SampleApplicationSession vuforiaAppSession;

    private DataSet mCurrentDataset;
    private int mCurrentDatasetSelectionIndex = 0;
    private int mStartDatasetsIndex = 0;
    private int mDatasetsNumber = 0;
    private ArrayList<String> mDatasetStrings = new ArrayList<String>();

    // Our OpenGL view:
    private SampleApplicationGLView mGlView;

    // Our renderer:
    private ImageTargetRenderer mRenderer;

    private GestureDetector mGestureDetector;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private boolean mSwitchDatasetAsap = false;
    private boolean mFlash = false;
    private boolean mContAutofocus = false;
    private boolean mExtendedTracking = false;

    private View mFlashOptionView;

    private RelativeLayout mUILayout;

    private SampleAppMenu mSampleAppMenu;

    LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    boolean mIsDroidDevice = false;

    private boolean mPredictionEnabled = true;

    private static HashMap<String, Integer> textureIndices;

    // The distance from the target for the GPS detector to register it:
    private final float DISTANCE_THRESHOLD_IN_M = 20000;

    // The field of view of the GPS detector:
    private final float FIELD_OF_VIEW = 150;

    private Sensor accelerometer;
    private SensorManager sensorManager;
    private Sensor magnetometer;

    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean lastAccelerometerSet = false;
    private boolean lastMagnetometerSet = false;
    private float[] rotationMatrix = new float[9];

    private double azimuthAngle;

    private LocationService location;

    private LocationDatabaseHandler locationDatabaseHandler;
    private SQLiteDatabase locationDatabase;

    private TextView lookingAtTextView;
    private TextView lookingAtTextViewRight;

    private Handler handler;

    // Called when the activity first starts or the user navigates back to an
    // activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        vuforiaAppSession = new SampleApplicationSession(this);

        startLoadingAnimation();

        // Add the building images to detect:
        mDatasetStrings.add("BuildingsNoonNormal.xml");
        mDatasetStrings.add("BuildingsNoonCropped.xml");
        mDatasetStrings.add("Buildings4PMNormal.xml");
        mDatasetStrings.add("Buildings4PMCropped.xml");

        vuforiaAppSession
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mGestureDetector = new GestureDetector(this, new GestureListener());

        // Load any sample specific textures:
        mTextures = new Vector<Texture>();

        textureIndices = new HashMap<>();
        loadTextures();

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        locationDatabaseHandler = new LocationDatabaseHandler(this);

        location = new LocationService(this);

        lookingAtTextView = (TextView)findViewById(R.id.lookingAtTextView);
        lookingAtTextViewRight = (TextView)findViewById(R.id.lookingAtTextViewRight);

        handler = new Handler(getMainLooper());
    }

    // Process Single Tap event to trigger autofocus
    private class GestureListener extends
            GestureDetector.SimpleOnGestureListener {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();


        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }


        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable() {
                public void run() {
                    boolean result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);

                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);

            return true;
        }
    }


    // We want to load specific textures from the APK, which we will later use
    // for rendering.

    private void loadTextures() {

        // Add all the old images of the buildings:

       mTextures.add(Texture.loadTextureFromApk("OldBuildings/1900-1930/Bliss.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1900-1930/Bliss", 0);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1940-1960/Bliss.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1940-1960/Bliss", 1);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1970-1990/Bliss.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1970-1990/Bliss", 2);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1900-1930/Fisk.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1900-1930/Fisk", 3);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1940-1960/Fisk.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1940-1960/Fisk", 4);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1970-1990/Fisk.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1970-1990/Fisk", 5);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1900-1930/Nicely.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1900-1930/Nicely", 6);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1940-1960/Nicely.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1940-1960/Nicely", 7);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1970-1990/Nicely.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1970-1990/Nicely", 8);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1900-1930/IssamFares.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1900-1930/IssamFares", 9);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1940-1960/IssamFares.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1940-1960/IssamFares", 10);

        mTextures.add(Texture.loadTextureFromApk("OldBuildings/1970-1990/IssamFares.jpg",
                getAssets()));
        textureIndices.put("OldBuildings/1970-1990/IssamFares", 11);

    }


    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume() {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        try {
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }

        lastAccelerometerSet = false;
        lastMagnetometerSet = false;
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        locationDatabase = locationDatabaseHandler.getReadableDatabase();

        handler.postDelayed(new Runnable() {
            public void run() {
                updateBuilding();
                handler.postDelayed(this, 200);
            }
        }, 1000);
    }


    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
    }


    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause() {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        // Turn off the flash
        if (mFlashOptionView != null && mFlash) {
            // OnCheckedChangeListener is called upon changing the checked state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                ((Switch) mFlashOptionView).setChecked(false);
            } else {
                ((CheckBox) mFlashOptionView).setChecked(false);
            }
        }

        try {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        locationDatabase.close();

        sensorManager.unregisterListener(this);
    }


    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        try {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }


    // Initializes AR application components.
    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        Device device = Device.getInstance();
        device.setViewerActive(true); // Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.setMode(Device.MODE.MODE_AR); // Select if we will be in AR or VR mode

        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new ImageTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
    }


    private void startLoadingAnimation() {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay,
                null);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

    }


    // Methods to load and destroy tracking data.
    @Override
    public boolean doLoadTrackersData() {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();

        if (mCurrentDataset == null)
            return false;

        if (!mCurrentDataset.load(
                mDatasetStrings.get(mCurrentDatasetSelectionIndex),
                STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;

        if (!objectTracker.activateDataSet(mCurrentDataset))
            return false;

        int numTrackables = mCurrentDataset.getNumTrackables();
        for (int count = 0; count < numTrackables; count++) {
            Trackable trackable = mCurrentDataset.getTrackable(count);
            if (isExtendedTrackingActive()) {
                trackable.startExtendedTracking();
            }

            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data "
                    + (String) trackable.getUserData());
        }

        return true;
    }


    @Override
    public boolean doUnloadTrackersData() {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset != null && mCurrentDataset.isActive()) {
            if (objectTracker.getActiveDataSet().equals(mCurrentDataset)
                    && !objectTracker.deactivateDataSet(mCurrentDataset)) {
                result = false;
            } else if (!objectTracker.destroyDataSet(mCurrentDataset)) {
                result = false;
            }

            mCurrentDataset = null;
        }

        return result;
    }


    @Override
    public void onInitARDone(SampleApplicationException exception) {

        if (exception == null) {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            try {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e) {
                Log.e(LOGTAG, e.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (result)
                mContAutofocus = true;
            else
                Log.e(LOGTAG, "Unable to enable continuous autofocus");

            mSampleAppMenu = new SampleAppMenu(this, this, "Image Targets",
                    mGlView, mUILayout, null);
            setSampleAppMenuSettings();

        } else {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }


    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message) {
        final String errorMessage = message;
        runOnUiThread(new Runnable() {
            public void run() {
                if (mErrorDialog != null) {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        ImageTargets.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle(getString(R.string.INIT_ERROR))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton(getString(R.string.button_OK),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    @Override
    public void onVuforiaUpdate(State state) {
        if (mSwitchDatasetAsap) {
            mSwitchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker
                    .getClassType());
            if (ot == null || mCurrentDataset == null
                    || ot.getActiveDataSet() == null) {
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
            }

            doUnloadTrackersData();
            doLoadTrackersData();
        }
    }


    @Override
    public boolean doInitTrackers() {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;

        // Trying to initialize the image tracker
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null) {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
        return result;
    }


    @Override
    public boolean doStartTrackers() {
        // Indicate if the trackers were started correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.start();

        return result;
    }


    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();

        return result;
    }


    @Override
    public boolean doDeinitTrackers() {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());

        return result;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Process the Gestures
        if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
            return true;

        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_MENU)
        {
            // Toggling predictive tracking only works for eyewear devices
            if (Device.getInstance() instanceof EyewearDevice)
            {
                // Toggle prediction
                // NOTE: You would typically want to check the result
                // of setPredictiveTracking to see if it was successful
                ((EyewearDevice) Device.getInstance()).setPredictiveTracking(!mPredictionEnabled);
                mPredictionEnabled = !mPredictionEnabled;
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }


    boolean isExtendedTrackingActive() {
        return mExtendedTracking;
    }

    final public static int CMD_BACK = -1;
    final public static int CMD_EXTENDED_TRACKING = 1;
    final public static int CMD_AUTOFOCUS = 2;
    final public static int CMD_FLASH = 3;
    final public static int CMD_CAMERA_FRONT = 4;
    final public static int CMD_CAMERA_REAR = 5;
    final public static int CMD_YEAR_1900_1930 = 6;
    final public static int CMD_YEAR_1940_1960 = 7;
    final public static int CMD_YEAR_1970_1990 = 8;
    final public static int CMD_DATASET_START_INDEX = 11;

    public static String CHOSEN_YEAR_DIRECTORY = "OldBuildings/1900-1930/";


    // This method sets the menu's settings
    private void setSampleAppMenuSettings() {
        SampleAppMenuGroup group;

        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem("Building Detector", -1);

        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_extended_tracking),
                CMD_EXTENDED_TRACKING, false);
        group.addSelectionItem(getString(R.string.menu_contAutofocus),
                CMD_AUTOFOCUS, mContAutofocus);
        mFlashOptionView = group.addSelectionItem(
                getString(R.string.menu_flash), CMD_FLASH, false);

        CameraInfo ci = new CameraInfo();
        boolean deviceHasFrontCamera = false;
        boolean deviceHasBackCamera = false;
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == CameraInfo.CAMERA_FACING_FRONT)
                deviceHasFrontCamera = true;
            else if (ci.facing == CameraInfo.CAMERA_FACING_BACK)
                deviceHasBackCamera = true;
        }

        if (deviceHasBackCamera && deviceHasFrontCamera) {
            /*group = mSampleAppMenu.addGroup(getString(R.string.menu_camera),
                    true);
            group.addRadioItem(getString(R.string.menu_camera_front),
                    CMD_CAMERA_FRONT, false);
            group.addRadioItem(getString(R.string.menu_camera_back),
                    CMD_CAMERA_REAR, true);*/
        }

        group = mSampleAppMenu
                .addGroup(getString(R.string.menu_datasets), true);
        mStartDatasetsIndex = CMD_DATASET_START_INDEX;
        mDatasetsNumber = mDatasetStrings.size();

        group.addRadioItem("Noon", mStartDatasetsIndex, true);
        group.addRadioItem("Noon (Cropped)", mStartDatasetsIndex + 1, false);
        group.addRadioItem("Afternoon", mStartDatasetsIndex + 2, false);
        group.addRadioItem("Afternoon (Cropped)", mStartDatasetsIndex + 3, false);

        // Add options to choose year of old photos:
        group = mSampleAppMenu
                .addGroup("YEAR", true);

        group.addRadioItem("1900 - 1930", CMD_YEAR_1900_1930, true);
        group.addRadioItem("1940 - 1960", CMD_YEAR_1940_1960, false);
        group.addRadioItem("1970 - 1990", CMD_YEAR_1970_1990, false);

        mSampleAppMenu.attachMenu();

    }

    // Returns the index of the old image building to draw:
    public static int GetTextureIndex(String buildingName) {
        String key = CHOSEN_YEAR_DIRECTORY + buildingName;
        if (!textureIndices.containsKey(key)) {
            return 0;
        } else {
            return textureIndices.get(key);
        }
    }

    @Override
    public boolean menuProcess(int command) {
        boolean result = true;

        switch (command) {
            case CMD_YEAR_1900_1930:
                CHOSEN_YEAR_DIRECTORY = "OldBuildings/1900-1930/";
                break;
            case CMD_YEAR_1940_1960:
                CHOSEN_YEAR_DIRECTORY = "OldBuildings/1940-1960/";
                break;
            case CMD_YEAR_1970_1990:
                CHOSEN_YEAR_DIRECTORY = "OldBuildings/1970-1990/";
                break;
            case CMD_BACK:
                finish();
                break;

            case CMD_FLASH:
                result = CameraDevice.getInstance().setFlashTorchMode(!mFlash);

                if (result) {
                    mFlash = !mFlash;
                } else {
                    showToast(getString(mFlash ? R.string.menu_flash_error_off
                            : R.string.menu_flash_error_on));
                    Log.e(LOGTAG,
                            getString(mFlash ? R.string.menu_flash_error_off
                                    : R.string.menu_flash_error_on));
                }
                break;

            case CMD_AUTOFOCUS:

                if (mContAutofocus) {
                    result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);

                    if (result) {
                        mContAutofocus = false;
                    } else {
                        showToast(getString(R.string.menu_contAutofocus_error_off));
                        Log.e(LOGTAG,
                                getString(R.string.menu_contAutofocus_error_off));
                    }
                } else {
                    result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (result) {
                        mContAutofocus = true;
                    } else {
                        showToast(getString(R.string.menu_contAutofocus_error_on));
                        Log.e(LOGTAG,
                                getString(R.string.menu_contAutofocus_error_on));
                    }
                }

                break;

            /*case CMD_CAMERA_FRONT:
            case CMD_CAMERA_REAR:

                // Turn off the flash
                if (mFlashOptionView != null && mFlash) {
                    // OnCheckedChangeListener is called upon changing the checked state
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        ((Switch) mFlashOptionView).setChecked(false);
                    } else {
                        ((CheckBox) mFlashOptionView).setChecked(false);
                    }
                }

                vuforiaAppSession.stopCamera();

                try {
                    vuforiaAppSession
                            .startAR(command == CMD_CAMERA_FRONT ? CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_FRONT
                                    : CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_BACK);

                    mRenderer.updateConfiguration();

                } catch (SampleApplicationException e) {
                    showToast(e.getString());
                    Log.e(LOGTAG, e.getString());
                    result = false;
                }
                doStartTrackers();
                break;*/

            case CMD_EXTENDED_TRACKING:
                for (int tIdx = 0; tIdx < mCurrentDataset.getNumTrackables(); tIdx++) {
                    Trackable trackable = mCurrentDataset.getTrackable(tIdx);

                    if (!mExtendedTracking) {
                        if (!trackable.startExtendedTracking()) {
                            Log.e(LOGTAG,
                                    "Failed to start extended tracking target");
                            result = false;
                        } else {
                            Log.d(LOGTAG,
                                    "Successfully started extended tracking target");
                        }
                    } else {
                        if (!trackable.stopExtendedTracking()) {
                            Log.e(LOGTAG,
                                    "Failed to stop extended tracking target");
                            result = false;
                        } else {
                            Log.d(LOGTAG,
                                    "Successfully started extended tracking target");
                        }
                    }
                }

                if (result)
                    mExtendedTracking = !mExtendedTracking;

                break;

            default:
                if (command >= mStartDatasetsIndex
                        && command < mStartDatasetsIndex + mDatasetsNumber) {
                    mSwitchDatasetAsap = true;
                    mCurrentDatasetSelectionIndex = command
                            - mStartDatasetsIndex;
                }
                break;
        }

        return result;
    }


    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // Calculate azimuth angle:
    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
            lastAccelerometerSet = true;
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
            lastMagnetometerSet = true;
        }
        if (lastAccelerometerSet && lastMagnetometerSet) {

            SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer);

            float[] directionVector = RotateVector(new float[]{1, 0, -1}, rotationMatrix);

            azimuthAngle = Math.toDegrees(Math.atan2(directionVector[1], directionVector[2]) + Math.PI / 2);

            if (azimuthAngle > 180) {
                azimuthAngle -= 360;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    // Rotates a vector by multiplying it with a rotation matrix:
    public float[] RotateVector(float[] vector, float[] rotationMatrix) {
        float[] rotatedVector = new float[vector.length];

        for (int i = 0; i < rotatedVector.length; i++) {
            for (int j = 0; j < vector.length; j++) {
                rotatedVector[i] += vector[j] * rotationMatrix[i + j * rotatedVector.length];
            }
        }

        return rotatedVector;
    }

    // Gets the building the user is currently looking at:
    private void updateBuilding(){
        if (!locationDatabase.isOpen()){
            return;
        }

        String[] projection = {
                LocationDatabaseContract.Locations._ID,
                LocationDatabaseContract.Locations.COLUMN_NAME,
                LocationDatabaseContract.Locations.COLUMN_LAT,
                LocationDatabaseContract.Locations.COLUMN_LNG
        };

        Cursor cursor = locationDatabase.query(
                LocationDatabaseContract.Locations.TABLE_NAME,  // The table to query
                projection,                               // The columns to return
                null,                                // The columns for the WHERE clause
                null,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                null                                 // The sort order
        );

        cursor.moveToFirst();

        double minAngle = 360;
        String closestBuilding = "-";

        while (!cursor.isAfterLast()){

            double locationLat = cursor.getDouble(cursor.getColumnIndexOrThrow(LocationDatabaseContract.Locations.COLUMN_LAT));
            double locationLng = cursor.getDouble(cursor.getColumnIndexOrThrow(LocationDatabaseContract.Locations.COLUMN_LNG));

            String buildingName = cursor.getString(cursor.getColumnIndexOrThrow(LocationDatabaseContract.Locations.COLUMN_NAME));

            float[] results = new float[3];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    locationLat,
                    locationLng,
                    results);

            float distance = results[0];

            double angleToLocation = Math.atan2(locationLng - location.getLongitude(), locationLat - location.getLatitude());
            angleToLocation *= -1f;
            angleToLocation = Math.toDegrees(angleToLocation);

            double deltaAngle = GetDeltaAngle(azimuthAngle, angleToLocation);

            if (distance < DISTANCE_THRESHOLD_IN_M){
                if (deltaAngle < FIELD_OF_VIEW/2 && deltaAngle < minAngle){
                    minAngle = deltaAngle;
                    closestBuilding = buildingName;
                }
            }

            cursor.moveToNext();
        }

        lookingAtTextView.setText("Looking towards: " + closestBuilding);
        lookingAtTextViewRight.setText("Looking towards: " + closestBuilding);
    }

    // Gets the difference between two angles:
    public double GetDeltaAngle(double angle1, double angle2) {
        if (angle1 * angle2 >= 0) {
            return Math.abs(angle1 - angle2);
        } else {
            double posAngle = angle1 > 0 ? angle1 : angle2;
            double negAngle = angle1 < 0 ? angle1 : angle2;

            double paddedNegAngle = 360 + negAngle;

            double diff = paddedNegAngle - posAngle;

            if (diff > 180) {
                diff = 360 - diff;
            }

            return diff;
        }
    }
}
