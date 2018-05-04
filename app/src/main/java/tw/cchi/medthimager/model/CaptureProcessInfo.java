package tw.cchi.medthimager.model;

import java.util.concurrent.atomic.AtomicBoolean;

import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.utils.AppUtils;

public class CaptureProcessInfo {
    private String filepathPrefix;
    private String dumpFilepath;
    private String title;

    private AtomicBoolean flirImageCaptured = new AtomicBoolean(false);
    private AtomicBoolean thermalDumpCaptured = new AtomicBoolean(false);

    public CaptureProcessInfo() {
        String filenamePrefix = AppUtils.generateCaptureFilename();
        filepathPrefix = AppUtils.getExportsDir() + "/" + filenamePrefix;
        dumpFilepath = filepathPrefix + Constants.POSTFIX_THERMAL_DUMP + ".dat";
        title = RawThermalDump.generateTitleFromFilepath(dumpFilepath);
    }

    public String getFilepathPrefix() {
        return filepathPrefix;
    }

    public String getDumpFilepath() {
        return dumpFilepath;
    }

    public String getTitle() {
        return title;
    }

    public void setFlirImageCaptured(boolean flirImageCaptured) {
        this.flirImageCaptured.set(flirImageCaptured);
    }

    public void setThermalDumpCaptured(boolean thermalDumpCaptured) {
        this.thermalDumpCaptured.set(thermalDumpCaptured);
    }

    public boolean isCaptureProcessDone() {
        return flirImageCaptured.get() && thermalDumpCaptured.get();
    }
}
