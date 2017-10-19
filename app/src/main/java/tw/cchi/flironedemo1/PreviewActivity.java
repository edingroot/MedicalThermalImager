package tw.cchi.flironedemo1;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.FlirUsbDevice;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;
import com.flir.flironesdk.SimulatedDevice;
import com.vashisthg.startpointseekbar.StartPointSeekBar;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.thermalproc.ROIDetector;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpParser;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;

public class PreviewActivity extends Activity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate {
    public static final int ACTION_PICK_FROM_GALLERY = 100;

    private volatile boolean simConnected = false;
    private volatile boolean streamingFrame = false;
    private volatile boolean runningThermalAnalysis = false;
    private volatile boolean runningContourProcessing = false;
    private volatile boolean imageCaptureRequested = false;
    private volatile boolean thermalAnalyzeRequested = false;
    private volatile boolean thermalDumpRequested = false;
    private OrientationEventListener orientationEventListener;
    private volatile Device flirOneDevice;
    private FrameProcessor frameProcessor;
    private Device.TuningState currentTuningState = Device.TuningState.Unknown;
    private ColorFilter originalChargingIndicatorColor = null;
    private volatile int[] thermalPixels = null;
    private volatile Bitmap thermalBitmap = null; // last frame updated to imageview
    private volatile Bitmap opacityMask = null;

    // Related to thermal analysis
    private volatile ROIDetector roiDetector;
    private ThermalDumpProcessor thermalDumpProcessor;
    private volatile Thread contourProcessingThread = null;
    private volatile RenderedImage lastRenderedImage = null;
    private volatile int selectedContourIndex = -1;
    private volatile double contrastRatio = 1;
    private volatile double roiDetectionThreshold = 1;
    private volatile double recognitionThreshold = 1;
    private int deviceRotation = 0;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private int thermalSpotX = -1;
    private int thermalSpotY = -1;
    private boolean showingMoreInfo = true;

    @BindView(R.id.fullscreen_content_controls_top) View topControlsView;
    @BindView(R.id.fullscreen_content_controls) View bottomControlsView;
    @BindView(R.id.fullscreen_content) View contentView;
    @BindView(R.id.imageView) ImageView thermalImageView;
    @BindView(R.id.pleaseConnect) TextView pleaseConnect;
    @BindView(R.id.layoutTempSpot) RelativeLayout layoutTempSpot;
    @BindView(R.id.spotMeterValue) TextView spotMeterValue;
    @BindView(R.id.batteryLevelTextView) TextView batteryLevelTextView;
    @BindView(R.id.batteryChargeIndicator) ImageView batteryChargeIndicator;

    @BindView(R.id.secondaryControlsContainer) View secondaryControlsContainer;
    @BindView(R.id.contrastSeekBar) StartPointSeekBar contrastSeekBar;
    @BindView(R.id.txtContrastValue) TextView txtContrastValue;
    @BindView(R.id.roiThresSeekBar) StartPointSeekBar roiThresSeekBar;
    @BindView(R.id.txtROIThresValue) TextView txtROIThresValue;
    @BindView(R.id.rcgnThresSeekBar) StartPointSeekBar rcgnThresSeekBar;
    @BindView(R.id.txtRcgnThresValue) TextView txtRcgnThresValue;
    @BindView(R.id.btnFilter) Button btnFilter;
    @BindView(R.id.btnRcgHigh) Button btnRcgHigh;
    @BindView(R.id.imgBtnPick) ImageButton imgBtnPick;

    ScaleGestureDetector mScaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initDebug();
        setContentView(R.layout.activity_preview);
        ButterKnife.bind(this);

        RenderedImage.ImageType defaultImageType = RenderedImage.ImageType.ThermalRGBA8888Image;
        frameProcessor = new FrameProcessor(this, this, EnumSet.of(defaultImageType, RenderedImage.ImageType.ThermalRadiometricKelvinImage));
        frameProcessor.setImagePalette(RenderedImage.Palette.Gray);

