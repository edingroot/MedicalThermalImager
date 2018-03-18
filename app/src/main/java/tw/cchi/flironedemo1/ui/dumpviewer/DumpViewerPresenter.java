package tw.cchi.flironedemo1.ui.dumpviewer;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.Config;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.di.BgThreadAvail;
import tw.cchi.flironedemo1.di.NewThread;
import tw.cchi.flironedemo1.helper.ThermalSpotsHelper;
import tw.cchi.flironedemo1.helper.ViewerTabResourcesHelper;
import tw.cchi.flironedemo1.model.ChartParameter;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;
import tw.cchi.flironedemo1.thermalproc.VisibleImageMask;
import tw.cchi.flironedemo1.ui.base.BasePresenter;

public class DumpViewerPresenter<V extends DumpViewerMvpView> extends BasePresenter<V> implements DumpViewerMvpPresenter<V> {

    @Inject AppCompatActivity activity;

    // Data models & helpers
    @Inject volatile ViewerTabResourcesHelper tabResources;
    private volatile ChartParameter thermalChartParameter;

    // States
    private int contrastRatio = 1;
    private boolean coloredMode = true;
    private boolean showingVisibleImage = false;
    private volatile boolean visibleImageAlignMode = false;
    private boolean showingChart = false;
    private int horizontalLineY = -1; // pY (on the thermal dump) of horizontal indicator on showingChart mode

