package tw.cchi.medthimager.ui;

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

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.component.ThermalSpotView;
import tw.cchi.medthimager.db.AppDatabase;
import tw.cchi.medthimager.db.helper.PatientThermalDumpsHelper;
import tw.cchi.medthimager.helper.CSVExportHelper;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.dialog.SelectPatientDialog;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerActivity;
import tw.cchi.medthimager.utils.AppUtils;
import tw.cchi.medthimager.utils.CommonUtils;

public class PreviewActivity extends BaseActivity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate {
    public static final int ACTION_PICK_FROM_GALLERY = 100;

    private volatile Device flirOneDevice;
    private FrameProcessor frameProcessor;
    private Device.TuningState currentTuningState = Device.TuningState.Unknown;
    private ColorFilter originalChargingIndicatorColor;
    private volatile RenderedImage lastRenderedImage;
    private volatile Bitmap opacityMask;

    private SelectPatientDialog selectPatientDialog;
    private PatientThermalDumpsHelper dbPatientDumpsHelper;
    private CSVExportHelper csvExportHelper;
    private ScaleGestureDetector mScaleDetector;

    private volatile boolean simConnected = false;
    private volatile boolean streamingFrame = false;
    private volatile boolean imageCaptureRequested = false;
    private int thermalSpotX = -1; // movable spot thermal indicator pX
    private int thermalSpotY = -1; // movable spot thermal indicator pY
    private String selectedPatientUUID = null;

    @BindView(R.id.controls_top) View topControlsView;
    @BindView(R.id.content_controls_bottom) View bottomControlsView;
    @BindView(R.id.fullscreen_content) View contentView;
    @BindView(R.id.imageView) ImageView thermalImageView;
    @BindView(R.id.pleaseConnect) TextView pleaseConnect;
    @BindView(R.id.batteryLevelTextView) TextView batteryLevelTextView;
    @BindView(R.id.batteryChargeIndicator) ImageView batteryChargeIndicator;
    @BindView(R.id.thermalSpotView) ThermalSpotView thermalSpotView;
    @BindView(R.id.txtTuningState) TextView editTuningState;

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
    }

    /**
     * Indicate to the user that the device has disconnected
     */
    public void onDeviceDisconnected(Device device) {
        Log.i(Config.TAG, "Device disconnected!");
        streamingFrame = false;
        flirOneDevice = null;

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

    // Frame Processor OnFrameProcessedListener method, will be called each time a rendered frame is produced
    public void onFrameProcessed(final RenderedImage renderedImage) {
        if (!streamingFrame) return;
        lastRenderedImage = renderedImage;

        if (renderedImage.imageType() == RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
            updateThermalSpotValue();

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


    @OnClick(R.id.imgBtnSelectPatient)
    public void onSelectPatientClick(View v) {
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

    @OnClick(R.id.btnTools)
    public void onToolsClick(View v) {
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

                    case R.id.action_pick_mask:
                        if (opacityMask == null) {
                            Intent galleryIntent = new Intent(
                                    Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(galleryIntent, ACTION_PICK_FROM_GALLERY);
                        } else {
                            opacityMask = null;
                        }
                        return true;

                    case R.id.export_csv:
                        SimpleDateFormat sdf = new SimpleDateFormat("MMdd-HHmmss", Locale.getDefault());
                        String csvFilepath = AppUtils.getExportsDir() + "/" + "RecordsExport_" + sdf.format(new Date()) + ".csv";
                        csvExportHelper.exportAllCaptureRecords(csvFilepath);
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

    @OnClick(R.id.btnTune)
    public void onTuneClick(View v) {
        if (flirOneDevice != null) {
            flirOneDevice.performTuning();
        }
    }

    @OnClick(R.id.imgBtnCapture)
    public void onCaptureImageClick(View v) {
        triggerImageCapture();
    }

    @OnClick(R.id.connect_sim_button)
    public void onConnectSimClick(View v) {
        if (flirOneDevice == null) {
            connectSimulatedDevice();

        } else if (flirOneDevice instanceof SimulatedDevice) {
            flirOneDevice.close();
            flirOneDevice = null;
            simConnected = false;
        }
    }


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
                captureRawThermalDump(lastRenderedImage, filepathPrefix + Config.POSTFIX_THERMAL_DUMP + ".dat");
            }
        }
    }

    private void updateThermalImageView(final Bitmap frame) {
        new Thread(new Runnable() {
            @Override
            public void run() {
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
            }
        }).start();
    }

    private synchronized void updateThermalSpotValue() {
        if (lastRenderedImage == null)
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
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
        }).start();
    }

    /**
     * @param x the pX value on the imageView
     * @param y the pY value on the imageView
     */
    private void handleThermalImageTouch(int x, int y) {
        // Calculate the correspondent point on the thermal image
        double ratio = (double) lastRenderedImage.width() / thermalImageView.getMeasuredWidth();
        int imgX = (int) (x * ratio);
        int imgY = (int) (y * ratio);
        thermalSpotX = CommonUtils.trimByRange(imgX, 1, lastRenderedImage.width() - 1);
        thermalSpotY = CommonUtils.trimByRange(imgY, 1, lastRenderedImage.height() - 1);

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
                    showToastMessage(getString(R.string.dump_failed));
                }
            }
        }).start();
    }

}

// Notes:
// Device OnFrameProcessedListener methods
// Called during device discovery, when a device is connected
// During this callback, you should save a reference to device
// You should also set the power update delegate for the device if you have one
// Go ahead and start frame stream as soon as connected, in this use case
// Finally we create a frame processor for rendering frames
