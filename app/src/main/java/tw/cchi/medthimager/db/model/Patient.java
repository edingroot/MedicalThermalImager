package tw.cchi.medthimager.db.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

@Entity(tableName = "patients")
public class Patient {
    public static final String DEFAULT_PATIENT_CUID = "AAAAAAAA-BBBB-CCCC-DDDD-123456789012";
    public static final String DEFAULT_PATIENT_UUID = "UAAAAAAA-BBBB-CCCC-DDDD-123456789012";
    public static final String DEFAULT_PATIENT_NAME = "Not Specified";

    @PrimaryKey
    @ColumnInfo(name = "cuid")
    @NonNull private String cuid;

    @ColumnInfo(name = "uuid")
    private String uuid = null;

    @ColumnInfo(name = "caseid")
    private String caseid = null;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "bed")
    private String bed = null;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @Ignore
    public Patient(@NonNull String cuid, String name) {
        this.cuid = cuid;
        this.name = name;
        this.createdAt = new Date();
    }

    @Ignore
    public Patient(String name) {
        this.cuid = UUID.randomUUID().toString();
        this.name = name;
        this.createdAt = new Date();
    }

    public Patient(@NonNull String cuid, String name, Date createdAt) {
        this.cuid = cuid;
        this.name = name;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getCuid() {
        return cuid;
    }

    public void setCuid(@NonNull String cuid) {
        this.cuid = cuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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

    public Date getCreatedAt() {
        return createdAt;
    }
}
