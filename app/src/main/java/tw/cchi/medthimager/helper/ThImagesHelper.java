package tw.cchi.medthimager.helper;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Constants;
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

    public Observable deleteInvalidCaptureRecords() {
        return Observable.create(emitter -> {
            for (CaptureRecord record : db.captureRecordDAO().getAll()) {
                String filepathPrefix = AppUtils.getExportsDir() + "/" + record.getFilenamePrefix();
                File dumpFile = new File(filepathPrefix + Constants.POSTFIX_THERMAL_DUMP + ".dat");
                File flirFile = new File(filepathPrefix + Constants.POSTFIX_FLIR_IMAGE + ".jpg");

                Log.i(TAG, "Checking record: " + dumpFile.getAbsolutePath());
                if (!dumpFile.exists() || !flirFile.exists()) {
                    db.captureRecordDAO().delete(record);
                    Log.i(TAG, "Deleting record: " + dumpFile.getAbsolutePath());
                }
            }

            emitter.onComplete();
        }).subscribeOn(Schedulers.io());
    }

    public Observable updateRecordsFromDumpFiles() {
        return Observable.create(mEmitter -> {
            String rootDir = AppUtils.getExportsDir();

            ArrayList<File> files = new ArrayList<>();
            for (File file : FileUtils.getAllFiles(rootDir)) {
                if (FileUtils.getExtension(file.getName()).equals("dat"))
                    files.add(file);
            }
            Flowable<File> filesFlowable = Flowable.fromArray(files.toArray(new File[files.size()]));

            filesFlowable.parallel()
                .runOn(Schedulers.io())
                .map(file -> {
                    Log.i(TAG, "Checking file: " + file.getAbsolutePath());

                    RawThermalDump rawThermalDump = RawThermalDump.readFromDumpFile(file.getAbsolutePath());
                    if (rawThermalDump == null) {
                        Log.e(TAG, "Error reading dump file: " + file.getAbsolutePath());
                    } else {
                        try {
                            findOrInsertRecordFromThermalDump(rawThermalDump).blockingSubscribe();
                        } catch (Error e) {
                            // Ignore error
                            e.printStackTrace();
                        }
                    }

                    return Observable.just(true);
                })
                .sequential()
                .subscribe(
                    o -> {},
                    e -> {},
                    mEmitter::onComplete
                );
        }).subscribeOn(Schedulers.io());
    }

    /**
     * For backward supporting raw thermal dump file format < v4.
     */
    public Observable<CaptureRecord> findOrInsertRecordFromThermalDump(@NonNull RawThermalDump thermalDump) {
        return Observable.<CaptureRecord>create(emitter -> {
            CaptureRecord captureRecord;

            if (thermalDump.getRecordUuid() != null) {
                captureRecord = db.captureRecordDAO().get(thermalDump.getRecordUuid());
            } else {
                captureRecord = db.captureRecordDAO()
                        .findByProps(thermalDump.getPatientCuid(), thermalDump.getTitle());
            }

            if (captureRecord == null) {
                String filenamePrefix = extractFilenamePrefix(thermalDump.getFilepath());
                Date capturedAt = thermalDump.getCaptureTimestamp();

                if (capturedAt == null) {
                    capturedAt = thermalDump.getCaptureTimestamp();
                    if (capturedAt == null) {
                        File flirImg = new File(extractFilepathPrefix(thermalDump.getFilepath()) + Constants.POSTFIX_FLIR_IMAGE + ".jpg");
                        if (flirImg.exists()) {
                            capturedAt = new Date(flirImg.lastModified());
                        } else {
                            File dumpFile = new File(thermalDump.getFilepath());
                            capturedAt = new Date(dumpFile.lastModified());
                        }
                    }
                }

                captureRecord = new CaptureRecord(
                        UUID.randomUUID().toString(),
                        thermalDump.getPatientCuid(),
                        thermalDump.getTitle(),
                        filenamePrefix, null,
                        capturedAt, false);

                db.captureRecordDAO().insertAndAutoCreatePatient(db.patientDAO(), captureRecord);
            }

            emitter.onNext(captureRecord);
            emitter.onComplete();
        }).subscribeOn(Schedulers.io());
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
                } else {
                    Log.e(TAG, "Failed to extract visible light image.");
                    emitter.onNext(false);
                    emitter.onComplete();
                }
            });
        }).subscribeOn(Schedulers.computation());
    }

    /**
     * Output example: "Unspecified/0602-155558-6"
     *
     * See also {@link tw.cchi.medthimager.model.CaptureProcessInfo}
     */
    public static String extractFilenamePrefix(String filePath) {
        return extractFilepathPrefix(filePath).replace(AppUtils.getExportsDir() + "/", "");
    }

    /**
     * Output example: "/storage/emulated/0/flirEx1/Unspecified/0602-155558-6"
     *
     * See also {@link tw.cchi.medthimager.model.CaptureProcessInfo}
     */
    public static String extractFilepathPrefix(String filePath) {
        String prefix = FileUtils.removeExtension(filePath);
        return prefix.substring(0, prefix.lastIndexOf("_"));
    }
}
