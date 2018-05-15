package tw.cchi.medthimager.helper.pref;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.support.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.di.PreferenceInfo;

@Singleton
public class AppPreferencesHelper implements PreferencesHelper {
    private static final String KEY_SELECTED_PATIENT_UUID = "KEY_SELECTED_PATIENT_UUID";
    private static final String KEY_DEFAULT_VISIBLE_OFFSET_X = "KEY_DEFAULT_VISIBLE_OFFSET_X";
    private static final String KEY_DEFAULT_VISIBLE_OFFSET_Y = "KEY_DEFAULT_VISIBLE_OFFSET_Y";

    private static final String KEY_CLEAR_SPOTS_ON_DISCONNECT = "KEY_CLEAR_SPOTS_ON_DISCONNECT";

    private final SharedPreferences mPrefs;

    @Inject
    public AppPreferencesHelper(@ApplicationContext Context context,
                                @PreferenceInfo String prefFileName) {
        mPrefs = context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public String getSelectedPatientUuid() {
        return mPrefs.getString(KEY_SELECTED_PATIENT_UUID, null);
    }

    @Override
    public void setSelectedPatientUuid(String selectedPatientUuid) {
        mPrefs.edit().putString(KEY_SELECTED_PATIENT_UUID, selectedPatientUuid).apply();
    }

    @Override
    public Point getDefaultVisibleOffset() {
        return new Point(
                mPrefs.getInt(KEY_DEFAULT_VISIBLE_OFFSET_X, Config.INIT_DEFAULT_VISIBLE_OFFSET_X),
                mPrefs.getInt(KEY_DEFAULT_VISIBLE_OFFSET_Y, Config.INIT_DEFAULT_VISIBLE_OFFSET_Y)
        );
    }

    @Override
    public void setDefaultVisibleOffset(Point offset) {
        mPrefs.edit()
                .putInt(KEY_DEFAULT_VISIBLE_OFFSET_X, offset.x)
                .putInt(KEY_DEFAULT_VISIBLE_OFFSET_Y, offset.y)
                .apply();
    }

    @Override
    public boolean getClearSpotsOnDisconnect() {
        return mPrefs.getBoolean(KEY_CLEAR_SPOTS_ON_DISCONNECT, false);
    }

    @Override
    public void setClearSpotsOnDisconnect(boolean enable) {
        mPrefs.edit().putBoolean(KEY_CLEAR_SPOTS_ON_DISCONNECT, enable).apply();
    }

}
