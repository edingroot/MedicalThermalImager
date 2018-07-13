package tw.cchi.medthimager.data.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import tw.cchi.medthimager.data.db.converter.DateConverter;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.CaptureRecordDAO;
import tw.cchi.medthimager.data.db.model.CaptureRecordTags;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.db.model.PatientDAO;

@Database(entities = {
        Patient.class,
        CaptureRecord.class,
        CaptureRecordTags.class
}, version = 5)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {
    private static final String TAG = AppDatabase.class.getSimpleName();
    private static final String DATABASE_NAME = "appdb.db";
    private static AppDatabase instance;

    public abstract PatientDAO patientDAO();
    public abstract CaptureRecordDAO captureRecordDAO();

    public static AppDatabase getInstance(final Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null)
                    instance = buildDatabase(context);
            }
        }
        return instance;
    }

    private static AppDatabase buildDatabase(Context context) {
        Log.i(TAG, "Building database: " + context.getDatabasePath(DATABASE_NAME));

        return Room.databaseBuilder(context, AppDatabase.class, DATABASE_NAME)
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        // Called when the database is created for the first time.
                        // This is called after all the ables are created.
                        Migrations.checkAndInsertDefaultPatient(db);
                    }

                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        // Do something every time database is open
                        Migrations.checkAndInsertDefaultPatient(db);
                    }
                })
                .addMigrations(Migrations.MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build();
    }
}
