package tw.cchi.medthimager.model;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.inject.Inject;

import io.reactivex.disposables.Disposable;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.ThermalDumpProcessor;

public class ViewerTabResources implements Disposable {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private static final ReentrantReadWriteLock listsLock = new ReentrantReadWriteLock();
    private int currentIndex = -1;
    private boolean disposed = false;

    private ArrayList<Boolean> hasLoaded = new ArrayList<>(); // <whether the tab had been loaded before>
    private ArrayList<String> thermalDumpPaths = new ArrayList<>(); // manage opened dumps by path because filepicker returns selected paths
    private ArrayList<RawThermalDump> rawThermalDumps = new ArrayList<>();
    private ArrayList<ThermalDumpProcessor> thermalDumpProcessors = new ArrayList<>();
    private ArrayList<Bitmap> grayBitmaps = new ArrayList<>();
    private ArrayList<Bitmap> coloredBitmaps = new ArrayList<>();
    private ArrayList<ThermalSpotsHelper> thermalSpotsHelpers = new ArrayList<>();

    @Inject
    public ViewerTabResources() {
    }

    /**
     * Add resources of the new tab except ThermalSpotsHelper.
     */
    public int addResources(String thermalDumpPath, RawThermalDump rawThermalDump,
                            ThermalDumpProcessor thermalDumpProcessor) {
        listsLock.writeLock().lock();

        hasLoaded.add(false);
        thermalDumpPaths.add(thermalDumpPath);
        rawThermalDumps.add(rawThermalDump);
        thermalDumpProcessors.add(thermalDumpProcessor);
        grayBitmaps.add(null);
        coloredBitmaps.add(null);
        thermalSpotsHelpers.add(null);

        listsLock.writeLock().unlock();
        return getCount();
    }

    public void removeResources(int removeIndex, int newIndex) {
        listsLock.writeLock().lock();

        hasLoaded.remove(removeIndex);
        thermalDumpPaths.remove(removeIndex);
        rawThermalDumps.get(removeIndex).dispose();
        rawThermalDumps.remove(removeIndex);
        thermalDumpProcessors.remove(removeIndex);
        grayBitmaps.remove(removeIndex);
        coloredBitmaps.remove(removeIndex);

        if (thermalSpotsHelpers.get(removeIndex) != null)
            thermalSpotsHelpers.get(removeIndex).dispose();
        thermalSpotsHelpers.remove(removeIndex);

        currentIndex = newIndex;

        listsLock.writeLock().unlock();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        Log.i(TAG, "ViewerTabResources@setCurrentIndex=" + index);
        this.currentIndex = index;
    }

    public int getCount() {
        listsLock.readLock().lock();
        int val = thermalDumpPaths.size();
        listsLock.readLock().unlock();

        return val;
    }

    public int indexOf(String thermalDumpPath) {
        listsLock.readLock().lock();
        int val = thermalDumpPaths.indexOf(thermalDumpPath);
        listsLock.readLock().unlock();

        return val;
    }

    public ArrayList<String> getThermalDumpPaths() {
        listsLock.readLock().lock();
        ArrayList<String> val = new ArrayList<>(thermalDumpPaths);
        listsLock.readLock().unlock();

        return val;
    }

    public ArrayList<RawThermalDump> getRawThermalDumps() {
        listsLock.readLock().lock();
        ArrayList<RawThermalDump> val = new ArrayList<>(rawThermalDumps);
        listsLock.readLock().unlock();
        return val;
    }

    public RawThermalDump getRawThermalDump() {
        if (currentIndex == -1)
            return null;

        listsLock.readLock().lock();
        RawThermalDump val = rawThermalDumps.get(currentIndex);
        listsLock.readLock().unlock();

        return val;
    }

    public ThermalDumpProcessor getThermalDumpProcessor() {
        if (currentIndex == -1)
            return null;

        listsLock.readLock().lock();
        ThermalDumpProcessor val = thermalDumpProcessors.get(currentIndex);
        listsLock.readLock().unlock();

        return val;
    }

    /**
     * This may be time consuming if bitmap of this tab hasn't cached (bitmap == null).
     */
    public Bitmap getThermalBitmap(int contrastRatio, boolean colored) {
        if (currentIndex == -1)
            return null;

        Log.d(TAG, "getThermalBitmap@start");

        listsLock.readLock().lock();
        Bitmap bitmap = colored ? coloredBitmaps.get(currentIndex) : grayBitmaps.get(currentIndex);

        if (bitmap == null) {
            bitmap = getThermalDumpProcessor().getBitmap(contrastRatio, colored);
            listsLock.readLock().unlock();
            setThermalBitmap(colored, bitmap);
        } else {
            listsLock.readLock().unlock();
        }

        Log.d(TAG, "getThermalBitmap@done");
        return bitmap;
    }

    private void setThermalBitmap(boolean colored, Bitmap thermalBitmap) {
         listsLock.writeLock().lock();

        if (colored)
            coloredBitmaps.set(currentIndex, thermalBitmap);
        else
            grayBitmaps.add(currentIndex, thermalBitmap);

        listsLock.writeLock().unlock();
    }

    public ThermalSpotsHelper getThermalSpotHelper() {
        if (currentIndex == -1)
            return null;

        listsLock.readLock().lock();
        ThermalSpotsHelper val = thermalSpotsHelpers.get(currentIndex);
        listsLock.readLock().unlock();

        return val;
    }

    public void setThermalSpotsHelper(ThermalSpotsHelper thermalSpotsHelper) {
        listsLock.writeLock().lock();
        thermalSpotsHelpers.set(currentIndex, thermalSpotsHelper);
        listsLock.writeLock().unlock();
    }

    public boolean hasLoaded() {
        if (currentIndex == -1)
            return false;

        listsLock.readLock().lock();
        boolean val = hasLoaded.get(currentIndex);
        listsLock.readLock().unlock();

        return val;
    }

    public void setHasLoaded(boolean hasLoaded) {
        listsLock.writeLock().lock();
        this.hasLoaded.set(currentIndex, hasLoaded);
        listsLock.writeLock().unlock();
    }

    @Override
    public void dispose() {
        while (getCount() > 0) {
            removeResources(0, 0);
        }

        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
