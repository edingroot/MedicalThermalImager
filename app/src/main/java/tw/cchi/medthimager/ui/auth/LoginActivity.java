package tw.cchi.medthimager.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.camera.CameraActivity;

public class LoginActivity extends BaseActivity implements LoginMvpView {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject LoginMvpPresenter<LoginMvpView> presenter;

    @BindView(R.id.editEmail) EditText editEmail;
    @BindView(R.id.editPassword) EditText editPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);
    }

    @OnClick(R.id.btnLogin)
    void onLoginClick() {
        presenter.login(editEmail.getText().toString(), editPassword.getText().toString());
    }

    @Override
    public void setCredentials(String email, String password) {
        editEmail.setText(email);
        editPassword.setText(password);
    }

    @Override
    public void launchCameraActivityAndFinish() {
        startActivity(new Intent(LoginActivity.this, CameraActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        presenter.onDetach();
        super.onDestroy();
    }
}
