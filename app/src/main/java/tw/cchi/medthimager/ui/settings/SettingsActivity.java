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
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.model.api.User;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.ui.base.BaseActivity;

public class SettingsActivity extends BaseActivity implements SettingsMvpView {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject SettingsMvpPresenter<SettingsMvpView> presenter;

    private CompositeDisposable broadcastSubs;

    @BindView(R.id.txtAuthStatusDescription) TextView txtAuthStatusDescription;
    @BindView(R.id.btnAuth) Button btnAuth;
    @BindView(R.id.swClearSpotsOnDisconn) SwitchCompat swClearSpotsOnDisconn;
    @BindView(R.id.swAutoApplyVisibleOffset) SwitchCompat swAutoApplyVisibleOffset;
    @BindView(R.id.txtSyncPatientsStatus) TextView txtSyncPatientsStatus;
    @BindView(R.id.txtSyncThImagesStatus) TextView txtSyncThImagesStatus;

    private boolean authenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        broadcastSubs = new CompositeDisposable();
        broadcastSubs.add(internalBroadcastEventPub
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> {
                    if (pair.first.equals(SyncBroadcastSender.EventName.SYNC_PATIENTS_DONE)) {
                        presenter.onSyncPatientsDone();
                    } else if (pair.first.equals(SyncBroadcastSender.EventName.SYNC_THIMAGES_DONE)) {
                        presenter.onSyncThImagesDone();
                    }
                }));
    }

    @OnClick(R.id.btnAuth)
    void onAuthClick() {
        if (authenticated) {
            presenter.logout();
        } else {
            presenter.login();
        }
    }

    @OnClick(R.id.btnSyncPatients)
    void onSyncPatientsClick() {
        presenter.syncPatients();
    }

    @OnClick(R.id.btnSyncThImages)
    void onSyncThImagesClick() {
        presenter.syncThImages();
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
    public void setSyncPatientsStatus(boolean syncing, String lastSynced) {
        if (syncing) {
            txtSyncPatientsStatus.setText(R.string.syncing);
        } else {
            txtSyncPatientsStatus.setText(getString(R.string.last_sync, lastSynced));
        }
    }

    @Override
    public void setSyncThImagesStatus(boolean syncing, String lastSynced) {
        if (syncing) {
            txtSyncThImagesStatus.setText(R.string.syncing);
        } else {
            txtSyncThImagesStatus.setText(getString(R.string.last_sync, lastSynced));
        }
    }

    @Override
    protected void onStop() {
        broadcastSubs.dispose();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (presenter != null)
            presenter.onDetach();
        super.onDestroy();
    }
}
