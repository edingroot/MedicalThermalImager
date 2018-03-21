package tw.cchi.medthimager.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface CaptureRecordDAO {
    @Query("select * from capture_records order by created_at desc")
    List<CaptureRecord> getAll();

    @Query("select * from capture_records where uuid in (:uuids)")
    List<CaptureRecord> loadAllByUUIDs(String[] uuids);

    @Query("select * from capture_records where patient_uuid = :patientUUID order by created_at desc")
    List<CaptureRecord> findByPatientUUID(String patientUUID);

    @Query("select * from capture_records where title like :title limit 1")
    CaptureRecord findByTitle(String title);

    @Insert
    void insertAll(CaptureRecord... captureRecords);

    @Delete
    void delete(CaptureRecord captureRecord);
}
