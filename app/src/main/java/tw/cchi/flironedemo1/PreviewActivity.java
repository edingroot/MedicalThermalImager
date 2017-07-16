package tw.cchi.flironedemo1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.FlirUsbDevice;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;
import com.flir.flironesdk.SimulatedDevice;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;
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
import tw.cchi.flironedemo1.thermalproc.ThermalAnalyzer;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;

public class PreviewActivity extends Activity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate {
    private volatile boolean streamingFrame = false;
    private volatile boolean imageCaptureRequested = false;
    private volatile boolean thermalAnalyzeRequested = false;
    private volatile boolean thermalDumpRequested = false;

    private OrientationEventListener orientationEventListener;
    private volatile Device flirOneDevice;
    private FrameProcessor frameProcessor;
    private String lastSavedPath;
    private Device.TuningState currentTuningState = Device.TuningState.Unknown;
    private ColorFilter originalChargingIndicatorColor = null;
    private Bitmap thermalBitmap = null;

    private int deviceRotation = 0;
    private boolean showingTopControls = true;

    @BindView(R.id.fullscreen_content_controls_top) View topControlsView;
    @BindView(R.id.fullscreen_content_controls) View bottomControlsView;
    @BindView(R.id.fullscreen_content) View contentView;
    @BindView(R.id.imageView) ImageView thermalImageView;

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

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (flirOneDevice != null && !streamingFrame) {
                    flirOneDevice.startFrameStream(PreviewActivity.this);
                    streamingFrame = true;
                }
            }
        });

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
                Log.d("ZOOM", "zoom ongoing, scale: " + detector.getScaleFactor());
                frameProcessor.setMSXDistance(detector.getScaleFactor());
                return false;
            }
        });

        contentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                return false;
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
        thermalImageView = (ImageView) findViewById(R.id.imageView);
        if (Device.getSupportedDeviceClasses(this).contains(FlirUsbDevice.class)) {
            findViewById(R.id.pleaseConnect).setVisibility(View.VISIBLE);
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

    @Override
    public void onStop() {
        // We must unregister our usb receiver, otherwise we will steal events from other apps
        Log.e("PreviewActivity", "onStop, stopping discovery!");
        Device.stopDiscovery();
        flirOneDevice = null;
        super.onStop();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    private void showMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PreviewActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateThermalImageView(final Bitmap frame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
            }
        });
    }

    public void onDeviceConnected(Device device) {
        Log.i("ExampleApp", "Device connected!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.pleaseConnect).setVisibility(View.GONE);
            }
        });

        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        this.streamingFrame = true;

        orientationEventListener.enable();
    }

    /**
     * Indicate to the user that the device has disconnected
     */
    public void onDeviceDisconnected(Device device) {
        Log.i("ExampleApp", "Device disconnected!");
        this.streamingFrame = false;

        final TextView levelTextView = (TextView) findViewById(R.id.batteryLevelTextView);
        final ImageView chargingIndicator = (ImageView) findViewById(R.id.batteryChargeIndicator);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.pleaseConnect).setVisibility(View.GONE);
                thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
                levelTextView.setText("--");
                chargingIndicator.setVisibility(View.GONE);
                thermalImageView.clearColorFilter();
                findViewById(R.id.tuningProgressBar).setVisibility(View.GONE);
                findViewById(R.id.tuningTextView).setVisibility(View.GONE);
                findViewById(R.id.connect_sim_button).setEnabled(true);
            }
        });
        flirOneDevice = null;
        orientationEventListener.disable();
    }

    /**
     * If using RenderedImage.ImageType.ThermalRadiometricKelvinImage, you should not rely on
     * the accuracy if tuningState is not Device.TuningState.Tuned
     *
     * @param tuningState
     */
    public void onTuningStateChanged(Device.TuningState tuningState) {
        Log.i("ExampleApp", "Tuning state changed changed!");

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
        Log.i("ExampleApp", "Battery charging state received!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView chargingIndicator = (ImageView) findViewById(R.id.batteryChargeIndicator);
                if (originalChargingIndicatorColor == null) {
                    originalChargingIndicatorColor = chargingIndicator.getColorFilter();
                }
                switch (batteryChargingState) {
                    case FAULT:
                    case FAULT_HEAT:
                        chargingIndicator.setColorFilter(Color.RED);
                        chargingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case FAULT_BAD_CHARGER:
                        chargingIndicator.setColorFilter(Color.DKGRAY);
                        chargingIndicator.setVisibility(View.VISIBLE);
                    case MANAGED_CHARGING:
                        chargingIndicator.setColorFilter(originalChargingIndicatorColor);
                        chargingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case NO_CHARGING:
                    default:
                        chargingIndicator.setVisibility(View.GONE);
                        break;
                }
            }
        });
    }

    @Override
    public void onBatteryPercentageReceived(final byte percentage) {
        Log.i("ExampleApp", "Battery percentage received!");

        final TextView levelTextView = (TextView) findViewById(R.id.batteryLevelTextView);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                levelTextView.setText(String.valueOf((int) percentage) + "%");
            }
        });
    }

    // StreamDelegate method
    public void onFrameReceived(Frame frame) {
        Log.v("ExampleApp", "Frame received!");

        if (currentTuningState != Device.TuningState.InProgress) {
            frameProcessor.processFrame(frame);
        }
    }

    // Frame Processor Delegate method, will be called each time a rendered frame is produced
    public void onFrameProcessed(final RenderedImage renderedImage) {
        if (renderedImage.imageType() == RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
            final int[] thermalPixels = renderedImage.thermalPixelValues();
            // Note: this code is not optimized

            // average the center 9 pixels for the spot meter
            int width = renderedImage.width();
            int height = renderedImage.height();
            int centerPixelIndex = width * (height / 2) + (width / 2);
            int[] centerPixelIndexes = new int[]{
                    centerPixelIndex, centerPixelIndex - 1, centerPixelIndex + 1,
                    centerPixelIndex - width,
                    centerPixelIndex - width - 1,
                    centerPixelIndex - width + 1,
                    centerPixelIndex + width,
                    centerPixelIndex + width - 1,
                    centerPixelIndex + width + 1
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
                    ((TextView) findViewById(R.id.spotMeterValue)).setText(spotMeterValue);
                }
            });

            // if radiometric is the only type, also show the image
            if (frameProcessor.getImageTypes().size() == 1) {
                // example of a custom colorization, maps temperatures 0-100C to 8-bit gray-scale
                byte[] argbPixels = new byte[width * height * 4];
                final byte aPixValue = (byte) 255;
                for (int p = 0; p < thermalPixels.length; p++) {
                    int destP = p * 4;
                    byte pixValue = (byte) (Math.min(0xff, Math.max(0x00, (thermalPixels[p] - 27315) * (255.0 / 10000.0))));

                    argbPixels[destP + 3] = aPixValue;
                    // red pixel
                    argbPixels[destP] = argbPixels[destP + 1] = argbPixels[destP + 2] = pixValue;
                }
                final Bitmap demoBitmap = Bitmap.createBitmap(width, renderedImage.height(), Bitmap.Config.ARGB_8888);

                demoBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argbPixels));

                updateThermalImageView(demoBitmap);
            }

            if (thermalAnalyzeRequested) {
                thermalAnalyzeRequested = false;
                flirOneDevice.stopFrameStream();
                this.streamingFrame = false;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("thermalAnalyze", "preprocess started");
                        RawThermalDump thermalDump = new RawThermalDump(renderedImage.width(), renderedImage.height(), thermalPixels);
                        ThermalDumpProcessor thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                        thermalDumpProcessor.autoFilter();
                        thermalDumpProcessor.filterBelow(2731 + 300); // 350
                        Mat processedImage = thermalDumpProcessor.generateThermalImage();
                        Log.i("thermalAnalyze", "preprocess completed");

                        Log.i("thermalAnalyze", "identifyContours started");
                        ROIDetector roiDetector = new ROIDetector(processedImage);
                        Mat contourImg = roiDetector.identifyContours(70); // 40
                        Log.i("thermalAnalyze", "identifyContours completed");

                        Bitmap resultBmp = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.RGB_565);
                        Utils.matToBitmap(contourImg, resultBmp);
                        updateThermalImageView(resultBmp);
                    }
                }).start();
            }

            if (thermalDumpRequested) {
                thermalDumpRequested = false;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ThermalAnalyzer thermalAnalyzer = new ThermalAnalyzer(renderedImage);
                        String filename = "thermal-raw_" + System.currentTimeMillis() + ".dat";
                        if (thermalAnalyzer.dumpRawThermalFile(filename)) {
                            showMessage("Dumped: " + filename);
                        } else {
                            showMessage("Dumped filed.");
                        }
                    }
                }).start();
            }

            // (DEBUG) Show image types
            Log.i("ImageTypes", "0");
            for (RenderedImage.ImageType type : frameProcessor.getImageTypes()) {
                Log.i("ImageTypes", type.toString());
            }

        } else {
            if (thermalBitmap == null) {
                thermalBitmap = renderedImage.getBitmap();
            } else {
                try {
                    renderedImage.copyToBitmap(thermalBitmap);
                } catch (IllegalArgumentException e) {
                    thermalBitmap = renderedImage.getBitmap();
                }
            }
            updateThermalImageView(thermalBitmap);
        }

        /*
        Capture this image if requested.
        */
        if (this.imageCaptureRequested) {
            imageCaptureRequested = false;
            thermalDumpRequested = true;
            final Context context = this;
            new Thread(new Runnable() {
                public void run() {
                    String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
                    String formatedDate = sdf.format(new Date());
                    String fileName = "FLIROne-" + formatedDate + ".jpg";
                    try {
                        lastSavedPath = path + "/" + fileName;
                        renderedImage.getFrame().save(new File(lastSavedPath), RenderedImage.Palette.Iron, RenderedImage.ImageType.BlendedMSXRGBA8888Image);

                        MediaScannerConnection.scanFile(context,
                                new String[]{path + "/" + fileName}, null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    @Override
                                    public void onScanCompleted(String path, Uri uri) {
                                        Log.i("ExternalStorage", "Scanned " + path + ":");
                                        Log.i("ExternalStorage", "-> uri=" + uri);
                                    }

                                });

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
            }).start();
        }
    }

    public void onTuneClicked(View v) {
        if (flirOneDevice != null) {
            flirOneDevice.performTuning();
        }
    }

    public void onCaptureImageClicked(View v) {
        if (flirOneDevice != null) {
            this.imageCaptureRequested = true;
        }
    }

    public void onAnalyzeClicked(View v) {
        if (flirOneDevice != null) {
            this.thermalAnalyzeRequested = true;
        }
    }

    public void onConnectSimClicked(View v) {
        if (flirOneDevice == null) {
            try {
                flirOneDevice = new SimulatedDevice(this, this, getResources().openRawResource(R.raw.sampleframes), 10);
                flirOneDevice.setPowerUpdateDelegate(this);
            } catch (Exception ex) {
                flirOneDevice = null;
                Log.w("FLIROneExampleApp", "IO EXCEPTION");
                ex.printStackTrace();
            }
        } else if (flirOneDevice instanceof SimulatedDevice) {
            flirOneDevice.close();
            flirOneDevice = null;
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

    public void onToggleMoreInfoClicked(View v) {
        if (showingTopControls) {
            topControlsView.setVisibility(View.INVISIBLE);
            showingTopControls = false;
        } else {
            topControlsView.setVisibility(View.VISIBLE);
            showingTopControls = true;
        }
    }

    public void onDumpThermalRawClicked(View v) {
        if (flirOneDevice != null && streamingFrame) {
            this.thermalDumpRequested = true;
        }
    }

}

// Notes:
// Device Delegate methods
// Called during device discovery, when a device is connected
// During this callback, you should save a reference to device
// You should also set the power update delegate for the device if you have one
// Go ahead and start frame stream as soon as connected, in this use case
// Finally we create a frame processor for rendering frames
