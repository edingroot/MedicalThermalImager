package tw.cchi.flironedemo1.ui.dumpviewer;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import java.util.ArrayList;

import io.reactivex.Observable;
import tw.cchi.flironedemo1.di.BgThreadAvail;
import tw.cchi.flironedemo1.helper.ThermalSpotsHelper;
import tw.cchi.flironedemo1.model.ChartParameter;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.VisibleImageMask;
import tw.cchi.flironedemo1.ui.base.MvpView;

public interface DumpViewerMvpView extends MvpView {

    Observable<Object> getThermalImageViewGlobalLayouts();

    Observable<Object> getVisibleImageViewLayoutObservable();


    ThermalSpotsHelper createThermalSpotsHelper(RawThermalDump rawThermalDump);

    void resizeVisibleImageViewToThermalImage();

    void launchImagePicker(ArrayList<String> pickedFiles);


    void updateThermalImageView(@Nullable Bitmap frame);

    int getThermalImageViewHeight();


    void setVisibleImageViewVisible(boolean visible, float opacity);

    void updateVisibleImageView(@Nullable VisibleImageMask mask, boolean visibleImageAlignMode);


    void setHorizontalLineVisible(boolean visible);

    void setHorizontalLineY(int y);


    void setThermalChartVisible(boolean visible);

    @BgThreadAvail
    void updateThermalChart(ChartParameter thermalChartParameter);


    int addDumpTab(String title);

    int removeDumpTab(int index);

}
