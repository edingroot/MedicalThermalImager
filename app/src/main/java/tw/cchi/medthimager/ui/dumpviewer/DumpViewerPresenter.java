package tw.cchi.medthimager.ui.dumpviewer;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.AppUtils;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.BgThreadAvail;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.di.UiThread;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.ChartParameter;
import tw.cchi.medthimager.model.ViewerTabResources;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.ThermalDumpProcessor;
import tw.cchi.medthimager.thermalproc.VisibleImageMask;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class DumpViewerPresenter<V extends DumpViewerMvpView> extends BasePresenter<V> implements DumpViewerMvpPresenter<V> {

    @Inject AppCompatActivity activity;

    // Data models & helpers
    @Inject volatile ViewerTabResources tabResources;
    private volatile ChartParameter thermalChartParameter;

    // States
    private int contrastRatio = 1;
    private boolean coloredMode = true;
    private boolean showingVisibleImage = false;
    private boolean visibleImageAlignMode = false;
    private boolean showingThermalSpots = true;
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

        // Only switch tab (update imageViews) when removing the last dump
        for (int i = 0; i < removePaths.size() - 1; i++) {
            removeThermalDump(tabResources.indexOf(removePaths.get(i)), false);
        }
        if (removePaths.size() > 0)
            removeThermalDump(tabResources.indexOf(removePaths.get(removePaths.size() - 1)), true);

        if (addPaths.size() > 0) {
            // Add thermal dumps "sequentially" on a background thread and update chart axis on complete
            Observable.create(new ObservableOnSubscribe<String>() {
                @Override
                public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                    for (String path : addPaths) {
                        emitter.onNext(path);
                    }
                    emitter.onComplete();
                }
            }).observeOn(Schedulers.computation())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

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
     * This may be time consuming due to tabResources.getThermalBitmap.
     *
     * This method should also be called after the first dump added (see addThermalDump()).
     */
    @UiThread
    @Override
    public synchronized void switchDumpTab(int position) {
        // Hide all spots of the last dump, switch to new tab resources, show spots of the new dump
        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        if (thermalSpotsHelper != null)
            thermalSpotsHelper.setSpotsVisible(false);
        tabResources.setCurrentIndex(position);

        // If resources of this tab haven't been loaded before,
        // which the following operations may takes more time,
        // show loading (block UI) before resources completely loaded.
        if (!tabResources.hasLoaded()) {
            getMvpView().showLoading();
        }

        // Hide loading after two tasks completed
        // Ref - about thread management of zip chain:
        //  https://stackoverflow.com/questions/44586663/why-is-rxjava-zip-operator-working-on-the-thread-which-last-emitted-value
        Observable.zip(
                loadThermalImageBitmap().subscribeOn(Schedulers.computation()),
                loadVisibleImage(tabResources.getRawThermalDump()).subscribeOn(Schedulers.computation()),
                new BiFunction<Boolean, Boolean, Boolean>() {
            @Override
            public Boolean apply(Boolean b, Boolean loadVisibleImageResult) throws Exception {
                if (showingVisibleImage) {
                    if (loadVisibleImageResult) {
                        visibleImageAlignMode = false;
                        displayVisibleImage(tabResources.getRawThermalDump());
                    } else {
                        // Hide visibleImageView
                        toggleVisibleImage();
                    }
                }

                return true;
            }
        }).observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<Boolean>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(Boolean result) {}

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                    getMvpView().hideLoading();
                    getMvpView().showSnackBar("Error occurred while switching dump tab.");
                }

                @Override
                public void onComplete() {
                    tabResources.setHasLoaded(true);

                    if (isViewAttached())
                        getMvpView().hideLoading();
                }
            });
    }

    /**
     * @param index
     * @param switchTab set to true while removing multiple dumps except the last dump
     * @return Index of the new active dump or -1 for no tab after removing
     */
    @Override
    public int removeThermalDump(int index, boolean switchTab) {
        int newIndex = getMvpView().removeDumpTab(index);

        tabResources.removeResources(index, newIndex);
        removeDataFromChartParameter(thermalChartParameter, index);
        getMvpView().updateThermalChart(thermalChartParameter);

        if (tabResources.getCount() != 0) {
            if (switchTab) {
                System.out.printf("removeThermalDump(%d), remainingCount=%d, newIndex=%d\n", index, tabResources.getCount(), newIndex);
                switchDumpTab(newIndex);
            }
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
    public void toggleThermalSpotsVisible() {
        if (tabResources.getCount() == 0)
            return;

        showingThermalSpots = !showingThermalSpots;
        tabResources.getThermalSpotHelper().setSpotsVisible(showingThermalSpots);
    }

    @Override
    public void addThermalSpot() {
        if (tabResources.getCount() == 0)
            return;

        if (!showingThermalSpots) {
            getMvpView().showSnackBar(R.string.spots_hidden);
            return;
        }

        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        int lastSpotId = thermalSpotsHelper.getLastSpotId();
        thermalSpotsHelper.addThermalSpot(lastSpotId == -1 ? 1 : lastSpotId + 1);
    }

    @Override
    public void removeLastThermalSpot() {
        if (tabResources.getCount() <= 1)
            return;

        if (!showingThermalSpots) {
            getMvpView().showSnackBar(R.string.spots_hidden);
            return;
        }

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
        if (tabResources.getRawThermalDump() != null) {
            int thermalDumpHeight = tabResources.getRawThermalDump().getHeight();
            double ratio = (double) thermalDumpHeight / getMvpView().getThermalImageViewHeight();
            horizontalLineY = AppUtils.trimByRange((int) (y * ratio), 1, thermalDumpHeight - 1);

            if (showingChart) {
                modifyChartParameter(thermalChartParameter, horizontalLineY);
                getMvpView().updateThermalChart(thermalChartParameter);
            }
        }
    }

    @Override
    public void toggleVisibleImage() {
        if (tabResources.getCount() == 0 && !showingVisibleImage)
            return;

        if (showingVisibleImage) {
            getMvpView().setVisibleImageViewVisible(false, 0);
            showingVisibleImage = visibleImageAlignMode = false;
        } else {
            if (displayVisibleImage(tabResources.getRawThermalDump())) {
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
        if (horizontalLineY == -1 || tabResources.getCount() == 0 && !showingChart)
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

    @Override
    public boolean isVisibleImageAlignMode() {
        return visibleImageAlignMode;
    }

    @Override
    public String getDumpTitle() {
        return tabResources.getRawThermalDump().getTitle();
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

    /**
     * Load thermal bitmap and create thermalSpotsHelper
     */
    private Observable<Boolean> loadThermalImageBitmap() {
        return Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(final ObservableEmitter<Boolean> emitter) throws Exception {

                Observable.create(new ObservableOnSubscribe<Bitmap>() {
                    @Override
                    public void subscribe(ObservableEmitter<Bitmap> emitter) throws Exception {
                        emitter.onNext(tabResources.getThermalBitmap(contrastRatio, coloredMode));
                        emitter.onComplete();
                    }
                }).subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Bitmap>() {
                        @Override
                        public void onSubscribe(Disposable d) {}

                        @Override
                        public void onNext(Bitmap bitmap) {
                            getMvpView().updateThermalImageView(bitmap);
                        }

                        @Override
                        public void onError(Throwable e) {
                            emitter.onError(e);
                        }

                        @Override
                        public void onComplete() {
                            // Create new thermalSpotsHelper if not existed
                            ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
                            if (thermalSpotsHelper != null) {
                                thermalSpotsHelper.setSpotsVisible(true && showingThermalSpots);
                                emitter.onNext(true);
                                emitter.onComplete();
                                return;
                            }

                            if (getMvpView().getThermalImageViewHeight() == 0) {
                                // This should be called after updateThermalImageView(), which was called in onNext() above
                                getMvpView().getThermalImageViewGlobalLayouts().take(1).subscribe(new Consumer<Object>() {
                                    @Override
                                    public void accept(Object o) throws Exception {
                                        tabResources.addThermalSpotsHelper(
                                                getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump())
                                        );
                                        emitter.onNext(true);
                                        emitter.onComplete();
                                    }
                                });
                            } else {
                                tabResources.addThermalSpotsHelper(
                                        getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump())
                                );
                                emitter.onNext(true);
                                emitter.onComplete();
                            }
                        }
                    });

            }
        });
    }

    /**
     * Attach and load visible of rawThermalDump
     *
     * PS. The proceed mask can be retrieved by rawThermalDump.getVisibleImageMask()
     *
     * @param rawThermalDump
     * @return boolean: succeed or not
     */
    private Observable<Boolean> loadVisibleImage(final RawThermalDump rawThermalDump) {
        return Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(final ObservableEmitter<Boolean> emitter) throws Exception {
                if (rawThermalDump.isVisibleImageAttached()) {
                    emitter.onNext(true);
                    emitter.onComplete();
                    return;
                }

                if (!rawThermalDump.attachVisibleImageMask()) {
                    getMvpView().showSnackBar("Failed to read visible image. Does the jpg file with same name exist?");
                    emitter.onNext(false);
                    emitter.onComplete();
                    return;
                }

                System.out.println("loadVisibleImage@start of dump: " + rawThermalDump.getTitle());
                rawThermalDump.getVisibleImageMask().processFrame(activity, new VisibleImageMask.OnFrameProcessedListener() {
                    @Override
                    public void onFrameProcessed(final VisibleImageMask maskInstance) {
                        System.out.println("loadAndShowVisibleImage@done of dump: " + rawThermalDump.getTitle());
                        emitter.onNext(true);
                        emitter.onComplete();
                    }
                });
            }
        });
    }

    private boolean displayVisibleImage(RawThermalDump rawThermalDump) {
        if (rawThermalDump.isVisibleImageAttached()) {
            getMvpView().updateVisibleImageView(rawThermalDump.getVisibleImageMask(), visibleImageAlignMode);
            return true;
        } else {
            return false;
        }
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
