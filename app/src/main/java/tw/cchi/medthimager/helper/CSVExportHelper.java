package tw.cchi.medthimager.helper;

import org.opencv.core.Point;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.FileUtils;
import tw.cchi.medthimager.util.annotation.NewThread;

public class CSVExportHelper {
    private AppDatabase database;

    @Inject
    public CSVExportHelper(AppDatabase database) {
        this.database = database;
    }

    @NewThread
    public Observable<Object> exportAllCaptureRecords(final String filepath) {
        return Observable.create(emitter -> {
            StringBuilder outputBuilder = new StringBuilder();
            int maxSpotCount = 0;

            Map<String, Patient> patientMap = getPatientMap();
            for (CaptureRecord captureRecord : database.captureRecordDAO().getAll()) {
                StringBuilder rowBuilder = new StringBuilder();

                Patient patient = patientMap.get(captureRecord.getPatientCuid());
                if (patient == null) {
                    rowBuilder.append(String.format("%s,%s,%s,%s,%s",
                        "-", "-", captureRecord.getFilenamePrefix(),
                        captureRecord.getCreatedAt(), captureRecord.getTitle()
                    ));
                } else {
                    rowBuilder.append(String.format("%s,%s,%s,%s,%s",
                        patient.getCuid(), patient.getName(), captureRecord.getFilenamePrefix(),
                        captureRecord.getCreatedAt(), captureRecord.getTitle()
                    ));
                }

                ArrayList<Double> spotValues = readSpotValues(captureRecord.getFilenamePrefix());
                if (spotValues != null) {
                    if (spotValues.size() > maxSpotCount)
                        maxSpotCount = spotValues.size();

                    for (double temp : spotValues)
                        rowBuilder.append(String.format(",%.2f", temp));
                } else {
                    System.out.println("Dump not found, skip reading spotValues of: " + captureRecord.getFilenamePrefix());
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

    public Observable updateRecordsFromDumpFiles(String rootDir) {
        return Observable.create(methodEmitter -> {

            Observable.<File>create(emitter -> {
                for (File file : FileUtils.getAllFiles(rootDir)) {
                    if (FileUtils.getExtension(file.getName()).equals("dat"))
                        emitter.onNext(file);
                }
            }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(
                    file -> {
                        RawThermalDump rawThermalDump = RawThermalDump.readFromDumpFile(file.getAbsolutePath());
                        if (rawThermalDump == null) {
                            methodEmitter.onError(new Error("Error reading dump file: " + file.getAbsolutePath()));
                            return;
                        }

                        CaptureRecord captureRecord = new CaptureRecord(
                                UUID.randomUUID().toString(), rawThermalDump.getPatientCuid(),
                                rawThermalDump.getTitle(), FileUtils.removeExtension(file.getName()), null);

                        database.captureRecordDAO()
                            .insertAndAutoCreatePatient(database.patientDAO(), captureRecord);
                    },
                    e -> {
                        e.printStackTrace();
                        methodEmitter.onError(e);
                    },
                    methodEmitter::onComplete
                );

        });
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
            for (Point point : spotMarkers) {
                System.out.printf("CSV Export (%.0f, %.0f) = %.2f\n", point.x, point.y, rawThermalDump.getTemperature9Average((int) point.x, (int) point.y));
                spotValues.add(rawThermalDump.getTemperature9Average((int) point.x, (int) point.y));
            }
        }

        return spotValues;
    }

}
