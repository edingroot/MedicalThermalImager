package tw.cchi.medthimager.service.sync.task;

import java.util.List;

import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;

/**
 * Sync information of a patient.
 */
public class SyncSinglePatientTask extends SyncTask {
    private static final long DEFAULT_TIMEOUT = 8 * 1000;
    private Patient targetPatient;
    private String mergeWithUuid;
    private boolean createNew = false;

    public SyncSinglePatientTask(Patient targetPatient) {
        super();
        this.timeout = DEFAULT_TIMEOUT;
        this.targetPatient = targetPatient;
        this.apiHelper = new ApiHelper(application);
    }

    public SyncSinglePatientTask(Patient targetPatient, String mergeWithUuid, boolean createNew) {
        super();
        this.mergeWithUuid = mergeWithUuid;
        this.createNew = createNew;
        this.targetPatient = targetPatient;
        this.apiHelper = new ApiHelper(application);
    }

    @Override
    void doWork() {
        if (!checkNetworkAndAuthed())
            return;

        handleCreatePatient(targetPatient);
    }

    private void handleCreatePatient(Patient patient) {
        SSPatient ssPatient = new SSPatient(patient);

        apiHelper.upSyncPatient(ssPatient, mergeWithUuid, createNew, true, new ApiHelper.OnPatientSyncListener() {
            @Override
            public void onSuccess(SSPatient ssPatient) {
                patient.setSsuuid(ssPatient.getUuid());
                dataManager.db.patientDAO().updateAll(patient);
            }

            @Override
            public void onConflictForceMerge(List<SSPatient> conflictPatients, String message) {
                broadcastSender.sendSyncPatientConflict(SyncBroadcastSender.ConflictType.FORCE_MERGE, patient, conflictPatients);
            }

            @Override
            public void onConflictCheck(List<SSPatient> conflictPatients, String message) {
                broadcastSender.sendSyncPatientConflict(SyncBroadcastSender.ConflictType.CHECK, patient, conflictPatients);
            }

            @Override
            public void onError(Throwable error) {
                showToast(R.string.error_occurred);
            }
        });
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
