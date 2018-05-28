package tw.cchi.medthimager.service.sync.task;

import android.util.Log;

import com.google.gson.Gson;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.db.AppDatabase;
import tw.cchi.medthimager.db.model.Patient;
import tw.cchi.medthimager.model.api.PatientResponse;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.util.CommonUtils;
import tw.cchi.medthimager.util.NetworkUtils;

/**
 * Sync all patients whose uuid field is null.
 */
public class SyncPatientsTask extends SyncTask {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private AppDatabase database;
    private Disposable uploadTask;

    private static final Gson gson = CommonUtils.getGsonInstance();

    public SyncPatientsTask(MvpApplication application, AppDatabase database) {
        super(application);
        this.database = database;
    }

    @Override
    public void run() {
        if (!NetworkUtils.isNetworkConnected(application)) {
            finish(new NetworkLostError());
        } else if (application.authedApiClient == null) {
            finish(new UnauthenticatedError());
        } else {
            showToast(application.getString(R.string.syncing_patient_list));
            syncPatients();
        }
    }

    private void syncPatients() {
        List<Patient> patients = database.patientDAO().findNullUuids();

        uploadTask = Observable.fromIterable(patients)
            .subscribe(
                patient -> {
                    SyncPatientsTask.this.handleCreatePatient(patient);
                    Thread.sleep(500);
                },
                Throwable::printStackTrace,
                () -> {
                    showToast(application.getString(R.string.syncing_patient_list_done));
                    finish();
                }
            );
    }

    private void handleCreatePatient(Patient patient) {
        if (application.authedApiClient == null) {
            uploadTask.dispose();
        }

        SSPatient ssPatient = SSPatient.fromLocalPatient(patient);
        application.authedApiClient.createPatient(ssPatient)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                (Response<PatientResponse> response) -> {
                    if ((response.code() == 200 || response.code() == 201) && response.body() != null) {
                        Log.i(TAG, "Patient " + patient.getName() + ", 200: " + response.body());
                        updateAfterCreated(patient, response.body().patient);
                    } else {
                        handleErrorResponse(patient, response);
                    }
                },
                Throwable::printStackTrace,
                () -> {}
            );
    }

    private void handleErrorResponse(Patient patient, Response response) {
        String responseString;
        PatientResponse patientResponse;

        try {
            responseString = response.errorBody().string();
            patientResponse = gson.fromJson(responseString, PatientResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Log.i(TAG, String.format("Patient %s, %d: %s", patient.getName(), response.code(), responseString));

        switch (response.code()) {
            case 409:
                // TODO
                break;

            case 412:
                // TODO
                break;

            default:
                // ignore
        }
    }

    private void updateAfterCreated(Patient patient, SSPatient ssPatient) {
        patient.setUuid(ssPatient.getUuid());
        database.patientDAO().updateAll(patient);
    }

    @Override
    public void dispose() {
        if (uploadTask != null)
            uploadTask.dispose();
        disposed = true;
    }
}
