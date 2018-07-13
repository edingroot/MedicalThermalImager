package tw.cchi.medthimager.data.db.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity(tableName = "capture_record_tags",
        foreignKeys = {
            @ForeignKey(
                entity = CaptureRecord.class,
                parentColumns = "uuid",
                childColumns = "capture_record_uuid",
                onDelete = ForeignKey.CASCADE)
        },
        indices = {
            @Index(value = "capture_record_uuid")
        })
public class CaptureRecordTags {
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    @NonNull private String uuid;

    @ColumnInfo(name = "capture_record_uuid")
    private String captureRecordUuid;

    @ColumnInfo(name = "tag_uuid")
    private String tagUuid;

    public CaptureRecordTags(@NonNull String uuid, String captureRecordUuid, String tagUuid) {
        this.uuid = uuid;
        this.captureRecordUuid = captureRecordUuid;
        this.tagUuid = tagUuid;
    }

    @NonNull
    public String getUuid() {
        return uuid;
    }

    public void setUuid(@NonNull String uuid) {
        this.uuid = uuid;
    }

    public String getCaptureRecordUuid() {
        return captureRecordUuid;
    }

    public void setCaptureRecordUuid(String captureRecordUuid) {
        this.captureRecordUuid = captureRecordUuid;
    }

    public String getTagUuid() {
        return tagUuid;
    }

    public void setTagUuid(String tagUuid) {
        this.tagUuid = tagUuid;
    }
}
