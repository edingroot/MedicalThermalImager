package tw.cchi.medthimager.service.sync.task;

import android.util.Log;

import java.io.File;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.di.component.DaggerServiceComponent;
import tw.cchi.medthimager.di.component.ServiceComponent;
import tw.cchi.medthimager.helper.ThImagesHelper;
import tw.cchi.medthimager.model.api.ThImage;
import tw.cchi.medthimager.service.sync.helper.SyncPatientHelper;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.util.CommonUtils;

public class UpSyncThImagesTask extends SyncTask {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();
    private static final long DEFAULT_TIMEOUT = 10 * 60 * 1000;

    private boolean doneBroadcastSent = false;

    @Inject ThImagesHelper thImagesHelper;

    public UpSyncThImagesTask() {
        super();
        this.timeout = DEFAULT_TIMEOUT;
    }

    @Override
    void inject() {
        super.inject();
        ServiceComponent component = DaggerServiceComponent.builder()
                .applicationComponent(application.getComponent())
                .build();
        component.inject(this);
    }

    @Override
    void doWork() {
        if (!checkNetworkAndAuthed())
            return;

        dataManager.pref.setLastSyncThImages(new Date());

        List<CaptureRecord> captureRecords = dataManager.db.captureRecordDAO().getSyncList();
        for (CaptureRecord record : captureRecords) {
            if (disposed) break;

            Patient patient = dataManager.db.patientDAO().getOrDefault(record.getPatientCuid());
            ThImage metadata = new ThImage(record.getUuid(), patient, record.getContishootGroup(),
                    record.getTitle(), record.getCreatedAt());

            // Check if files exist, if not, skip upload and delete record
            File dumpFile = new File(record.getFilepathPrefix() + Constants.POSTFIX_THERMAL_DUMP + ".dat");
            File flirFile = new File(record.getFilepathPrefix() + Constants.POSTFIX_FLIR_IMAGE + ".jpg");
            File visibleFile = new File(record.getFilepathPrefix() + Constants.POSTFIX_VISIBLE_IMAGE + ".png");
            if (!dumpFile.exists() || !flirFile.exists()) {
                Log.e(TAG, "Dump record: " + record.getFilepathPrefix() +
                        ", either dump file or flir file not exist, delete record from local database.");
                dataManager.db.captureRecordDAO().delete(record);
                continue;
            }

            Observable.<Boolean>create(emitter -> {
                if (!visibleFile.exists()) {
                    thImagesHelper.extractVisibleImage(RawThermalDump.readFromDumpFile(dumpFile.getAbsolutePath()))
                        .blockingSubscribe(emitter::onNext);
                } else {
                    emitter.onNext(true);
                }
                emitter.onComplete();
            }).blockingSubscribe(success -> {
                if (success) {
                    // Sync patient if needed
                    if (metadata.getPatient_uuid() == null && !metadata.getPatient_cuid().equals(Patient.DEFAULT_PATIENT_CUID)) {
                        new SyncPatientHelper(dataManager, apiHelper).syncPatient(metadata.getPatient_cuid()).blockingSubscribe(patientUuid -> {
                            if (!patientUuid.isEmpty()) {
                                performUpload(metadata, dumpFile, flirFile, visibleFile);
                            }
                            // Ignore error
                            /* else {
                                showToast(R.string.upload_conflict_syncing_patient);
                                throw new Error(getString(R.string.upload_conflict_syncing_patient));
                            } */
                        });
                    } else {
                        performUpload(metadata, dumpFile, flirFile, visibleFile);
                    }

                    // Short sleep between each upload
                    CommonUtils.sleep(200);
                }
            });
        }

        dataManager.pref.setLastSyncThImages(new Date());
        sendSyncDone();
    }

    private void performUpload(ThImage metadata, File dumpFile, File flirFile, File visibleFile) {
        if (disposed)
            return;

        apiHelper.uploadThImage(metadata, dumpFile, flirFile, visibleFile,
                true, new ApiHelper.OnThImageUploadListener() {
                    @Override
                    public void onSuccess(ThImage thImage) {
                        // Update local db
                        CaptureRecord captureRecord = dataManager.db.captureRecordDAO().get(metadata.getUuid());
                        captureRecord.setSynced(true);
                        dataManager.db.captureRecordDAO().update(captureRecord);
                    }

                    @Override
                    public void onError(Throwable error) {
                        // ignore
                    }
                });
    }

    private void sendSyncDone() {
        if (!doneBroadcastSent) {
            broadcastSender.sendSyncThImagesDone();
            doneBroadcastSent = true;
        }
    }

    @Override
    public void dispose() {
        sendSyncDone();
        disposed = true;
    }
}
