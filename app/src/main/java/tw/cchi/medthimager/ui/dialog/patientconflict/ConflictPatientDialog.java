package tw.cchi.medthimager.ui.dialog.patientconflict;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.ui.adapter.PatientSelectRecyclerAdapter;
import tw.cchi.medthimager.ui.base.BaseDialog;
import tw.cchi.medthimager.ui.camera.selectpatient.SelectPatientDialog;
import tw.cchi.medthimager.util.DateTimeUtils;

public class ConflictPatientDialog extends BaseDialog implements ConflictPatientMvpView {
    private static final String FRAGMENT_TAG = "ConflictPatientDialog";
    private static final String ARG_CONFLICT_TYPE = "ARG_CONFLICT_TYPE";
    private static final String ARG_LOCAL_PATIENT = "ARG_LOCAL_PATIENT";
    private static final String ARG_PATIENT_LIST = "ARG_PATIENT_LIST";

    @Inject ConflictPatientMvpPresenter<ConflictPatientMvpView> presenter;

    PatientSelectRecyclerAdapter patientRecyclerAdapter;

    @BindView(R.id.txtLocalPatientInfo) TextView txtLocalPatientInfo;
    @BindView(R.id.recyclerConflictedPatients) RecyclerView recyclerConflictedPatients;
    @BindView(R.id.btnKeepBoth) Button btnKeepBoth;

    public static ConflictPatientDialog newInstance(SyncBroadcastSender.ConflictType conflictType,
                                                    Patient localPatient, List<SSPatient> conflictedPatients) {
        ConflictPatientDialog fragment = new ConflictPatientDialog();

        Bundle bundle = new Bundle();
        bundle.putSerializable(ARG_CONFLICT_TYPE, conflictType);
        bundle.putParcelable(ARG_LOCAL_PATIENT, localPatient);
        bundle.putParcelableArrayList(ARG_PATIENT_LIST, new ArrayList<>(conflictedPatients));
        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_patient_conflict, container, false);

        ActivityComponent component = getActivityComponent();
        if (component != null) {
            component.inject(this);
            setUnBinder(ButterKnife.bind(this, view));
            presenter.onAttach(this);
        }

        // Get arguments
        if (getArguments() != null) {
            presenter.setParams(
                    (SyncBroadcastSender.ConflictType) getArguments().getSerializable(ARG_CONFLICT_TYPE),
                    getArguments().getParcelable(ARG_LOCAL_PATIENT),
                    getArguments().getParcelableArrayList(ARG_PATIENT_LIST));
        }

        getDialog().setCanceledOnTouchOutside(true);

        return view;
    }

    public void show(FragmentManager fragmentManager) {
        super.show(fragmentManager, FRAGMENT_TAG);
    }


    @OnClick(R.id.btnMerge)
    void onMergeClick() {
        if (patientRecyclerAdapter != null)
            presenter.callMerge(patientRecyclerAdapter.getSelectedPosition());
    }

    @OnClick(R.id.btnKeepBoth)
    void onKeepBothClick() {
        if (patientRecyclerAdapter != null)
            presenter.callKeepBoth(patientRecyclerAdapter.getSelectedPosition());
    }

    @OnClick(R.id.btnCancel)
    void onCancelClick() {
        dismiss();
    }


    @Override
    public void initConflictedPatientList(List<SSPatient> conflictedPatients) {
        patientRecyclerAdapter = new PatientSelectRecyclerAdapter();
        patientRecyclerAdapter.setShowRemove(false);
        patientRecyclerAdapter.setSSPatients(conflictedPatients);

        // Default selection
        if (conflictedPatients.size() > 0) {
            patientRecyclerAdapter.setSelectedPosition(0);
        }

        recyclerConflictedPatients.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerConflictedPatients.setAdapter(patientRecyclerAdapter);
    }

    @Override
    public void setLocalPatientInfo(String text) {
        txtLocalPatientInfo.setText(text);
    }

    @Override
    public void setBtnKeepBothVisible(boolean visible) {
        btnKeepBoth.setVisibility(visible ? View.VISIBLE : View.GONE);
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
