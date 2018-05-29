package tw.cchi.medthimager.data.network;

import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.PatientResponse;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.util.CommonUtils;
import tw.cchi.medthimager.util.NetworkUtils;

public class ApiHelper {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject MvpApplication application;

    @Inject
    public ApiHelper(MvpApplication application) {
        this.application = application;
    }

    public Observable<Boolean> refreshUserProfile() {
        if (!application.checkNetworkAuthedAndAct()) {
            return Observable.just(false);
        }

        return Observable.create(emitter -> {
            application.getSession().getApiClient().getProfile()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    (Response<User> response) -> {
                        if (response.code() == 200) {
                            application.getSession().setUser(response.body());
                            emitter.onNext(true);
                        } else {
                            emitter.onNext(false);
                        }
                        emitter.onComplete();
                    },
                    e -> {
                        emitter.onNext(false);
                        emitter.onComplete();
                    },
                    () -> {}
                );
        });
    }

    public boolean syncPatient(SSPatient ssPatient, @NonNull OnPatientSyncListener listener) {
        if (!application.checkNetworkAuthedAndAct())
            return false;

        application.getSession().getApiClient().createPatient(ssPatient)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                (Response<PatientResponse> response) -> {
                    if ((response.code() == 200 || response.code() == 201) && response.body() != null) {
                        Log.i(TAG, "Patient " + ssPatient.getName() + ", 200: " + response.body());
                        listener.onSuccess(response.body().patient);
                    } else if (response.errorBody() != null) {
                        Gson gson = CommonUtils.getGsonInstance();
                        String responseString;
                        PatientResponse patientResponse;

                        try {
                            responseString = response.errorBody().string();
                            patientResponse = gson.fromJson(responseString, PatientResponse.class);
                        } catch (Exception e) {
                            listener.onError(e);
                            return;
                        }

                        Log.e(TAG, String.format("Patient %s, %d: %s", ssPatient.getName(), response.code(), responseString));

                        switch (response.code()) {
                            case 409:
                                listener.onConflictForceMerge(patientResponse.conflicted_patients, patientResponse.message);
                                break;

                            case 412:
                                listener.onConflictCheck(patientResponse.conflicted_patients, patientResponse.message);
                                break;

                            default:
                                listener.onError(new Errors.UnhandledStateError());
                        }
                    }
                },
                listener::onError,
                () -> {}
            );

        return true;
    }

    public interface OnPatientSyncListener {
        void onSuccess(SSPatient ssPatient);

        // 409
        void onConflictForceMerge(List<SSPatient> conflictPatients, String message);

        // 412
        void onConflictCheck(List<SSPatient> conflictPatients, String message);

        void onError(Throwable error);
    }
}
