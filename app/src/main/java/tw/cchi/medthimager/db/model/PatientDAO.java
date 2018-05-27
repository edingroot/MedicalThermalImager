package tw.cchi.medthimager.db.model;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public abstract class PatientDAO {

    @Query("select * from patients")
    public abstract List<Patient> getAll();

    @Query("select * from patients where cuid = :cuid")
    public abstract Patient get(String cuid);

    public Patient getOrDefault(String cuid) {
        Patient patient = get(cuid);
        return patient == null ? get(Patient.DEFAULT_PATIENT_CUID) : patient;
    }

    @Query("select * from patients where name like :name limit 1")
    public abstract Patient findByName(String name);

    @Query("select * from patients where uuid is null")
    public abstract List<Patient> findNullUuids();

    @Query("select * from patients where cuid in (:cuids)")
    public abstract List<Patient> findAllByCUIDs(String[] cuids);

    @Insert
    public abstract void insertAll(Patient... patients);

    @Delete
    public abstract void delete(Patient patient);

}
