package tw.cchi.medthimager.db.helper;


import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
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
     */
    public Observable addCaptureRecord(final String patientUUID, final String title, final String filenamePrefix) {
        return Observable.create(emitter ->
            database.captureRecordDAO().insertAll(new CaptureRecord(patientUUID, title, filenamePrefix)))
            .subscribeOn(Schedulers.io());
    }

}
