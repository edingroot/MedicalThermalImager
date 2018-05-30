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
public class UpSyncPatientTask extends SyncTask {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private Patient targetPatient;
    private String mergeWithUuid;
    private boolean createNew = false;

    public UpSyncPatientTask(Patient targetPatient) {
        super();
        this.targetPatient = targetPatient;
        this.apiHelper = new ApiHelper(application);
    }

    public UpSyncPatientTask(Patient targetPatient, String mergeWithUuid, boolean createNew) {
        super();
        this.mergeWithUuid = mergeWithUuid;
        this.createNew = createNew;
        this.targetPatient = targetPatient;
        this.apiHelper = new ApiHelper(application);
    }

    @Override
    public void run(SyncService syncService) {
        super.run(syncService);
        if (!checkNetworkAndAuthed()) {
            return;
        }

        if (checkNetworkAndAuthed()) {
            UpSyncPatientTask.this.handleCreatePatient(targetPatient);
        }
    }

    private void handleCreatePatient(Patient patient) {
        if (!application.getSession().isActive()) {
            return;
        }

        SSPatient ssPatient = new SSPatient(patient);
        if (mergeWithUuid != null) {
            ssPatient.setMerge_with(mergeWithUuid);
            ssPatient.setCreate_new(createNew);
        }

        apiHelper.upSyncPatient(ssPatient, new ApiHelper.OnPatientSyncListener() {
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
