package tw.cchi.medthimager.ui.settings;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.SwitchCompat;
import android.widget.Button;
import android.widget.TextView;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.model.User;
import tw.cchi.medthimager.ui.base.BaseActivity;

public class SettingsActivity extends BaseActivity implements SettingsMvpView {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject SettingsMvpPresenter<SettingsMvpView> presenter;

    @BindView(R.id.txtAuthStatusDescription) TextView txtAuthStatusDescription;
    @BindView(R.id.btnAuth) Button btnAuth;
    @BindView(R.id.swClearSpotsOnDisconn) SwitchCompat swClearSpotsOnDisconn;
    @BindView(R.id.swAutoApplyVisibleOffset) SwitchCompat swAutoApplyVisibleOffset;

    private boolean authenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);
    }

    @OnClick(R.id.btnAuth)
    void onAuthClick() {
        if (authenticated) {
            presenter.logout();
        } else {
            presenter.login();
        }
    }

    @OnCheckedChanged(R.id.swClearSpotsOnDisconn)
    void onSwClearSpotsOnDisconnChanged(SwitchCompat sw) {
        presenter.setClearSpotsOnDisconnect(sw.isChecked());
    }

    @OnCheckedChanged(R.id.swAutoApplyVisibleOffset)
    void onSwAutoApplyVisibleOffsetChanged(SwitchCompat sw) {
        presenter.setAutoSetVisibleOffset(sw.isChecked());
    }

    /**
     * @param user can be null if authenticated is false
     */
    @Override
    public void setAuthState(boolean authenticated, @Nullable User user) {
        this.authenticated = authenticated;
        if (authenticated) {
            if (user != null) {
                txtAuthStatusDescription.setText(getString(R.string.user_brief, user.getName(), user.getEmail()));
            } else {
                txtAuthStatusDescription.setText(getString(R.string.user_brief, "INVALID", "INVALID"));
            }
            btnAuth.setText(R.string.logout);
        } else {
            txtAuthStatusDescription.setText(R.string.unauthenticated);
            btnAuth.setText(R.string.login);
        }
    }

    @Override
    public void setSwClearSpotsOnDisconn(boolean checked) {
        swClearSpotsOnDisconn.setChecked(checked);
    }

    @Override
    public void setSwAutoApplyVisibleOffset(boolean checked) {
        swAutoApplyVisibleOffset.setChecked(checked);
    }

    @Override
    protected void onDestroy() {
        if (presenter != null)
            presenter.onDetach();
        super.onDestroy();
    }
}
