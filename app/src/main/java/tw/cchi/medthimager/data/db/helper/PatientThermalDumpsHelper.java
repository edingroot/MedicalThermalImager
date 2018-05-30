package tw.cchi.medthimager.data.db.helper;


import android.support.annotation.Nullable;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.db.model.CaptureRecord;

public class PatientThermalDumpsHelper {
    private AppDatabase database;

    @Inject
    public PatientThermalDumpsHelper(AppDatabase database) {
        this.database = database;
    }

    /**
     * @param patientCuid null if no patient selected
     */
    public Observable addCaptureRecord(final String patientCuid, final String title, final String filenamePrefix,@Nullable String contishootGroup) {
        return Observable.create(emitter -> {
                database.captureRecordDAO().insertAll(new CaptureRecord(patientCuid, title, filenamePrefix, contishootGroup));
                emitter.onComplete();
            }).subscribeOn(Schedulers.io());
    }

}
