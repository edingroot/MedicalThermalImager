package tw.cchi.medthimager.data.network;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;
import tw.cchi.medthimager.Errors;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.model.api.PatientCreateRequest;
import tw.cchi.medthimager.model.api.PatientResponse;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.model.api.ThImage;
import tw.cchi.medthimager.model.api.ThImageResponse;
import tw.cchi.medthimager.model.api.User;
import tw.cchi.medthimager.util.CommonUtils;

public class ApiHelper {
    private Gson gson;

    @Inject MvpApplication application;

    @Inject
    public ApiHelper(MvpApplication application) {
        this.application = application;
        this.gson = CommonUtils.getGsonInstance();
    }

    public Observable<Boolean> refreshUserProfile() {
        if (!application.checkNetworkAuthed(false)) {
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

    /**
     * Note: error thrown by retrofit will not be handled.
     */
    public boolean upSyncPatient(SSPatient ssPatient, String mergeWith, boolean createNew,
                                 boolean blocking, @NonNull OnPatientSyncListener listener) {
        if (!application.checkNetworkAuthed(false))
            return false;

        Observable<Response<PatientResponse>> observable = application.getSession().getApiClient()
                .createPatient(new PatientCreateRequest(ssPatient, mergeWith, createNew));

        if (blocking) {
            observable.blockingSubscribe(r -> handleUpSyncPatientResponse(r, listener), listener::onError);
        } else {
            observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(r -> handleUpSyncPatientResponse(r, listener), listener::onError);
        }

        return true;
    }

    public boolean uploadThImage(ThImage metadata, File dumpFile, File flirFile,
                                             @Nullable File visibleFile, boolean blocking,
                                             @NonNull OnThImageUploadListener listener) {
        if (!application.checkNetworkAuthed(false))
            return false;
        
        Map<String, RequestBody> partMap = new HashMap<>();
        MediaType mediaType = MediaType.parse("application/octet-stream");

        partMap.put("dump_file\"; filename=\"" + dumpFile.getName(), RequestBody.create(mediaType, dumpFile));
        partMap.put("flir_file\"; filename=\"" + flirFile.getName(), RequestBody.create(mediaType, flirFile));
        if (visibleFile != null)
            partMap.put("visible_file\"; filename=\"" + visibleFile.getName(), RequestBody.create(mediaType, visibleFile));

        Observable<Response<ThImageResponse>> observable =
                application.getSession().getApiClient().uploadThImage(metadata, partMap);

        if (blocking) {
            observable.blockingSubscribe(r -> handleUploadThImageResponse(r, listener), listener::onError);
        } else {
            observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(r -> handleUploadThImageResponse(r, listener), listener::onError);
        }

        return true;
    }


    private void handleUpSyncPatientResponse(Response<PatientResponse> response, @NonNull OnPatientSyncListener listener) {
        if ((response.code() == 200 || response.code() == 201) && response.body() != null) {
            listener.onSuccess(response.body().patient);
        } else if (response.errorBody() != null) {
            PatientResponse patientResponse;

            try {
                patientResponse = gson.fromJson(response.errorBody().string(), PatientResponse.class);
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
                    listener.onError(new Errors.UnhandledStateError(patientResponse.message));
            }
        }
    }

    private void handleUploadThImageResponse(Response<ThImageResponse> response, @NonNull OnThImageUploadListener listener) {
        if (response.code() == 201 && response.body() != null) {
            listener.onSuccess(response.body().thImage);
        } else if (response.errorBody() != null) {
            ThImageResponse thImageResponse;
            try {
                thImageResponse = gson.fromJson(response.errorBody().string(), ThImageResponse.class);
            } catch (Exception e) {
                listener.onError(e);
                return;
            }
            listener.onError(new Errors.UnhandledStateError(thImageResponse.message));
        } else {
            listener.onError(new Errors.UnhandledStateError());
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

    public interface OnThImageUploadListener {
        void onSuccess(ThImage thImage);

        void onError(Throwable error);
    }
}
