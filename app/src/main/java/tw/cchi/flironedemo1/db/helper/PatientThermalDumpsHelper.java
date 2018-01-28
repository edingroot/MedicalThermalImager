package tw.cchi.flironedemo1.db.helper;


import tw.cchi.flironedemo1.db.AppDatabase;
import tw.cchi.flironedemo1.db.CaptureRecord;

public class PatientThermalDumpsHelper {
    private AppDatabase database;

    public PatientThermalDumpsHelper(AppDatabase database) {
        this.database = database;
    }

    /**
     * @param patientUUID null if no patient selected
     * @param filenamePrefix
     * @return
     */
    public void addCaptureRecord(final String patientUUID, final String title, final String filenamePrefix) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                database.captureRecordDAO().insertAll(new CaptureRecord(patientUUID, title, filenamePrefix));
            }
        }).start();
    }

}
