package tw.cchi.medthimager.ui.camera.patientmgmt;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.ui.adapter.PatientSelectRecyclerAdapter;
import tw.cchi.medthimager.ui.base.BaseDialog;

public class PatientMgmtDialog extends BaseDialog
        implements PatientMgmtMvpView, PatientSelectRecyclerAdapter.OnInteractionListener {
    private final String FRAGMENT_TAG = "PatientMgmtDialog";

    @Inject PatientMgmtMvpPresenter<PatientMgmtMvpView> presenter;

    private CompositeDisposable broadcastSubs;
    private OnInteractionListener onInteractionListener;
    private String preSelectedPatientCuid;
    @Inject PatientSelectRecyclerAdapter patientRecyclerAdapter;

    @BindView(R.id.editCaseId) EditText editCaseId;
    @BindView(R.id.editBed) EditText editBed;
    @BindView(R.id.editName) EditText editName;
    @BindView(R.id.recyclerPatientList) RecyclerView recyclerPatientList;

    public static PatientMgmtDialog newInstance() {
        PatientMgmtDialog fragment = new PatientMgmtDialog();
        Bundle bundle = new Bundle();

        fragment.setArguments(bundle);
        return fragment;
    }

    public void show(FragmentManager fragmentManager, OnInteractionListener onResultListener) {
        this.onInteractionListener = onResultListener;
        super.show(fragmentManager, FRAGMENT_TAG);
    }

    public void setSelectedPatient(@Nullable String patientCuid) {
        if (patientCuid == null)
            patientCuid = Patient.DEFAULT_PATIENT_CUID;

        // This method may be called before view created
        if (presenter != null)
            presenter.setSelected(patientCuid);
        else
            preSelectedPatientCuid = patientCuid;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_patient_mgmt, container, false);

        ActivityComponent component = getActivityComponent();
        if (component != null) {
            component.inject(this);
            setUnBinder(ButterKnife.bind(this, view));
            presenter.onAttach(this);
        }

        getDialog().setCanceledOnTouchOutside(true);

        // Patient list
        // TODO: remove function is temporary disabled due to local logic / server side api not implemented
        patientRecyclerAdapter.setShowRemove(false);
        patientRecyclerAdapter.setOnInteractionListener(this);
        recyclerPatientList.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false));
        recyclerPatientList.setAdapter(patientRecyclerAdapter);

        if (preSelectedPatientCuid != null)
            presenter.setSelected(preSelectedPatientCuid);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        broadcastSubs = new CompositeDisposable();
        broadcastSubs.add(getBaseActivity().internalBroadcastEventPub
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> {
                    if (pair.first.equals(SyncBroadcastSender.EventName.SYNC_PATIENTS_DONE)) {
                        presenter.onSyncPatientsDone();
                    }
                }));
    }


    @OnClick(R.id.btnAdd)
    public void onAddClick() {
        presenter.addPatient(editCaseId.getText().toString(), editBed.getText().toString(),
                             editName.getText().toString());

        editCaseId.setText("");
        editBed.setText("");
        editName.setText("");
    }

    @OnClick(R.id.btnSelect)
    public void onSelectClick() {
        Patient patient = presenter.getPatientByPosition(getSelectedPosition());
        onInteractionListener.onSelect(patient);
        presenter.processSelectPatient(patient);
    }

    @OnClick(R.id.btnCancel)
    public void onCancelClick() {
        dismiss();
    }

    @Override
    public void setPatients(List<Patient> patients) {
        patientRecyclerAdapter.setPatients(new ArrayList<>(patients));
    }

    @Override
    public int getSelectedPosition() {
        return patientRecyclerAdapter.getSelectedPosition();
    }

    @Override
    public void setSelectedPosition(int position) {
        patientRecyclerAdapter.setSelectedPosition(position);
    }

    @Override
    public void dismiss() {
        super.dismiss(FRAGMENT_TAG);
    }

    @Override
    public void onStop() {
        broadcastSubs.dispose();
        super.onStop();
    }

    // PatientSelectRecyclerAdapter.OnInteractionListener
    @Override
    public void onSelected(View v, int position) {
        this.setSelectedPosition(position);
    }

    // PatientSelectRecyclerAdapter.OnInteractionListener
    @Override
    public void onRemoveClicked(View v, int position) {
        Patient patient = presenter.getPatientByPosition(position);

        showAlertDialog(getString(R.string.confirm),
            getString(R.string.confirm_remove_patient, patient.getName()),
            (dialog, which) -> {
                // Yes
                presenter.removePatient(position);
            },
            (dialog, which) -> {
                // No
            });
    }


    @Override
    public void onDestroyView() {
        presenter.onDetach();
        super.onDestroyView();
    }

    public interface OnInteractionListener {
        void onSelect(Patient patient);
    }
}
