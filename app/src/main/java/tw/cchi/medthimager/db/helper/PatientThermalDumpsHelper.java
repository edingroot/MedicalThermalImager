package tw.cchi.medthimager.db.helper;


import javax.inject.Inject;

import tw.cchi.medthimager.db.AppDatabase;
import tw.cchi.medthimager.db.CaptureRecord;

public class PatientThermalDumpsHelper {
    private AppDatabase database;

    @Inject
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
