package tw.cchi.medthimager.ui.dumpviewer;

import java.util.ArrayList;

import tw.cchi.medthimager.di.BgThreadAvail;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.di.UiThread;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface DumpViewerMvpPresenter<V extends DumpViewerMvpView> extends MvpPresenter<V> {

    void pickImages();

    void updateDumpsAfterPick(ArrayList<String> selectedPaths);


    @UiThread
    void switchDumpTab(int position);

    int removeThermalDump(int index, boolean switchTab);

    @NewThread
    void updateVisibleImageOffset(int offsetX, int offsetY);

    void saveColoredThermalImage();


    void toggleThermalSpotsVisible();

    void addThermalSpot();

    void removeLastThermalSpot();

    void copyThermalSpots();

    void pasteThermalSpots();

    @BgThreadAvail
    void updateHorizontalLine(int y);


    void toggleVisibleImage();

    void toggleVisibleImageAlignMode();

    void toggleColoredMode();

    void toggleHorizonChart();


    int getTabsCount();

    String getDumpTitle();

    boolean isVisibleImageAlignMode();

    boolean isSpotsVisible();

    boolean existCopiedSpots();

}
