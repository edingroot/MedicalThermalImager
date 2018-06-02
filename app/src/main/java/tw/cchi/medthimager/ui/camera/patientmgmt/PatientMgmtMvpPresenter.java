package tw.cchi.medthimager.ui.camera.patientmgmt;

import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface PatientMgmtMvpPresenter<V extends PatientMgmtMvpView> extends MvpPresenter<V> {

    void addPatient(String caseId, String bed, String name);

    void removePatient(int position);

    void setSelected(String patientCuid);

    void processSelectPatient(Patient patient);


    Patient getPatientByPosition(int position);

}
