package com.sodavision.pylonandroid;

import static org.opencv.core.Core.mean;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.basler.pylon.EGrabStrategy;

import org.genicam.genapi.IBoolean;
import org.genicam.genapi.IFloat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.android.OpenCVLoader;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity implements LogTarget {


    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
            Log.e("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
        }
    }

    private static final int        PERMISSION_STORAGE_REQUEST = 0xBAce;
    private static final int        PERMISSION_CAMERA_REQUEST = 0xBAcf;
    private static final String     LOG_TAG ="PylonAndroid";

    private boolean isStopGrabbingImage = true;
    private String pixelFormatSelectedItemName = null;

    private PylonGrab m_PylonGrab = null;
    private String                  m_RootPathPictures = null;
    private AtomicBoolean           m_isCameraValid = new AtomicBoolean(false);
    private AtomicBoolean           m_isStoragePermissionGrant = new AtomicBoolean(false);
    private AtomicBoolean           m_isCameraPermissionGrant = new AtomicBoolean(false);
    private AtomicBoolean           m_isOnDestroyCalled = new AtomicBoolean(false);
    private Bitmap.CompressFormat   m_CurrentCompressFormat = Bitmap.CompressFormat.JPEG;

    private Bitmap grabImage = null;
    private Thread cameraLiveCaptureThread = null;

    private static double MAXIMUM_EXPOSURE_TIME = 0;
    private static double MINIMUM_EXPOSURE_TIME = 0;
    private static double MAXIMUM_GAIN = 0;
    private static double MINIMUM_GAIN = 0;

    private IFloat exposureTime = null;
    private IFloat gain = null;

    private boolean isFullScreen = false;

    private LinearLayout fullScreenLinearLayout = null;
    private LinearLayout settingViewLinearLayout = null;

    private TextView logTextView = null;
    private Spinner pixelFormatSpinner = null;
    private Spinner compressionFormatSpinner = null;

    private SeekBar exposureTimeSeekBar = null;
    private SeekBar gainSeekBar = null;
    private TextView exposureTimeTextView = null;
    private TextView gainTextView = null;
    private Spinner exposureTimeSpinner = null;
    private Spinner gainSpinner = null;

    private Spinner balanceWhiteSpinner = null;

    private Button liveButton = null;
    private Button saveImageButton = null;
    private Button stopCaptureButton = null;

    private Button acquisitionFrameRateConfirmButton = null;
    private IBoolean acquisitionFrameRateEnable = null;
    private CheckBox acquisitionFrameRateCheckBox = null;
    private EditText acquisitionFrameRateEditText = null;

    //UV TABLE VARIABLES
    int imageRows = 2146;
    int imageCols = 2663;
    private Mat uRawToSin = new Mat(imageRows, imageCols, CvType.CV_32F);
    private Mat vRawToSin = new Mat(imageRows, imageCols, CvType.CV_32F);
    private Mat uSinToTan = new Mat(imageRows, imageCols, CvType.CV_32F);
    private Mat vSinToTan = new Mat(imageRows, imageCols, CvType.CV_32F);
    private Mat uTanToSin = new Mat(imageRows, imageCols, CvType.CV_32F);
    private Mat vTanToSin = new Mat(imageRows, imageCols, CvType.CV_32F);

    //> App / Activity Lifecycle
    //------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------



    /** Activity create function.
     *  Called when the system creates our activity.
     **/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prepare the UI.
        setContentView(R.layout.activity_main);

        // lock the orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        logTextView = findViewById(R.id.logTextView);
        logTextView.setMovementMethod(new ScrollingMovementMethod() );

        pixelFormatSpinner = findViewById(R.id.pixelFormatSpinner);

        compressionFormatSpinner = findViewById( R.id.compressionFormatSpinner);
        compressionFormatSpinner.setOnItemSelectedListener(compressionFormatSpinnerOnItemSelectedListenerHandler);

        // LinearLayout Initialization
        fullScreenLinearLayout = findViewById(R.id.fullScreenLinearLayout);
        settingViewLinearLayout = findViewById(R.id.settingViewLinearLayout);
        fullScreenLinearLayout.setEnabled(false);
        fullScreenLinearLayout.setVisibility(View.INVISIBLE);

        // Prepare the camera, start an additional thread to keep the UI responsive.
        Thread sampleThread = new Thread( new PrepareCameraRunnable());
        //sampleThread.start(); //sean comment this out to allow it to run without the camera.
        //mostly I'm doing this so that I can try to implement the image processor on the bus

        // RUN OPEN CV IMAGE PROCESSOR TEST
        testImageProcessor("/storage/emulated/0/Documents/ONRDetector/TestPics");

        // Exposure time UI initialization
        exposureTimeTextView = findViewById(R.id.exposureTimeTextView);
        exposureTimeSeekBar = findViewById(R.id.exposureTimeSeekBar);
        exposureTimeSeekBar.setOnSeekBarChangeListener(exposureTimeSeekBarOnSeekBarChangeListenerHandler);

        exposureTimeSpinner = findViewById(R.id.exposureTimeSpinner);
        exposureTimeSpinner.setOnItemSelectedListener(exposureTimeSpinnerOnItemSelectedListenerHandler);

        // Gain UI initialization
        gainTextView = findViewById(R.id.gainTextView);
        gainSeekBar = findViewById(R.id.gainSeekBar);
        gainSeekBar.setOnSeekBarChangeListener(gainSeekBarOnSeekBarChangeListenerHandler);

        gainSpinner = findViewById(R.id.gainSpinner);
        gainSpinner.setOnItemSelectedListener(gainSpinnerOnItemSelectedListenerHandler);

        // Balance white UI initialization
        balanceWhiteSpinner = findViewById(R.id.balanceWhiteSpinner);
        balanceWhiteSpinner.setOnItemSelectedListener(balanceWhiteSpinnerOnItemSelectedListenerHandler);

        // Acquisition frame rate UI initialization
        acquisitionFrameRateConfirmButton = findViewById(R.id.acquisitionFrameConfirmButton);
        acquisitionFrameRateEditText = findViewById(R.id.acquisitionFrameRateEditText);
        acquisitionFrameRateEditText.setEnabled(false);
        acquisitionFrameRateCheckBox = findViewById(R.id.acquisitionFrameRateCheckBox);
        // CheckBox Listener setting
        acquisitionFrameRateCheckBox.setOnCheckedChangeListener(acquisitionFrameRateCheckBoxOnCheckedChangeListenerHandler);

        // Button initialization
        liveButton = findViewById(R.id.liveButton);
        saveImageButton = findViewById(R.id.saveImageButton);
        stopCaptureButton = findViewById(R.id.stopCaptureButton);

        // Disable UI
        tryChangeEnableUIState(false);

        // Prepare the path and permission to Write files.
        requestRights();
        m_RootPathPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        Log(LogLevel.Info, "Save images to: " + m_RootPathPictures);
    }


    /**
     *  Perform any final cleanup before an activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Mark that the App / Activity goes away
        m_isOnDestroyCalled.set(true);

        // Close / abort any camera usage and free used resources
        if( m_PylonGrab != null ) {
            m_PylonGrab.close();
        }
    }

    //> Listener
    //------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------

    /**
     *  Spinner OnItemSelectedListener initialization
     */

    /**
     * Called if a new item (PixelFormat) selected
     */
    private AdapterView.OnItemSelectedListener pixelFormatSpinnerOnItemSelectedListenerHandler = new AdapterView.OnItemSelectedListener()
    {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            try {
                String name = parent.getItemAtPosition(position).toString();

                // record the selected item
                pixelFormatSelectedItemName = name;

                Log(LogLevel.Info, "Selected pixelformat: " + name);
            }
            catch(Exception e) {
                Log(LogLevel.Error, "Exception setting pixel format: " + e.getMessage() );
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };


    /**
     * Called if a new item (Image format) selected
     */
    private AdapterView.OnItemSelectedListener compressionFormatSpinnerOnItemSelectedListenerHandler = new AdapterView.OnItemSelectedListener()
    {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
            // Convert the string to Bitmap.CompressFormat
            String compressionFormatName = parent.getItemAtPosition(position).toString();
            if( compressionFormatName.equals("PNG")) {
                m_CurrentCompressFormat = Bitmap.CompressFormat.PNG;
            }
            else if(compressionFormatName.equals("JPEG")) {
                m_CurrentCompressFormat = Bitmap.CompressFormat.JPEG;
            }
            else {
                Log( LogLevel.Error,"Unknown compression format " + compressionFormatName + " using png default" );
                return;
            }

            Log(LogLevel.Info, "Selected compression format :" + compressionFormatName);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    /**
     * Called if a new item (exposure time mode) selected
     */

    private AdapterView.OnItemSelectedListener exposureTimeSpinnerOnItemSelectedListenerHandler = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

            if(exposureTime!=null) {
                String itemName = adapterView.getItemAtPosition(i).toString();
                // different mode of exposure time is selected, different action will be done
                if (itemName.equals("Off")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("ExposureAuto").setValue("Off");
                    exposureTimeSeekBar.setEnabled(true);
                    exposureTimeTextView.setText(String.format("%.1f", exposureTime.getValue()));
                    int progress = (int) ValueConversionTool.cameraValueToSeekBarConversion(exposureTime.getValue(), MAXIMUM_EXPOSURE_TIME, MINIMUM_EXPOSURE_TIME,
                            exposureTimeSeekBar.getMax(), exposureTimeSeekBar.getMin());
                    exposureTimeSeekBar.setProgress(progress);

                } else if (itemName.equals("Once")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("ExposureAuto").setValue("Once");
                    exposureTimeSeekBar.setEnabled(false);
                } else if (itemName.equals("Continuous")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("ExposureAuto").setValue("Continuous");
                    exposureTimeSeekBar.setEnabled(false);
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };

    /**
     * Called if a new item (gain mode) selected
     */
    private AdapterView.OnItemSelectedListener gainSpinnerOnItemSelectedListenerHandler = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if(gain!=null) {
                String itemName = adapterView.getItemAtPosition(i).toString();
                // different mode of gain is selected, different action will be done
                if (itemName.equals("Off")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("GainAuto").setValue("Off");
                    gainSeekBar.setEnabled(true);
                    gainTextView.setText(String.format("%.1f", gain.getValue()));
                    int progress = (int) ValueConversionTool.cameraValueToSeekBarConversion(gain.getValue(), MAXIMUM_GAIN, MINIMUM_GAIN,
                            gainSeekBar.getMax(), gainSeekBar.getMin());
                    gainSeekBar.setProgress(progress);

                } else if (itemName.equals("Once")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("GainAuto").setValue("Once");
                    gainSeekBar.setEnabled(false);
                } else if (itemName.equals("Continuous")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("GainAuto").setValue("Continuous");
                    gainSeekBar.setEnabled(false);
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };

    /**
     * Called if a new item (balance white mode) selected
     */

    private AdapterView.OnItemSelectedListener balanceWhiteSpinnerOnItemSelectedListenerHandler = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if(m_PylonGrab!=null)
            {
                String itemName = adapterView.getItemAtPosition(i).toString();
                // different mode of balance white is selected, different action will be done
                if (itemName.equals("Off")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("BalanceWhiteAuto").setValue("Off");
                } else if (itemName.equals("Once")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("BalanceWhiteAuto").setValue("Once");
                } else if (itemName.equals("Continuous")) {
                    m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("BalanceWhiteAuto").setValue("Continuous");
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };

    /**
     * SeekBar OnSeekBarChangeListener initialization
     */

    /**
     * change exposure time based on process value of SeekBar
     */
    private SeekBar.OnSeekBarChangeListener exposureTimeSeekBarOnSeekBarChangeListenerHandler = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            if(exposureTime!=null) {
                double value = ValueConversionTool.seekBarValueToCameraValueConversion(exposureTimeSeekBar.getProgress(), MAXIMUM_EXPOSURE_TIME, MINIMUM_EXPOSURE_TIME,
                        exposureTimeSeekBar.getMax(), exposureTimeSeekBar.getMin());
                exposureTime.setValue(value);
                exposureTimeTextView.setText(String.format("%.1f",value));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    /**
     * change gain based on process value of SeekBar
     */
    private SeekBar.OnSeekBarChangeListener gainSeekBarOnSeekBarChangeListenerHandler = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            if(gain!=null) {
                double value = ValueConversionTool.seekBarValueToCameraValueConversion(i, MAXIMUM_GAIN, MINIMUM_GAIN,
                        gainSeekBar.getMax(), gainSeekBar.getMin());
                gain.setValue(value);
                gainTextView.setText(String.format("%.1f",value));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    /**
     * CheckBox OnCheckedChangeListener initialization
     */

    /**
     * When the check box is checked, the change of acquisition frame rate will be valid
     */
    private CompoundButton.OnCheckedChangeListener acquisitionFrameRateCheckBoxOnCheckedChangeListenerHandler = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

            if(acquisitionFrameRateEnable!=null) {
                acquisitionFrameRateEnable.setValue(b);
                acquisitionFrameRateEditText.setEnabled(b);
            }
        }
    };

    /**
     * Button OnClick events
     */

    /**
     * Called if the user wants to save one image.
     */
    public void saveImageButton_OnClick(View view)
    {
        // Start an additional thread to save image.
        Thread sampleThread = new Thread( new SaveSingleImageRunnable());
        sampleThread.start();
    }

    /**
     * Called if the user wants to stop capturing images, user can change camera or save image after that
     */
    public void stopCaptureButton_OnClick(View view)
    {
        // Disable the UI ...
        //tryChangeEnableUIState(false);

        //Stop grabbing and display grabbed image on the ImageView
        isStopGrabbingImage = true;

        // user can save image after clicking live and save image button
        saveImageButton.setEnabled(true);
        liveButton.setEnabled(true);

        // make stop capture button disable
        stopCaptureButton.setEnabled(false);
    }

    /**
     * Called if the user wants to save a lot of images.
     */
    public void liveButton_OnClick(View view)
    {
        // Disable the UI ...
        //tryChangeEnableUIState(false);

        // make stop capture button enable
        stopCaptureButton.setEnabled(true);

        saveImageButton.setEnabled(false);
        liveButton.setEnabled(false);

        // Start to display live images.
        if (isStopGrabbingImage) {
            isStopGrabbingImage = false;
            // Start an additional thread to display images continuously.
            cameraLiveCaptureThread = new Thread(new LiveRunnable());
            cameraLiveCaptureThread.start();
        }
    }

    /**
     * To end this application
     */
    public void exitButton_OnClick(View view)
    {
        exit();
    }

    /**
     * Called if the user wants to set frame rate.
     */
    public void acquisitionFrameButton_OnClick(View view)
    {
        String text = acquisitionFrameRateEditText.getText().toString();
        IFloat acquisitionFrame = m_PylonGrab.getCameraInstance().getNodeMap().getFloatNode("AcquisitionFrameRate");
        if(!text.equals(""))
        {
            acquisitionFrame.setValue(Integer.parseInt(text));
        }
    }

    /**
     * Called if the user wants to turn to full screen mode
     */
    public void fullScreenButton_OnClick(View view)
    {
        if(isFullScreen)
        {
            isFullScreen = false;
        }
        else
        {
            isFullScreen = true;
        }

        if(isFullScreen)
        {
            // make full screen layout visible
            fullScreenLinearLayout.setEnabled(true);
            fullScreenLinearLayout.setVisibility(View.VISIBLE);

            // make setting layout invisible
            settingViewLinearLayout.setEnabled(false);
            settingViewLinearLayout.setVisibility(View.INVISIBLE);

            // set screen orientation horizontal
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        else
        {
            // make full screen layout invisible
            fullScreenLinearLayout.setEnabled(false);
            fullScreenLinearLayout.setVisibility(View.INVISIBLE);

            // make setting layout visible
            settingViewLinearLayout.setEnabled(true);
            settingViewLinearLayout.setVisibility(View.VISIBLE);

            // set screen orientation vertical
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    //> Helpers
    //------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------

    /**
     * exit the application
     **/

    private void exit()
    {
        m_PylonGrab = null;

        int currentVersion = Build.VERSION.SDK_INT;

        Intent startMain= new Intent(Intent.ACTION_MAIN);

        startMain.addCategory(Intent.CATEGORY_HOME);

        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(startMain);

        System.exit(0);
    }

    /**
     * this part is for setting the range of seekbar
     * if maximum value > BOUNDARY_VALUE, SeekBar value range will be from minimum value to maximum value
     * else it will be always from 0 to BOUNDARY_VALUE
     **/
    private void gainAndExposureTimeInitialization()
    {
        exposureTime = m_PylonGrab.getCameraInstance().getNodeMap().getFloatNode("ExposureTime");
        gain = m_PylonGrab.getCameraInstance().getNodeMap().getFloatNode("Gain");

        MAXIMUM_EXPOSURE_TIME = exposureTime.getMax();
        MINIMUM_EXPOSURE_TIME = exposureTime.getMin();
        MAXIMUM_GAIN = gain.getMax();
        MINIMUM_GAIN = gain.getMin();

        // if maximum values of exposure time and gain larger than boundary value
        // seekbar maximum value will be equal to boundary value
        // otherwise, it will be set to its own maximum value
        if (MAXIMUM_EXPOSURE_TIME > ValueConversionTool.getBoundaryValue())
        {
            exposureTimeSeekBar.setMin(0);
            exposureTimeSeekBar.setMax((int)ValueConversionTool.getBoundaryValue());
        }
        else
        {
            exposureTimeSeekBar.setMin((int)MINIMUM_EXPOSURE_TIME);
            exposureTimeSeekBar.setMax((int)MAXIMUM_EXPOSURE_TIME);
        }

        if (MAXIMUM_GAIN > ValueConversionTool.getBoundaryValue())
        {
            gainSeekBar.setMin(0);
            gainSeekBar.setMax((int)ValueConversionTool.getBoundaryValue());
        }
        else
        {
            gainSeekBar.setMin((int)MINIMUM_GAIN);
            gainSeekBar.setMax((int)MAXIMUM_GAIN);
        }

        // initialize SeekBar Value according to preset value in camera
        exposureTimeSeekBar.setProgress((int)(ValueConversionTool.cameraValueToSeekBarConversion(exposureTime.getValue(),MAXIMUM_EXPOSURE_TIME,
            MINIMUM_EXPOSURE_TIME,exposureTimeSeekBar.getMax(),exposureTimeSeekBar.getMin())));

        gainSeekBar.setProgress((int)(ValueConversionTool.cameraValueToSeekBarConversion(gain.getValue(),
                MAXIMUM_GAIN,MINIMUM_GAIN,gainSeekBar.getMax(),gainSeekBar.getMin())));
        exposureTimeTextView.setText(""+(int)exposureTime.getValue());

        gainTextView.setText(""+(int)gain.getValue());
    }

    private void pixelFormatChanging(String name)
    {
        // user must stop Grabbing when changing pixel format
        m_PylonGrab.getCameraInstance().stopGrabbing();
        m_PylonGrab.setPixelFormat(name);
        m_PylonGrab.getCameraInstance().startGrabbing(EGrabStrategy.GrabStrategy_LatestImageOnly);
    }

    /** Implement LogTarget Log() function.
     *  @param logLevel Severity of the log message.
     *  @param logText  Text to log.
     *  Can be called from non-UI threads and will invoke logging in UI thread.
     **/
    @Override
    public void Log(LogLevel logLevel, String logText) {

        // Log to the Android Logcat
        switch(logLevel) {
            case Info:
                Log.i(LOG_TAG, logText);
                break;
            case Error:
                Log.e(LOG_TAG, logText);
                break;
            case Warning:
                Log.w(LOG_TAG, logText);
                break;
            default:
                Log.d(LOG_TAG, logText);
                break;
        }

        // Display the text on the UI.
        if( !m_isOnDestroyCalled.get()) {
            MainActivity.this.runOnUiThread(new Log2UIRunnable(logLevel, logText));
        }
    }

    /**
     * We try to enable/disable the UI.
     *  The sample needs a valid camera and we the permission to write to the mass storage.
     *  Both process are asynchronous and at the end both call this function.
     *  Finally we prevent the UI to enable again during the clean up process.
     */
    synchronized void tryChangeEnableUIState(boolean newUIState)
    {
        boolean tmpState = newUIState;

        if( !m_isCameraValid.get() || !m_isStoragePermissionGrant.get() || !m_isCameraPermissionGrant.get() || m_isOnDestroyCalled.get())
        {
            tmpState = false;
        }
        pixelFormatSpinner.setEnabled(tmpState);
        compressionFormatSpinner.setEnabled(tmpState);
        liveButton.setEnabled(tmpState);
        acquisitionFrameRateConfirmButton.setEnabled(tmpState);
        exposureTimeSpinner.setEnabled(tmpState);
        gainSpinner.setEnabled(tmpState);
        balanceWhiteSpinner.setEnabled(tmpState);
    }

    /** Request rights to access pictures folder and camera. This can be asynchronous.
     **/
    private void requestRights()
    {
        // Check camera permission
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED )
        {
            Log(LogLevel.Info,"Asking for permission to access CAMERA");
            String[] permission = new String[] {Manifest.permission.CAMERA};
            ActivityCompat.requestPermissions(this,permission,PERMISSION_CAMERA_REQUEST) ;
        }
        else {
            Log(LogLevel.Info,"Permission to access CAMERA already granted");
            m_isCameraPermissionGrant.set(true);
        }
        
        // Check storage permission (only for older Android versions)
        if(Build.VERSION.SDK_INT <= 32) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED )
            {
                Log(LogLevel.Info,"Asking for permission to write access EXTERNAL_STORAGE");
                String[] permission = new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE};
                ActivityCompat.requestPermissions(this,permission,PERMISSION_STORAGE_REQUEST) ;
            }
            else {
                Log(LogLevel.Info,"Permission to write EXTERNAL_STORAGE already granted");
                m_isStoragePermissionGrant.set(true);
            }
        } else {
            // For Android 13+, we don't need WRITE_EXTERNAL_STORAGE permission
            Log(LogLevel.Info,"Android 13+ detected, no WRITE_EXTERNAL_STORAGE permission needed");
            m_isStoragePermissionGrant.set(true);
        }
        
        // Check if all permissions are granted
        if(m_isCameraPermissionGrant.get() && m_isStoragePermissionGrant.get()) {
            tryChangeEnableUIState(true);
        }
    }

    private void testImageProcessor(String folderPath){
        Log(LogLevel.Info, "Folder path: " + folderPath);
        Path dir = Paths.get(folderPath);
        loadUVTables();

        // Use try-with-resources to ensure the stream is closed automatically
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile) // Filter for regular files only
                    .forEach(path -> {
                        // Process each file here
                        Log(LogLevel.Info, "Processing file: " + path.getFileName());
                        runImageProcessor(BitmapFactory.decodeFile(path.toString()), path.getFileName());
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runImageProcessor(Bitmap bitmap, Path imageName){
        //LOAD IMAGE
        Mat rawMat = new Mat();
        Utils.bitmapToMat(bitmap, rawMat);
        Imgproc.cvtColor(rawMat, rawMat, Imgproc.COLOR_RGB2GRAY);

        //REMAP IMAGE TO SIN AND TAN
        Mat remappedToSin = new Mat();
        Imgproc.remap(rawMat, remappedToSin, uRawToSin, vRawToSin, Imgproc.INTER_LINEAR);
        Mat remappedToTan = new Mat();
        Imgproc.remap(remappedToSin,remappedToTan,uSinToTan,vSinToTan,Imgproc.INTER_LINEAR);

        //PRE PROCESS THE IMAGE
        Mat binary = new Mat();
        Imgproc.threshold(remappedToTan, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        //RUN THE HOUGH TRANSFORM
        Mat lines = new Mat();
        Imgproc.HoughLines(binary, lines, 1, Math.PI/180, 150); // dude said that it returns lines in order of vote count https://stackoverflow.com/questions/11352528/opencv-hough-strongest-lines
        double maxRho = lines.get(0,0)[0], maxTheta = lines.get(0, 0)[1];

        //GENERATE BINARY IMAGE OF THE LINE
        //if we stick with this algo then you gotta adapt this to work better for horizontal lines, cause if the lines horizontal then you'll only get like 1 X pixel
        Mat binaryLine = new Mat(imageRows, imageCols, CvType.CV_8U);
        for (int y = 0; y < imageRows; y++){
            int x = (int) ((maxRho - y * Math.sin(maxTheta)) / Math.cos(maxTheta));
            if (x >= 0 && x < imageCols){
                binaryLine.put(y, x, 255);
            }
        }

        //USE ORIGINAL IMAGE AND REMAP LINE TO SINE PROJECTION
        Mat remapedLine = new Mat();
        Imgproc.remap(binaryLine, remapedLine, uTanToSin, vTanToSin, Imgproc.INTER_LINEAR);

        //LOOP THOUGH PITCH TO DETERMINE THE MAX PITCH
        List<Double> resultList = new ArrayList<>(); double maxResult = 0.0; Mat debugLines = new Mat();
        for (int pitch = 50; pitch < 250; pitch++){

            //CREATE TRANSLATED IMAGE OF NINE LINES (i used for loops in matlab but since this algo is probably not going to be used I'll do it this new way, only here)
            Mat nineLineImage = new Mat();
            remapedLine.copyTo(nineLineImage);
            Mat translationMatrix = new Mat(2, 3, CvType.CV_32F);
            Mat transforms = new Mat(8, 2, CvType.CV_32F);
            float p = (float) pitch;
            transforms.put(0, 0, -p, -p, 0, -p, p, -p, p, 0, p, p, 0, p, -p, p, -p, 0);
            for (int i = 0; i < 8; i++){
                translationMatrix.put(0, 0, 1, 0, transforms.get(i, 0)[0], 0, 1, transforms.get(i, 1)[0]);
                Mat translatedLine = new Mat();
                Imgproc.warpAffine(remapedLine, translatedLine, translationMatrix, remapedLine.size());
                Core.add(nineLineImage, translatedLine, nineLineImage);
            }

            //MULTIPLY THE TRANSLATED LINES BY THE ORIGINAL REMAPED IMAGE AND STORE THE RESULT
            Mat multiplicationResult = new Mat();
            Core.multiply(nineLineImage, remappedToSin, multiplicationResult);
            Scalar resultScalar = Core.sumElems(multiplicationResult);
            double result = resultScalar.val[0];
            resultList.add(result);
            if (result > maxResult){
                maxResult = result;
                nineLineImage.copyTo(debugLines);
            }
        }

        //SAVE AN IMAGE (for debugging, maybe could be used on final version when event is triggered)
        Mat comboImage = new Mat();
        Core.add(remappedToSin, debugLines, comboImage);
        String fileSavePath = getExternalFilesDir(null).getAbsolutePath() + "/" + imageName;
        Imgcodecs.imwrite(fileSavePath, comboImage);
    }

    private void loadUVTables(){
        File file = new File(getExternalFilesDir(null), "uRawToSin.txt");
        uRawToSin.put(0, 0, SaveImageCommand.readCsvToArray(file.getAbsolutePath()));
        file = new File(getExternalFilesDir(null), "vRawToSin.txt");
        vRawToSin.put(0, 0, SaveImageCommand.readCsvToArray(file.getAbsolutePath()));

        file = new File(getExternalFilesDir(null), "uSinToTan.txt");
        uSinToTan.put(0, 0, SaveImageCommand.readCsvToArray(file.getAbsolutePath()));
        file = new File(getExternalFilesDir(null), "vSinToTan.txt");
        vSinToTan.put(0, 0, SaveImageCommand.readCsvToArray(file.getAbsolutePath()));

        file = new File(getExternalFilesDir(null), "uTanToSin.txt");
        uTanToSin.put(0, 0, SaveImageCommand.readCsvToArray(file.getAbsolutePath()));
        file = new File(getExternalFilesDir(null), "vTanToSin.txt");
        vTanToSin.put(0, 0, SaveImageCommand.readCsvToArray(file.getAbsolutePath()));
    }


    /** Save a Java bitmap to file.
     **/
    public static void saveImage(String imagePath, Bitmap.CompressFormat compressFormat, Bitmap bitmap, Context context) throws IOException
    {
        // Add file extension, e.g., ".png"
        imagePath += "." + compressFormat.toString().toLowerCase();
        //> save the bitmap as compressed image.
        File file = new File(imagePath);
        FileOutputStream outputStream = new FileOutputStream(file);
        bitmap.compress(compressFormat,100, outputStream );
        outputStream.flush();
        outputStream.close();


        // send a broadcast to notify album that a new photo is saved
        // and the album will update, so use can find photo in album //sean commenting this out, I don't think i need
//        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//        Uri uri = Uri.fromFile(file);
//        intent.setData(uri);
//        context.sendBroadcast(intent);
    }

    /** Callback for rights request.
     *  Called after request for permissions was answered by user.
     **/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissionsRequested, @NonNull int[] requestResults)
    {
        super.onRequestPermissionsResult(requestCode, permissionsRequested, requestResults);
        if( requestCode == PERMISSION_STORAGE_REQUEST ) {
            if(requestResults.length > 0 && requestResults[0] == PackageManager.PERMISSION_GRANTED ) {
                m_isStoragePermissionGrant.set(true);
                Log(LogLevel.Info,"User granted access to external storage");
            }
            else {
                Log( LogLevel.Error, "User declined request for permission to write to external storage");
            }
        }
        else if( requestCode == PERMISSION_CAMERA_REQUEST ) {
            if(requestResults.length > 0 && requestResults[0] == PackageManager.PERMISSION_GRANTED ) {
                m_isCameraPermissionGrant.set(true);
                Log(LogLevel.Info,"User granted access to camera");
            }
            else {
                Log( LogLevel.Error, "User declined request for permission to access camera");
            }
        }
        
        // Check if all permissions are granted
        if(m_isCameraPermissionGrant.get() && m_isStoragePermissionGrant.get()) {
            tryChangeEnableUIState(true);
        }
    }

    //> Runnable
    //------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------

    /**
     * Execute camera discovery/configuration in an additional thread.
     */
    private class PrepareCameraRunnable implements Runnable
    {
        @Override
        public void run()
        {
           
            try
            {
                //> Create wrapper class for pylon logic.
                //  The constructor search and open the first camera.
                m_PylonGrab = new PylonGrab(MainActivity.this);

                // Set some camera default settings.
                m_PylonGrab.prepareCamera();

                // Mark that the camera a prepared.
                m_isCameraValid.set(true);

                //set Exposure initialized mode
                m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("ExposureAuto").setValue("Off");

                // set Gain initialized mode
                m_PylonGrab.getCameraInstance().getNodeMap().getEnumerationNode("GainAuto").setValue("Off");

                // get the object that make acquisition rate enable
                acquisitionFrameRateEnable = m_PylonGrab.getCameraInstance().getNodeMap().getBooleanNode("AcquisitionFrameRateEnable");

                // start to monitor the camera connection status
                // new Thread( new CameraConnectionDetector()).start();
                // Display the result.
//                if( !m_isOnDestroyCalled.get()) {
//
//                    MainActivity.this.runOnUiThread(new UpdateUIAfterSampleRunRunnable(m_PylonGrab.grabImage()));
//                }


            }
            catch(Exception e)
            {
                Log(LogLevel.Error, e.getMessage() );
                MainActivity.this.runOnUiThread(new  Runnable() {
                    @Override
                    public  void  run() {
                        exit();
                    }
                });
                return;
            }

            // Create a function that is processed by the UI thread to access the UI.
            Runnable runnable = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        // Create a drop-down spinner for pixel formats.
                        List<String> supportedPixelFormats = m_PylonGrab.getSupportedPixelFormats();

                        if(supportedPixelFormats.size()==0)
                        {
                            supportedPixelFormats.add("None");
                        }

                        ArrayAdapter<String> m_PixelFormatArrayAdapter = new ArrayAdapter<>(
                                MainActivity.this,
                                android.R.layout.simple_spinner_item,
                                supportedPixelFormats);
                        m_PixelFormatArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        pixelFormatSpinner.setAdapter(m_PixelFormatArrayAdapter);

                        // Set default grab format to Mono8 or BayerXX8.
                        String grabPixelFormat = m_PylonGrab.getSupportedBayer8Format();
                        if (grabPixelFormat == null) {
                            grabPixelFormat = "Mono8";
                        }
                        int formatPos = m_PixelFormatArrayAdapter.getPosition(grabPixelFormat);
                        pixelFormatSpinner.setOnItemSelectedListener(pixelFormatSpinnerOnItemSelectedListenerHandler);
                        pixelFormatSpinner.setSelection(formatPos);

                        // Enable the UI
                        tryChangeEnableUIState(true);

                        // Gain and Exposure time Initialization
                        gainAndExposureTimeInitialization();

                    } catch(Exception e)
                    {
                        Log(LogLevel.Error, e.getMessage() );
                    }
                }};
            MainActivity.this.runOnUiThread( runnable );
        }
    }


    /**
     * Capture one image, convert und save this to the mass storage and finally display it.
     */
    private class SaveSingleImageRunnable implements Runnable
    {
        @Override
        public void run()
        {
            // Execute the sample code.
            try {
                if (grabImage != null) {
                    // Fetch system time and create filename.
                    String timeStamp = new SimpleDateFormat("_yyyyMMdd_HHmmss", Locale.ENGLISH).format(Calendar.getInstance().getTime());
                    String fullFilePath = m_RootPathPictures + File.separator + "PylonImgSingle" + timeStamp;

                    saveImage(fullFilePath , m_CurrentCompressFormat, grabImage, MainActivity.this);
                } else {
                    Log(LogLevel.Error, "Error: No bitmap from grab");
                }
            } catch (Exception e) {
                Log(LogLevel.Error, "Exception while handling onClick SaveImage: " + e.getMessage());
            }

            // Display the result.
            if( !m_isOnDestroyCalled.get()) {
                MainActivity.this.runOnUiThread(new UpdateUIAfterSampleRunRunnable(grabImage));
            }
        }
    }


    /**
     * Live view implementation.
     */
    private class LiveRunnable implements Runnable
    {
        @Override
        public void run()
        {
            //set camera grabbing strategy
            m_PylonGrab.getCameraInstance().startGrabbing(EGrabStrategy.GrabStrategy_LatestImageOnly);

            while (true) {

                if(!isStopGrabbingImage) {
                    // Execute the sample code.
                    try {
                        // Fetch system time and create filename.
                        grabImage = m_PylonGrab.grabImage();  //SEAN Does this get called? Is this the picture?
                        Mat testMat = new Mat();
                        Utils.bitmapToMat(grabImage, testMat);
                        Scalar testAvg = mean(testMat);
                        Log(LogLevel.Info, "Is this the average? " + testAvg);

                        // For the thread security, pixel format will be changed here. This is for real-time changing
                        // the most safety way to do it is to build a button for stopping capturing and then changing the pixel format.
                        if (pixelFormatSelectedItemName != null) {
                            pixelFormatChanging(pixelFormatSelectedItemName);
                            pixelFormatSelectedItemName = null;
                        }
                        String timeStamp = new SimpleDateFormat("_yyyyMMdd_HHmmss", Locale.ENGLISH).format(Calendar.getInstance().getTime());
                        String fullFilePath = m_RootPathPictures + File.separator + "PylonImgSingle" + timeStamp;
                        saveImage(fullFilePath , m_CurrentCompressFormat, grabImage, MainActivity.this);

                    } catch (Exception e) {
                        Log(LogLevel.Error, "Exception while handling onClick SaveImage: " + e.getMessage());
                    }

                    // Display the result.
                    if (!m_isOnDestroyCalled.get()) {
                        MainActivity.this.runOnUiThread(new UpdateUIAfterSampleRunRunnable(grabImage));
                    }
                }
                else
                {
                    m_PylonGrab.getCameraInstance().stopGrabbing();
                    break;
                }
            }
        }
    }


    /**
     * Update the UI after the sample code finish.
     *  Executed by the UI-Thread.
     */
    private class UpdateUIAfterSampleRunRunnable implements Runnable
    {
        Bitmap m_ResultImage;
        public UpdateUIAfterSampleRunRunnable(Bitmap resultImage){
            m_ResultImage = resultImage;
        }

        @Override
        public void run()
        {

                // Display the result, if set
                if(m_ResultImage != null) {

                    // display images in ImageView continuously
                    ImageView windowsViewLiveView = findViewById(R.id.windowsLiveViewImageView);
                    windowsViewLiveView.setImageBitmap(m_ResultImage);

                    ImageView fullScreenLiveView = findViewById(R.id.fullScreenLiveView);
                    fullScreenLiveView.setImageBitmap(m_ResultImage);

                    // get the frame rate of camera
                    TextView frameRateTextView = (TextView)findViewById(R.id.frameRateTextView);
                    frameRateTextView.setText(String.format("%.1f",
                            m_PylonGrab.getCameraInstance().getNodeMap().getFloatNode("ResultingFrameRate").getValue())+" fps");
            }

            // Enable the UI again
            //tryChangeEnableUIState(true);
        }
    }


    /**
     * Display the logging information.
     *  Executed by the UI-Thread.
     */
    class Log2UIRunnable implements Runnable
    {
        final LogLevel    m_LogLevel;
        final String                m_LogText;

        Log2UIRunnable( LogLevel logLevel, String logText) {
            m_LogLevel = logLevel;
            m_LogText = logText;
        }

        @Override
        public void run()
        {
            // Display the text on the UI.
            if( m_LogLevel != LogLevel.Trace) {
                if(logTextView.getLineCount() > 1000 ) {
                    int charsToRemove = logTextView.getText().length() / 2;
                    logTextView.getEditableText().delete( 0, charsToRemove);
                }
                logTextView.append(m_LogLevel.toString() + ": " + m_LogText + "\n");
            }
        }
    }

}
