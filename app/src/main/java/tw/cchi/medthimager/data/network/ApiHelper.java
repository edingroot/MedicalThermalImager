package tw.cchi.medthimager.data.network;

import android.support.annotation.NonNull;

import com.google.gson.Gson;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.model.api.PatientCreateRequest;
import tw.cchi.medthimager.model.api.PatientResponse;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.util.CommonUtils;

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
                    }
                );
        });
    }

    public boolean upSyncPatient(SSPatient ssPatient, String mergeWith, boolean createNew,
                                 boolean blocking, @NonNull OnPatientSyncListener listener) {
        if (!application.checkNetworkAuthedAndAct())
            return false;

        Observable<Response<PatientResponse>> observable = application.getSession().getApiClient()
                .createPatient(new PatientCreateRequest(ssPatient, mergeWith, createNew));

        if (blocking) {
            observable.blockingSubscribe(r -> handleUpSyncPatientResponse(r, listener));
        } else {
            observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(r -> handleUpSyncPatientResponse(r, listener));
        }

        return true;
    }

    private void handleUpSyncPatientResponse(Response<PatientResponse> response, @NonNull OnPatientSyncListener listener) {
        if ((response.code() == 200 || response.code() == 201) && response.body() != null) {
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
