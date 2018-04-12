package tw.cchi.medthimager.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

@Entity(tableName = "patients")
public class Patient {
    public static final String DEFAULT_PATIENT_UUID = "AAAAAAAA-BBBB-CCCC-DDDD-123456789012";

    @PrimaryKey
    @ColumnInfo(name = "uuid")
    @NonNull private String uuid;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @Ignore
    public Patient(@NonNull String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.createdAt = new Date();
    }

    @Ignore
    public Patient(String name) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.createdAt = new Date();
    }

    public Patient(@NonNull String uuid, String name, Date createdAt) {
        this.uuid = uuid;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(@NonNull String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