        /* contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetAnalysis();
            }
        }); */

        mScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                Log.d(Config.TAG, "zoom ongoing, scale: " + detector.getScaleFactor());
//                frameProcessor.setMSXDistance(detector.getScaleFactor());
                return false;
            }
        });

        contentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (thermalImageView.getMeasuredHeight() > 0) {
                    // Calculate actual touched position on the thermal image
                    int x = (int) event.getX();
                    int y = (int) event.getY() - thermalImageView.getTop();
                    if (y >= 0 && y <= thermalImageView.getMeasuredHeight()) {
                        handleThermalImageTouch(x, y);
                    }
                }
                mScaleDetector.onTouchEvent(event);

                // Consume the event, which onClick event will not triggered
                return true;
            }
        });

        contrastSeekBar.setOnSeekBarChangeListener(new StartPointSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onOnSeekBarValueChange(StartPointSeekBar bar, double value) {
                PreviewActivity.this.onContrastSeekBarChanged(bar, value);
            }
        });

        roiThresSeekBar.setOnSeekBarChangeListener(new StartPointSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onOnSeekBarValueChange(StartPointSeekBar bar, double value) {
                PreviewActivity.this.onROIThresSeekBarChanged(bar, value);
            }
        });

        rcgnThresSeekBar.setOnSeekBarChangeListener(new StartPointSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onOnSeekBarValueChange(StartPointSeekBar bar, double value) {
                PreviewActivity.this.onRcgnThresSeekBarChanged(bar, value);
            }
        });

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                deviceRotation = orientation;
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Device.getSupportedDeviceClasses(this).contains(FlirUsbDevice.class)) {
            pleaseConnect.setVisibility(View.VISIBLE);
        }

        try {
            Device.startDiscovery(this, this);
        } catch (IllegalStateException e) {
            // it's okay if we've already started discovery
        } catch (SecurityException e) {
            // On some platforms, we need the user to select the app to give us permisison to the USB device.
            Toast.makeText(this, "Please insert FLIR One and select " + getString(R.string.app_name), Toast.LENGTH_LONG).show();
            // There is likely a cleaner way to recover, but for now, exit the activity and
            // wait for user to follow the instructions;
            finish();
        }

        secondaryControlsContainer.setVisibility(View.GONE);
        this.contrastRatio = contrastSeekBar.getProgress();
        txtContrastValue.setText(String.format("%.2f", contrastRatio));
        this.roiDetectionThreshold = roiThresSeekBar.getProgress();
        txtROIThresValue.setText(String.format("%d", (int) roiDetectionThreshold));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (flirOneDevice != null) {
            flirOneDevice.stopFrameStream();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (flirOneDevice != null) {
            flirOneDevice.startFrameStream(this);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ACTION_PICK_FROM_GALLERY:
                if (resultCode == Activity.RESULT_OK) {
                    // Pick image from gallery
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    // Get the cursor
                    Cursor cursor = this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                    // Move to first row
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String imgDecodableString = cursor.getString(columnIndex);
                    cursor.close();
                    opacityMask = BitmapFactory.decodeFile(imgDecodableString);
                    imgBtnPick.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));

                    // Reconnect sim device if it was connected previously
                    if (simConnected && flirOneDevice == null) {
                        connectSimulatedDevice();
                    }
                }
                break;
        }
    }

    @Override
    public void onStop() {
        // We must unregister our usb receiver, otherwise we will steal events from other apps
        Log.e(Config.TAG, "PreviewActivity onStop, stopping discovery!");
        Device.stopDiscovery();
        flirOneDevice = null;
        super.onStop();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    public void onDeviceConnected(Device device) {
        Log.i(Config.TAG, "Device connected!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pleaseConnect.setVisibility(View.GONE);
            }
        });

        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        streamingFrame = true;
        orientationEventListener.enable();

        if (showingMoreInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    topControlsView.setVisibility(View.VISIBLE);
                    secondaryControlsContainer.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    /**
     * Indicate to the user that the device has disconnected
     */
    public void onDeviceDisconnected(Device device) {
        Log.i(Config.TAG, "Device disconnected!");
        streamingFrame = false;
        flirOneDevice = null;
        thermalDumpProcessor = null;
        roiDetector = null;
        thermalBitmap = null;
        orientationEventListener.disable();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pleaseConnect.setVisibility(View.VISIBLE);
                thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565));
                batteryLevelTextView.setText("--");
                batteryChargeIndicator.setVisibility(View.GONE);
                spotMeterValue.setText("");
                thermalImageView.clearColorFilter();
                thermalImageView.setImageResource(android.R.color.transparent);
                findViewById(R.id.tuningProgressBar).setVisibility(View.GONE);
                findViewById(R.id.tuningTextView).setVisibility(View.GONE);
                findViewById(R.id.connect_sim_button).setEnabled(true);
            }
        });
    }

    /**
     * If using RenderedImage.ImageType.ThermalRadiometricKelvinImage, you should not rely on
     * the accuracy if tuningState is not Device.TuningState.Tuned
     *
     * @param tuningState
     */
    public void onTuningStateChanged(Device.TuningState tuningState) {
        Log.i(Config.TAG, "Tuning state changed changed!");

        currentTuningState = tuningState;
        if (tuningState == Device.TuningState.InProgress) {
            runOnUiThread(new Thread() {
                @Override
                public void run() {
                    super.run();
                    thermalImageView.setColorFilter(Color.DKGRAY, PorterDuff.Mode.DARKEN);
                    findViewById(R.id.tuningProgressBar).setVisibility(View.VISIBLE);
                    findViewById(R.id.tuningTextView).setVisibility(View.VISIBLE);
                }
            });
        } else {
            runOnUiThread(new Thread() {
                @Override
                public void run() {
                    super.run();
                    thermalImageView.clearColorFilter();
                    findViewById(R.id.tuningProgressBar).setVisibility(View.GONE);
                    findViewById(R.id.tuningTextView).setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    public void onAutomaticTuningChanged(boolean deviceWillTuneAutomatically) {

    }

    @Override
    public void onBatteryChargingStateReceived(final Device.BatteryChargingState batteryChargingState) {
        Log.i(Config.TAG, "Battery charging state received!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (originalChargingIndicatorColor == null) {
                    originalChargingIndicatorColor = batteryChargeIndicator.getColorFilter();
                }
                switch (batteryChargingState) {
                    case FAULT:
                    case FAULT_HEAT:
                        batteryChargeIndicator.setColorFilter(Color.RED);
                        batteryChargeIndicator.setVisibility(View.VISIBLE);
                        break;
                    case FAULT_BAD_CHARGER:
                        batteryChargeIndicator.setColorFilter(Color.DKGRAY);
                        batteryChargeIndicator.setVisibility(View.VISIBLE);
                    case MANAGED_CHARGING:
                        batteryChargeIndicator.setColorFilter(originalChargingIndicatorColor);
                        batteryChargeIndicator.setVisibility(View.VISIBLE);
                        break;
                    case NO_CHARGING:
                    default:
                        batteryChargeIndicator.setVisibility(View.GONE);
                        break;
                }
            }
        });
    }

    @Override
    public void onBatteryPercentageReceived(final byte percentage) {
        // Log.i(Config.TAG, "Battery percentage received!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                batteryLevelTextView.setText(String.valueOf((int) percentage) + "%");
            }
        });
    }

    // StreamDelegate method
    public void onFrameReceived(Frame frame) {
        if (currentTuningState != Device.TuningState.InProgress && streamingFrame) {
            frameProcessor.processFrame(frame);
        }
    }

    // Frame Processor Delegate method, will be called each time a rendered frame is produced
    public void onFrameProcessed(final RenderedImage renderedImage) {
        if (!streamingFrame)
            return;
        lastRenderedImage = renderedImage;

        if (imageCaptureRequested) {
            imageCaptureRequested = false;
            captureThermalImage(renderedImage);

            // Dump thermal data as well when capturing image
            thermalDumpRequested = true;
        }

        if (renderedImage.imageType() == RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
            this.imageWidth = renderedImage.width();
            this.imageHeight = renderedImage.height();
            this.thermalPixels = renderedImage.thermalPixelValues();

            updateThermalSpotValue();

            // if radiometric is the only type, also show the image
            if (frameProcessor.getImageTypes().size() == 1) {
                // example of a custom colorization, maps temperatures 0-100C to 8-bit gray-scale
                byte[] argbPixels = new byte[imageWidth * imageHeight * 4];
                final byte aPixValue = (byte) 255;
                for (int p = 0; p < thermalPixels.length; p++) {
                    int destP = p * 4;
                    byte pixValue = (byte) (Math.min(0xff, Math.max(0x00, (thermalPixels[p] - 27315) * (255.0 / 10000.0))));

                    argbPixels[destP + 3] = aPixValue;
                    // red pixel
                    argbPixels[destP] = argbPixels[destP + 1] = argbPixels[destP + 2] = pixValue;
                }

                final Bitmap demoBitmap = Bitmap.createBitmap(imageWidth, renderedImage.height(), Bitmap.Config.RGB_565);
                demoBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argbPixels));
                updateThermalImageView(demoBitmap);
            }

            if (thermalAnalyzeRequested) {
                thermalAnalyzeRequested = false;
                if (runningThermalAnalysis) {
                    // Terminate the previous unfinished thermal analysis thread
                    if (runningContourProcessing) {
                        runningContourProcessing = false;
                        contourProcessingThread.stop();
                    }
                } else {
                    performThermalAnalysis(renderedImage);
                }
            }
            if (thermalDumpRequested) {
                thermalDumpRequested = false;
                dumpThermalData(renderedImage);
            }

            // (DEBUG) Show image types
            // for (RenderedImage.ImageType type : frameProcessor.getImageTypes()) {
            //     Log.i(Config.TAG, "ImageType=" + type);
            // }
        } else {
            /*if (thermalBitmap == null) {
                thermalBitmap = renderedImage.getBitmap();
            } else {
                try {
                    renderedImage.copyToBitmap(thermalBitmap);
                } catch (IllegalArgumentException e) {
                    thermalBitmap = renderedImage.getBitmap();
                }
            }*/
            updateThermalImageView(renderedImage.getBitmap());
        }
    }


    public void onTuneClicked(View v) {
        if (flirOneDevice != null) {
            flirOneDevice.performTuning();
        }
    }

    public void onCaptureImageClicked(View v) {
        if (flirOneDevice != null) {
            if (streamingFrame) {
                this.imageCaptureRequested = true;
            } else {
                captureProcessedImage();
                dumpThermalData(lastRenderedImage);
            }
        }
    }

    public void onAnalyzeClicked(View v) {
        if (flirOneDevice != null) {
            if (streamingFrame) {
                this.thermalAnalyzeRequested = true;
            } else {
                resetAnalysis();
            }
        }
    }

    public void onConnectSimClicked(View v) {
        if (flirOneDevice == null) {
            connectSimulatedDevice();

        } else if (flirOneDevice instanceof SimulatedDevice) {
            flirOneDevice.close();
            flirOneDevice = null;
            simConnected = false;
        }
    }

    public void onRotateClicked(View v) {
        ToggleButton theSwitch = (ToggleButton) v;
        if (theSwitch.isChecked()) {
            thermalImageView.setRotation(180);
        } else {
            thermalImageView.setRotation(0);
        }
    }

    public void onImagePickClicked(View v) {
        if (opacityMask == null) {
            Intent galleryIntent = new Intent(
                    Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, ACTION_PICK_FROM_GALLERY);
        } else {
            opacityMask = null;
            imgBtnPick.setBackgroundColor(0);
        }
    }

    public void onDumpThermalRawClicked(View v) {
        if (flirOneDevice != null && streamingFrame && !thermalDumpRequested) {
            this.thermalDumpRequested = true;
        }
    }


    /* ---------- Secondary control panel ---------- */

    public void onToggleMoreInfoClicked(View v) {
        if (showingMoreInfo) {
            topControlsView.setVisibility(View.INVISIBLE);
            secondaryControlsContainer.setVisibility(View.GONE);
            showingMoreInfo = false;
        } else {
            topControlsView.setVisibility(View.VISIBLE);
            // Check if device is connected & is under thermal analysis result preview mode
            // if (flirOneDevice != null && thermalDumpProcessor != null)
            if (flirOneDevice != null)
                secondaryControlsContainer.setVisibility(View.VISIBLE);
            showingMoreInfo = true;
        }
    }

    public void onContrastSeekBarChanged(StartPointSeekBar bar, double value) {
        this.contrastRatio = value;
        txtContrastValue.setText(String.format("%.2f", contrastRatio));
        adjustContrast(contrastRatio);
    }

    public void onROIThresSeekBarChanged(StartPointSeekBar bar, double value) {
        this.roiDetectionThreshold = value;
        txtROIThresValue.setText(String.format("%d", (int) roiDetectionThreshold));
    }

    public void onRcgnThresSeekBarChanged(StartPointSeekBar bar, double value) {
        this.recognitionThreshold = value;
        txtRcgnThresValue.setText(String.format("%d", (int) recognitionThreshold));
    }

    public void onFilterClicked(View v) {
        if (selectedContourIndex == -1) {
            Toast.makeText(this, getString(R.string.noContourSelected), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.processingContour), Toast.LENGTH_SHORT).show();
            runningContourProcessing = true;
            final MatOfPoint contour = roiDetector.getContours().get(selectedContourIndex);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // Run filtering
                    Log.i(Config.TAG, "thermalAnalysis filterFromContour & generate image started");
                    thermalDumpProcessor.filterFromContour(contour);
                    // thermalDumpProcessor.autoFilter();
                    Mat processedImage = thermalDumpProcessor.getImage(contrastRatio);
                    Log.i(Config.TAG, "thermalAnalysis filterFromContour & generate image finished");

                    // Show filtered result
                    ROIDetector.drawSelectedContour(processedImage, contour);
                    Bitmap resultBmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                    Utils.matToBitmap(processedImage, resultBmp);

                    // Check if task is stopped by main program
                    if (runningContourProcessing) {
                        updateThermalImageView(resultBmp);
                        runningContourProcessing = false;
                    }
                }
            }).start();
        }
    }

    public void onRcgHighClicked(View v) {
        // TODO
    }

    /* ---------- /Secondary control panel ---------- */


    private void showToastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PreviewActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scanMediaStorageAndAnimate(String filename) {
        // Call the system media scanner
        MediaScannerConnection.scanFile(PreviewActivity.this,
                new String[]{filename}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(Config.TAG, "ExternalStorage Scanned " + path + ":");
                        Log.i(Config.TAG, "ExternalStorage -> uri=" + uri);
                    }

                });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.animate().setDuration(50).scaleY(0).withEndAction((new Runnable() {
                    public void run() {
                        thermalImageView.animate().setDuration(50).scaleY(1);
                    }
                }));
            }
        });
    }

    // -----------------------------------------------------------------

    private void connectSimulatedDevice() {
        try {
            flirOneDevice = new SimulatedDevice(this, this, getResources().openRawResource(R.raw.sampleframes), 10);
            flirOneDevice.setPowerUpdateDelegate(this);
        } catch (Exception ex) {
            flirOneDevice = null;
            Log.w(Config.TAG, "IO EXCEPTION");
            ex.printStackTrace();
        }
        simConnected = true;
    }

    private void updateThermalImageView(final Bitmap frame) {
        // Draw opacity mask if assigned
        if (opacityMask != null) {
            Canvas canvas = new Canvas(frame);
            Paint alphaPaint = new Paint();
            alphaPaint.setAlpha(Config.OPACITY_MASK_ALPHA);
            canvas.drawBitmap(opacityMask, 0, 0, alphaPaint);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
            }
        });

        // Perform a native deep copy to avoid object referencing
        thermalBitmap = frame.copy(frame.getConfig(), frame.isMutable());
    }

    private void updateThermalSpotValue() {
        if (thermalPixels == null)
            return;

        // Note: this code is not optimized
        // average the center 9 pixels for the spot meter
        int centerPixelIndex;
        if (thermalSpotX == -1) {
            centerPixelIndex = imageWidth * (imageHeight / 2) + (imageWidth / 2);
        } else {
            centerPixelIndex = imageWidth * thermalSpotY + thermalSpotX;
        }
        int[] centerPixelIndexes = new int[]{
                centerPixelIndex, centerPixelIndex - 1, centerPixelIndex + 1,
                centerPixelIndex - imageWidth,
                centerPixelIndex - imageWidth - 1,
                centerPixelIndex - imageWidth + 1,
                centerPixelIndex + imageWidth,
                centerPixelIndex + imageWidth - 1,
                centerPixelIndex + imageWidth + 1
        };

        double averageTemp = 0;
        for (int i = 0; i < centerPixelIndexes.length; i++) {
            // Remember: all primitives are signed, we want the unsigned value,
            // we've used renderedImage.thermalPixelValues() to get unsigned values
            int pixelValue = thermalPixels[centerPixelIndexes[i]];
            averageTemp += (((double) pixelValue) - averageTemp) / ((double) i + 1);
        }
        double averageC = (averageTemp / 100) - 273.15;
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        final String spotMeterValue = numberFormat.format(averageC) + "ºC";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PreviewActivity.this.spotMeterValue.setText(spotMeterValue);
            }
        });
    }

    private void handleThermalImageTouch(int x, int y) {
        // Calculate the correspondent point on the thermal image
        double ratio = (double) imageWidth / thermalImageView.getMeasuredWidth();
        int imgX = (int) (x * ratio);
        int imgY = (int) (y * ratio);
        Log.i(Config.TAG, String.format("Actual touched point: x=%d, y=%d\n", imgX, imgY));
        thermalSpotX = AppUtils.trimByRange(imgX, 1, imageWidth - 1);
        thermalSpotY = AppUtils.trimByRange(imgY, 1, imageHeight - 1);

        // If is in thermal analysis result preview mode and the previous analysis is finished, handle selection.
        if (flirOneDevice != null && !streamingFrame && !runningContourProcessing) {
            int contourIndex = roiDetector.getSelectedContourIndex(imgX, imgY);
            // If there is a contour selected
            if (contourIndex != -1 && contourIndex != selectedContourIndex) {
                selectedContourIndex = contourIndex;
                handleContourSelected(roiDetector.getContours().get(contourIndex));
            }
        }

        // Set indication spot location
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutTempSpot.getLayoutParams();
        params.leftMargin = x - layoutTempSpot.getMeasuredWidth() / 2;
        params.topMargin = y + layoutTempSpot.getMeasuredHeight() / 2;
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        layoutTempSpot.setLayoutParams(params);
        updateThermalSpotValue();
    }

    private void captureThermalImage(final RenderedImage renderedImage) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        final String filename = AppUtils.getExportsDir() + "/" + sdf.format(new Date()) + "_thermalImage.png";

        new Thread(new Runnable() {
            public void run() {
                try {
                    // Save the original thermal image
                    renderedImage.getFrame().save(new File(filename), RenderedImage.Palette.Iron, RenderedImage.ImageType.BlendedMSXRGBA8888Image);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                scanMediaStorageAndAnimate(filename);
            }
        }).start();
    }

    private void captureProcessedImage() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        final String filename = AppUtils.getExportsDir() + "/" + sdf.format(new Date()) + "_processedImage.png";

        new Thread(new Runnable() {
            public void run() {
                try {
                    // Save the processed thermal image
                    if (thermalBitmap != null) {
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(filename);
                            Mat img = thermalDumpProcessor.getImage(contrastRatio);
                            Bitmap bmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                            Utils.matToBitmap(img, bmp);
                            // PNG is a lossless format, the compression factor (100) is ignored
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (out != null) {
                                    out.close();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                scanMediaStorageAndAnimate(filename);

            }
        }).start();
    }

    private void dumpThermalData(final RenderedImage renderedImage) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        final String filename = AppUtils.getExportsDir() + "/" + sdf.format(new Date()) + "_rawThermal.dat";

        new Thread(new Runnable() {
            @Override
            public void run() {
                ThermalDumpParser thermalDumpParser = new ThermalDumpParser(renderedImage);
                if (thermalDumpParser.dumpRawThermalFile(filename)) {
                    showToastMessage("Dumped: " + filename);
                } else {
                    showToastMessage("Dumped filed.");
                }
            }
        }).start();
    }

    private void performThermalAnalysis(final RenderedImage renderedImage) {
        flirOneDevice.stopFrameStream();
        this.streamingFrame = false;

        contourProcessingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(Config.TAG, "thermalAnalyze preprocess started");
                RawThermalDump thermalDump = new RawThermalDump(renderedImage.width(), renderedImage.height(), thermalPixels);
                thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                thermalDumpProcessor.autoFilter();
                thermalDumpProcessor.filterBelow(2731 + 200); // 320, 350
                thermalDumpProcessor.filterAbove(2731 + 600);
                Mat processedImage = thermalDumpProcessor.getImage(contrastRatio);
                Log.i(Config.TAG, "thermalAnalyze preprocess finished");

                Log.i(Config.TAG, "thermalAnalyze recognizeContours started");
                roiDetector = new ROIDetector(processedImage);
                selectedContourIndex = -1;
                Mat contourImg = roiDetector.recognizeContours((int) roiDetectionThreshold); // 140, 40
                Log.i(Config.TAG, "thermalAnalyze recognizeContours finished");

                Bitmap resultBmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                Utils.matToBitmap(contourImg, resultBmp);
                updateThermalImageView(resultBmp);

                if (showingMoreInfo) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            secondaryControlsContainer.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
        contourProcessingThread.start();
    }

    private void resetAnalysis() {
        if (flirOneDevice != null && !streamingFrame) {
            flirOneDevice.startFrameStream(PreviewActivity.this);
            streamingFrame = true;
            thermalDumpProcessor = null;
        }
    }

    private void handleContourSelected(final MatOfPoint contour) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Change selected contour color
                Mat imgSelected = thermalDumpProcessor.getImage(contrastRatio);
                ROIDetector.drawSelectedContour(imgSelected, contour);
                Bitmap resultBmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                Utils.matToBitmap(imgSelected, resultBmp);
                updateThermalImageView(resultBmp);
            }
        }).start();
    }

    private void adjustContrast(final double ratio) {
        if (thermalDumpProcessor == null)
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Mat processedImage = thermalDumpProcessor.getImage(ratio);
                if (roiDetector != null && roiDetector.getContours() != null) {
                    if (selectedContourIndex != -1)
                        ROIDetector.drawSelectedContour(processedImage, roiDetector.getContours().get(selectedContourIndex));
                    else
                        ROIDetector.drawAllContours(processedImage, true, roiDetector.getContours());
                }

                Bitmap resultBmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                Utils.matToBitmap(processedImage, resultBmp);
                updateThermalImageView(resultBmp);

                PreviewActivity.this.runningContourProcessing = false;
            }
        }).start();
    }

}

// Notes:
// Device Delegate methods
// Called during device discovery, when a device is connected
// During this callback, you should save a reference to device
// You should also set the power update delegate for the device if you have one
// Go ahead and start frame stream as soon as connected, in this use case
// Finally we create a frame processor for rendering frames
