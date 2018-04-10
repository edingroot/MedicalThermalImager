package tw.cchi.medthimager.ui.camera.contishot;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.ui.base.BaseDialog;

public class ContiShotDialog extends BaseDialog implements ContiShotMvpView {
    private static final String TAG = "ContiShotDialog";

    public interface OnResultListener {
        void onStart(ContiShotDialog dialog, int interval, int times);
    }
    private OnResultListener onResultListener;

    @Inject ContiShotMvpPresenter<ContiShotMvpView> presenter;

    @BindView(R.id.editInterval) EditText editInterval;
    @BindView(R.id.editTimes) EditText editTimes;

    public static ContiShotDialog newInstance() {
        ContiShotDialog fragment = new ContiShotDialog();
        Bundle bundle = new Bundle();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_conti_shot, container, false);

        ActivityComponent component = getActivityComponent();
        if (component != null) {
            component.inject(this);
            setUnBinder(ButterKnife.bind(this, view));
            presenter.onAttach(this);
        }

        return view;
    }

    public void show(FragmentManager fragmentManager, OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
        super.show(fragmentManager, TAG);
    }

    @OnClick(R.id.btnOk)
    public void onOkClick(View v) {
        boolean success = presenter.parseResult(
            editInterval.getText().toString(), editTimes.getText().toString()
        );

        if (!success) {
            showToast(R.string.invalid_input);
        }
    }

    @Override
    public void dismissDialog() {
        super.dismissDialog(TAG);
    }

    @Override
    public OnResultListener getListener() {
        return onResultListener;
    }

    @Override
    public void onDestroyView() {
        presenter.onDetach();
        super.onDestroyView();
    }
}
