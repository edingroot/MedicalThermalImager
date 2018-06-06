package tw.cchi.medthimager.service.sync.task;

import android.support.annotation.Nullable;

import java.io.File;
import java.util.List;

import io.reactivex.Observable;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.model.api.ThImage;

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
            syncPatient(metadata.getPatient_cuid()).blockingSubscribe(success -> {
                if (success) {
                    performUpload();
                } else {
                    showToast(R.string.upload_conflict_syncing_patient);
                    throw new Error(getString(R.string.upload_conflict_syncing_patient));
                }
            });
        } else {
            performUpload();
        }
    }

    private Observable<Boolean> syncPatient(String patientCuid) {
        return Observable.create(emitter -> {
            // Patient not yet synced
            Patient patient = dataManager.db.patientDAO().get(patientCuid);
            apiHelper.upSyncPatient(new SSPatient(patient), null, false,
                true, new ApiHelper.OnPatientSyncListener() {
                    @Override
                    public void onSuccess(SSPatient ssPatient) {
                        metadata.setPatient_uuid(ssPatient.getUuid());
                        emitter.onNext(true);
                        emitter.onComplete();
                    }

                    @Override
                    public void onConflictForceMerge(List<SSPatient> conflictPatients, String message) {
                        emitter.onNext(false);
                        emitter.onComplete();
                    }

                    @Override
                    public void onConflictCheck(List<SSPatient> conflictPatients, String message) {
                        emitter.onNext(false);
                        emitter.onComplete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        emitter.onNext(false);
                        emitter.onComplete();
                    }
                });
        });
    }

    private void performUpload() {
        if (disposed)
            return;

        apiHelper.uploadThImage(this.metadata, this.dumpFile, this.flirFile, this.visibleFile,
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
