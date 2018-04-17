package tw.cchi.medthimager.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.support.annotation.NonNull;

import tw.cchi.medthimager.db.converter.DateConverter;

@Database(entities = {
        Patient.class,
        CaptureRecord.class
}, version = 1)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {
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
        return Room.databaseBuilder(context,
            AppDatabase.class, DATABASE_NAME)
            .addCallback(new RoomDatabase.Callback() {
                @Override
                public void onCreate (@NonNull SupportSQLiteDatabase db){
                    String sql = String.format("insert into patients (`uuid`, `name`, `created_at`) values " +
                        "('', 'Not Specified', '2000-1-1 00:00:00')", Patient.DEFAULT_PATIENT_UUID);

                    db.execSQL(sql);
                }

                @Override
                public void onOpen (@NonNull SupportSQLiteDatabase db){
                    // Do something every time database is open
                }
            }).build();
    }
}
