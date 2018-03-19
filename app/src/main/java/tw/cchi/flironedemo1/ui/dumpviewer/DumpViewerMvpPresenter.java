package tw.cchi.flironedemo1.ui.dumpviewer;

import java.util.ArrayList;

import tw.cchi.flironedemo1.di.BgThreadAvail;
import tw.cchi.flironedemo1.di.NewThread;
import tw.cchi.flironedemo1.helper.ThermalSpotsHelper;
import tw.cchi.flironedemo1.ui.base.MvpPresenter;

public interface DumpViewerMvpPresenter<V extends DumpViewerMvpView> extends MvpPresenter<V> {

    void pickImages();

    void updateDumpsAfterPick(ArrayList<String> selectedPaths);


    void switchDumpTab(int position);

    int removeThermalDump(int index);

    @NewThread
    void updateVisibleImageOffset(int offsetX, int offsetY);


    void toggleThermalSpotsVisible();

    void addThermalSpot();

    void removeLastThermalSpot();

    @BgThreadAvail
    void updateHorizontalLine(int y);


    void toggleVisibleImage();

    void toggleVisibleImageAlignMode();

    void toggleColoredMode();

    void toggleHorizonChart();


    boolean isVisibleImageAlignMode();

    String getDumpTitle();

    ThermalSpotsHelper getThermalSpotsHelper();

}
