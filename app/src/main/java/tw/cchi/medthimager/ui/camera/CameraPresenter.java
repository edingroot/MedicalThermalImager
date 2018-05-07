package tw.cchi.medthimager.ui.camera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;
import com.flir.flironesdk.SimulatedDevice;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.db.AppDatabase;
import tw.cchi.medthimager.db.Patient;
import tw.cchi.medthimager.db.helper.PatientThermalDumpsHelper;
import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.helper.CSVExportHelper;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.CaptureProcessInfo;
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.utils.AppUtils;

public class CameraPresenter<V extends CameraMvpView> extends BasePresenter<V>
    implements CameraMvpPresenter<V>, Device.Delegate, Device.StreamDelegate,
                FrameProcessor.Delegate, Device.PowerUpdateDelegate {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;
    @Inject AppDatabase database;
    @Inject PatientThermalDumpsHelper dbPatientDumpsHelper;
    @Inject CSVExportHelper csvExportHelper;

    private volatile Device flirOneDevice;
    private Device.TuningState tuningState = Device.TuningState.Unknown;
    private FrameProcessor frameProcessor;
    private ThermalSpotsHelper thermalSpotsHelper;
    private Timer contiShootTimer;

    private Bitmap opacityMask; // TODO: volatile?
    private volatile RenderedImage lastRenderedImage;
    private volatile ContiShootParameters contiShootParams;

    private volatile boolean simConnected = false; // simulated device connected
    private volatile boolean streamingFrame = false;
    private volatile boolean contiShooting = false; // TODO: volatile?
    private CaptureProcessInfo captureProcessInfo = null;
    private String patientUuid = Patient.DEFAULT_PATIENT_UUID;

    @Inject
    public CameraPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);

        OpenCVLoader.initDebug();

        frameProcessor = new FrameProcessor(activity, this, EnumSet.of(
            RenderedImage.ImageType.ThermalRGBA8888Image,
            RenderedImage.ImageType.ThermalRadiometricKelvinImage
        ));
        frameProcessor.setImagePalette(RenderedImage.Palette.Gray);
        frameProcessor.setEmissivity(0.98f); // human skin, water, frost

        // Load saved values from shared preferences
        patientUuid = preferencesHelper.getSelectedPatientUuid();

        // Query and display last selected patient name
        Observable.<String>create(emitter ->
            emitter.onNext(database.patientDAO().getOrDefault(patientUuid).getName())
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(getMvpView()::setPatientStatusText);

        getMvpView().setSingleShootMode();
    }

    @Override
    public boolean startDeviceDiscovery() {
        try {
            Device.startDiscovery(activity, this);
        } catch (IllegalStateException e) {
            // it's okay if we've already started discovery
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    @Override
    public void unregisterFlir() {
        // We must unregister our usb receiver, otherwise we will steal events from other apps
        Device.stopDiscovery();
        flirOneDevice = null;
    }

    @Override
    public void checkAndConnectSimDevice() {
        if (flirOneDevice == null) {
            connectSimulatedDevice();
        } else if (flirOneDevice instanceof SimulatedDevice) {
            flirOneDevice.close();
            flirOneDevice = null;
            simConnected = false;
        }
    }

    /**
     * Reconnect sim device if it was connected previously
     */
    @Override
    public void checkReconnectSimDevice() {
        if (simConnected && flirOneDevice == null) {
            connectSimulatedDevice();
        }
    }

    @Override
    public void frameStreamControl(boolean start) {
        if (flirOneDevice != null) {
            if (start)
                flirOneDevice.startFrameStream(this);
            else
                flirOneDevice.stopFrameStream();
        }
    }

    @Override
    public void performTune() {
        // If device is connected and it's not a simulated device
        if (flirOneDevice != null) {
            flirOneDevice.performTuning();
        }
    }

    @Override
    public boolean triggerImageCapture() {
        if (isDeviceAttached() && streamingFrame && captureProcessInfo == null) {
            captureProcessInfo = new CaptureProcessInfo();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void startContiShooting(ContiShootParameters parameters) {
        contiShooting = true;
        contiShootParams = parameters;
        contiShootParams.timeStart = new Date();
        contiShootParams.capturedCount = 0;
        contiShootParams.secondsToNextTick = Config.CONTI_SHOOT_START_DELAY;

        getMvpView().setContinuousShootMode(
            contiShootParams.capturedCount, contiShootParams.totalCaptures);
        getMvpView().updateContinuousShootCountdown(contiShootParams.secondsToNextTick);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                activity.runOnUiThread(() -> {
                    if (--contiShootParams.secondsToNextTick <= 0) {
                        getMvpView().updateContinuousShootCountdown(contiShootParams.secondsToNextTick = 0);
                        handleContiShootTick();
                        getMvpView().updateContinuousShootCountdown(contiShootParams.secondsToNextTick = contiShootParams.interval);
                    } else {
                        getMvpView().updateContinuousShootCountdown(contiShootParams.secondsToNextTick);
                    }
                });
            }
        };
        contiShootTimer = new Timer();
        contiShootTimer.schedule(timerTask, 0, 1000);

        getMvpView().showToast(R.string.conti_shoot_starting, Config.CONTI_SHOOT_START_DELAY);
    }

    private void handleContiShootTick() {
        if (tuningState == Device.TuningState.InProgress) {
            getMvpView().showSnackBar(
                R.string.conti_shoot_tuning_skip,
                contiShootParams.capturedCount, contiShootParams.totalCaptures
            );
            return;
        }

        if (!triggerImageCapture()) {
            contiShooting = false;
            contiShootTimer.cancel();
            contiShootTimer = null;

            if (isViewAttached()) {
                getMvpView().setSingleShootMode();
                getMvpView().showMessageAlertDialog(
                    activity.getString(R.string.error),
                    activity.getString(R.string.conti_shoot_failed_report,
                        contiShootParams.capturedCount, contiShootParams.totalCaptures)
                );
            }
        } else if (++contiShootParams.capturedCount >= contiShootParams.totalCaptures) {
            finishContiShooting(false);
        } else {
            getMvpView().setContinuousShootMode(
                contiShootParams.capturedCount, contiShootParams.totalCaptures);
        }
    }

    /**
     * @param showMessageByToast would be useful while activity pausing
     */
    @Override
    public void finishContiShooting(boolean showMessageByToast) {
        contiShooting = false;
        contiShootTimer.cancel();
        contiShootTimer = null;

        if (isViewAttached()) {
            activity.runOnUiThread(() -> {
                getMvpView().setSingleShootMode();
                if (showMessageByToast) {
                    getMvpView().showToast(
                        activity.getString(R.string.conti_shoot_finished_report,
                            contiShootParams.capturedCount,
                            contiShootParams.totalCaptures)
                    );
                } else {
                    getMvpView().showMessageAlertDialog(
                        activity.getString(R.string.information),
                        activity.getString(R.string.conti_shoot_finished_report,
                            contiShootParams.capturedCount,
                            contiShootParams.totalCaptures)
                    );
                }
            });
        }
    }

    @Override
    public void addThermalSpot() {
        if (thermalSpotsHelper != null) {
            int lastSpotId = thermalSpotsHelper.getLastSpotId();
            thermalSpotsHelper.addSpot(lastSpotId == -1 ? 1 : lastSpotId + 1);
        }
    }

    @Override
    public void removeLastThermalSpot() {
        if (thermalSpotsHelper != null) {
            thermalSpotsHelper.removeLastSpot();
        }
    }

    @Override
    public void clearThermalSpots() {
        if (thermalSpotsHelper != null) {
            thermalSpotsHelper.clearAllSpots();
        }
    }

    @Override
    public void exportAllRecordsToCSV() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd-HHmmss", Locale.getDefault());
        String csvFilepath = AppUtils.getExportsDir() + "/" + "RecordsExport_" + sdf.format(new Date()) + ".csv";

        csvExportHelper.exportAllCaptureRecords(csvFilepath)
            .observeOn(AndroidSchedulers.mainThread()).subscribe(
                o -> {},
                e -> {
                    getMvpView().showSnackBar("Error exporting to file: " + csvFilepath);
                    e.printStackTrace();
                },
                () -> {
                    getMvpView().showToast("Exported to file: " + csvFilepath);
                }
        );
    }

    // -------------------------------------- Delegate Methods ----------------------------------- //

    @Override
    public void onDeviceConnected(Device device) {
        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        streamingFrame = true;
        activity.runOnUiThread(() -> getMvpView().setDeviceConnected());
    }

    @Override
    public void onDeviceDisconnected(Device device) {
        streamingFrame = false;
        flirOneDevice = null;

        if (!activity.isDestroyed() && !activity.isFinishing()) {
            // TODO: clear preselected thermal spots while device disconnected?
//            if (thermalSpotsHelper != null) {
//                thermalSpotsHelper.dispose();
//            }

            getMvpView().showSnackBar(R.string.flirone_disconnected);
            activity.runOnUiThread(() -> getMvpView().setDeviceDisconnected());
        }
    }

    /**
     * If using RenderedImage.ImageType.ThermalRadiometricKelvinImage, you should not rely on
     * the accuracy if tuningState is not Device.TuningState.Tuned
     */
    @Override
    public void onTuningStateChanged(Device.TuningState tuningState) {
        this.tuningState = tuningState;
        activity.runOnUiThread(() -> getMvpView().setDeviceTuningState(tuningState));
    }

    @Override
    public void onAutomaticTuningChanged(boolean b) {
    }

    @Override
    public void onFrameReceived(Frame frame) {
        if (tuningState != Device.TuningState.InProgress && streamingFrame) {
            frameProcessor.processFrame(frame, FrameProcessor.QueuingOption.CLEAR_QUEUED);
        }
    }

    @Override
    public void onFrameProcessed(RenderedImage renderedImage) {
        if (!streamingFrame) return;
        lastRenderedImage = renderedImage;

        if (renderedImage.imageType() == RenderedImage.ImageType.ThermalRadiometricKelvinImage) {
            if (thermalSpotsHelper != null)
                thermalSpotsHelper.updateThermalValuesFromImage(renderedImage);

            // If image capture in progress
            if (captureProcessInfo != null) {
                captureRawThermalDump(renderedImage, captureProcessInfo.getDumpFilepath());
                captureFLIRImage(renderedImage, captureProcessInfo.getFilepathPrefix() + Constants.POSTFIX_FLIR_IMAGE + ".jpg");

                dbPatientDumpsHelper.addCaptureRecord(patientUuid, captureProcessInfo.getTitle(), captureProcessInfo.getFilepathPrefix()).subscribe();
                captureProcessInfo = null;
            }
        } else if (renderedImage.imageType() == RenderedImage.ImageType.ThermalRGBA8888Image) {
            updateThermalImageView(renderedImage);
        }

        // (DEBUG) Show image types
        // for (RenderedImage.ImageType type : frameProcessor.getImageTypes()) {
        //     Log.i(TAG, "ImageType=" + type);
        // }
    }

    @Override
    public void onBatteryChargingStateReceived(Device.BatteryChargingState batteryChargingState) {
        activity.runOnUiThread(() -> getMvpView().setDeviceChargingState(batteryChargingState));
    }

    @Override
    public void onBatteryPercentageReceived(byte percentage) {
        activity.runOnUiThread(() -> getMvpView().setDeviceBatteryPercentage(percentage));
    }

    // --------------------------------- Getter / Setter / Updates ------------------------------- //

    @Override
    public String getCurrentPatientUuid() {
        return patientUuid;
    }

    @Override
    public void setCurrentPatient(String patientUUID) {
        patientUuid = patientUUID;
        preferencesHelper.setSelectedPatientUuid(patientUUID);

        // Query and display last selected patient name
        Observable.<String>create(emitter ->
            emitter.onNext(database.patientDAO().getOrDefault(patientUuid).getName())
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(getMvpView()::setPatientStatusText);
    }

    @Override
    public boolean isDeviceAttached() {
        return flirOneDevice != null;
    }

    @Override
    public boolean isContiShootingMode() {
        return contiShooting;
    }

    @Override
    public boolean isOpacityMaskAttached() {
        return opacityMask != null;
    }

    @NewThread
    @Override
    public void setOpacityMask(String imagePath) {
        Observable.create(emitter ->
            opacityMask = BitmapFactory.decodeFile(imagePath)
        ).subscribeOn(Schedulers.io()).subscribe();
    }

    @NewThread @BgThreadCapable
    @Override
    public void updateThermalImageView(RenderedImage renderedImage) {
        Observable.<Bitmap>create(emitter -> {
            Bitmap frame = renderedImage.getBitmap();

            // Draw opacity mask if assigned
            if (opacityMask != null) {
                Canvas canvas = new Canvas(frame);
                Paint alphaPaint = new Paint();
                alphaPaint.setAlpha(Config.PREVIEW_MASK_ALPHA);
                canvas.drawBitmap(opacityMask, 0, 0, alphaPaint);
            }

            emitter.onNext(frame);
            emitter.onComplete();
        }).subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(
                frame -> getMvpView().updateThermalImageView(frame),
                e -> {},
                () -> {

                    if (thermalSpotsHelper == null) {
                        // Wait measured width and height to be correct
                        // TODO: better approach?
                        mainLooperHandler.postDelayed(() -> {
                            if (getMvpView().getThermalImageViewHeight() == 0) {
                                // This should be called after getMvpView().updateThermalImageView()
                                getMvpView().getThermalImageViewGlobalLayouts().take(1).subscribe(o -> {
                                    thermalSpotsHelper = getMvpView().createThermalSpotsHelper(lastRenderedImage);
                                    getMvpView().setSpotsControlEnabled(true);
                                });
                            } else {
                                thermalSpotsHelper = getMvpView().createThermalSpotsHelper(lastRenderedImage);
                                getMvpView().setSpotsControlEnabled(true);
                            }
                        }, 150);
                    } else {
                        getMvpView().setSpotsControlEnabled(true);
                    }
                }
        );
    }

    // ------------------------------------------------------------------------------------------- //

    private void connectSimulatedDevice() {
        try {
            flirOneDevice = new SimulatedDevice(this, activity,
                activity.getResources().openRawResource(R.raw.sampleframes), 10);
            flirOneDevice.setPowerUpdateDelegate(this);
        } catch (Exception e) {
            flirOneDevice = null;
            e.printStackTrace();
        }

        simConnected = true;
    }

    @NewThread
    private void captureFLIRImage(final RenderedImage renderedImage, final String filename) {
        Observable.create(emitter -> {
            try {
                // Save the original thermal image
                renderedImage.getFrame().save(new File(filename), frameProcessor);
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
            o -> {},
            e -> {
                getMvpView().showSnackBar(R.string.error_occurred);
                e.printStackTrace();
            },
            () -> {
                scanMediaStorage(filename);
                getMvpView().animateFlash();
            }
        );
    }

    @NewThread @BgThreadCapable
    private void captureRawThermalDump(final RenderedImage renderedImage, final String filename) {
        Observable.create(emitter -> {
            RawThermalDump rawThermalDump = new RawThermalDump(renderedImage);
            if (thermalSpotsHelper != null) {
                ArrayList<Point> preSelectedSpots = thermalSpotsHelper.getPreSelectedSpots();
                if (preSelectedSpots.size() > 0)
                    rawThermalDump.setSpotMarkers(preSelectedSpots);
            }

            if (rawThermalDump.saveToFile(filename)) {
                scanMediaStorage(filename);
                emitter.onComplete();
            } else {
                emitter.onError(new Error());
            }
        }).subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(
            o -> {},
            e -> getMvpView().showToast(R.string.dump_failed),
            () -> getMvpView().showToast(R.string.dumped_, filename)
        );
    }

    private void scanMediaStorage(String filename) {
        // Call the system media scanner
        Log.i(TAG, "scanning media storage");
        activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(filename))));
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
