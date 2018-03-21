package tw.cchi.medthimager.ui.dumpviewer;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import java.util.ArrayList;

import io.reactivex.Observable;
import tw.cchi.medthimager.di.BgThreadAvail;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.ChartParameter;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.VisibleImageMask;
import tw.cchi.medthimager.ui.base.MvpView;

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
