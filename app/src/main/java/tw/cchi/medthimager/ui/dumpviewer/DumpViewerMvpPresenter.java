package tw.cchi.medthimager.ui.dumpviewer;

import android.support.annotation.UiThread;

import java.util.ArrayList;

import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;
import tw.cchi.medthimager.util.annotation.NewThread;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface DumpViewerMvpPresenter<V extends DumpViewerMvpView> extends MvpPresenter<V> {

    void pickDumps();

    void onDumpsPicked(ArrayList<String> selectedPaths);


    @UiThread
    boolean switchDumpTab(int position);

    int closeThermalDump(int index, boolean switchTab);

    void deleteThermalDump();

    void saveColoredThermalImage();

    void saveVisibleLightImage();

    @NewThread
    void updateVisibleLightImageOffset(int offsetX, int offsetY);

    void saveAllVisibleLightImageFromOpened();


    boolean setThermalSpotsVisible(boolean visible);

    boolean isSpotsVisible();

    void addThermalSpot();

    void removeLastThermalSpot();

    void copyThermalSpots();

    void pasteThermalSpots();

    void clearThermalSpots();

    @BgThreadCapable
    void updateHorizontalLine(int y);


    void toggleVisibleImage(boolean show);

    void toggleVisibleImageAlignMode();

    void toggleColoredMode(boolean colored);

    void toggleHorizonChart(boolean show);


    int getTabsCount();

    String getDumpTitle();

    boolean isVisibleImageAlignMode();

    boolean existCopiedSpots();


    @NewThread
    void setThImageNotSynced(RawThermalDump rawThermalDump);

    @NewThread
    void upSyncThermalImages();

}
