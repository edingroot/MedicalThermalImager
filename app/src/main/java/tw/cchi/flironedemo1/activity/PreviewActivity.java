package tw.cchi.flironedemo1.activity;

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
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.Config;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.db.AppDatabase;
import tw.cchi.flironedemo1.db.helper.PatientThermalDumpsHelper;
import tw.cchi.flironedemo1.dialog.SelectPatientDialog;
import tw.cchi.flironedemo1.helper.CSVExportHelper;
import tw.cchi.flironedemo1.thermalproc.ROIDetector;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;
import tw.cchi.flironedemo1.view.ThermalSpotView;

public class PreviewActivity extends BaseActivity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate {
    public static final int ACTION_PICK_FROM_GALLERY = 100;

    private volatile Device flirOneDevice;
    private FrameProcessor frameProcessor;
    private Device.TuningState currentTuningState = Device.TuningState.Unknown;
    private ColorFilter originalChargingIndicatorColor;
    private volatile Bitmap thermalBitmap; // last frame updated to imageview
    private volatile Bitmap opacityMask;
    private SelectPatientDialog selectPatientDialog;
    private PatientThermalDumpsHelper dbPatientDumpsHelper;
    private CSVExportHelper csvExportHelper;

    private volatile boolean simConnected = false;
    private volatile boolean streamingFrame = false;
    private volatile boolean runningThermalAnalysis = false;
    private volatile boolean runningContourProcessing = false;
    private volatile boolean imageCaptureRequested = false;
    private volatile boolean thermalAnalyzeRequested = false;
    private boolean showingMoreInfo = false;
    private int thermalSpotX = -1; // movable spot thermal indicator pX
    private int thermalSpotY = -1; // movable spot thermal indicator pY
    private String selectedPatientUUID = null;

    // Thermal analysis related
    private volatile ROIDetector roiDetector;
    private ThermalDumpProcessor thermalDumpProcessor;
    private volatile Thread contourProcessingThread;
    private volatile RenderedImage lastRenderedImage;
    private volatile int selectedContourIndex = -1;
    private volatile double contrastRatio = 1;
    private volatile double roiDetectionThreshold = 1;
    private volatile double recognitionThreshold = 1;

    @BindView(R.id.controls_top) View topControlsView;
    @BindView(R.id.content_controls_bottom) View bottomControlsView;
    @BindView(R.id.fullscreen_content) View contentView;
    @BindView(R.id.imageView) ImageView thermalImageView;
    @BindView(R.id.pleaseConnect) TextView pleaseConnect;
    @BindView(R.id.batteryLevelTextView) TextView batteryLevelTextView;
    @BindView(R.id.batteryChargeIndicator) ImageView batteryChargeIndicator;
    @BindView(R.id.thermalSpotView) ThermalSpotView thermalSpotView;

    @BindView(R.id.secondaryControlsContainer) View secondaryControlsContainer;
    @BindView(R.id.contrastSeekBar) StartPointSeekBar contrastSeekBar;
    @BindView(R.id.txtContrastValue) TextView txtContrastValue;
    @BindView(R.id.roiThresSeekBar) StartPointSeekBar roiThresSeekBar;
    @BindView(R.id.txtROIThresValue) TextView txtROIThresValue;
    @BindView(R.id.rcgnThresSeekBar) StartPointSeekBar rcgnThresSeekBar;
    @BindView(R.id.txtRcgnThresValue) TextView txtRcgnThresValue;
    @BindView(R.id.btnFilter) Button btnFilter;
    @BindView(R.id.btnRcgHigh) Button btnRcgHigh;
    @BindView(R.id.btnTools) Button btnTools;
    @BindView(R.id.editTuningState) TextView editTuningState;

    ScaleGestureDetector mScaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initDebug();
        setContentView(R.layout.activity_preview);
        ButterKnife.bind(this);

        dbPatientDumpsHelper = new PatientThermalDumpsHelper(AppDatabase.getInstance(this));
        csvExportHelper = new CSVExportHelper(this, AppDatabase.getInstance(this));

        frameProcessor = new FrameProcessor(this, this, EnumSet.of(
                RenderedImage.ImageType.ThermalRGBA8888Image,
                RenderedImage.ImageType.ThermalRadiometricKelvinImage
        ));
        frameProcessor.setImagePalette(RenderedImage.Palette.Gray);
        frameProcessor.setEmissivity(0.98f); // human skin, water, frost

