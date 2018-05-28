package tw.cchi.medthimager.service.sync.task;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.util.NetworkUtils;

/**
 * Sync all patients whose uuid field is null.
 */
public class SyncPatientsTask extends SyncTask {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private ApiHelper apiHelper;
    private int conflictCount = 0;
    private Disposable uploadTask;

    public SyncPatientsTask(SyncService syncService, MvpApplication application) {
        super(syncService, application);
        this.apiHelper = new ApiHelper(application);
    }

    @Override
    public void run() {
        if (!NetworkUtils.isNetworkConnected(application)) {
            finish(new Errors.NetworkLostError());
        } else if (!application.getSession().isActive()) {
            finish(new Errors.UnauthenticatedError());
        } else {
            showToast(application.getString(R.string.syncing_patient_list));
            syncPatients();
        }
    }

    private void syncPatients() {
        conflictCount = 0;
        dataManager.pref.setSyncPatientConflictCount(0);

        List<Patient> patients = dataManager.db.patientDAO().getSyncList();

        uploadTask = Observable.fromIterable(patients)
            .subscribe(
                patient -> {
                    SyncPatientsTask.this.handleCreatePatient(patient);
                    Thread.sleep(500);
                },
                Throwable::printStackTrace,
                () -> {
                    dataManager.pref.setSyncPatientConflictCount(conflictCount);
                    showToast(application.getString(R.string.syncing_patient_list_done));
                    finish();
                }
            );
    }

    private void handleCreatePatient(Patient patient) {
        if (!application.getSession().isActive()) {
            uploadTask.dispose();
            return;
        }

        SSPatient ssPatient = SSPatient.fromLocalPatient(patient);
        apiHelper.syncPatient(ssPatient, new ApiHelper.OnPatientSyncListener() {
            @Override
            public void onSuccess(SSPatient ssPatient) {
                patient.setSsuuid(ssPatient.getUuid());
                dataManager.db.patientDAO().updateAll(patient);
            }

            @Override
            public void onConflictStrict(List<SSPatient> conflictPatients, String message) {
                conflictCount++;
                broadcastSender.sendSyncPatientConflict(SyncBroadcastSender.ConflictType.STRICT, patient, conflictPatients);
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

    @Override
    public void dispose() {
        if (uploadTask != null)
            uploadTask.dispose();
        disposed = true;
    }
}
