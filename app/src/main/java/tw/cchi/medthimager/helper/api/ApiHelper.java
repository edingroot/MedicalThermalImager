package tw.cchi.medthimager.helper.api;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.helper.session.Session;
import tw.cchi.medthimager.model.User;

public class ApiHelper {
    @Inject MvpApplication application;

    @Inject
    public ApiHelper(MvpApplication application) {
        this.application = application;
    }

    public Observable<Boolean> refreshUserProfile() {
        Session currentSession = application.getSession();
        if (application.authedApiClient == null || !currentSession.isActive()) {
            return Observable.just(false);
        }

        return Observable.create(emitter -> {
            application.authedApiClient.getProfile()
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

}
