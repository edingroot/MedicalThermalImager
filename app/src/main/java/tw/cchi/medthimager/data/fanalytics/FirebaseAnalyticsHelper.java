package tw.cchi.medthimager.data.fanalytics;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.flir.flironesdk.Device;
import com.google.firebase.analytics.FirebaseAnalytics;

import javax.inject.Inject;

import tw.cchi.medthimager.BuildConfig;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.util.AppUtils;

public class FirebaseAnalyticsHelper {
    private final String TAG = Config.TAGPRE + "FAHelper";

    private Context context;
    private FirebaseAnalytics firebaseAnalytics;

    @Inject
    public FirebaseAnalyticsHelper(@ApplicationContext Context context) {
        this.context = context;
        this.firebaseAnalytics = FirebaseAnalytics.getInstance(context);

        boolean enableAnalytics = !BuildConfig.DEBUG &&
                                  Config.ENABLE_ANALYTICS_COLLECTION &&
                                  !AppUtils.checkIsRunningInFirebaseTestLab(context);

        firebaseAnalytics.setAnalyticsCollectionEnabled(enableAnalytics);
        Log.i(TAG, "Enabled FA collection: " + enableAnalytics);

        if (enableAnalytics) {
            initialize();
        }
    }

    private void initialize() {
        String deviceId = AppUtils.getDeviceId(context);
        String macAddr = AppUtils.getWlanMacAddr();
        Log.i(TAG, String.format("Initializing with %s=%s, %s=%s",
            UserProperty.DEVICE_ID, deviceId,
            UserProperty.WIFI_MAC_ADDR, macAddr));

        firebaseAnalytics.setUserProperty(UserProperty.DEVICE_ID, deviceId);
        firebaseAnalytics.setUserProperty(UserProperty.WIFI_MAC_ADDR, macAddr);
    }

    /**
     * [Warning] Check if analytics collection enabled before sending data.
     *   (disabled if running in test lab)
     */
    public FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    // ------------------------------------- Logging Methods ------------------------------------- //

    public void logSimpleEvent(String name, @Nullable String value) {
        Bundle params = new Bundle();
        params.putString(Param.NAME, name);
        params.putString(Param.VALUE, value);
        logEvent(Event.SIMPLE_EVENT, params);
    }

    public void logCameraConnected(boolean connected) {
        logEvent(connected ? Event.CAMERA_CONNECTED : Event.CAMERA_DISCONNECTED, null);
    }

    public void logConnectSimulatedDevice() {
        logEvent(Event.CONNECT_SIMULATED_DEVICE, null);
    }

    public void logCameraChargingStateChanged(Device.BatteryChargingState batteryChargingState) {
        Bundle params = new Bundle();
        params.putString(Param.STATE, batteryChargingState.toString());
        logEvent(Event.CAMERA_CHARGING_STATE_CHANGED, params);
    }

    public void logTuningStateChanged(Device.TuningState tuningState) {
        Bundle params = new Bundle();
        params.putString(Param.STATE, tuningState.toString());
        logEvent(Event.TUNING_STATE_CHANGED, params);
    }

    public void logAutomaticTuningChanged(boolean enabled) {
        Bundle params = new Bundle();
        params.putBoolean(Param.ENABLED, enabled);
        logEvent(Event.AUTOMATIC_TUNING_CHANGED, params);
    }

    public void logManuallyTune() {
        logEvent(Event.MANUALLY_TUNE, null);
    }

    /**
     * @param contiShootParams should be null if contiShootMode is false
     */
    public void logCameraCapture(boolean contiShootMode, @Nullable ContiShootParameters contiShootParams) {
        Bundle params = new Bundle();
        params.putBoolean(Param.IS_CONTI_SHOOT_MODE, contiShootMode);

        if (contiShootParams != null) {
            params.putInt(Param.CAPTURED_COUNT, contiShootParams.capturedCount);
            params.putInt(Param.TOTAL_CAPTURES, contiShootParams.totalCaptures);
            params.putInt(Param.INTERVAL, contiShootParams.interval);
        }

        logEvent(Event.CAMERA_CAPTURE, params);
    }

    /**
     * @param start true for start, false for finish
     */
    public void logContiShootStart(boolean start, ContiShootParameters contiShootParams) {
        Bundle params = new Bundle();
        if (contiShootParams != null) {
            params.putInt(Param.CAPTURED_COUNT, contiShootParams.capturedCount);
            params.putInt(Param.TOTAL_CAPTURES, contiShootParams.totalCaptures);
            params.putInt(Param.INTERVAL, contiShootParams.interval);
        }
        logEvent(start ? Event.CONTISHOOT_START : Event.CONTISHOOT_FINISH, params);
    }

    public void logTuningWhileContiShoot(ContiShootParameters contiShootParams) {
        Bundle params = new Bundle();
        if (contiShootParams != null) {
            params.putInt(Param.CAPTURED_COUNT, contiShootParams.capturedCount);
            params.putInt(Param.TOTAL_CAPTURES, contiShootParams.totalCaptures);
            params.putInt(Param.INTERVAL, contiShootParams.interval);
        }
        logEvent(Event.TUNING_WHILE_CONTI_SHOOT, params);
    }


    public void logSetCurrentPatient(String patientCuid) {
        Bundle params = new Bundle();
        params.putString(Param.CUID, patientCuid);
        logEvent(Event.SET_CURRENT_PATIENT, params);
    }

    // ------------------------------------- /Logging Methods ------------------------------------- //

    private void logEvent(@NonNull String event, @Nullable Bundle params) {
        firebaseAnalytics.logEvent(Event.PREFIX + event, params);
    }

}
