package tw.cchi.medthimager.ui.dialog.updateremider;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.ui.base.BaseDialog;

public class UpdateReminderDialog extends BaseDialog implements UpdateReminderMvpView {
    private static final String FRAGMENT_TAG = "UpdateReminderDialog";

    @Inject UpdateReminderMvpPresenter<UpdateReminderMvpView> presenter;

    @BindView(R.id.txtCurrentVersion) TextView txtCurrentVersion;
    @BindView(R.id.txtNewVersion) TextView txtNewVersion;

    public static UpdateReminderDialog newInstance() {
        return new UpdateReminderDialog();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_update_reminder, container, false);

        ActivityComponent component = getActivityComponent();
        if (component != null) {
            component.inject(this);
            setUnBinder(ButterKnife.bind(this, view));
            presenter.onAttach(this);
        }

        getDialog().setCanceledOnTouchOutside(true);

        return view;
    }

    public void show(FragmentManager fragmentManager) {
        super.show(fragmentManager, FRAGMENT_TAG);
    }


    @OnClick(R.id.btnUpdate)
    void onUpdateClick() {
        presenter.update();
    }

    @OnClick(R.id.btnIgnore)
    void onIgnoreClick() {
        presenter.ignore();
    }


    @Override
    public void setCurrentVersion(String versionName) {
        txtCurrentVersion.setText(getString(R.string.current_version_, versionName));
    }

    @Override
    public void setNewVersion(String versionName) {
        txtNewVersion.setText(getString(R.string.new_version_, versionName));
    }

    @Override
    public void launchGooglePlayPage(String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=" + packageName));
        startActivity(intent);
    }

    @Override
    public void dismiss() {
        super.dismiss(FRAGMENT_TAG);
    }

    @Override
    public void onDestroyView() {
        presenter.onDetach();
        super.onDestroyView();
    }
}
