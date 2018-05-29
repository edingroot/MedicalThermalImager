package tw.cchi.medthimager.service.sync.task;

import java.util.List;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.SyncService;

/**
 * Sync information of a patient.
 */
public class SyncPatientTask extends SyncTask {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private ApiHelper apiHelper;
    private Patient targetPatient;
    private String mergeWith;
    private boolean createNew = false;

    public SyncPatientTask(SyncService syncService, Patient targetPatient) {
        super(syncService);
        this.targetPatient = targetPatient;
        this.apiHelper = new ApiHelper(application);
    }

    public SyncPatientTask(SyncService syncService, Patient targetPatient, String mergeWith, boolean createNew) {
        super(syncService);
        this.mergeWith = mergeWith;
        this.createNew = createNew;
        this.targetPatient = targetPatient;
        this.apiHelper = new ApiHelper(application);
    }

    @Override
    public void run() {
        if (!checkNetworkAndAuthed())
            return;

        SyncPatientTask.this.handleCreatePatient(targetPatient);
    }

    private void handleCreatePatient(Patient patient) {
        if (!application.getSession().isActive()) {
            return;
        }

        SSPatient ssPatient = SSPatient.fromLocalPatient(patient);
        if (mergeWith != null) {
            ssPatient.setMerge_with(mergeWith);
            ssPatient.setCreate_new(createNew);
        }

        apiHelper.syncPatient(ssPatient, new ApiHelper.OnPatientSyncListener() {
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
