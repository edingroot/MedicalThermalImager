package tw.cchi.medthimager.ui.camera.contishoot;

import android.os.Bundle;
import android.support.annotation.NonNull;
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
import tw.cchi.medthimager.model.ContiShootParameters;
import tw.cchi.medthimager.ui.base.BaseDialog;

public class ContiShootDialog extends BaseDialog implements ContiShootMvpView {
    private static final String TAG = "ContiShootDialog";

    public interface OnResultListener {
        void onResult(ContiShootDialog dialog, ContiShootParameters parameters);
    }
    private OnResultListener onResultListener;

    @Inject ContiShootMvpPresenter<ContiShootMvpView> presenter;

    @BindView(R.id.editPeriod) EditText editPeriod;
    @BindView(R.id.editCaptures) EditText editCaptures;

    public static ContiShootDialog newInstance() {
        ContiShootDialog fragment = new ContiShootDialog();
        Bundle bundle = new Bundle();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_conti_shot, container, false);

        ActivityComponent component = getActivityComponent();
        if (component != null) {
            component.inject(this);
            setUnBinder(ButterKnife.bind(this, view));
            presenter.onAttach(this);
        }

        getDialog().setCanceledOnTouchOutside(true);

        return view;
    }

    public void show(FragmentManager fragmentManager, OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
        super.show(fragmentManager, TAG);
    }

    @OnClick(R.id.btnStart)
    public void onStartClick(View v) {
        boolean success = presenter.parseResult(
            editPeriod.getText().toString(), editCaptures.getText().toString()
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
