package tw.cchi.medthimager.data.db.model;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;
import android.arch.persistence.room.Update;

import java.util.List;

import static android.arch.persistence.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class CaptureRecordDAO {

    @Query("select * from capture_records order by created_at desc")
    public abstract List<CaptureRecord> getAll();

    @Query("select * from capture_records where uuid = :uuid")
    public abstract CaptureRecord get(String uuid);

    @Query("select * from capture_records where uuid in (:uuids)")
    public abstract List<CaptureRecord> get(String[] uuids);

    @Query("select * from capture_records where synced is 0")
    public abstract List<CaptureRecord> getSyncList();

    @Query("select * from capture_records where patient_cuid = :patientCuid order by created_at desc")
    public abstract List<CaptureRecord> findByPatientCuid(String patientCuid);

    @Query("select * from capture_records where title like :title limit 1")
    public abstract CaptureRecord findByTitle(String title);

    @Query("select * from capture_records where patient_cuid = :patientCuid and title = :title limit 1")
    public abstract CaptureRecord findByProps(String patientCuid, String title);

    @Query("select capture_record_tags.uuid from capture_record_tags where capture_record_uuid = :uuid")
    public abstract List<String> getTags(String uuid);


    @Insert
    public abstract void insertAll(CaptureRecord... captureRecords);

    @Update(onConflict = REPLACE)
    public abstract void update(CaptureRecord captureRecord);

    @Delete
    public abstract void delete(CaptureRecord captureRecord);

    @Transaction
    public void insertAndAutoCreatePatient(PatientDAO patientDAO, CaptureRecord captureRecord) {
        String patientCuid = captureRecord.getPatientCuid();

        if (patientCuid == null || patientCuid.length() == 0) {
            // Patient not specified
            patientCuid = Patient.DEFAULT_PATIENT_CUID;
            captureRecord.setPatientCuid(patientCuid);
        } else {
            if (patientDAO.get(patientCuid) == null) {
                // Patient not found in app database
                // TODO: get patient name from server?
                String patientName = patientCuid;
                patientDAO.insertAll(new Patient(patientCuid, patientName));
            }
        }

        insertAll(captureRecord);
    }

}
