package tw.cchi.flironedemo1.helper;

import android.app.Activity;
import android.widget.Toast;

import org.opencv.core.Point;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.Config;
import tw.cchi.flironedemo1.db.AppDatabase;
import tw.cchi.flironedemo1.db.CaptureRecord;
import tw.cchi.flironedemo1.db.Patient;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;

public class CSVExportHelper {
    private Activity activity;
    private AppDatabase database;

    public CSVExportHelper(Activity activity, AppDatabase database) {
        this.activity = activity;
        this.database = database;
    }

    public void exportAllCaptureRecords(final String filepath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder outputBuilder = new StringBuilder();
                int maxSpotCount = 0;

                Map<String, Patient> patientMap = getPatientMap();
                for (CaptureRecord captureRecord : database.captureRecordDAO().getAll()) {
                    StringBuilder rowBuilder = new StringBuilder();

                    Patient patient = patientMap.get(captureRecord.getPatientUuid());
                    if (patient == null) {
                        rowBuilder.append(String.format("%s,%s,%s,%s,%s",
                                "-", "-", captureRecord.getFilenamePrefix(),
                                captureRecord.getCreatedAt(), captureRecord.getTitle()
                        ));
                    } else {
                        rowBuilder.append(String.format("%s,%s,%s,%s,%s",
                                patient.getUuid(), patient.getName(), captureRecord.getFilenamePrefix(),
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
                        // TODO: remove this record from database
                        continue;
                    }

                    outputBuilder.append(rowBuilder).append("\n");
                }

                if (outputBuilder.length() == 0) {
                    showToastMessage("No data exported.");
                    return;
                }

                // Title row
                StringBuilder titleRowBuilder = new StringBuilder("patientUUID,patientName,filenamePrefix,takenAt,title");
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
                    showToastMessage("Exported to file: " + filepath);
                } catch (Exception e) {
                    showToastMessage("Error exporting to file: " + filepath);
                    e.printStackTrace();
                } finally {
                    try {
                        // Close the writer regardless of what happens...
                        outputWriter.close();
                    } catch (Exception e) {}
                }
            }
        }).start();
    }

    private Map<String, Patient> getPatientMap() {
        Map<String, Patient> patientMap = new HashMap<>();
        for (Patient patient : database.patientDAO().getAll()) {
            patientMap.put(patient.getUuid(), patient);
        }
        return patientMap;
    }

    private ArrayList<Double> readSpotValues(String filenamePrefix) {
        String dumpFilepath =
                AppUtils.getExportsDir() + "/" + filenamePrefix + Config.POSTFIX_THERMAL_DUMP + ".dat";
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

    private void showToastMessage(final String message) {
        if (activity.isDestroyed()) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
