package tw.cchi.medthimager.data.db.model;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tw.cchi.medthimager.model.api.Tag;

@Dao
public abstract class CaptureRecordTagsDAO {

    @Insert
    public abstract void insertAll(List<CaptureRecordTags> captureRecordTags);

    @Delete
    public abstract void delete(CaptureRecord captureRecord);

    @Query("delete from capture_record_tags where capture_record_uuid = :captureRecordUuid")
    public abstract void deleteTagsOfCaptureRecord(String captureRecordUuid);


    @Query("select capture_record_tags.tag_uuid from capture_record_tags where capture_record_uuid = :uuid")
    public abstract List<String> getTagsOfCaptureRecord(String uuid);

    @Transaction
    public void setTagsOfCaptureRecord(CaptureRecord captureRecord, Set<Tag> tags) {
        deleteTagsOfCaptureRecord(captureRecord.getUuid());

        List<CaptureRecordTags> rows = new ArrayList<>();
        for (Tag tag : tags) {
            rows.add(new CaptureRecordTags(captureRecord.getUuid(), tag.getUuid()));
        }
        insertAll(rows);
    }

}
