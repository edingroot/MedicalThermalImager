package tw.cchi.flironedemo1.helper;

import android.graphics.Bitmap;
import android.util.SparseArray;

import java.util.ArrayList;

import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;

public class ViewerTabResourcesHelper {
    private final Object listsLock = new Object();
    private int currentIndex = -1;

    private volatile ArrayList<String> thermalDumpPaths = new ArrayList<>(); // manage opened dumps by path because filepicker returns selected paths
    private volatile ArrayList<RawThermalDump> rawThermalDumps = new ArrayList<>();
    private volatile ArrayList<ThermalDumpProcessor> thermalDumpProcessors = new ArrayList<>();
    private volatile ArrayList<Bitmap> thermalBitmaps = new ArrayList<>();
    private SparseArray<ThermalSpotsHelper> thermalSpotsHelpers = new SparseArray<>(); // <thermalDumpIndex, ThermalSpotsHelper>

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public int getCount() {
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
     * @return {{@link #getCount()}}
     */
    public int addResources(String thermalDumpPath, RawThermalDump rawThermalDump,
                            ThermalDumpProcessor thermalDumpProcessor, Bitmap thermalBitmap) {
        synchronized (listsLock) {
            thermalDumpPaths.add(thermalDumpPath);
            rawThermalDumps.add(rawThermalDump);
            thermalDumpProcessors.add(thermalDumpProcessor);
            thermalBitmaps.add(thermalBitmap);
        }
        return getCount();
    }

    public void removeResources(int removeIndex, int newIndex) {
        synchronized (listsLock) {
            thermalDumpPaths.remove(removeIndex);
            rawThermalDumps.remove(removeIndex);
            thermalDumpProcessors.remove(removeIndex);
            thermalBitmaps.remove(removeIndex);
            thermalSpotsHelpers.remove(removeIndex);
        }
        currentIndex = newIndex;
    }

    public void addThermalSpotsHelper(ThermalSpotsHelper thermalSpotsHelper) {
        thermalSpotsHelpers.append(currentIndex, thermalSpotsHelper);
    }

}
