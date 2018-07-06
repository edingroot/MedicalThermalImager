package tw.cchi.medthimager.ui.camera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.support.annotation.UiThread;
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
import java.util.UUID;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.helper.CSVExportHelper;
import tw.cchi.medthimager.helper.ThImagesHelper;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.CaptureProcessInfo;
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.model.api.ThImage;
import tw.cchi.medthimager.service.sync.task.SyncSingleThImageTask;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.CommonUtils;
import tw.cchi.medthimager.util.FileUtils;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;
import tw.cchi.medthimager.util.annotation.NewThread;

public class CameraPresenter<V extends CameraMvpView> extends BasePresenter<V>
    implements CameraMvpPresenter<V>, Device.Delegate, Device.StreamDelegate,
                FrameProcessor.Delegate, Device.PowerUpdateDelegate {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;
    @Inject CSVExportHelper csvExportHelper;
    @Inject ThImagesHelper thImagesHelper;

    private volatile Device flirOneDevice;
    private Device.TuningState tuningState = Device.TuningState.Unknown;
    private FrameProcessor frameProcessor;
    private ThermalSpotsHelper thermalSpotsHelper;
    private Timer contiShootTimer;
    private boolean clearSpotsOnDisconnect;

    private Bitmap opacityMask;
    private volatile RenderedImage lastRenderedImage;
    private volatile ContiShootParameters contiShootParams;

    // States
    private volatile boolean simConnected = false; // simulated device connected
    private volatile boolean streamingFrame = false;
    private volatile boolean contiShooting = false;
    private CaptureProcessInfo captureProcessInfo = null;
    private Patient currentPatient;

    static {
        OpenCVLoader.initDebug();
    }

    @Inject
    public CameraPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
        loadSettings();

        application.flirDeviceDelegate.setListener(this);
        application.flirFrameProcessorDelegate.setListener(this);

        frameProcessor = new FrameProcessor(activity.getApplicationContext(), application.flirFrameProcessorDelegate, EnumSet.of(
                RenderedImage.ImageType.ThermalRGBA8888Image,
                RenderedImage.ImageType.ThermalRadiometricKelvinImage));
        frameProcessor.setImagePalette(RenderedImage.Palette.Gray);
        frameProcessor.setEmissivity(0.98f); // human skin, water, frost

        // Query and display last selected currentPatient name
        Observable.<String>create(emitter -> {
            // Load saved values from shared preferences
            currentPatient = dataManager.db.patientDAO().getOrDefault(dataManager.pref.getSelectedPatientCuid());
            emitter.onNext(currentPatient.getName());
        }).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(getMvpView()::setPatientStatusText);

        getMvpView().setSingleShootMode();
    }

    public void loadSettings() {
        clearSpotsOnDisconnect = dataManager.pref.getClearSpotsOnDisconnectEnabled();
    }

    @Override
    public boolean startDeviceDiscovery() {
        try {
            Device.startDiscovery(activity.getApplicationContext(), application.flirDeviceDelegate);
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
        // Log event
        dataManager.analytics.logManuallyTune();

        // If device is connected and it's not a simulated device
        if (flirOneDevice != null) {
            flirOneDevice.performTuning();
        }
    }

    @Override
    public boolean triggerImageCapture() {
        if (isDeviceAttached() && streamingFrame && captureProcessInfo == null) {
            // this.currentPatient may be null if the query executed in onAttach() hasn't finish
            captureProcessInfo = new CaptureProcessInfo(currentPatient);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void startContiShooting(ContiShootParameters parameters) {
        // Log event
        dataManager.analytics.logContiShootStart(true, contiShootParams);

        contiShooting = true;
        contiShootParams = parameters;
        contiShootParams.timeStart = new Date();
        contiShootParams.capturedCount = 0;
        contiShootParams.secondsToNextTick = Config.CONTI_SHOOT_START_DELAY;

        flirOneDevice.setAutomaticTuning(false);

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

    @UiThread
    private void handleContiShootTick() {
        if (tuningState == Device.TuningState.InProgress) {
            // Log event
            dataManager.analytics.logTuningWhileContiShoot(contiShootParams);

            getMvpView().showSnackBar(
                R.string.conti_shoot_tuning_skip,
                contiShootParams.capturedCount, contiShootParams.totalCaptures);
            return;
        }

        if (!triggerImageCapture()) {
            finishContiShooting(false, true);
        } else {
            contiShootParams.capturedCount++;

            if (contiShootParams.capturedCount >= contiShootParams.totalCaptures) {
                finishContiShooting(true, true);
            } else {
                getMvpView().setContinuousShootMode(
                        contiShootParams.capturedCount, contiShootParams.totalCaptures);
            }
        }
    }

    /**
     * @param showMessageByDialog would be useful if set to false while activity pausing
     */
    @BgThreadCapable
    @Override
    public void finishContiShooting(boolean success, boolean showMessageByDialog) {
        // Log event
        dataManager.analytics.logContiShootStart(false, contiShootParams);

        contiShooting = false;
        contiShootTimer.cancel();
        contiShootTimer = null;

        flirOneDevice.setAutomaticTuning(true);

        if (isViewAttached()) {
            activity.runOnUiThread(() -> {
                getMvpView().setSingleShootMode();

                String message;
                if (success) {
                    message = activity.getString(R.string.conti_shoot_finished_report,
                        contiShootParams.capturedCount,
                        contiShootParams.totalCaptures);
                } else {
                    message = activity.getString(R.string.conti_shoot_failed_report,
                        contiShootParams.capturedCount, contiShootParams.totalCaptures);
                }

                if (showMessageByDialog) {
                    String dialogTitle = activity.getString(success ? R.string.information : R.string.error);
                    getMvpView().showMessageAlertDialog(dialogTitle, message);
                } else {
                    getMvpView().showToast(message);
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
        // Log event
        dataManager.analytics.logCameraConnected(true);

        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        streamingFrame = true;
        activity.runOnUiThread(() -> getMvpView().setDeviceConnected());
    }

    @Override
    public void onDeviceDisconnected(Device device) {
        // Log event
        dataManager.analytics.logCameraConnected(false);

        streamingFrame = false;
        flirOneDevice = null;

        if (!activity.isDestroyed() && !activity.isFinishing()) {
            // Clear preselected thermal spots on camera disconnect
            if (clearSpotsOnDisconnect && thermalSpotsHelper != null) {
                thermalSpotsHelper.dispose();
                thermalSpotsHelper = null;
            }

            if (!getMvpView().isActivityStopping())
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
        // Log event
        dataManager.analytics.logTuningStateChanged(tuningState);

        this.tuningState = tuningState;
        activity.runOnUiThread(() -> getMvpView().setDeviceTuningState(tuningState));
    }

    @Override
    public void onAutomaticTuningChanged(boolean deviceWillTuneAutomatically) {
        // Log event
        dataManager.analytics.logAutomaticTuningChanged(deviceWillTuneAutomatically);
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
                processCaptures(renderedImage, captureProcessInfo, contiShooting, contiShootParams);
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
        // Log event
        dataManager.analytics.logCameraChargingStateChanged(batteryChargingState);

        activity.runOnUiThread(() -> getMvpView().setDeviceChargingState(batteryChargingState));
    }

    @Override
    public void onBatteryPercentageReceived(byte percentage) {
        activity.runOnUiThread(() -> getMvpView().setDeviceBatteryPercentage(percentage));
    }

    // --------------------------------- Getter / Setter / Updates ------------------------------- //

    @Override
    public String getCurrentPatientCuid() {
        return currentPatient != null ? currentPatient.getCuid() : Patient.DEFAULT_PATIENT_CUID;
    }

    @Override
    public String getCurrentPatientName() {
        return currentPatient != null ? currentPatient.getName() : Patient.DEFAULT_PATIENT_NAME;
    }

    @Override
    public void setCurrentPatient(final String patientCuid) {
        // Log event
        dataManager.analytics.logSetCurrentPatient(patientCuid);

        // Query and display last selected currentPatient name
        Observable.<String>create(emitter -> {
            dataManager.pref.setSelectedPatientCuid(patientCuid);
            currentPatient = dataManager.db.patientDAO().getOrDefault(patientCuid);
            emitter.onNext(currentPatient.getName());
        }).subscribeOn(Schedulers.io())
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
        // Log event
        dataManager.analytics.logConnectSimulatedDevice();

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

    private void processCaptures(RenderedImage renderedImage, CaptureProcessInfo currCaptureProcessInfo,
                                 boolean currContiShooting, ContiShootParameters currContiShootParams) {
        // Async capture & upload
        Observable.zip(
            captureRawThermalDump(renderedImage, currCaptureProcessInfo.getDumpFilepath(), currCaptureProcessInfo.getTitle()),
            captureFLIRImage(renderedImage, currCaptureProcessInfo.getFlirFilepath()),
            (rawThermalDump, captureSuccess) -> {
                if (!captureSuccess) {
                    getMvpView().showToast(R.string.dump_failed);
                }

                return rawThermalDump;
            }
        ).subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .flatMap(rawThermalDump -> {
                // Wait until flir image saved
                FileUtils.waitUntilExists(currCaptureProcessInfo.getFlirFilepath());
                CommonUtils.sleep(100);

                // Extract visible light image
                return thImagesHelper.extractVisibleImage(rawThermalDump);
            })
            .subscribe(
                extractVisibleSuccess -> {
                    // Create record in local db
                    String contiShootUuid = currContiShooting ? currContiShootParams.groupUuid : null;
                    CaptureRecord captureRecord = new CaptureRecord(
                            UUID.randomUUID().toString(),
                            currCaptureProcessInfo.getPatient().getCuid(),
                            currCaptureProcessInfo.getTitle(),
                            currentPatient.getName() + "/" + currCaptureProcessInfo.getFilepathPrefix(),
                            contiShootUuid);

                    dataManager.db.captureRecordDAO().insertAll(captureRecord);

                    // Upload to server if available
                    uploadCapturedFiles(currCaptureProcessInfo.getPatient(), captureRecord,
                                        currCaptureProcessInfo, extractVisibleSuccess);
                },
                e -> {
                    if (isViewAttached()) {
                        getMvpView().showToast(R.string.dump_failed);
                    }
                }
            );
    }

    private Observable<RawThermalDump> captureRawThermalDump(RenderedImage renderedImage, String filename, String title) {
        return Observable.<RawThermalDump>create(emitter -> {
            RawThermalDump rawThermalDump = new RawThermalDump(renderedImage, title);

            if (thermalSpotsHelper != null) {
                // Set preselected spots if exists
                ArrayList<Point> preSelectedSpots = thermalSpotsHelper.getPreSelectedSpots();
                if (preSelectedSpots.size() > 0)
                    rawThermalDump.setSpotMarkers(preSelectedSpots);

                // Auto set visible light image offset based on config in shared preferences
                if (dataManager.pref.getAutoApplyVisibleOffsetEnabled()) {
                    android.graphics.Point defaultOffset = dataManager.pref.getDefaultVisibleOffset();
                    rawThermalDump.setVisibleOffsetX(defaultOffset.x);
                    rawThermalDump.setVisibleOffsetY(defaultOffset.y);
                }
            }

            if (rawThermalDump.saveToFile(filename)) {
                scanMediaStorage(filename);
                emitter.onNext(rawThermalDump);
            } else {
                emitter.onError(new Errors.FileSavingError());
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.computation());
    }

    private Observable<Boolean> captureFLIRImage(RenderedImage renderedImage, String filename) {
        // Log event
        dataManager.analytics.logCameraCapture(contiShooting, contiShootParams);

        return Observable.<Boolean>create(emitter -> {
            try {
                // Save the original thermal image
                renderedImage.getFrame().save(new File(filename), frameProcessor);
                getMvpView().animateFlash();

                scanMediaStorage(filename);
                emitter.onNext(true);
            } catch (Exception e) {
                emitter.onNext(false);
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.io());
    }

    private void scanMediaStorage(String filename) {
        // Log event
        dataManager.analytics.logSimpleEvent("scanMediaStorage", "filename=" + filename);

        // Call the system media scanner
        Log.i(TAG, "scanning media storage");
        activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(filename))));
    }

    @NewThread
    private void uploadCapturedFiles(Patient patient, CaptureRecord captureRecord,
                                     CaptureProcessInfo captureProcessInfo, boolean uploadVisibleImage) {
        application.connectSyncService().subscribe(syncService -> {
            ThImage thImage = new ThImage(captureRecord.getUuid(), patient,
                    captureRecord.getContishootGroup(), captureRecord.getTitle(), captureRecord.getCreatedAt());

            syncService.scheduleNewTask(new SyncSingleThImageTask(thImage,
                    new File(captureProcessInfo.getDumpFilepath()),
                    new File(captureProcessInfo.getFlirFilepath()),
                    uploadVisibleImage ? new File(captureProcessInfo.getVisibleFilepath()) : null));
        });
    }

    @Override
    public void onDetach() {
        // Avoid memory leaks
        application.flirDeviceDelegate.setListener(null);
        application.flirFrameProcessorDelegate.setListener(null);

        super.onDetach();
    }
}
