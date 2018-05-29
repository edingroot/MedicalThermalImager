package tw.cchi.medthimager.ui.dialog.patientconflict;

import java.util.List;

import tw.cchi.medthimager.model.api.SSPatient;
import tw.cchi.medthimager.ui.base.DialogMvpView;

public interface ConflictPatientMvpView extends DialogMvpView {

    void initConflictedPatientList(List<SSPatient> conflictedPatients);

    void setLocalPatientInfo(String text);

    void setBtnKeepBothVisible(boolean visible);

    void dismiss();

}
