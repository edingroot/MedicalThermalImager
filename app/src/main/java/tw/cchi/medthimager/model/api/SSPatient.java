package tw.cchi.medthimager.model.api;

import android.support.annotation.Nullable;

import java.util.Date;

import tw.cchi.medthimager.db.model.Patient;

/**
 * Server-side patient object
 */
public class SSPatient {
    private String uuid;
    private String caseid;
    private String name;
    private String bed;
    private String comments;
    private int created_by;
    private Date created_at;
    private Date updated_at;

    public static SSPatient fromLocalPatient(Patient patient) {
        SSPatient ssPatient = new SSPatient();
        ssPatient.uuid = patient.getUuid();
        ssPatient.caseid = patient.getCaseid();
        ssPatient.name = patient.getName();
        ssPatient.bed = patient.getBed();
        return ssPatient;
    }

    public String getUuid() {
        return uuid;
    }

    @Nullable
    public String getCaseid() {
        return caseid;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getBed() {
        return bed;
    }

    public String getComments() {
        return comments;
    }

    public Date getCreated_at() {
        return created_at;
    }

    public Date getUpdated_at() {
        return updated_at;
    }
}
