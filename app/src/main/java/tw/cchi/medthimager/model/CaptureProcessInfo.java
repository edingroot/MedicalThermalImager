package tw.cchi.medthimager.model;

import android.support.annotation.Nullable;

import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.utils.AppUtils;
import tw.cchi.medthimager.utils.ThermalDumpUtils;

/**
 * New instance should be created every time when capture requested.
 */
public class CaptureProcessInfo {
    private String title;
    private String filepathPrefix;
    private String flirFilepath;
    private String dumpFilepath;

    /**
     * Generate filenames based on system time and other parameters.
     *
     * @param subdir left null or empty if store in root of app (external) exports dir
     */
    public CaptureProcessInfo(@Nullable String subdir) {
        subdir = subdir == null || subdir.isEmpty() ? "" : subdir + "/";

        this.filepathPrefix = AppUtils.getExportsDir() + "/" +
            subdir + ThermalDumpUtils.generateCaptureFilename();

        this.flirFilepath = filepathPrefix + Constants.POSTFIX_FLIR_IMAGE + ".jpg";
        this.dumpFilepath = filepathPrefix + Constants.POSTFIX_THERMAL_DUMP + ".dat";

        this.title = RawThermalDump.generateTitleFromFilepath(dumpFilepath);
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
