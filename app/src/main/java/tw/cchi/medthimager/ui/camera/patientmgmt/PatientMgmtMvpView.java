package tw.cchi.medthimager.ui.camera.patientmgmt;

import java.util.List;

import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.ui.base.DialogMvpView;

public interface PatientMgmtMvpView extends DialogMvpView {

    void setPatients(List<Patient> patients);

    int getSelectedPosition();

    void setSelectedPosition(int position);

    void dismiss();

}
