package tw.cchi.flironedemo1;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
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
import org.opencv.core.Scalar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.thermalproc.ROIDetector;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalAnalyzer;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;

import static org.opencv.imgproc.Imgproc.drawContours;

public class PreviewActivity extends Activity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate {
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

    // Related to thermal analysis
    private volatile int[] thermalPixels = null;
    private volatile ROIDetector roiDetector;
    private ThermalDumpProcessor thermalDumpProcessor;
    private RawThermalDump thermalDump;
    private volatile Bitmap thermalBitmap = null;
    private volatile int selectedContourIndex = -1;
    private volatile double contrastRatio = 1;
    private volatile Thread contourProcessingThread = null;

    private int deviceRotation = 0;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private int thermalSpotX = -1;
    private int thermalSpotY = -1;
    private boolean showingTopControls = true;

    @BindView(R.id.fullscreen_content_controls_top) View topControlsView;
    @BindView(R.id.fullscreen_content_controls) View bottomControlsView;
    @BindView(R.id.fullscreen_content) View contentView;
    @BindView(R.id.imageView) ImageView thermalImageView;
    @BindView(R.id.layoutTempSpot) RelativeLayout layoutTempSpot;
    @BindView(R.id.spotMeterValue) TextView spotMeterValue;
    @BindView(R.id.batteryLevelTextView) TextView batteryLevelTextView;
    @BindView(R.id.batteryChargeIndicator) ImageView batteryChargeIndicator;

    @BindView(R.id.secondaryControlsContainer) View secondaryControlsContainer;
    @BindView(R.id.contrastSeekBar) StartPointSeekBar contrastSeekBar;

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
                findViewById(R.id.pleaseConnect).setVisibility(View.GONE);
            }
        });

        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        streamingFrame = true;

        orientationEventListener.enable();
    }

    /**
     * Indicate to the user that the device has disconnected
     */
    public void onDeviceDisconnected(Device device) {
        Log.i(Config.TAG, "Device disconnected!");
        streamingFrame = false;
        flirOneDevice = null;
        orientationEventListener.disable();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.pleaseConnect).setVisibility(View.VISIBLE);
                thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
                batteryLevelTextView.setText("--");
                batteryChargeIndicator.setVisibility(View.GONE);
                spotMeterValue.setText("");
                thermalImageView.clearColorFilter();
                thermalImageView.setImageResource(0);
