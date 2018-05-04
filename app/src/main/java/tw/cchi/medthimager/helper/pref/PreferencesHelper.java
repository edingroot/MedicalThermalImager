package tw.cchi.medthimager.helper.pref;

import android.support.annotation.Nullable;

public interface PreferencesHelper {

//    enum LoggedInMode {
//        LOGGED_OUT,
//        FB,
//        SERVER
//    }

    @Nullable
    String getSelectedPatientUuid();

    void setSelectedPatientUuid(String selectedPatientUuid);

}
