package tw.cchi.medthimager.data.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.migration.Migration;
import android.database.Cursor;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import tw.cchi.medthimager.data.db.model.Patient;

final class Migrations {

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("alter table capture_records add contishoot_group text default null");

            checkAndInsertDefaultPatient(db);
        }
    };


    static void checkAndInsertDefaultPatient(@NonNull SupportSQLiteDatabase db) {
        // Check exists
        Cursor cursor = db.query(String.format("select cuid from patients where cuid='%s'", Patient.DEFAULT_PATIENT_CUID));
        if (cursor != null && cursor.moveToNext() && cursor.getCount() != 0) {
            return;
        }

        // Create record
        String sql = String.format(
                "insert into patients (`cuid`, `ssuuid`, `name`, `bed`, `sync_enabled`, `created_at`) values ('%s', null, '%s', '%s', 0, '2000-1-1 00:00:00')",
                Patient.DEFAULT_PATIENT_CUID, Patient.DEFAULT_PATIENT_NAME, Patient.DEFAULT_PATIENT_BED);
        db.execSQL(sql);
    }


    private static void dropAllTables(@NonNull SupportSQLiteDatabase db) {
        List<String> tables = new ArrayList<>();

        Cursor c = db.query("select name from sqlite_master WHERE type='table'");
        while (c.moveToNext()) {
            tables.add(c.getString(0));
        }

        for (String table : tables) {
            db.execSQL("drop table if exists " + table);
        }
    }
}
