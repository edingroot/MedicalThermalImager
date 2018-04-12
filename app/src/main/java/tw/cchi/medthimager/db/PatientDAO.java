package tw.cchi.medthimager.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface PatientDAO {

    @Query("select * from patients")
    List<Patient> getAll();

    @Query("select * from patients where uuid = :uuid")
    Patient get(String uuid);

    @Query("select * from patients where uuid in (:uuids)")
    List<Patient> findAllByUUIDs(String[] uuids);

    @Query("select * from patients where name like :name limit 1")
    Patient findByName(String name);

    @Insert
    void insertAll(Patient... patients);

    @Delete
    void delete(Patient patient);

}