        /* contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onSelected(View view) {
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
                    if (y >= 0 && y < thermalImageView.getMeasuredHeight()) {
                        handleThermalImageTouch(x, y);
                    }
                }
                mScaleDetector.onTouchEvent(event);

                // Consume the event, which onSelected event will not triggered
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
        if (selectPatientDialog != null) selectPatientDialog.dismiss();
        if (flirOneDevice != null) flirOneDevice.stopFrameStream();
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
        Device.stopDiscovery();
        flirOneDevice = null;
        super.onStop();
    }

    @Override
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
                    String filepath = cursor.getString(columnIndex);
                    cursor.close();
                    opacityMask = BitmapFactory.decodeFile(filepath);

                    // Reconnect sim device if it was connected previously
                    if (simConnected && flirOneDevice == null) {
                        connectSimulatedDevice();
                    }
                }
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                triggerImageCapture();
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    public void onDeviceConnected(Device device) {
        Log.i(Config.TAG, "Device connected!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pleaseConnect.setVisibility(View.GONE);
                thermalSpotView.setVisibility(View.VISIBLE);
            }
        });

        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        streamingFrame = true;

        if (showingMoreInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
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

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pleaseConnect.setVisibility(View.VISIBLE);
                thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565));
                batteryLevelTextView.setText("--");
                batteryChargeIndicator.setVisibility(View.GONE);
                thermalSpotView.setVisibility(View.GONE);
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
        this.currentTuningState = tuningState;
        final String tuningStateName = tuningState.name();
        Log.i(Config.TAG, "Tuning state changed changed: " + tuningStateName);

        if (tuningState == Device.TuningState.InProgress) {
            runOnUiThread(new Thread() {
                @Override
                public void run() {
                    super.run();
                    editTuningState.setText(tuningStateName);
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
                    editTuningState.setText(tuningStateName);
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

    // Frame Processor BitmapUpdateListener method, will be called each time a rendered frame is produced
    public void onFrameProcessed(final RenderedImage renderedImage) {
        if (!streamingFrame) return;
        lastRenderedImage = renderedImage;

        if (renderedImage.imageType() == RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
            updateThermalSpotValue();

            if (thermalAnalyzeRequested) {
                thermalAnalyzeRequested = false;
                if (runningThermalAnalysis) {
                    // Terminate the previous unfinished thermal analysis thread
                    if (runningContourProcessing) {
                        runningContourProcessing = false;
                        contourProcessingThread.stop();
                    }
                    runningThermalAnalysis = false;
                } else {
                    performThermalAnalysis(renderedImage);
                }
            }

            if (imageCaptureRequested) {
                imageCaptureRequested = false;

                String filenamePrefix = AppUtils.generateCaptureFilename();
                String filepathPrefix = AppUtils.getExportsDir() + "/" + filenamePrefix;
                String dumpFilepath = filepathPrefix + Config.POSTFIX_THERMAL_DUMP + ".dat";
                captureFLIRImage(renderedImage, filepathPrefix + Config.POSTFIX_FLIR_IMAGE + ".jpg");
                captureRawThermalDump(renderedImage, dumpFilepath);

                String title = RawThermalDump.generateTitleFromFilepath(dumpFilepath);
                dbPatientDumpsHelper.addCaptureRecord(selectedPatientUUID, title, filenamePrefix);
            }

            // (DEBUG) Show image types
            // for (RenderedImage.ImageType type : frameProcessor.getImageTypes()) {
            //     Log.i(Config.TAG, "ImageType=" + type);
            // }
        } else {
            updateThermalImageView(renderedImage.getBitmap());
        }
    }


    public void onTuneClicked(View v) {
        if (flirOneDevice != null) {
            flirOneDevice.performTuning();
        }
    }

    public void onCaptureImageClicked(View v) {
        triggerImageCapture();
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

    public void onSelectPatientClicked(View v) {
        if (selectPatientDialog == null) {
            selectPatientDialog =
                    new SelectPatientDialog(this, new SelectPatientDialog.OnInteractionListener() {
                @Override
                public void onOkClicked(String selectedPatientUUID) {
                    PreviewActivity.this.selectedPatientUUID = selectedPatientUUID;
                }
            });
        }

        selectPatientDialog.setSelectedPatientUUID(selectedPatientUUID);
        selectPatientDialog.show();
    }

    public void onToolsClicked(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.inflate(R.menu.preview_tools_menu);

        String pickMaskTitle = getString(opacityMask == null ? R.string.pick_mask : R.string.unset_mask);
        popup.getMenu().findItem(R.id.action_pick_mask).setTitle(pickMaskTitle);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_dump_viewer:
                        startActivity(new Intent(PreviewActivity.this, DumpViewerActivity.class));
                        return true;

                    case R.id.export_csv:
                        SimpleDateFormat sdf = new SimpleDateFormat("MMdd-HHmmss", Locale.getDefault());
                        String csvFilepath = AppUtils.getExportsDir() + "/" + "RecordsExport_" + sdf.format(new Date()) + ".csv";
                        csvExportHelper.exportAllCaptureRecords(csvFilepath);
                        return true;

                    case R.id.action_pick_mask:
                        if (opacityMask == null) {
                            Intent galleryIntent = new Intent(
                                    Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(galleryIntent, ACTION_PICK_FROM_GALLERY);
                        } else {
                            opacityMask = null;
                        }
                        return true;

                    case R.id.action_switch_rotate:
                        if (thermalImageView.getRotation() == 0f) {
                            thermalImageView.setRotation(180f);
                        } else {
                            thermalImageView.setRotation(0f);
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    /* ---------- Secondary control panel ---------- */

