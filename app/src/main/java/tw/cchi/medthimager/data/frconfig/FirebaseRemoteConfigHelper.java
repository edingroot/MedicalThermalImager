package tw.cchi.medthimager.data.frconfig;

import android.content.Context;
import android.util.Log;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.util.AppUtils;

public class FirebaseRemoteConfigHelper {
    private final String TAG = Config.TAGPRE + "FRCHelper";

    // Keys
    private static final String KEY_LATEST_VERSION_CODE = "latest_version_code";
    private static final String KEY_LATEST_VERSION_NAME = "latest_version_name";

    private Context context;
    private FirebaseRemoteConfig firebaseRemoteConfig;

    @Inject
    public FirebaseRemoteConfigHelper(@ApplicationContext Context context) {
        this.context = context;
        this.firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        setDefaults();
        fetchConfigs();
    }

    private void setDefaults() {
        Map<String, Object> defaults = new HashMap<>();

        defaults.put(KEY_LATEST_VERSION_CODE, AppUtils.getVersionCode(context));
        defaults.put(KEY_LATEST_VERSION_NAME, AppUtils.getVersionName(context));

        firebaseRemoteConfig.setDefaults(defaults);
    }

    private Observable<Boolean> fetchConfigs() {
        return Observable.<Boolean>create(emitter -> {
            firebaseRemoteConfig.fetch(Config.REMOTE_CONFIG_CACHE_EXPIRATION)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // After config data is successfully fetched,
                            // it must be activated before newly fetched values are returned.
                            firebaseRemoteConfig.activateFetched();
                            Log.i(TAG, "Fetch config successful");
                        } else {
                            Log.i(TAG, "Fetch config failed");
                            task.getException().printStackTrace();
                        }

                        emitter.onNext(task.isSuccessful());
                        emitter.onComplete();
                    });
        }).subscribeOn(Schedulers.io());
    }


    public int getLatestVersionCode() {
        fetchConfigs().subscribe();
        return (int) firebaseRemoteConfig.getLong(KEY_LATEST_VERSION_CODE);
    }

    public String getLatestVersionName() {
        fetchConfigs().subscribe();
        return firebaseRemoteConfig.getString(KEY_LATEST_VERSION_NAME);
    }

}
