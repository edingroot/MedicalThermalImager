package tw.cchi.medthimager.helper;

import android.util.Log;

import org.opencv.core.Point;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.ui.base.MvpView;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.annotation.NewThread;

public class CSVExportHelper {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private AppDatabase database;

    @Inject ThImagesHelper thImagesHelper;

    @Inject
    public CSVExportHelper(AppDatabase database) {
        this.database = database;
    }

    @NewThread
    public Observable<Object> exportAllCaptureRecords(final MvpView mvpView, final String filepath) {
        return Observable.create(emitter -> {
            StringBuilder outputBuilder = new StringBuilder();
            int maxSpotCount = 0;

            // Update capture records before exporting csv
            Log.i(TAG, "Updating capture records");
            thImagesHelper.updateRecordsFromDumpFiles().blockingSubscribe();

            Map<String, Patient> patientMap = getPatientMap();
            List<CaptureRecord> captureRecords = database.captureRecordDAO().getAll();

            if (captureRecords.size() == 0) {
                mvpView.showToast("No capture record found");
                emitter.onComplete();
                return;
            } else {
                mvpView.showToast("Exporting " + captureRecords.size() + " records to csv...");
            }

            for (CaptureRecord captureRecord : captureRecords) {
                StringBuilder rowBuilder = new StringBuilder();
                String filenamePrefix = ThImagesHelper.extractFilenamePrefix(captureRecord.getFilepathPrefix());

                Patient patient = patientMap.get(captureRecord.getPatientCuid());
                if (patient == null) {
                    rowBuilder.append(String.format("%s,%s,%s,%s,%s",
                            "-", "-", filenamePrefix,
                            captureRecord.getCreatedAt(), captureRecord.getTitle()
                    ));
                } else {
                    rowBuilder.append(String.format("%s,%s,%s,%s,%s",
                            patient.getCuid(), patient.getName(), filenamePrefix,
                            captureRecord.getCreatedAt(), captureRecord.getTitle()
                    ));
                }

                ArrayList<Double> spotValues = readSpotValues(filenamePrefix);
                if (spotValues != null) {
                    if (spotValues.size() > maxSpotCount)
                        maxSpotCount = spotValues.size();

                    for (double temp : spotValues)
                        rowBuilder.append(String.format(",%.2f", temp));
                } else {
                    Log.i(TAG, "Dump not found, skip reading spotValues of: " + filenamePrefix);
                    // TODO: remove this record from database?
                    continue;
                }

                outputBuilder.append(rowBuilder).append("\n");
            }

            if (outputBuilder.length() == 0) {
                emitter.onError(new Error("No data exported."));
                return;
            }

            // Title row
            StringBuilder titleRowBuilder = new StringBuilder("patientCuid,patientName,filenamePrefix,takenAt,title");
            for (int i = 1; i <= maxSpotCount; i++) {
                titleRowBuilder.append(",spot").append(i);
            }
            titleRowBuilder.append("\n");
            outputBuilder.insert(0, titleRowBuilder);

            // Write to file
            BufferedWriter outputWriter = null;
            File outputFile = new File(filepath);
            try {
                outputWriter = new BufferedWriter(new FileWriter(outputFile));
                outputWriter.write(outputBuilder.toString());
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            } finally {
                try {
                    // Close the writer regardless of what happens...
                    if (outputWriter != null)
                        outputWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }).subscribeOn(Schedulers.computation());
    }

    private Map<String, Patient> getPatientMap() {
        Map<String, Patient> patientMap = new HashMap<>();
        for (Patient patient : database.patientDAO().getAll()) {
            patientMap.put(patient.getCuid(), patient);
        }
        return patientMap;
    }

    private ArrayList<Double> readSpotValues(String filenamePrefix) {
        String dumpFilepath =
                AppUtils.getExportsDir() + "/" + filenamePrefix + Constants.POSTFIX_THERMAL_DUMP + ".dat";
        RawThermalDump rawThermalDump = RawThermalDump.readFromDumpFile(dumpFilepath);
        if (rawThermalDump == null)
            return null;

        ArrayList<Double> spotValues = new ArrayList<>();
        ArrayList<Point> spotMarkers = rawThermalDump.getSpotMarkers();
        if (spotMarkers != null) {
            for (Point point : spotMarkers)
                spotValues.add(rawThermalDump.getTemperature9Average((int) point.x, (int) point.y));
        }

        return spotValues;
    }

}
