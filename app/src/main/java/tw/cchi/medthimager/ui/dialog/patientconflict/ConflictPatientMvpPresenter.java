package tw.cchi.medthimager.ui.dialog.patientconflict;

import java.util.List;

import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.service.sync.SyncBroadcastSender;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface ConflictPatientMvpPresenter<V extends ConflictPatientMvpView> extends MvpPresenter<V> {

    void setParams(SyncBroadcastSender.ConflictType conflictType, Patient localPatient,
                   List<SSPatient> conflictedPatients);

    void callMerge(int selectedPosition);

    void callKeepBoth(int selectedPosition);

}
