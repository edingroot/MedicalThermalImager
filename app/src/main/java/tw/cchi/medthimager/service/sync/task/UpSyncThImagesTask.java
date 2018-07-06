package tw.cchi.medthimager.service.sync.task;

import java.io.File;
import java.util.List;

import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.ThImage;
import tw.cchi.medthimager.service.sync.helper.SyncPatientHelper;
import tw.cchi.medthimager.util.CommonUtils;

public class UpSyncThImagesTask extends SyncTask {
    private static final long DEFAULT_TIMEOUT = 120 * 1000;
    private boolean doneBroadcastSent = false;

    public UpSyncThImagesTask() {
        super();
        this.timeout = DEFAULT_TIMEOUT;
    }

    @Override
    void doWork() {
        if (!checkNetworkAndAuthed())
            return;

        List<CaptureRecord> captureRecords = dataManager.db.captureRecordDAO().getSyncList();
        for (CaptureRecord record : captureRecords) {
            if (disposed) break;

            Patient patient = dataManager.db.patientDAO().getOrDefault(record.getPatientCuid());
            ThImage metadata = new ThImage(record.getUuid(), patient, record.getContishootGroup(),
                    record.getTitle(), record.getCreatedAt());

            // Check if files exist, if not, skip upload and delete record
            File dumpFile = new File(record.getFilenamePrefix() + Constants.POSTFIX_THERMAL_DUMP + ".dat");
            File flirFile = new File(record.getFilenamePrefix() + Constants.POSTFIX_FLIR_IMAGE + ".jpg");
            File visibleFile = new File(record.getFilenamePrefix() + Constants.POSTFIX_VISIBLE_IMAGE + ".png");
            if (!dumpFile.exists() || !flirFile.exists() || !visibleFile.exists()) {
                dataManager.db.captureRecordDAO().delete(record);
                continue;
            }

            // Sync patient if needed
            if (metadata.getPatient_uuid() == null && !metadata.getPatient_cuid().equals(Patient.DEFAULT_PATIENT_CUID)) {
                new SyncPatientHelper(dataManager, apiHelper).syncPatient(metadata.getPatient_cuid()).blockingSubscribe(patientUuid -> {
                    if (!patientUuid.isEmpty()) {
                        performUpload(metadata, dumpFile, flirFile, visibleFile);
                    } else {
                        // Ignore error
                        // showToast(R.string.upload_conflict_syncing_patient);
                        // throw new Error(getString(R.string.upload_conflict_syncing_patient));
                    }
                });
            } else {
                performUpload(metadata, dumpFile, flirFile, visibleFile);
            }
            
            // Short sleep between each upload
            CommonUtils.sleep(300);
        }

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
