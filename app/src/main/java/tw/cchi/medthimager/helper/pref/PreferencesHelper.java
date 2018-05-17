package tw.cchi.medthimager.helper.pref;

import android.graphics.Point;
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

    boolean getAutoApplyVisibleOffsetEnabled();

    void setAutoApplyVisibleOffsetEnabled(boolean enabled);

    Point getDefaultVisibleOffset();

    void setDefaultVisibleOffset(Point offset);

    boolean getClearSpotsOnDisconnectEnabled();

    void setClearSpotsOnDisconnect(boolean enable);

}
