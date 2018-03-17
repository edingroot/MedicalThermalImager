package tw.cchi.flironedemo1.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

@Entity(tableName = "patients")
public class Patient {
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    @NonNull private String uuid = "";

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @Ignore
    public Patient(String name) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.createdAt = new Date(System.currentTimeMillis());
    }

    public Patient(String uuid, String name, Date createdAt) {
        this.uuid = uuid;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
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
