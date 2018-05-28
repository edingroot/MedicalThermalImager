package tw.cchi.medthimager.model.api;

import java.util.List;

public class PatientResponse {
    public String message;
    public SSPatient patient;
    public List<SSPatient> conflicted_patients;
}
