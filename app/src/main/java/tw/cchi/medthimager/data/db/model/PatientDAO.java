package tw.cchi.medthimager.data.db.model;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public abstract class PatientDAO {

    @Query("select count(*) from patients")
    public abstract int getCount();

    @Query("select * from patients")
    public abstract List<Patient> getAll();

    @Query("select * from patients where cuid = :cuid")
    public abstract Patient get(String cuid);

    public Patient getOrDefault(String cuid) {
        Patient patient = get(cuid);
        return patient == null ? get(Patient.DEFAULT_PATIENT_CUID) : patient;
    }

    @Query("select cuid from patients where ssuuid = :ssuuid")
    public abstract String getCuidBySsuuid(String ssuuid);

    @Query("select * from patients where name like :name limit 1")
    public abstract Patient getByName(String name);

    @Query("select * from patients where sync_enabled is 1 and ssuuid is null" +
            " and cuid != '" + Patient.DEFAULT_PATIENT_CUID + "'")
    public abstract List<Patient> getSyncList();

    @Query("select * from patients where cuid in (:cuids)")
    public abstract List<Patient> findAllByCUIDs(String[] cuids);

    @Insert
    public abstract void insertAll(Patient... patients);

    @Update
    public abstract void updateAll(Patient... patients);

    @Delete
    public abstract void delete(Patient patient);

}
