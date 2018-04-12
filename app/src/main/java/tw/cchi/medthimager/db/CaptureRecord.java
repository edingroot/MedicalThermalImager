package tw.cchi.medthimager.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

@Entity(tableName = "capture_records",
        foreignKeys = {
            @ForeignKey(
                entity = Patient.class,
                parentColumns = "uuid",
                childColumns = "patient_uuid",
                onDelete = ForeignKey.CASCADE)
        },
        indices = {
            @Index(value = "patient_uuid")
        })
public class CaptureRecord {
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    @NonNull private String uuid;

    @ColumnInfo(name = "patient_uuid")
    private String patientUuid;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "filename_prefix")
    private String filenamePrefix;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @Ignore
    public CaptureRecord(String patientUuid, String title, String filenamePrefix) {
        this.uuid = UUID.randomUUID().toString();
        this.patientUuid = patientUuid;
        this.title = title;
        this.filenamePrefix = filenamePrefix;
        this.createdAt = new Date();
    }

    public CaptureRecord(@NonNull String uuid, String patientUuid, String title, String filenamePrefix, Date createdAt) {
        this.uuid = uuid;
        this.patientUuid = patientUuid;
        this.title = title;
        this.filenamePrefix = filenamePrefix;
        this.createdAt = createdAt;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(@NonNull String uuid) {
        this.uuid = uuid;
    }

    public String getPatientUuid() {
        return patientUuid;
    }

    public void setPatientUuid(String patientUuid) {
        this.patientUuid = patientUuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilenamePrefix() {
        return filenamePrefix;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