//                thermalImageView.setImageResource(android.R.color.transparent);
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
        // Log.i(Config.TAG, "Battery percentage received!");
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
        if (currentTuningState != Device.TuningState.InProgress && streamingFrame) {
            frameProcessor.processFrame(frame);
        }
    }

    // Frame Processor Delegate method, will be called each time a rendered frame is produced
    public void onFrameProcessed(final RenderedImage renderedImage) {
        if (!streamingFrame)
            return;

        if (imageCaptureRequested) {
            imageCaptureRequested = false;
            captureImage(renderedImage);

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

                final Bitmap demoBitmap = Bitmap.createBitmap(imageWidth, renderedImage.height(), Bitmap.Config.ARGB_8888);
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
            if (streamingFrame) {
                this.thermalAnalyzeRequested = true;
            } else {
                resetAnalysis();
            }
        }
    }

    public void onConnectSimClicked(View v) {
        if (flirOneDevice == null) {
            try {
                flirOneDevice = new SimulatedDevice(this, this, getResources().openRawResource(R.raw.sampleframes), 10);
                flirOneDevice.setPowerUpdateDelegate(this);
            } catch (Exception ex) {
                flirOneDevice = null;
                Log.w(Config.TAG, "IO EXCEPTION");
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
            secondaryControlsContainer.setVisibility(View.GONE);
            showingTopControls = false;
        } else {
            topControlsView.setVisibility(View.VISIBLE);
            // Check if device is connected & on thermal analysis result preview mode
            if (flirOneDevice != null && thermalDumpProcessor != null)
                secondaryControlsContainer.setVisibility(View.VISIBLE);
            showingTopControls = true;
        }
    }

    public void onDumpThermalRawClicked(View v) {
        if (flirOneDevice != null && streamingFrame && !thermalDumpRequested) {
            this.thermalDumpRequested = true;
        }
    }

    public void onContrastSeekBarChanged(StartPointSeekBar bar, double value) {
        this.contrastRatio = value;
        adjustContourContrast(contrastRatio);
    }


    private void showToastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PreviewActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void captureImage(final RenderedImage renderedImage) {
        new Thread(new Runnable() {
            public void run() {
                // String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                String path = AppUtils.getExportsDir();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
                String[] filenames = {sdf.format(new Date()) + "_thermalImage.jpg", sdf.format(new Date()) + "_processedImage.jpg"};
                try {
                    // Save the original thermal image
                    renderedImage.getFrame().save(new File(path + "/" + filenames[0]), RenderedImage.Palette.Iron, RenderedImage.ImageType.BlendedMSXRGBA8888Image);

                    // Save the processed thermal image
                    if (thermalBitmap != null) {
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(path + "/" + filenames[1]);
                            // PNG is a lossless format, the compression factor (100) is ignored
                            Mat img = thermalDumpProcessor.getGeneratedImage();
                            Bitmap bmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                            Utils.matToBitmap(img, bmp);
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

                    // Call the system media scanner
                    for (int i = 0; i < filenames.length; i++) {
                        MediaScannerConnection.scanFile(PreviewActivity.this,
                                new String[]{path + "/" + filenames[i]}, null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    @Override
                                    public void onScanCompleted(String path, Uri uri) {
                                        Log.i(Config.TAG, "ExternalStorage Scanned " + path + ":");
                                        Log.i(Config.TAG, "ExternalStorage -> uri=" + uri);
                                    }

                                });
                    }

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

    private void dumpThermalData(final RenderedImage renderedImage) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ThermalAnalyzer thermalAnalyzer = new ThermalAnalyzer(renderedImage);
                String filename = "thermal-raw_" + System.currentTimeMillis() + ".dat";
                if (thermalAnalyzer.dumpRawThermalFile(filename)) {
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
                thermalDump = new RawThermalDump(renderedImage.width(), renderedImage.height(), thermalPixels);
                thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                thermalDumpProcessor.autoFilter();
                thermalDumpProcessor.filterBelow(2731 + 320); // 350
                Mat processedImage = thermalDumpProcessor.getGeneratedImage();
                Log.i(Config.TAG, "thermalAnalyze preprocess finished");

                Log.i(Config.TAG, "thermalAnalyze recognizeContours started");
                roiDetector = new ROIDetector(processedImage);
                Mat contourImg = roiDetector.recognizeContours(140); // 40
                selectedContourIndex = -1;
                Log.i(Config.TAG, "thermalAnalyze recognizeContours finished");

                Bitmap resultBmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                Utils.matToBitmap(contourImg, resultBmp);
                updateThermalImageView(resultBmp);
            }
        });
        contourProcessingThread.start();
    }

    private void resetAnalysis() {
        if (flirOneDevice != null && !streamingFrame) {
            flirOneDevice.startFrameStream(PreviewActivity.this);
            streamingFrame = true;
            thermalDump = null;
            thermalDumpProcessor = null;
        }
    }

    private void updateThermalImageView(final Bitmap frame) {
        thermalBitmap = frame;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
            }
        });
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

    private void adjustContourContrast(final double ratio) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(Config.TAG, String.format("Contrast adjustment (ratio=%.2f) started", ratio));
                Mat processedImage = thermalDumpProcessor.getGeneratedImage(ratio);
                Log.i(Config.TAG, String.format("Contrast adjustment (ratio=%.2f) finished", ratio));

                Bitmap resultBmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565);
                Utils.matToBitmap(processedImage, resultBmp);
                updateThermalImageView(resultBmp);

                PreviewActivity.this.runningContourProcessing = false;
            }
        }).start();
    }

    private void handleThermalImageTouch(int x, int y) {
        boolean ignoreMovingSpot = false;

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
            if (contourIndex != -1) {
                if (contourIndex != this.selectedContourIndex) {
                    this.runningContourProcessing = true;
                    this.selectedContourIndex = contourIndex;
                    ignoreMovingSpot = true;
                    Toast.makeText(this, "Processing selected area", Toast.LENGTH_SHORT).show();

                    final MatOfPoint contour = roiDetector.getContours().get(contourIndex);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(Config.TAG, "thermalAnalysis filterByContour & generate image started");
                            thermalDumpProcessor.filterByContour(contour);
                            // thermalDumpProcessor.autoFilter();
                            Mat processedImage = thermalDumpProcessor.getGeneratedImage();
                            Log.i(Config.TAG, "thermalAnalysis filterByContour & generate image finished");

                            ArrayList<MatOfPoint> contours = new ArrayList<>();
                            contours.add(contour);
                            drawContours(processedImage, contours, -1, new Scalar(255), 1);

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
        }

        // Set indication spot location
        if (!ignoreMovingSpot) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutTempSpot.getLayoutParams();
            params.leftMargin = x - layoutTempSpot.getMeasuredWidth() / 2;
            params.topMargin = y + layoutTempSpot.getMeasuredHeight() / 2;
            params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
            params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
            layoutTempSpot.setLayoutParams(params);
            updateThermalSpotValue();
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
