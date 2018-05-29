package tw.cchi.medthimager.ui.dialog.patientconflict;

import android.support.v7.app.AppCompatActivity;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.service.sync.task.SyncPatientsTask;
import tw.cchi.medthimager.service.sync.task.SyncSinglePatientTask;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.util.DateTimeUtils;

public class ConflictPatientPresenter<V extends ConflictPatientMvpView> extends BasePresenter<V> implements ConflictPatientMvpPresenter<V> {

    @Inject AppCompatActivity activity;

    private SyncBroadcastSender.ConflictType conflictType;
    private Patient localPatient;
    private List<SSPatient> conflictedPatients;

    @Inject
    public ConflictPatientPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
    }

    @Override
    public void setParams(SyncBroadcastSender.ConflictType conflictType, Patient localPatient,
                          List<SSPatient> conflictedPatients) {
        this.conflictType = conflictType;
        this.localPatient = localPatient;
        this.conflictedPatients = conflictedPatients;

        initDisplay();
    }

    private void initDisplay() {
        if (conflictType == SyncBroadcastSender.ConflictType.FORCE_MERGE) {
            getMvpView().setBtnKeepBothVisible(false);
        }

        String createdAt = DateTimeUtils.timestampToDateString(localPatient.getCreatedAt().getTime());
        String bed = localPatient.getBed() == null ? "-" : localPatient.getBed();
        String name = localPatient.getName() == null ? "-" : localPatient.getName();
        getMvpView().setLocalPatientInfo(activity.getString(R.string.local_patient_info, bed, name, createdAt));

        getMvpView().initConflictedPatientList(conflictedPatients);
    }

    @Override
    public void callMerge(int selectedPosition) {
        if (selectedPosition == -1)
            return;

        SSPatient mergeTarget = conflictedPatients.get(selectedPosition);
        application.getSyncService(syncService -> {
            if (application.checkNetworkAuthedAndAct()) {
                syncService.scheduleNewTask(new SyncSinglePatientTask(
                        localPatient, mergeTarget.getUuid(), false));
            }
        });

        getMvpView().dismiss();
    }

    @Override
    public void callKeepBoth(int selectedPosition) {
        if (selectedPosition == -1 || conflictType == SyncBroadcastSender.ConflictType.FORCE_MERGE)
            return;

        SSPatient mergeTarget = conflictedPatients.get(selectedPosition);
        application.getSyncService(syncService -> {
            if (application.checkNetworkAuthedAndAct()) {
                syncService.scheduleNewTask(new SyncSinglePatientTask(
                        localPatient, mergeTarget.getUuid(), true));
            }
        });

        getMvpView().dismiss();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
