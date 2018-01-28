package tw.cchi.flironedemo1.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

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
    private String uuid;

    @ColumnInfo(name = "patient_uuid")
    private String patientUuid;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "path_dump")
    private String dumpPath;

    @ColumnInfo(name = "path_image")
    private String imagePath;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @Ignore
    public CaptureRecord(String title, String dumpPath, String imagePath) {
        this.uuid = UUID.randomUUID().toString();
        this.title = title;
        this.dumpPath = dumpPath;
        this.imagePath = imagePath;
        this.createdAt = new Date(System.currentTimeMillis());
    }

    public CaptureRecord(String uuid, String title, String dumpPath, String imagePath, Date createdAt) {
        this.uuid = uuid;
        this.title = title;
        this.dumpPath = dumpPath;
        this.imagePath = imagePath;
        this.createdAt = createdAt;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
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

    public String getDumpPath() {
        return dumpPath;
    }

    public String getImagePath() {
        return imagePath;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
