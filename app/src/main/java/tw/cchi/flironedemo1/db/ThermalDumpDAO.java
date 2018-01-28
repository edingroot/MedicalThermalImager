package tw.cchi.flironedemo1.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface ThermalDumpDAO {
    @Query("select * from thermaldumps order by created_at desc")
    List<ThermalDump> getAll();

    @Query("select * from thermaldumps where uuid in (:uuids)")
    List<ThermalDump> loadAllByUUIDs(String[] uuids);

    @Query("select * from thermaldumps where patient_uuid = :patientUUID  order by created_at desc")
    List<ThermalDump> findByPatientUUID(String patientUUID);

    @Query("select * from thermaldumps where title like :title limit 1")
    ThermalDump findByTitle(String title);

    @Insert
    void insertAll(ThermalDump... thermaldumps);

    @Delete
    void delete(ThermalDump thermaldump);
}
