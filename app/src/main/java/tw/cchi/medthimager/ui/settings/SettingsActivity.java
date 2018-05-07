package tw.cchi.medthimager.ui.settings;

import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.ui.base.BaseActivity;

public class SettingsActivity extends BaseActivity implements SettingsMvpView {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject SettingsMvpPresenter<SettingsMvpView> presenter;

    @BindView(R.id.swClearSpotsOnDisconn) SwitchCompat swClearSpotsOnDisconn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);
    }

    @OnCheckedChanged(R.id.swClearSpotsOnDisconn)
    void onSwClearSpotsOnDisconnChanged(SwitchCompat sw) {
        presenter.setClearSpotsOnDisconnect(sw.isChecked());
    }

    @Override
    public void setSwClearSpotsOnDisconnChanged(boolean checked) {
        swClearSpotsOnDisconn.setChecked(checked);
    }

    @Override
    protected void onDestroy() {
        presenter.onDetach();
        super.onDestroy();
    }
}
