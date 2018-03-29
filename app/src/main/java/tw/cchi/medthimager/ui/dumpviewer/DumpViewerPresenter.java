package tw.cchi.medthimager.ui.dumpviewer;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;

import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.di.NewThread;
import tw.cchi.medthimager.di.UiThread;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.ChartParameter;
import tw.cchi.medthimager.model.ViewerTabResources;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.ThermalDumpProcessor;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.utils.CommonUtils;
import tw.cchi.medthimager.utils.ImageUtils;

public class DumpViewerPresenter<V extends DumpViewerMvpView> extends BasePresenter<V> implements DumpViewerMvpPresenter<V> {

    @Inject AppCompatActivity activity;

    // Data models & helpers
    @Inject volatile ViewerTabResources tabResources;
    private volatile ChartParameter thermalChartParameter;
    private ArrayList<org.opencv.core.Point> copiedSpotMarkers;

    // States
    private int contrastRatio = 1;
    private boolean coloredMode = true;
    private boolean showingVisibleImage = false;
    private boolean visibleImageAlignMode = false;
    private boolean showingThermalSpots = true;
    private boolean showingChart = false;
    private int horizontalLineY = -1; // pY (on the thermal dump) of horizontal indicator on showingChart mode

    private Disposable switchDumpTabTask;

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
        getMvpView().getVisibleImageViewLayoutObservable().subscribe(o -> {
            if (showingVisibleImage) {
                getMvpView().resizeVisibleImageViewToThermalImage();
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
        getMvpView().showLoading();

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
            Observable.<String>create(emitter -> {
                for (String path : addPaths)
                    emitter.onNext(path);
                emitter.onComplete();
            }).observeOn(Schedulers.computation()).subscribe(
                this::addThermalDump, // onNext
                Throwable::printStackTrace, // onError
                () -> { // onComplete
                    if (isViewAttached()) {
                        updateThermalChartAxis();

                        // Loading may have been hidden in switchDumpTab() in addThermalDump()
                        getMvpView().hideLoading();
                    }
                }
            );
        } else {
            if (tabResources.getCount() == 0)
                getMvpView().updateThermalImageView(null);

            // Loading may have been hidden in switchDumpTab() in removeThermalDump()
            getMvpView().hideLoading();
        }
    }

    /**
     * This may be time consuming due to tabResources.getThermalBitmap.
     *
     * This method should also be called after the first dump added (see addThermalDump()).
     *
     * @return false to reject tab switching
     */
    @UiThread
    @Override
    public synchronized boolean switchDumpTab(int position) {
        if (switchDumpTabTask != null && !switchDumpTabTask.isDisposed()) {
            System.out.printf("switchDumpTab(%d)@rejected\n", position);
            return false;
        }
        System.out.printf("switchDumpTab(%d)@locked\n", position);

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
        switchDumpTabTask = Observable.zip(
            loadThermalImageBitmap().subscribeOn(Schedulers.computation()),
            loadVisibleImage(tabResources.getRawThermalDump()).subscribeOn(Schedulers.computation()),
            (b, loadVisibleImageResult) -> {
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
        ).observeOn(AndroidSchedulers.mainThread()).subscribe(
            b -> {},
            e -> {
                e.printStackTrace();
                getMvpView().hideLoading();
                getMvpView().showSnackBar("Error occurred while switching dump tab.");
            },
            () -> {
                tabResources.setHasLoaded(true);

                if (isViewAttached())
                    getMvpView().hideLoading();

                System.out.printf("switchDumpTab(%d)@unlocked\n", position);
            }
        );

        return true;
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
                switchDumpTab(newIndex);
            }
        } else {
            getMvpView().updateThermalImageView(null);

            // Hide visibleImage and chart
            if (showingVisibleImage) toggleVisibleImage();
            if (showingChart) toggleHorizonChart();
        }

        return newIndex;
    }

