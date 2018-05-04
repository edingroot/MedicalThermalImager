package tw.cchi.medthimager.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public abstract class PatientDAO {

    @Query("select * from patients")
    public abstract List<Patient> getAll();

    @Query("select * from patients where uuid = :uuid")
    public abstract Patient get(String uuid);

    public Patient getOrDefault(String uuid) {
        Patient patient = get(uuid);
        return patient == null ? get(Patient.DEFAULT_PATIENT_UUID) : patient;
    }

    @Query("select * from patients where uuid in (:uuids)")
    public abstract List<Patient> findAllByUUIDs(String[] uuids);

    @Query("select * from patients where name like :name limit 1")
    public abstract Patient findByName(String name);

    @Insert
    public abstract void insertAll(Patient... patients);

    @Delete
    public abstract void delete(Patient patient);

}
