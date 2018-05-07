package tw.cchi.medthimager.ui.settings;

import android.os.Bundle;

import tw.cchi.medthimager.R;
import tw.cchi.medthimager.ui.base.BaseActivity;

public class SettingsActivity extends BaseActivity implements SettingsMvpView {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

}
