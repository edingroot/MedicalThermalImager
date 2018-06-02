package tw.cchi.medthimager.model;

import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.ThermalDumpUtils;

/**
 * New instance should be created every time when capture requested.
 */
public class CaptureProcessInfo {
    private Patient patient;
    private String title;
    private String filepathPrefix;
    private String flirFilepath;
    private String dumpFilepath;

    /**
     * Generate filenames based on system time and other parameters.
     */
    public CaptureProcessInfo(Patient patient) {
        this.patient = patient;

        String subdir = patient == null ? null : patient.getName();

        // Store in root of app (external) exports dir if null or empty
        subdir = subdir == null || subdir.isEmpty() ? "" : subdir + "/";

        this.filepathPrefix = AppUtils.getExportsDir() + "/" +
            subdir + ThermalDumpUtils.generateCaptureFilename();

        this.flirFilepath = filepathPrefix + Constants.POSTFIX_FLIR_IMAGE + ".jpg";
        this.dumpFilepath = filepathPrefix + Constants.POSTFIX_THERMAL_DUMP + ".dat";

        this.title = RawThermalDump.generateTitleFromFilepath(dumpFilepath);
    }

    public Patient getPatient() {
        return patient;
    }

    public String getTitle() {
        return title;
    }

    public String getFilepathPrefix() {
        return filepathPrefix;
    }

    public String getFlirFilepath() {
        return flirFilepath;
    }

    public String getDumpFilepath() {
        return dumpFilepath;
    }
}
