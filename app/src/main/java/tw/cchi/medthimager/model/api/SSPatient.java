package tw.cchi.medthimager.model.api;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import java.util.Date;

import tw.cchi.medthimager.data.db.model.Patient;

/**
 * Server-side patient object
 */
public class SSPatient implements Parcelable {
    private String uuid;
    private String caseid;
    private String name;
    private String bed;
    private String comments;
    private int created_by;
    private Date created_at;
    private Date updated_at;

    private String merge_with;
    private boolean create_new = false;

    /**
     * Convert from local Patient model.
     */
    public SSPatient(Patient patient) {
        this.uuid = patient.getSsuuid();
        this.caseid = patient.getCaseid();
        this.name = patient.getName();
        this.bed = patient.getBed();
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

    public void setMerge_with(String merge_with) {
        this.merge_with = merge_with;
    }

    public void setCreate_new(boolean create_new) {
        this.create_new = create_new;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.uuid);
        dest.writeString(this.caseid);
        dest.writeString(this.name);
        dest.writeString(this.bed);
        dest.writeString(this.comments);
        dest.writeInt(this.created_by);
        dest.writeLong(this.created_at != null ? this.created_at.getTime() : -1);
        dest.writeLong(this.updated_at != null ? this.updated_at.getTime() : -1);
    }

    public SSPatient() {
    }

    protected SSPatient(Parcel in) {
        this.uuid = in.readString();
        this.caseid = in.readString();
        this.name = in.readString();
        this.bed = in.readString();
        this.comments = in.readString();
        this.created_by = in.readInt();
        long tmpCreated_at = in.readLong();
        this.created_at = tmpCreated_at == -1 ? null : new Date(tmpCreated_at);
        long tmpUpdated_at = in.readLong();
        this.updated_at = tmpUpdated_at == -1 ? null : new Date(tmpUpdated_at);
    }

    public static final Parcelable.Creator<SSPatient> CREATOR = new Parcelable.Creator<SSPatient>() {
        @Override
        public SSPatient createFromParcel(Parcel source) {
            return new SSPatient(source);
        }

        @Override
        public SSPatient[] newArray(int size) {
            return new SSPatient[size];
        }
    };
}
