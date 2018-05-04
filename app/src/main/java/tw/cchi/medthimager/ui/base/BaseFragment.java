package tw.cchi.medthimager.ui.base;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.view.View;

import butterknife.Unbinder;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.utils.CommonUtils;

public abstract class BaseFragment extends Fragment implements MvpView {

    private BaseActivity mActivity;
    private Unbinder mUnBinder;
    private Handler mainHandler;
    private ProgressDialog loadingDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUp(view);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) context;
            this.mActivity = activity;
            activity.onFragmentAttached();
        }
    }

    @Override
    public void showLoading() {
        mainHandler.post(() -> {
            if (loadingDialog == null || !loadingDialog.isShowing()) {
                // TODO: check this.getActivity()
                loadingDialog = CommonUtils.showLoadingDialog(this.getActivity());
            }
        });
    }

    @Override
    public void hideLoading() {
        mainHandler.post(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    @Override
    public void showSnackBar(String message) {
        if (mActivity != null) {
            showSnackBar(message);
        }
    }

    @Override
    public void showSnackBar(@StringRes int resId) {
        if (mActivity != null) {
            showSnackBar(resId);
        }
    }

    @Override
    public void showToast(String message) {
        if (mActivity != null) {
            mActivity.showToast(message);
        }
    }

    @Override
    public void showToast(@StringRes int resId) {
        if (mActivity != null) {
            mActivity.showToast(resId);
        }
    }

    @Override
    public void onDetach() {
        mActivity = null;
        super.onDetach();
    }

    @Override
    public void hideKeyboard() {
        if (mActivity != null) {
            mActivity.hideKeyboard();
        }
    }

    public ActivityComponent getActivityComponent() {
        if (mActivity != null) {
            return mActivity.getActivityComponent();
        }
        return null;
    }

    public BaseActivity getBaseActivity() {
        return mActivity;
    }

    public void setUnBinder(Unbinder unBinder) {
        mUnBinder = unBinder;
    }

    protected abstract void setUp(View view);

    @Override
    public void onDestroy() {
        if (mUnBinder != null) {
            mUnBinder.unbind();
        }
        super.onDestroy();
    }

    public interface Callback {
        void onFragmentAttached();

        void onFragmentDetached(String tag);
    }
}
