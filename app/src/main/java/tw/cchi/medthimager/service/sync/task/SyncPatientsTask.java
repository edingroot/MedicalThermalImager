package tw.cchi.medthimager.service.sync.task;

import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.util.CommonUtils;

/**
 * Sync all patients whose uuid field is null.
 */
public class SyncPatientsTask extends SyncTask {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private int conflictCount = 0;
    private Disposable uploadTask;
    private Disposable downloadTask;

    public SyncPatientsTask() {
        super();
    }

    @Override
    public void run(SyncService syncService) {
        super.run(syncService);
        if (!checkNetworkAndAuthed()) {
            broadcastSender.sendSyncPatientsDone();
            return;
        }

        upSyncPatients();
        downSyncPatients();

        CommonUtils.sleep(500);
        broadcastSender.sendSyncPatientsDone();
    }

    private void upSyncPatients() {
        conflictCount = 0;
        dataManager.pref.setSyncPatientConflictCount(0);

        List<Patient> patients = dataManager.db.patientDAO().getSyncList();

        uploadTask = Observable.fromIterable(patients)
            .subscribe(
                patient -> {
                    SyncPatientsTask.this.handleCreatePatient(patient);
                    // Short sleep between each upload
                    Thread.sleep(500);
                },
                e -> {},
                () -> {
                    dataManager.pref.setLastSyncPatients(new Date());
                    dataManager.pref.setSyncPatientConflictCount(conflictCount);
                    finish();
                }
            );
    }

    private void handleCreatePatient(Patient patient) {
        if (!checkNetworkAndAuthed())
            return;

        SSPatient ssPatient = new SSPatient(patient);
        apiHelper.upSyncPatient(ssPatient, new ApiHelper.OnPatientSyncListener() {
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
        if (!checkNetworkAndAuthed())
            return;

        downloadTask = application.getSession().getApiClient().getAllPatients()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                    (Response<List<SSPatient>> response) -> {
                        if (response.code() == 200 && response.body() != null) {
                            handleRemotePatientList(response.body());
                        }
                    },
                    e -> {}
                );
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
        if (uploadTask != null)
            uploadTask.dispose();

        if (downloadTask != null)
            downloadTask.dispose();

        disposed = true;
    }
}
