package tw.cchi.flironedemo1.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;

import tw.cchi.flironedemo1.db.converter.DateConverter;

@Database(entities = {
        Patient.class,
        ThermalDump.class
}, version = 1)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "appdb.db";
    private static AppDatabase instance;

    public abstract PatientDAO patientDAO();

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
                .build();
    }
}