    @Override
    @NewThread
    public void updateVisibleImageOffset(final int imageViewOffsetX, final int imageViewOffsetY) {
        if (tabResources.getCount() == 0)
            return;

        Observable.create(emitter -> {
            double ratio = 10 * tabResources.getRawThermalDump().getHeight() / getMvpView().getThermalImageViewHeight();
            int dumpPixelOffsetX = (int) (imageViewOffsetX * ratio);
            int dumpPixelOffsetY = (int) (imageViewOffsetY * ratio);

            RawThermalDump rawThermalDump = tabResources.getRawThermalDump();
            rawThermalDump.setVisibleOffsetX(dumpPixelOffsetX);
            rawThermalDump.setVisibleOffsetY(dumpPixelOffsetY);
            rawThermalDump.save();

        }).subscribeOn(Schedulers.io()).subscribe();
    }

    @NewThread
    @Override
    public void saveColoredThermalImage() {
        if (tabResources.getCount() == 0)
            return;

        String dumpPath = tabResources.getRawThermalDump().getFilepath();
        String exportPath = dumpPath.substring(0, dumpPath.lastIndexOf("_")) + Config.POSTFIX_COLORED_IMAGE + ".png";

        Observable.create(emitter -> {
            if (ImageUtils.saveBitmap(tabResources.getThermalBitmap(contrastRatio, coloredMode), exportPath)) {
                emitter.onComplete();
            } else {
                emitter.onError(new Error());
            }
        }).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                o -> {},
                e -> {
                    if (isViewAttached())
                        getMvpView().showToast(R.string.dump_failed);
                },
                () -> {
                    if (isViewAttached())
                        getMvpView().showToast(R.string.colored_image_dumped, new File(exportPath).getName());
                }
        );
    }

    @Override
    public void saveVisibleLightImage() {
        if (tabResources.getCount() == 0)
            return;

        if (!tabResources.getRawThermalDump().isVisibleImageAttached()) {
            getMvpView().showToast(R.string.error_occurred);
        }

        String dumpPath = tabResources.getRawThermalDump().getFilepath();
        String exportPath = dumpPath.substring(0, dumpPath.lastIndexOf("_")) + Config.POSTFIX_VISIBLE_IMAGE + ".png";

        Observable.create(emitter -> {
            Bitmap alignedVisibleImage = tabResources.getRawThermalDump()
                .getVisibleImageMask().getAlignedVisibleBitmap();

            if (alignedVisibleImage == null) {
                emitter.onError(new Error());
                return;
            }

            if (ImageUtils.saveBitmap(alignedVisibleImage, exportPath)) {
                emitter.onComplete();
            } else {
                emitter.onError(new Error());
            }
        }).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                o -> {},
                e -> {
                    if (isViewAttached())
                        getMvpView().showToast(R.string.dump_failed);
                },
                () -> {
                    if (isViewAttached())
                        getMvpView().showToast(R.string.visible_image_dumped, new File(exportPath).getName());
                }
            );
    }

    @Override
    public void toggleThermalSpotsVisible() {
        if (tabResources.getThermalSpotHelper() == null)
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
        thermalSpotsHelper.addSpot(lastSpotId == -1 ? 1 : lastSpotId + 1);
    }

    @Override
    public void removeLastThermalSpot() {
        if (tabResources.getCount() == 0)
            return;

        if (!showingThermalSpots) {
            getMvpView().showSnackBar(R.string.spots_hidden);
            return;
        }

        tabResources.getThermalSpotHelper().removeLastSpot();
    }

    @Override
    public void copyThermalSpots() {
        if (tabResources.getCount() == 0)
            return;

        ArrayList<Point> spotMarkers = tabResources.getRawThermalDump().getSpotMarkers();
        if (spotMarkers.size() == 0) {
            getMvpView().showToast(R.string.no_spot_to_copied);
        } else {
            copiedSpotMarkers = CommonUtils.cloneArrayList(spotMarkers);
        }
    }

    @Override
    public synchronized void pasteThermalSpots() {
        if (tabResources.getCount() == 0)
            return;

        // ThermalSpotsHelper should have been created while switching to this tab
        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        if (thermalSpotsHelper != null) {
            thermalSpotsHelper.dispose();
        }

        Observable.create(emitter -> {
            RawThermalDump rawThermalDump = tabResources.getRawThermalDump();
            rawThermalDump.setSpotMarkers(CommonUtils.cloneArrayList(copiedSpotMarkers));
            rawThermalDump.save();

            tabResources.setThermalSpotsHelper(
                getMvpView().createThermalSpotsHelper(rawThermalDump)
            );
        }).subscribeOn(Schedulers.computation())
            .subscribe();
    }

    @Override
    public void clearThermalSpots() {
        if (tabResources.getThermalSpotHelper() == null)
            return;

        tabResources.getThermalSpotHelper().clearAllSpots();
    }

    @Override
    @BgThreadCapable
    public void updateHorizontalLine(final int y) {
        if (tabResources.getCount() == 0 || !showingChart)
            return;

        // Set horizontal line location
        activity.runOnUiThread(() -> getMvpView().setHorizontalLineY(y));

        // Calculate the correspondent point on the thermal image
        if (tabResources.getRawThermalDump() != null) {
            int thermalDumpHeight = tabResources.getRawThermalDump().getHeight();
            double ratio = (double) thermalDumpHeight / getMvpView().getThermalImageViewHeight();
            horizontalLineY = CommonUtils.trimByRange((int) (y * ratio), 1, thermalDumpHeight - 1);

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
    public int getTabsCount() {
        return tabResources.getCount();
    }

    @Override
    public String getDumpTitle() {
        if (tabResources.getCount() == 0)
            return "";

        return tabResources.getRawThermalDump().getTitle();
    }

    @Override
    public boolean isVisibleImageAlignMode() {
        return visibleImageAlignMode;
    }

    @Override
    public boolean isSpotsVisible() {
        return showingThermalSpots;
    }

    @Override
    public boolean existCopiedSpots() {
        return copiedSpotMarkers != null;
    }


    @BgThreadCapable
    private synchronized void addThermalDump(final String filepath) {
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

            activity.runOnUiThread(() -> {
                final int newIndex = getMvpView().addDumpTab(thermalDump.getTitle());
                if (tabResources.getCurrentIndex() != newIndex) {
                    switchDumpTab(newIndex);
                }
            });
        } else {
            getMvpView().showSnackBar("Failed reading thermal dump");
        }
    }

    /**
     * Load thermal bitmap and create thermalSpotsHelper
     */
    private Observable<Boolean> loadThermalImageBitmap() {
        return Observable.create(emitter -> {

            Observable.<Bitmap>create(emitter1 -> {
                emitter1.onNext(tabResources.getThermalBitmap(contrastRatio, coloredMode));
                emitter1.onComplete();
            }).subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    bitmap -> {
                        if (isViewAttached())
                            getMvpView().updateThermalImageView(bitmap);
                    },
                    emitter::onError,
                    () -> {
                        if (!isViewAttached()) return;

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
                            getMvpView().getThermalImageViewGlobalLayouts().take(1).subscribe(o -> {
                                tabResources.setThermalSpotsHelper(
                                    getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump())
                                );
                                emitter.onNext(true);
                                emitter.onComplete();
                            });
                        } else {
                            tabResources.setThermalSpotsHelper(
                                getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump())
                            );
                            emitter.onNext(true);
                            emitter.onComplete();
                        }
                    }
                );

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
        return Observable.create(emitter -> {
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
            rawThermalDump.getVisibleImageMask().processFrame(activity, maskInstance -> {
                System.out.println("loadVisibleImage@done of dump: " + rawThermalDump.getTitle());
                emitter.onNext(true);
                emitter.onComplete();
            });
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