    public void onToggleMoreInfoClicked(View v) {
        // Check if device is connected & is under thermal analysis result preview mode
        // if (flirOneDevice != null && thermalDumpProcessor != null)
        if (flirOneDevice != null) {
            if (showingMoreInfo) {
                secondaryControlsContainer.setVisibility(View.GONE);
                showingMoreInfo = false;
            } else {
                secondaryControlsContainer.setVisibility(View.VISIBLE);
                showingMoreInfo = true;
            }
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
                    Mat processedImage = thermalDumpProcessor.getImageMat(contrastRatio);
                    Log.i(Config.TAG, "thermalAnalysis filterFromContour & generate image finished");

                    // Show filtered result
                    ROIDetector.drawSelectedContour(processedImage, contour);
                    Bitmap resultBmp = Bitmap.createBitmap(lastRenderedImage.width(), lastRenderedImage.height(), Bitmap.Config.RGB_565);
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

    private void scanMediaStorage(String filename, boolean animateFlash) {
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

        if (animateFlash) {
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

    private void triggerImageCapture() {
        if (flirOneDevice != null) {
            if (streamingFrame) {
                this.imageCaptureRequested = true;
            } else {
                String filepathPrefix = AppUtils.getExportsDir() + "/" + AppUtils.generateCaptureFilename();
                captureProcessedImage(filepathPrefix + Config.POSTFIX_PROCEED_IMAGE + ".jpg");
                captureRawThermalDump(lastRenderedImage, filepathPrefix + Config.POSTFIX_THERMAL_DUMP + ".dat");
            }
        }
    }

    private void updateThermalImageView(final Bitmap frame) {
        // Draw opacity mask if assigned
        if (opacityMask != null) {
            Canvas canvas = new Canvas(frame);
            Paint alphaPaint = new Paint();
            alphaPaint.setAlpha(Config.PREVIEW_MASK_ALPHA);
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
        if (lastRenderedImage == null)
            return;

        final double averageC;
        RawThermalDump rawThermalDump = new RawThermalDump(
                1, lastRenderedImage.width(), lastRenderedImage.height(), lastRenderedImage.thermalPixelValues());
        if (thermalSpotX == -1) {
            averageC = rawThermalDump.getTemperature9Average(rawThermalDump.getWidth() / 2, rawThermalDump.getHeight() / 2);
        } else {
            averageC = rawThermalDump.getTemperature9Average(thermalSpotX, thermalSpotY);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PreviewActivity.this.thermalSpotView.setTemperature(averageC);
            }
        });
    }

    /**
     *
     * @param x the pX value on the imageView
     * @param y the pY value on the imageView
     */
    private void handleThermalImageTouch(int x, int y) {
        // Calculate the correspondent point on the thermal image
        double ratio = (double) lastRenderedImage.width() / thermalImageView.getMeasuredWidth();
        int imgX = (int) (x * ratio);
        int imgY = (int) (y * ratio);
        thermalSpotX = AppUtils.trimByRange(imgX, 1, lastRenderedImage.width() - 1);
        thermalSpotY = AppUtils.trimByRange(imgY, 1, lastRenderedImage.height() - 1);

        // If is in thermal analysis result preview mode and the previous analysis is finished, handle selection.
        if (flirOneDevice != null && !streamingFrame && !runningContourProcessing) {
            int contourIndex = roiDetector.getSelectedContourIndex(imgX, imgY);
            // If there is a contour selected
            if (contourIndex != -1 && contourIndex != selectedContourIndex) {
                selectedContourIndex = contourIndex;
                handleContourSelected(roiDetector.getContours().get(contourIndex));
            }
        }

        thermalSpotView.setCenterPosition(
                x + thermalImageView.getLeft(),
                y + thermalImageView.getTop()
        );

        updateThermalSpotValue();
    }

    private void captureFLIRImage(final RenderedImage renderedImage, final String filename) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    // Save the original thermal image
                    renderedImage.getFrame().save(new File(filename), RenderedImage.Palette.Gray, RenderedImage.ImageType.ThermalRGBA8888Image);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                scanMediaStorage(filename, true);
            }
        }).start();
    }

    private void captureRawThermalDump(final RenderedImage renderedImage, final String filename) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RawThermalDump rawThermalDump = new RawThermalDump(renderedImage);
                if (rawThermalDump.saveToFile(filename)) {
                    showToastMessage("Dumped: " + filename);
                    scanMediaStorage(filename, false);
                } else {
                    showToastMessage("Dumped filed.");
                }
            }
        }).start();
    }

    private void captureProcessedImage(final String filename) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    // Save the processed thermal image
                    if (thermalBitmap != null) {
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(filename);
                            Mat img = thermalDumpProcessor.getImageMat(contrastRatio);
                            Bitmap bmp = Bitmap.createBitmap(lastRenderedImage.width(), lastRenderedImage.height(), Bitmap.Config.RGB_565);
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
                                scanMediaStorage(filename, false);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void performThermalAnalysis(final RenderedImage renderedImage) {
        runningThermalAnalysis = true;
        flirOneDevice.stopFrameStream();
        streamingFrame = false;

        contourProcessingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(Config.TAG, "thermalAnalyze preprocess started");
                RawThermalDump thermalDump = new RawThermalDump(1, renderedImage.width(), renderedImage.height(), renderedImage.thermalPixelValues());
                thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                thermalDumpProcessor.autoFilter();
                thermalDumpProcessor.filterBelow(2731 + 200); // 320, 350
                thermalDumpProcessor.filterAbove(2731 + 600);
                Mat processedImage = thermalDumpProcessor.getImageMat(contrastRatio);
                Log.i(Config.TAG, "thermalAnalyze preprocess finished");

                Log.i(Config.TAG, "thermalAnalyze recognizeContours started");
                roiDetector = new ROIDetector(processedImage);
                selectedContourIndex = -1;
                Mat contourImg = roiDetector.recognizeContours((int) roiDetectionThreshold); // 140, 40
                Log.i(Config.TAG, "thermalAnalyze recognizeContours finished");

                Bitmap resultBmp = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.RGB_565);
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
                Mat imgSelected = thermalDumpProcessor.getImageMat(contrastRatio);
                ROIDetector.drawSelectedContour(imgSelected, contour);
                Bitmap resultBmp = Bitmap.createBitmap(lastRenderedImage.width(), lastRenderedImage.height(), Bitmap.Config.RGB_565);
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
                Mat processedImage = thermalDumpProcessor.getImageMat(ratio);
                if (roiDetector != null && roiDetector.getContours() != null) {
                    if (selectedContourIndex != -1)
                        ROIDetector.drawSelectedContour(processedImage, roiDetector.getContours().get(selectedContourIndex));
                    else
                        ROIDetector.drawAllContours(processedImage, true, roiDetector.getContours());
                }

                Bitmap resultBmp = Bitmap.createBitmap(lastRenderedImage.width(), lastRenderedImage.height(), Bitmap.Config.RGB_565);
                Utils.matToBitmap(processedImage, resultBmp);
                updateThermalImageView(resultBmp);

                PreviewActivity.this.runningContourProcessing = false;
            }
        }).start();
    }

}

// Notes:
// Device BitmapUpdateListener methods
// Called during device discovery, when a device is connected
// During this callback, you should save a reference to device
// You should also set the power update delegate for the device if you have one
// Go ahead and start frame stream as soon as connected, in this use case
// Finally we create a frame processor for rendering frames