    @Inject
    public DumpViewerPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);

        thermalChartParameter = new ChartParameter(ChartParameter.ChartType.MULTI_LINE_CURVE);
        thermalChartParameter.setAlpha(0.6f);

        // Wait until the view have been measured (visibility state considered)
        // Ref: https://stackoverflow.com/questions/36586146/ongloballayoutlistener-vs-postrunnable
        getMvpView().getVisibleImageViewLayoutObservable().subscribe(new Consumer<Object>() {
            @Override
            public void accept(Object o) throws Exception {
                if (showingVisibleImage) {
                    getMvpView().resizeVisibleImageViewToThermalImage();
                }
            }
        });

        // Launch image picker on activity first started
        pickImages();
        getMvpView().showToast(R.string.pick_thermal_images);
    }


    @Override
    public void pickImages() {
        getMvpView().launchImagePicker(tabResources.getThermalDumpPaths());
    }

    @Override
    public void updateDumpsAfterPick(ArrayList<String> selectedPaths) {
        final ArrayList<String> addPaths = selectedPaths;
        ArrayList<String> removePaths = new ArrayList<>(tabResources.getThermalDumpPaths());
        ArrayList<String> currentPaths = new ArrayList<>(tabResources.getThermalDumpPaths());

        for (String path : currentPaths) {
            // Selected file has already been added
            if (addPaths.contains(path)) {
                addPaths.remove(path);
                removePaths.remove(path);
            }
        }

        boolean needUpdateImageView = false;
        int currentIndex = tabResources.getCurrentIndex();
        for (String removePath : removePaths) {
            int removeIndex = tabResources.indexOf(removePath);
            int newIndex = removeThermalDump(currentIndex);

            // If the dump removing is the one that currently displaying & still have some other dumps opened,
            // then update thermal image display.
            if (currentIndex == removeIndex && newIndex != -1)
                needUpdateImageView = true;
        }

        if (needUpdateImageView) {
            getMvpView().updateThermalImageView(tabResources.getThermalBitmap(contrastRatio, coloredMode));

            // Show thermal spots
            ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
            if (thermalSpotsHelper != null)
                thermalSpotsHelper.setSpotsVisible(true);
        }

        if (addPaths.size() > 0) {
            // TODO: Fix
            // Make a short delay to avoid various async problems :P
            // and also make dumps added sequentially

            // Add thermal dumps sequentially and update chart axis on complete
            Observable.create(new ObservableOnSubscribe<String>() {
                @Override
                public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                    for (String path : addPaths) {
                        emitter.onNext(path);
                    }
                    emitter.onComplete();
                }
            }).subscribeOn(Schedulers.computation()).subscribe(new Observer<String>() {
                @Override
                public void onSubscribe(Disposable d) {
                }

                @Override
                public void onNext(String path) {
                    addThermalDump(path);
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void onComplete() {
                    if (isViewAttached()) {
                        updateThermalChartAxis();
                    }
                }
            });
        } else {
            if (tabResources.getCount() == 0)
                getMvpView().updateThermalImageView(null);
        }
    }

    /**
     * This method should also be called after the first dump added.
     */
    @Override
    public void switchDumpTab(int position) {
        // Hide all spots of the last dump, switch to new tab resources, show spots of the new dump
        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        if (thermalSpotsHelper != null)
            thermalSpotsHelper.setSpotsVisible(false);
        tabResources.setCurrentIndex(position);

        if (showingVisibleImage) {
            visibleImageAlignMode = false;
            loadAndShowVisibleImage(tabResources.getRawThermalDump());
        }

        Bitmap frame = tabResources.getThermalBitmap(contrastRatio, coloredMode);
        getMvpView().updateThermalImageView(frame);

        thermalSpotsHelper = tabResources.getThermalSpotHelper();
        if (thermalSpotsHelper != null) {
            thermalSpotsHelper.setSpotsVisible(true);
        } else {
            // Create new thermalSpotsHelper if not existed after view measured
            getMvpView().getThermalImageViewGlobalLayouts().take(1).subscribe(new Consumer<Object>() {
                @Override
                public void accept(Object o) throws Exception {
                    System.out.printf("On thermalImageView globalLayout: tabResources.getCount=%d, tabResources.currIndex=%d\n",
                            tabResources.getCount(),
                            tabResources.getCurrentIndex()
                    );
                    tabResources.addThermalSpotsHelper(getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump()));
                }
            });
        }
    }

    /**
     * @param index Index of the new active dump or -1 for no tab after removing
     * @return
     */
    @Override
    public int removeThermalDump(int index) {
        int newIndex = getMvpView().removeDumpTab(index);

        tabResources.removeResources(index, newIndex);
        removeDataFromChartParameter(thermalChartParameter, index);
        getMvpView().updateThermalChart(thermalChartParameter);

        if (tabResources.getCount() != 0) {
            switchDumpTab(newIndex);
        } else {
            getMvpView().updateThermalImageView(null);

            // Hide visibleImage and chart
            if (showingVisibleImage) toggleVisibleImage();
            if (showingChart) toggleHorizonChart();
        }
        System.gc();

        return newIndex;
    }

    @Override
    @NewThread
    public void updateVisibleImageOffset(final int offsetX, final int offsetY) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RawThermalDump rawThermalDump = tabResources.getRawThermalDump();
                rawThermalDump.setVisibleOffsetX(offsetX);
                rawThermalDump.setVisibleOffsetY(offsetY);
                rawThermalDump.save();
            }
        }).start();
    }

    @Override
    public void addThermalSpot() {
        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        int lastSpotId = thermalSpotsHelper.getLastSpotId();
        thermalSpotsHelper.addThermalSpot(lastSpotId == -1 ? 1 : lastSpotId + 1);
    }

    @Override
    public void removeLastThermalSpot() {
        tabResources.getThermalSpotHelper().removeLastThermalSpot();
    }

    @Override
    @BgThreadAvail
    public void updateHorizontalLine(final int y) {
        if (tabResources.getCount() == 0)
            return;

        // Set horizontal line location
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getMvpView().setHorizontalLineY(y);
            }
        });

        // Calculate the correspondent point on the thermal image
        int thermalDumpHeight = tabResources.getRawThermalDump().getHeight();
        double ratio = (double) thermalDumpHeight / getMvpView().getThermalImageViewHeight();
        horizontalLineY = AppUtils.trimByRange((int) (y * ratio), 1, thermalDumpHeight - 1);

        if (showingChart) {
            modifyChartParameter(thermalChartParameter, horizontalLineY);
            getMvpView().updateThermalChart(thermalChartParameter);
        }
    }

    @Override
    public void toggleVisibleImage() {
        if (tabResources.getCount() == 0)
            return;

        if (showingVisibleImage) {
            getMvpView().setVisibleImageViewVisible(false, 0);
            showingVisibleImage = visibleImageAlignMode = false;
        } else {
            if (loadAndShowVisibleImage(tabResources.getRawThermalDump())) {
                showingVisibleImage = true;
            } else {
                showingVisibleImage = visibleImageAlignMode = false;
            }
        }
    }

    @Override
    public void toggleVisibleImageAlignMode() {
        if (tabResources.getCount() == 0)
            return;

        if (visibleImageAlignMode) {
            visibleImageAlignMode = false;
        } else {
            visibleImageAlignMode = true;
            if (!showingVisibleImage) {
                // Show visible image first
                toggleVisibleImage();
            }
        }

        float opacity = visibleImageAlignMode ? Config.DUMP_VISUAL_MASK_ALPHA / 255f : 1f;
        getMvpView().setVisibleImageViewVisible(true, opacity);
    }

    @Override
    public void toggleColoredMode() {
        coloredMode = !coloredMode;
        getMvpView().updateThermalImageView(tabResources.getThermalBitmap(contrastRatio, coloredMode));
    }

    @Override
    public void toggleHorizonChart() {
        if (tabResources.getCount() == 0 || horizontalLineY == -1)
            return;

        if (showingChart) {
            getMvpView().setThermalChartVisible(false);
            getMvpView().setHorizontalLineVisible(false);
            showingChart = false;
        } else {
            modifyChartParameter(thermalChartParameter, horizontalLineY);
            getMvpView().updateThermalChart(thermalChartParameter);
            getMvpView().setThermalChartVisible(true);
            getMvpView().setHorizontalLineVisible(true);
            showingChart = true;
        }
    }

    public boolean isVisibleImageAlignMode() {
        return visibleImageAlignMode;
    }

    @Override
    public String getDumpTitle() {
        return tabResources.getRawThermalDump().getTitle();
    }

    @Override
    public ThermalSpotsHelper getThermalSpotsHelper() {
        return tabResources.getThermalSpotHelper();
    }


    @BgThreadAvail
    private void addThermalDump(final String filepath) {
        RawThermalDump thermalDump = RawThermalDump.readFromDumpFile(filepath);

        if (thermalDump != null) {
            ThermalDumpProcessor thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);

            if (horizontalLineY == -1) {
                horizontalLineY = thermalDump.getHeight() / 2;
            }
            updateHorizontalLine(horizontalLineY);

            tabResources.addResources(filepath, thermalDump, thermalDumpProcessor);
            addDumpDataToChartParameter(thermalChartParameter, thermalDump, horizontalLineY);
            getMvpView().updateThermalChart(thermalChartParameter);

            final int newIndex = getMvpView().addDumpTab(thermalDump.getTitle());
            System.out.printf("addThermalDump: currIndex=%d, newIndex=%d\n", tabResources.getCurrentIndex(), newIndex);
            if (tabResources.getCurrentIndex() != newIndex) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switchDumpTab(newIndex);
                    }
                });
            }
        } else {
            getMvpView().showSnackBar("Failed reading thermal dump");
        }
    }

    private boolean loadAndShowVisibleImage(RawThermalDump rawThermalDump) {
        getMvpView().updateVisibleImageView(null, visibleImageAlignMode);

        if (!rawThermalDump.isVisibleImageAttached()) {
            if (!rawThermalDump.attachVisibleImageMask()) {
                getMvpView().showSnackBar("Failed to read visible image. Does the jpg file with same name exist?");
                return false;
            }

            rawThermalDump.getVisibleImageMask().processFrame(activity, new VisibleImageMask.OnFrameProcessedListener() {
                @Override
                public void onFrameProcessed(final VisibleImageMask maskInstance) {
                    if (isViewAttached()) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                getMvpView().updateVisibleImageView(maskInstance, visibleImageAlignMode);
                            }
                        });
                    }
                }
            });
        } else {
            getMvpView().updateVisibleImageView(rawThermalDump.getVisibleImageMask(), visibleImageAlignMode);
        }

        return true;
    }

    /**
     * Add thermal values on specific horizontal line on the thermal dump to the chart parameter.
     * [Note] Not calling updateChartAxis() here because it will be called when all thermalDumps are added
     *
     * @param chartParameter
     * @param rawThermalDump
     * @param y
     */
    private synchronized void addDumpDataToChartParameter(ChartParameter chartParameter, RawThermalDump rawThermalDump, int y) {
        int width = rawThermalDump.getWidth();
        float[] temperaturePoints = new float[width];

        for (int i = 0; i < width; i++) {
            temperaturePoints[i] = rawThermalDump.getTemperatureAt(i, y);
        }
        chartParameter.addFloatArray(rawThermalDump.getTitle(), temperaturePoints);
    }

    /**
     * Remove data (float array) from chart parameter by index.
     *  Note: Not calling updateChartAxis() here
     *
     * @param chartParameter
     * @param index
     */
    private synchronized void removeDataFromChartParameter(ChartParameter chartParameter, int index) {
        chartParameter.removeFloatArray(index);
    }

    private void modifyChartParameter(ChartParameter chartParameter, int y) {
        ArrayList<RawThermalDump> rawThermalDumps = tabResources.getRawThermalDumps();
        for (int i = 0; i < rawThermalDumps.size(); i++) {
            RawThermalDump rawThermalDump = rawThermalDumps.get(i);
            float[] temperaturePoints = new float[rawThermalDump.getWidth()];

            for (int j = 0; j < rawThermalDump.getWidth(); j++) {
                temperaturePoints[j] = rawThermalDump.getTemperatureAt(j, y);
            }

            chartParameter.updateFloatArray(i, temperaturePoints);
        }
    }

    /**
     * Calculate max and min temperature among all thermal dumps and update chart axis.
     */
    private void updateThermalChartAxis() {
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        for (RawThermalDump rawThermalDump : tabResources.getRawThermalDumps()) {
            if (rawThermalDump.getMaxTemperature() > max)
                max = rawThermalDump.getMaxTemperature();
            if (rawThermalDump.getMinTemperature() < min)
                min = rawThermalDump.getMinTemperature();
        }

        if (max != thermalChartParameter.getAxisMax() || min != thermalChartParameter.getAxisMin()) {
            /* max = (float) Math.ceil(max) + 9;
            max -= max % 10;
            min = (float) Math.floor(min);
            min -= min % 10; */

            thermalChartParameter.setAxisMax(max);
            thermalChartParameter.setAxisMin(min);

            if (showingChart) {
                getMvpView().updateThermalChart(thermalChartParameter);
            }
        }
    }


    @Override
    public void onDetach() {
        super.onDetach();
    }

}
