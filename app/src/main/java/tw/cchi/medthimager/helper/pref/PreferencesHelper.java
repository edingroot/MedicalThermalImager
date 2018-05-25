package tw.cchi.medthimager.helper.pref;

import android.graphics.Point;
import android.support.annotation.Nullable;

import tw.cchi.medthimager.model.api.AccessTokens;
import tw.cchi.medthimager.model.User;

public interface PreferencesHelper {

    // ------------------------- Authentication ------------------------- //

    boolean isAuthenticated();

    void setAuthenticated(boolean authenticated);

    @Nullable
    AccessTokens getAccessTokens();

    void setAccessTokens(@Nullable AccessTokens accessTokens);

    @Nullable
    User getUser();

    void setUser(@Nullable User user);

    // ------------------------- States ------------------------- //

    @Nullable
    String getSelectedPatientUuid();

    void setSelectedPatientUuid(String selectedPatientUuid);


    // ------------------------- Settings ------------------------- //

    boolean getAutoApplyVisibleOffsetEnabled();

    void setAutoApplyVisibleOffsetEnabled(boolean enabled);

    Point getDefaultVisibleOffset();

    void setDefaultVisibleOffset(Point offset);

    boolean getClearSpotsOnDisconnectEnabled();

    void setClearSpotsOnDisconnect(boolean enable);

}
