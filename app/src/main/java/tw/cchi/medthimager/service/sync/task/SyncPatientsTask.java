package tw.cchi.medthimager.service.sync.task;

import java.util.Date;
import java.util.List;

import retrofit2.Response;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.util.CommonUtils;

/**
 * Sync all patients whose uuid field is null.
 */
public class SyncPatientsTask extends SyncTask {
    private int conflictCount = 0;

    public SyncPatientsTask() {
        super();
    }

    @Override
    void doWork() {
        if (!checkNetworkAndAuthed()) {
            broadcastSender.sendSyncPatientsDone();
            return;
        }

        upSyncPatients();
        downSyncPatients();

        dataManager.pref.setLastSyncPatients(new Date());
        CommonUtils.sleep(500);
        broadcastSender.sendSyncPatientsDone();
    }

    private void upSyncPatients() {
        conflictCount = 0;
        dataManager.pref.setSyncPatientConflictCount(0);

        List<Patient> patients = dataManager.db.patientDAO().getSyncList();
        for (Patient patient : patients) {
            if (disposed) break;
            handleCreatePatient(patient);
            // Short sleep between each upload
            CommonUtils.sleep(300);
        }

        dataManager.pref.setSyncPatientConflictCount(conflictCount);
    }

    private void handleCreatePatient(Patient patient) {
        if (disposed || !checkNetworkAndAuthed())
            return;

        SSPatient ssPatient = new SSPatient(patient);
        apiHelper.upSyncPatient(ssPatient, true, new ApiHelper.OnPatientSyncListener() {
            @Override
            public void onSuccess(SSPatient ssPatient) {
                patient.setSsuuid(ssPatient.getUuid());
                dataManager.db.patientDAO().updateAll(patient);
            }

            @Override
            public void onConflictForceMerge(List<SSPatient> conflictPatients, String message) {
                conflictCount++;
                broadcastSender.sendSyncPatientConflict(SyncBroadcastSender.ConflictType.FORCE_MERGE, patient, conflictPatients);
            }

            @Override
            public void onConflictCheck(List<SSPatient> conflictPatients, String message) {
                conflictCount++;
                broadcastSender.sendSyncPatientConflict(SyncBroadcastSender.ConflictType.CHECK, patient, conflictPatients);
            }

            @Override
            public void onError(Throwable error) {
                // ignore
            }
        });
    }

    private void downSyncPatients() {
        if (disposed || !checkNetworkAndAuthed())
            return;

        application.getSession().getApiClient().getAllPatients()
            .blockingSubscribe(
                (Response<List<SSPatient>> response) -> {
                    if (response.code() == 200 && response.body() != null) {
                        handleRemotePatientList(response.body());
                    }
                },
                e -> {});
    }

    private void handleRemotePatientList(List<SSPatient> ssPatients) {
        for (SSPatient ssPatient : ssPatients) {
            if (dataManager.db.patientDAO().getCuidBySsuuid(ssPatient.getUuid()) == null) {
                dataManager.db.patientDAO().insertAll(new Patient(ssPatient));
            }
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
