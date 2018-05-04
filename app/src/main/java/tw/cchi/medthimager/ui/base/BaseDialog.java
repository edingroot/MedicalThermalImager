package tw.cchi.medthimager.ui.base;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RelativeLayout;

import butterknife.Unbinder;
import tw.cchi.medthimager.di.component.ActivityComponent;

public abstract class BaseDialog extends DialogFragment implements DialogMvpView {

    private BaseActivity mActivity;
    private Unbinder mUnBinder;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof BaseActivity) {
            BaseActivity mActivity = (BaseActivity) context;
            this.mActivity = mActivity;
            mActivity.onFragmentAttached();
        }
    }

    @Override
    public void showLoading() {
        if (mActivity != null) {
            mActivity.showLoading();
        }
    }

    @Override
    public void hideLoading() {
        if (mActivity != null) {
            mActivity.hideLoading();
        }
    }

    @Override
    public AlertDialog showMessageAlertDialog(String title, String message) {
        if (mActivity != null) {
            return mActivity.showMessageAlertDialog(title, message);
        } else {
            return null;
        }
    }

    @Override
    public AlertDialog showAlertDialog(String title, String message, DialogInterface.OnClickListener onYesClicked, DialogInterface.OnClickListener onNoClicked) {
        if (mActivity != null) {
            return mActivity.showAlertDialog(title, message, onYesClicked, onNoClicked);
        } else {
            return null;
        }
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
    public void showSnackBar(int resId, Object... formatArgs) {
        if (mActivity != null) {
            showSnackBar(resId, formatArgs);
        }
    }

    @Override
    public void showToast(String message) {
        if (mActivity != null) {
            showToast(message);
        }
    }

    @Override
    public void showToast(@StringRes int resId) {
        if (mActivity != null) {
            showToast(resId);
        }
    }

    @Override
    public void showToast(@StringRes int resId, Object... formatArgs) {
        if (mActivity != null) {
            showToast(resId, formatArgs);
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

    public BaseActivity getBaseActivity() {
        return mActivity;
    }

    public ActivityComponent getActivityComponent() {
        if (mActivity != null) {
            return mActivity.getActivityComponent();
        }
        return null;
    }

    public void setUnBinder(Unbinder unBinder) {
        mUnBinder = unBinder;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // the content
        final RelativeLayout root = new RelativeLayout(getActivity());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // creating the fullscreen dialog
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(false);

        return dialog;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void show(FragmentManager fragmentManager, String tag) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        Fragment prevFragment = fragmentManager.findFragmentByTag(tag);
        if (prevFragment != null) {
            transaction.remove(prevFragment);
        }
        transaction.addToBackStack(null);
        show(transaction, tag);
    }

    @Override
    public void dismiss(String tag) {
        super.dismiss();
        getBaseActivity().onFragmentDetached(tag);
    }

    @Override
    public void onDestroy() {
        if (mUnBinder != null) {
            mUnBinder.unbind();
        }
        super.onDestroy();
    }
}