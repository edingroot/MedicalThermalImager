package tw.cchi.medthimager.model.api;

import android.support.annotation.Nullable;

public class PatientCreateRequest {
    private final SSPatient patient;
    private String merge_with = null;
    private boolean create_new = false;

    public PatientCreateRequest(SSPatient patient) {
        this.patient = patient;
    }

    public PatientCreateRequest(SSPatient patient, String merge_with, boolean create_new) {
        this.patient = patient;
        this.merge_with = merge_with;
        this.create_new = create_new;
    }

    public SSPatient getPatient() {
        return patient;
    }

    @Nullable
    public String getMerge_with() {
        return merge_with;
    }

    public boolean isCreate_new() {
        return create_new;
    }
}
