package tw.cchi.medthimager.ui.base;

import android.content.DialogInterface;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

/**
 * Base interface that any class that wants to act as a View in the MVP (Model View Presenter)
 * pattern must implement. Generally this interface will be extended by a more specific interface
 * that then usually will be implemented by an Activity or Fragment.
 */
public interface MvpView {

    void showLoading();

    void hideLoading();

    AlertDialog showAlertDialog(String title, String message,
                                DialogInterface.OnClickListener onYesClicked, DialogInterface.OnClickListener onNoClicked);

    void showSnackBar(String message);

    void showSnackBar(@StringRes int resId);

    void showToast(String message);

    void showToast(@StringRes int resId);

    void hideKeyboard();

}
