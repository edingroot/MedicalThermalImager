package tw.cchi.medthimager.model.api;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import java.util.Date;
import java.util.List;

import tw.cchi.medthimager.data.db.model.Patient;

public class ThImage implements Parcelable {
    private String uuid;
    private List<String> tags;
    private String patient_uuid;
    private String patient_cuid;
    private String title;
    private String contishoot_group;
    private Date captured_at;

    private int uploaded_by;
    private Date created_at;
    private Date updated_at;

    /**
     * @param uuid
     * @param tags tag uuids
     * @param patient
     * @param contishootGroup
     * @param title
     * @param capturedAt
     */
    public ThImage(String uuid, List<String> tags, Patient patient, @Nullable String contishootGroup,
                   String title, Date capturedAt) {
        this.uuid = uuid;
        this.tags = tags;
        this.patient_uuid = patient.getSsuuid();
        this.patient_cuid = patient.getCuid();
        this.title = title;
        this.contishoot_group = contishootGroup;
        this.captured_at = capturedAt;
    }

    public String getUuid() {
        return uuid;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getPatient_uuid() {
        return patient_uuid;
    }

    public void setPatient_uuid(String patient_uuid) {
        this.patient_uuid = patient_uuid;
    }

    public String getPatient_cuid() {
        return patient_cuid;
    }

    public void setPatient_cuid(String patient_cuid) {
        this.patient_cuid = patient_cuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContishoot_group() {
        return contishoot_group;
    }

    public void setContishoot_group(String contishoot_group) {
        this.contishoot_group = contishoot_group;
    }

    public Date getCaptured_at() {
        return captured_at;
    }

    public void setCaptured_at(Date captured_at) {
        this.captured_at = captured_at;
    }

    public int getUploaded_by() {
        return uploaded_by;
    }

    public void setUploaded_by(int uploaded_by) {
        this.uploaded_by = uploaded_by;
    }

    public Date getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Date created_at) {
        this.created_at = created_at;
    }

    public Date getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(Date updated_at) {
        this.updated_at = updated_at;
    }

    public static Creator<ThImage> getCREATOR() {
        return CREATOR;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.uuid);
        dest.writeString(this.patient_uuid);
        dest.writeString(this.patient_cuid);
        dest.writeString(this.title);
        dest.writeString(this.contishoot_group);
        dest.writeLong(this.captured_at != null ? this.captured_at.getTime() : -1);
        dest.writeInt(this.uploaded_by);
        dest.writeLong(this.created_at != null ? this.created_at.getTime() : -1);
        dest.writeLong(this.updated_at != null ? this.updated_at.getTime() : -1);
    }

    protected ThImage(Parcel in) {
        this.uuid = in.readString();
        this.patient_uuid = in.readString();
        this.patient_cuid = in.readString();
        this.title = in.readString();
        this.contishoot_group = in.readString();
        long tmpCaptured_at = in.readLong();
        this.captured_at = tmpCaptured_at == -1 ? null : new Date(tmpCaptured_at);
        this.uploaded_by = in.readInt();
        long tmpCreated_at = in.readLong();
        this.created_at = tmpCreated_at == -1 ? null : new Date(tmpCreated_at);
        long tmpUpdated_at = in.readLong();
        this.updated_at = tmpUpdated_at == -1 ? null : new Date(tmpUpdated_at);
    }

    public static final Creator<ThImage> CREATOR = new Creator<ThImage>() {
        @Override
        public ThImage createFromParcel(Parcel source) {
            return new ThImage(source);
        }

        @Override
        public ThImage[] newArray(int size) {
            return new ThImage[size];
        }
    };
}
