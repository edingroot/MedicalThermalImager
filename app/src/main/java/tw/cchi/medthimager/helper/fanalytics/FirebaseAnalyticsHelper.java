package tw.cchi.medthimager.helper.fanalytics;

import android.content.Context;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.utils.AppUtils;

public class FirebaseAnalyticsHelper {
    private final String TAG = Config.TAGPRE + "FAHelper";

    private Context context;
    private FirebaseAnalytics mFirebaseAnalytics;

    public FirebaseAnalyticsHelper(Context context, FirebaseAnalytics firebaseAnalytics) {
        this.context = context;
        this.mFirebaseAnalytics = firebaseAnalytics;
        initialize();
    }

    private void initialize() {
        String deviceId = AppUtils.getDeviceId(context);
        String macAddr = AppUtils.getWlanMacAddr();
        Log.i(TAG, String.format("Initializing with %s=%s, %s=%s",
            UserProperty.DEVICE_ID, deviceId,
            UserProperty.WIFI_MAC_ADDR, macAddr
        ));

        mFirebaseAnalytics.setUserProperty(UserProperty.DEVICE_ID, deviceId);
        mFirebaseAnalytics.setUserProperty(UserProperty.WIFI_MAC_ADDR, macAddr);
    }

    public FirebaseAnalytics getFirebaseAnalytics() {
        return mFirebaseAnalytics;
    }


//    public void logSomeEvent() {
//        Bundle bundle = new Bundle();
//        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id);
//        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, name);
//        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image");
//        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
//    }

}
