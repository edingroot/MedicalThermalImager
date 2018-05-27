package tw.cchi.medthimager.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;

import java.util.List;

@Dao
public abstract class CaptureRecordDAO {

    @Query("select * from capture_records order by created_at desc")
    public abstract List<CaptureRecord> getAll();

    @Query("select * from capture_records where uuid in (:uuids)")
    public abstract List<CaptureRecord> loadAllByUUIDs(String[] uuids);

    @Query("select * from capture_records where patient_cuid = :patientCuid order by created_at desc")
    public abstract List<CaptureRecord> findByPatientCuid(String patientCuid);

    @Query("select * from capture_records where title like :title limit 1")
    public abstract CaptureRecord findByTitle(String title);

    @Insert
    public abstract void insertAll(CaptureRecord... captureRecords);

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
