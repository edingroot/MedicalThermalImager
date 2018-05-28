package tw.cchi.medthimager.ui.base;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.Unbinder;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.di.component.DaggerActivityComponent;
import tw.cchi.medthimager.di.module.ActivityModule;
import tw.cchi.medthimager.helper.session.SessionManager;
import tw.cchi.medthimager.ui.auth.LoginActivity;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.NetworkUtils;

public abstract class BaseActivity extends AppCompatActivity
        implements MvpView, BaseFragment.Callback, SessionManager.AuthEventListener {
    private ActivityComponent mActivityComponent;
    private Unbinder mUnBinder;
    private Handler mainLooperHandler;
    private ProgressDialog loadingDialog;

    protected MvpApplication application;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivityComponent = DaggerActivityComponent.builder()
                .activityModule(new ActivityModule(this))
                .applicationComponent(((MvpApplication) getApplication()).getComponent())
                .build();
        mainLooperHandler = new Handler(Looper.getMainLooper());

        application = (MvpApplication) getApplication();
        application.sessionManager.addAuthEventListener(this);

        finishIfNotAuthorized();
    }

    public ActivityComponent getActivityComponent() {
        return mActivityComponent;
    }

    @Override
    public void showLoading() {
        mainLooperHandler.post(() -> {
            if (loadingDialog == null || !loadingDialog.isShowing()) {
                loadingDialog = AppUtils.showLoadingDialog(this);
            }
        });
    }

    @Override
    public void hideLoading() {
        mainLooperHandler.post(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    @Override
    public AlertDialog showMessageAlertDialog(String title, String message) {
        return new AlertDialog.Builder(this, R.style.MyAlertDialog)
            .setTitle(title).setMessage(message)
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .show();
    }

    @Override
    public AlertDialog showAlertDialog(String title, String message,
                                DialogInterface.OnClickListener onYesClicked, DialogInterface.OnClickListener onNoClicked) {
        return new AlertDialog.Builder(this, R.style.MyAlertDialog)
                .setTitle(title).setMessage(message)
                .setPositiveButton("Yes", onYesClicked)
                .setNegativeButton("No", onNoClicked)
                .show();
    }

    @Override
    public void showSnackBar(String message) {
        Snackbar snackbar = Snackbar.make(
                findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT);
        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);

        textView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        snackbar.show();
    }

    @Override
    public void showSnackBar(@StringRes int resId) {
        showSnackBar(getString(resId));
    }

    @Override
    public void showSnackBar(int resId, Object... formatArgs) {
        showSnackBar(getString(resId, formatArgs));
    }

    @Override
    public void showToast(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showToast(@StringRes int resId) {
        showToast(getString(resId));
    }

    @Override
    public void showToast(@StringRes int resId, Object... formatArgs) {
        showToast(getString(resId, formatArgs));
    }

    @Override
    public boolean isNetworkConnected() {
        return NetworkUtils.isNetworkConnected(getApplicationContext());
    }

    @Override
    public void onFragmentAttached() {
    }

    @Override
    public void onFragmentDetached(String tag) {
    }

    public void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void setUnBinder(Unbinder unBinder) {
        mUnBinder = unBinder;
    }

    private void finishIfNotAuthorized() {
        for (Class guestActivityClass : Config.GUEST_ACTIVITIES) {
            if (getClass() == guestActivityClass)
                return;
        }

        if (!application.getSession().isActive()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onLogin() {
    }

    @Override
    public void onLogout() {
        mainLooperHandler.post(() -> showToast(R.string.please_login));

        // Explicitly finish active activities
        // Login activity will be started by SessionManager
        finish();
    }

    @Override
    protected void onDestroy() {
        application.sessionManager.removeAuthEventListener(this);

        if (mUnBinder != null)
            mUnBinder.unbind();

        super.onDestroy();
    }


    /* @TargetApi(Build.VERSION_CODES.M)
    public void requestPermissionsSafely(String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, requestCode);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    } */
}
