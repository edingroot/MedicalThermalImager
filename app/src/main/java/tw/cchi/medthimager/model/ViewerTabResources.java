package tw.cchi.medthimager.model;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import java.util.ArrayList;

import javax.inject.Inject;

import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.ThermalDumpProcessor;

public class ViewerTabResources {
    private final Object listsLock = new Object();
    private int currentIndex = -1;

    private ArrayList<Boolean> hasLoaded = new ArrayList(); // <whether the tab had been loaded before>
    private ArrayList<String> thermalDumpPaths = new ArrayList<>(); // manage opened dumps by path because filepicker returns selected paths
    private ArrayList<RawThermalDump> rawThermalDumps = new ArrayList<>();
    private ArrayList<ThermalDumpProcessor> thermalDumpProcessors = new ArrayList<>();
    private ArrayList<Bitmap> grayBitmaps = new ArrayList<>();
    private ArrayList<Bitmap> coloredBitmaps = new ArrayList<>();
    private SparseArray<ThermalSpotsHelper> thermalSpotsHelpers = new SparseArray<>(); // <tabIndex, ThermalSpotHelper>

    @Inject
    public ViewerTabResources() {
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        System.out.println("ViewerTabResources@setCurrentIndex=" + index);
        this.currentIndex = index;
    }

    public int getCount() {
        synchronized (listsLock) {
            return thermalDumpPaths.size();
        }
    }

    public int indexOf(String thermalDumpPath) {
        synchronized (listsLock) {
            return thermalDumpPaths.indexOf(thermalDumpPath);
        }
    }

    public ArrayList<String> getThermalDumpPaths() {
        synchronized (listsLock) {
            return thermalDumpPaths;
        }
    }

    public ArrayList<RawThermalDump> getRawThermalDumps() {
        synchronized (listsLock) {
            return rawThermalDumps;
        }
    }

    @Nullable
    public String getThermlDumpPath() {
        if (currentIndex != -1)
            return null;

        synchronized (listsLock) {
            return thermalDumpPaths.get(currentIndex);
        }
    }

    @Nullable
    public RawThermalDump getRawThermalDump() {
        if (currentIndex == -1)
            return null;

        synchronized (listsLock) {
            return rawThermalDumps.get(currentIndex);
        }
    }

    @Nullable
    public ThermalDumpProcessor getThermalDumpProcessor() {
        if (currentIndex == -1)
            return null;

        synchronized (listsLock) {
            return thermalDumpProcessors.get(currentIndex);
        }
    }

    /**
     * This may be time consuming if bitmap of this tab hasn't cached (bitmap == null).
     */
    @Nullable
    public Bitmap getThermalBitmap(int contrastRatio, boolean colored) {
        if (currentIndex == -1)
            return null;

        System.out.println("getThermalBitmap@start");

        Bitmap bitmap;
        synchronized (listsLock) {
            bitmap = colored ? coloredBitmaps.get(currentIndex) : grayBitmaps.get(currentIndex);
        }
        if (bitmap == null) {
            bitmap = getThermalDumpProcessor().getBitmap(contrastRatio, colored);
            setThermalBitmap(colored, bitmap);
        }

        System.out.println("getThermalBitmap@done");
        return bitmap;
    }

    private void setThermalBitmap(boolean colored, Bitmap thermalBitmap) {
        synchronized (listsLock) {
            if (colored)
                coloredBitmaps.set(currentIndex, thermalBitmap);
            else
                grayBitmaps.add(currentIndex, thermalBitmap);
        }
    }

    @Nullable
    public ThermalSpotsHelper getThermalSpotHelper() {
        System.out.print("ViewerTabResources@getThermalSpotHelper, keys: ");
        for (int i = 0; i < thermalSpotsHelpers.size(); i++)
            System.out.printf("%d ", thermalSpotsHelpers.keyAt(i));
        System.out.println();

        if (currentIndex == -1) {
            System.out.printf("ViewerTabResources@getThermalSpotHelper, tabCount=%d, currentIndex=%d, helpersCount=%d, returnNull=true\n",
                getCount(), currentIndex, thermalSpotsHelpers.size());
            return null;
        }

        synchronized (listsLock) {
            ThermalSpotsHelper helper = thermalSpotsHelpers.get(currentIndex);
            System.out.printf("ViewerTabResources@getThermalSpotHelper, tabCount=%d, currentIndex=%d, helpersCount=%d, returnNull=%b\n",
                getCount(), currentIndex, thermalSpotsHelpers.size(), helper == null);

            return helper;
        }
    }

    public void setThermalSpotsHelper(ThermalSpotsHelper thermalSpotsHelper) {
        synchronized (listsLock) {
            thermalSpotsHelpers.put(currentIndex, thermalSpotsHelper);
        }
    }

    /**
     * Add resources of the new tab except ThermalSpotsHelper.
     *
     * @param thermalDumpPath
     * @param rawThermalDump
     * @param thermalDumpProcessor
     * @return
     */
    public int addResources(String thermalDumpPath, RawThermalDump rawThermalDump,
                            ThermalDumpProcessor thermalDumpProcessor) {
        synchronized (listsLock) {
            thermalDumpPaths.add(thermalDumpPath);
            rawThermalDumps.add(rawThermalDump);
            thermalDumpProcessors.add(thermalDumpProcessor);
            grayBitmaps.add(null);
            coloredBitmaps.add(null);
            hasLoaded.add(false);
        }
        return getCount();
    }

    public void removeResources(int removeIndex, int newIndex) {
        synchronized (listsLock) {
            thermalDumpPaths.remove(removeIndex);
            rawThermalDumps.remove(removeIndex);
            thermalDumpProcessors.remove(removeIndex);
            grayBitmaps.remove(removeIndex);
            coloredBitmaps.remove(removeIndex);
            hasLoaded.remove(removeIndex);

            if (getThermalSpotHelper() != null) {
                getThermalSpotHelper().dispose();
                thermalSpotsHelpers.remove(removeIndex);
            }
        }
        currentIndex = newIndex;
    }

    public boolean hasLoaded() {
        if (currentIndex == -1)
            return false;

        synchronized (listsLock) {
            return hasLoaded.get(currentIndex);
        }
    }

    public void setHasLoaded(boolean hasLoaded) {
        synchronized (listsLock) {
            this.hasLoaded.set(currentIndex, hasLoaded);
        }
    }
}
