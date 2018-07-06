package tw.cchi.medthimager.service.sync.task;

import android.support.annotation.Nullable;

import java.io.File;

import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.ThImage;
import tw.cchi.medthimager.service.sync.helper.SyncPatientHelper;

public class SyncSingleThImageTask extends SyncTask {
    private static final long DEFAULT_TIMEOUT = 30 * 1000;
    private ThImage metadata;
    private File dumpFile;
    private File flirFile;
    private File visibleFile;

    public SyncSingleThImageTask(ThImage metadata, File dumpFile, File flirFile, @Nullable File visibleFile) {
        super();
        this.timeout = DEFAULT_TIMEOUT;
        this.metadata = metadata;
        this.dumpFile = dumpFile;
        this.flirFile = flirFile;
        this.visibleFile = visibleFile;
    }

    @Override
    void doWork() {
        if (!checkNetworkAndAuthed())
            return;

        if (metadata.getPatient_uuid() == null && !metadata.getPatient_cuid().equals(Patient.DEFAULT_PATIENT_CUID)) {
            new SyncPatientHelper(dataManager, apiHelper).syncPatient(metadata.getPatient_cuid()).blockingSubscribe(patientUuid -> {
                if (!patientUuid.isEmpty()) {
                    performUpload(this.metadata, this.dumpFile, this.flirFile, this.visibleFile);
                } else {
                    showToast(R.string.upload_conflict_syncing_patient);
                    throw new Error(getString(R.string.upload_conflict_syncing_patient));
                }
            });
        } else {
            performUpload(this.metadata, this.dumpFile, this.flirFile, this.visibleFile);
        }
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

    @Override
    public void dispose() {
        disposed = true;
    }
}
