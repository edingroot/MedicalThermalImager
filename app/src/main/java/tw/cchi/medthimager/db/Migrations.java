package tw.cchi.medthimager.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.database.Cursor;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class Migrations {

//    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
//        @Override
//        public void migrate(@NonNull SupportSQLiteDatabase db) {
//            // ...
//            checkAndInsertDefaultPatient(db);
//        }
//    };


    static void checkAndInsertDefaultPatient(@NonNull SupportSQLiteDatabase db) {
        // Check exists
        Cursor cursor = db.query(String.format("select cuid from patients where cuid='%s'", Patient.DEFAULT_PATIENT_CUID));
        if (cursor != null && cursor.moveToNext() && cursor.getCount() != 0) {
            return;
        }

        // Create record
        String sql = String.format(
                "insert into patients (`cuid`, `name`, `created_at`) values ('%s', '%s', '2000-1-1 00:00:00')",
                Patient.DEFAULT_PATIENT_CUID, Patient.DEFAULT_PATIENT_NAME);
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
