package tw.cchi.flironedemo1.helper;

import android.graphics.Bitmap;
import android.util.SparseArray;

import java.util.ArrayList;

import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;

public class ViewerTabResourcesHelper {
    private int currentIndex = 0;
    private volatile ArrayList<String> thermalDumpPaths = new ArrayList<>(); // manage opened dumps by path because filepicker returns selected paths
    private volatile ArrayList<RawThermalDump> rawThermalDumps = new ArrayList<>();
    private volatile ArrayList<ThermalDumpProcessor> thermalDumpProcessors = new ArrayList<>();
    private volatile ArrayList<Bitmap> thermalBitmaps = new ArrayList<>();
    private SparseArray<ThermalSpotsHelper> thermalSpotsHelpers = new SparseArray<>(); // <thermalDumpIndex, ThermalSpotsHelper>

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = currentIndex;
    }

    public int tabsCount() {
        return thermalDumpPaths.size();
    }

    public int indexOf(String thermalDumpPath) {
        return thermalDumpPaths.indexOf(thermalDumpPath);
    }

    public ArrayList<String> getThermalDumpPaths() {
        return thermalDumpPaths;
    }

    public ArrayList<RawThermalDump> getRawThermalDumps() {
        return rawThermalDumps;
    }


    public String getThermlDumpPath() {
        return thermalDumpPaths.get(currentIndex);
    }

    public RawThermalDump getRawThermalDump() {
        return rawThermalDumps.get(currentIndex);
    }

    public ThermalDumpProcessor getThermalDumpProcessor() {
        return thermalDumpProcessors.get(currentIndex);
    }

    public Bitmap getThermalBitmap() {
        return thermalBitmaps.get(currentIndex);
    }

    public ThermalSpotsHelper getThermalSpotHelper() {
        return thermalSpotsHelpers.get(currentIndex);
    }


    /**
     * Add resources of the new tab except ThermalSpotsHelper
     *
     * @param thermalDumpPath
     * @param rawThermalDump
     * @param thermalDumpProcessor
     * @param thermalBitmap
     * @return the new currentIndex
     */
    public int addResources(String thermalDumpPath, RawThermalDump rawThermalDump,
                            ThermalDumpProcessor thermalDumpProcessor, Bitmap thermalBitmap) {
        currentIndex++;

        thermalDumpPaths.add(thermalDumpPath);
        rawThermalDumps.add(rawThermalDump);
        thermalDumpProcessors.add(thermalDumpProcessor);
        thermalBitmaps.add(thermalBitmap);

        return currentIndex;
    }

    public void removeResources(int removeIndex, int newIndex) {
        thermalDumpPaths.remove(removeIndex);
        rawThermalDumps.remove(removeIndex);
        thermalDumpProcessors.remove(removeIndex);
        thermalBitmaps.remove(removeIndex);
        thermalSpotsHelpers.remove(removeIndex);
        currentIndex = newIndex;
    }

    public void setThermalSpotsHelper(ThermalSpotsHelper thermalSpotsHelper) {
        thermalSpotsHelpers.setValueAt(currentIndex, thermalSpotsHelper);
    }

}
