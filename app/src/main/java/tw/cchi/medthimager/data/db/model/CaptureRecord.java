package tw.cchi.medthimager.data.db.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;
import java.util.UUID;

import static android.arch.persistence.room.OnConflictStrategy.REPLACE;

@Entity(tableName = "capture_records",
        foreignKeys = {
            @ForeignKey(
                entity = Patient.class,
                parentColumns = "cuid",
                childColumns = "patient_cuid",
                onDelete = ForeignKey.CASCADE)
        },
        indices = {
            @Index(value = "patient_cuid")
        })
public class CaptureRecord {
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    @NonNull private String uuid;

    @ColumnInfo(name = "patient_cuid")
    private String patientCuid;

    @ColumnInfo(name = "title")
    private String title;

    /**
     * See {@link tw.cchi.medthimager.model.CaptureProcessInfo#CaptureProcessInfo(Patient)}
     */
    @ColumnInfo(name = "filename_prefix")
    private String filenamePrefix;

    @ColumnInfo(name = "contishoot_group")
    private String contishootGroup;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @ColumnInfo(name = "synced")
    private boolean synced = false;

    public CaptureRecord(@NonNull String uuid, String patientCuid, String title, String filenamePrefix, String contishootGroup, Date createdAt, boolean synced) {
        this.uuid = uuid;
        this.patientCuid = patientCuid;
        this.title = title;
        this.filenamePrefix = filenamePrefix;
        this.contishootGroup = contishootGroup;
        this.createdAt = createdAt;
        this.synced = synced;
    }

    public String getUuid() {
        return uuid;
    }

    public String getPatientCuid() {
        return patientCuid;
    }

    public void setPatientCuid(String patientCuid) {
        this.patientCuid = patientCuid;
        this.synced = false;
    }

    public String getTitle() {
        return title;
    }

    public String getFilenamePrefix() {
        return filenamePrefix;
    }

    public String getContishootGroup() {
        return contishootGroup;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
