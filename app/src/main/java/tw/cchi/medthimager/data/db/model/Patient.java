package tw.cchi.medthimager.data.db.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

import tw.cchi.medthimager.model.api.SSPatient;

@Entity(tableName = "patients")
public class Patient implements Parcelable {
    public static final String DEFAULT_PATIENT_CUID = "AAAAAAAA-BBBB-CCCC-DDDD-123456789012";
    public static final String DEFAULT_PATIENT_BED = "00J00A";
    public static final String DEFAULT_PATIENT_NAME = "Unspecified";

    @PrimaryKey
    @ColumnInfo(name = "cuid")
    @NonNull private String cuid;

    @ColumnInfo(name = "ssuuid")
    private String ssuuid = null;

    @ColumnInfo(name = "caseid")
    private String caseid = null;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "bed")
    private String bed = null;

    @ColumnInfo(name = "sync_enabled")
    private boolean syncEnabled = true;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @Ignore
    public Patient(@NonNull String cuid, String name) {
        this.cuid = cuid;
        this.name = name;
        this.createdAt = new Date();
    }

    @Ignore
    public Patient(String caseid, String bed, String name) {
        this.cuid = UUID.randomUUID().toString();
        this.caseid = caseid;
        this.bed = bed;
        this.name = name;
        this.createdAt = new Date();
    }

    /**
     * Convert from (remote) SSPatient model.
     *
     * Note: cuid is regenerated.
     */
    @Ignore
    public Patient(SSPatient ssPatient) {
        this.cuid = UUID.randomUUID().toString();
        this.ssuuid = ssPatient.getUuid();
        this.caseid = ssPatient.getCaseid();
        this.name = ssPatient.getName();
        this.bed = ssPatient.getBed();
        this.createdAt = ssPatient.getCreated_at();
    }

    public Patient(@NonNull String cuid, String ssuuid, String caseid, String name, String bed, boolean syncEnabled, Date createdAt) {
        this.cuid = cuid;
        this.ssuuid = ssuuid;
        this.caseid = caseid;
        this.name = name;
        this.bed = bed;
        this.syncEnabled = syncEnabled;
        this.createdAt = createdAt;
    }

    public boolean isDefaultPatient() {
        return cuid.equals(DEFAULT_PATIENT_CUID);
    }

    @NonNull
    public String getCuid() {
        return cuid;
    }

    public void setCuid(@NonNull String cuid) {
        this.cuid = cuid;
    }

    public String getSsuuid() {
        return ssuuid;
    }

    public void setSsuuid(String ssuuid) {
        this.ssuuid = ssuuid;
    }

    public String getCaseid() {
        return caseid;
    }

    public void setCaseid(String caseid) {
        this.caseid = caseid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBed() {
        return bed;
    }

    public void setBed(String bed) {
        this.bed = bed;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public Date getCreatedAt() {
        return createdAt;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.cuid);
        dest.writeString(this.ssuuid);
        dest.writeString(this.caseid);
        dest.writeString(this.name);
        dest.writeString(this.bed);
        dest.writeByte(this.syncEnabled ? (byte) 1 : (byte) 0);
        dest.writeLong(this.createdAt != null ? this.createdAt.getTime() : -1);
    }

    protected Patient(Parcel in) {
        this.cuid = in.readString();
        this.ssuuid = in.readString();
        this.caseid = in.readString();
        this.name = in.readString();
        this.bed = in.readString();
        this.syncEnabled = in.readByte() != 0;
        long tmpCreatedAt = in.readLong();
        this.createdAt = tmpCreatedAt == -1 ? null : new Date(tmpCreatedAt);
    }

    public static final Parcelable.Creator<Patient> CREATOR = new Parcelable.Creator<Patient>() {
        @Override
        public Patient createFromParcel(Parcel source) {
            return new Patient(source);
        }

        @Override
        public Patient[] newArray(int size) {
            return new Patient[size];
        }
    };
}
