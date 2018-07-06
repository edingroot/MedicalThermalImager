package tw.cchi.medthimager.helper;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.util.UUID;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.VisibleImageExtractor;
import tw.cchi.medthimager.thermalproc.VisibleImageMask;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.FileUtils;
import tw.cchi.medthimager.util.ImageUtils;

public class ThImagesHelper {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();
    private AppDatabase db;

    @Inject VisibleImageExtractor visibleImageExtractor;

    @Inject
    public ThImagesHelper(AppDatabase db) {
        this.db = db;
    }

    public Observable updateRecordsFromDumpFiles(String rootDir) {
        return Observable.create(methodEmitter -> {
            for (File file : FileUtils.getAllFiles(rootDir)) {
                if (FileUtils.getExtension(file.getName()).equals("dat")) {
                    RawThermalDump rawThermalDump = RawThermalDump.readFromDumpFile(file.getAbsolutePath());
                    if (rawThermalDump == null)
                        Log.e(TAG, "Error reading dump file: " + file.getAbsolutePath());
                    else
                        findOrInsertRecordFromThermalDump(rawThermalDump).blockingSubscribe();
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    public Observable<CaptureRecord> findOrInsertRecordFromThermalDump(@NonNull RawThermalDump rawThermalDump) {
        return Observable.<CaptureRecord>create(emitter -> {
            CaptureRecord captureRecord = db.captureRecordDAO().findByProps(
                    rawThermalDump.getPatientCuid(), rawThermalDump.getTitle());

            if (captureRecord == null) {
                String filenamePrefix = extractFilenamePrefix(rawThermalDump.getFilepath());
                captureRecord = new CaptureRecord(
                        UUID.randomUUID().toString(), rawThermalDump.getPatientCuid(),
                        rawThermalDump.getTitle(), filenamePrefix, null);
                db.captureRecordDAO().insertAndAutoCreatePatient(db.patientDAO(), captureRecord);
            }

            emitter.onNext(captureRecord);
            emitter.onComplete();
        }).subscribeOn(Schedulers.io());
    }

    /**
     * See also {@link tw.cchi.medthimager.model.CaptureProcessInfo}
     */
    public String extractFilenamePrefix(String filePath) {
        String prefix = FileUtils.removeExtension(filePath);
        prefix = prefix.substring(0, prefix.lastIndexOf("_"));
        return prefix.replace(AppUtils.getExportsDir() + "/", "");
    }

    public Observable<Boolean> extractVisibleImage(RawThermalDump rawThermalDump) {
        return Observable.<Boolean>create(emitter -> {
            visibleImageExtractor.extractImage(rawThermalDump.getFlirImagePath(), visibleImage -> {
                if (visibleImage != null) {
                    rawThermalDump.attachVisibleImageMask(visibleImage, 0, 0);
                    VisibleImageMask visibleImageMask = rawThermalDump.getVisibleImageMask();
                    if (visibleImageMask != null &&
                        ImageUtils.saveBitmap(visibleImageMask.getAlignedVisibleBitmap(), rawThermalDump.getVisibleImagePath())) {
                        emitter.onNext(true);
                        emitter.onComplete();
                    }
                }

                Log.e(TAG, "Failed to extract visible light image.");
                emitter.onNext(false);
                emitter.onComplete();
            });
        }).subscribeOn(Schedulers.computation());
    }
}
