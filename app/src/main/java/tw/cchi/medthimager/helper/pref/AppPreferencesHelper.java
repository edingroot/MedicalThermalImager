package tw.cchi.medthimager.helper.pref;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.di.PreferenceInfo;

@Singleton
public class AppPreferencesHelper implements PreferencesHelper {
    private static final String KEY_SELECTED_PATIENT_UUID = "KEY_SELECTED_PATIENT_UUID";

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

}