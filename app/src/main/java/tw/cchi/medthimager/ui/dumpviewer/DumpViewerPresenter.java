/*
 * Ref:
 *  [Avoiding memory leaks]
 *      https://android-developers.googleblog.com/2009/01/avoiding-memory-leaks.html
 */

package tw.cchi.medthimager.ui.dumpviewer;

import android.graphics.Bitmap;
import android.os.Handler;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.CaptureRecord;
import tw.cchi.medthimager.helper.ThImagesHelper;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.ChartParameter;
import tw.cchi.medthimager.model.ViewerTabResources;
import tw.cchi.medthimager.service.sync.task.UpSyncThImagesTask;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.ThermalDumpProcessor;
import tw.cchi.medthimager.thermalproc.VisibleImageExtractor;
import tw.cchi.medthimager.thermalproc.VisibleImageMask;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.util.AppUtils;
import tw.cchi.medthimager.util.CommonUtils;
import tw.cchi.medthimager.util.ImageUtils;
import tw.cchi.medthimager.util.ThermalDumpUtils;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;
import tw.cchi.medthimager.util.annotation.NewThread;

public class DumpViewerPresenter<V extends DumpViewerMvpView> extends BasePresenter<V> implements DumpViewerMvpPresenter<V> {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;

    // Data models & helpers
    @Inject VisibleImageExtractor visibleImageExtractor;
    @Inject ThImagesHelper thImagesHelper;
    @Inject volatile ViewerTabResources tabResources;
    private volatile ChartParameter<Float> thermalChartParameter;
    private ArrayList<org.opencv.core.Point> copiedSpotMarkers;

    // States
    private int contrastRatio = 1;
    private boolean showingVisibleImage = false;
    private boolean visibleImageAlignMode = false;
    private boolean coloredMode = true;
    private boolean showingChart = false;
    private int horizontalLineY = -1; // pY (on the thermal dump) of horizontal indicator on showingChart mode
    private boolean showingThermalSpots = true;
    private boolean savingAllVisibleImages = false;

    private Disposable switchDumpTabTask;

    @Inject
    public DumpViewerPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);

        if (visibleImageExtractor == null) {
            visibleImageExtractor = new VisibleImageExtractor(activity.getApplicationContext());
        }

        thermalChartParameter = new ChartParameter<>(ChartParameter.ChartType.MULTI_LINE_CURVE);
        thermalChartParameter.setAlpha(0.6f);

        disposables.add(tabResources);

        // Wait until the view have been measured (visibility state considered)
        // Ref: https://stackoverflow.com/questions/36586146/ongloballayoutlistener-vs-postrunnable
        disposables.add(getMvpView().getVisibleImageViewLayoutObservable().subscribe(o -> {
            if (showingVisibleImage) {
                getMvpView().resizeVisibleImageViewToThermalImage();
            }
        }));

        // Launch image picker on activity first started
        pickDumps();
        getMvpView().showToast(R.string.pick_thermal_images);
    }


    @Override
    public void pickDumps() {
        getMvpView().launchImagePicker(tabResources.getThermalDumpPaths());
    }

    @Override
    public void onDumpsPicked(ArrayList<String> selectedPaths) {
        getMvpView().showLoading();

        final ArrayList<String> addPaths = selectedPaths;
        ArrayList<String> removePaths = new ArrayList<>(tabResources.getThermalDumpPaths());
        ArrayList<String> currentPaths = new ArrayList<>(tabResources.getThermalDumpPaths());

        Collections.sort(addPaths);

        for (String path : currentPaths) {
            // Selected file has already been added
            if (addPaths.contains(path)) {
                addPaths.remove(path);
                removePaths.remove(path);
            }
        }

        // Only switch tab (update imageViews) when removing the last dump
        for (int i = 0; i < removePaths.size() - 1; i++) {
            closeThermalDump(tabResources.indexOf(removePaths.get(i)), false);
        }
        if (removePaths.size() > 0)
            closeThermalDump(tabResources.indexOf(removePaths.get(removePaths.size() - 1)), true);

        if (addPaths.size() > 0) {
            // Add thermal dumps "sequentially" on a background thread and update chart axis on complete
            Observable.<String>create(emitter -> {
                for (String path : addPaths)
                    emitter.onNext(path);
                emitter.onComplete();
            }).observeOn(Schedulers.computation()).subscribe(
                this::addThermalDump, // onNext
                e -> { // onError
                    e.printStackTrace();
                    if (isViewAttached()) {
                        getMvpView().hideLoading();
                        getMvpView().showSnackBar(R.string.error_occurred);
                    }
                },
                () -> { // onComplete
                    if (isViewAttached()) {
                        try { // Avoid crash if activity stopped while calling updateThermalChartAxis()
                            updateThermalChartAxis();

                            Log.d(TAG, "onDumpsPicked@complete");

                            // switchDumpTab() called by addThermalDump() may not yet finished
                            if (tabResources.hasLoaded()) {
                                getMvpView().hideLoading();
                            }
                        } catch (NullPointerException ignored) {}
                    }
                }
            );
        } else {
            if (tabResources.getCount() == 0)
                getMvpView().updateThermalImageView(null);

            // Loading may have been hidden in switchDumpTab() in closeThermalDump()
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
            Log.d(TAG, "switchDumpTab(" + position + ")@rejected");
            return false;
        }
        Log.d(TAG, "switchDumpTab(" + position + ")@locked");

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
                        toggleVisibleImage(false);
                    }
                }

                return true;
            }
        ).observeOn(AndroidSchedulers.mainThread()).subscribe(
            b -> {},
            e -> {
                e.printStackTrace();
                getMvpView().hideLoading();
                getMvpView().showSnackBar(R.string.error_switch_dump_tab);
            },
            () -> {
                tabResources.setHasLoaded(true);
                if (isViewAttached())
                    getMvpView().hideLoading();

                Log.d(TAG, "switchDumpTab(" + position + ")@unlocked");

                if (savingAllVisibleImages) {
                    saveVisibleLightImage();

                    new Handler().postDelayed(() -> {
                        if (tabResources.getCurrentIndex() + 1 < tabResources.getCount()) {
                            switchDumpTab(tabResources.getCurrentIndex() + 1);
                        } else {
                            savingAllVisibleImages = false;
                            switchDumpTab(0);
                        }
                    }, Config.DUMP_ALL_VISIBLE_INTERVAL);
                }
            }
        );

        return true;
    }

    /**
     * @param switchTab set to true while removing multiple dumps except the last dump
     * @return Index of the new active dump or -1 for no tab after removing
     */
    @Override
    public int closeThermalDump(int index, boolean switchTab) {
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
            if (showingVisibleImage) toggleVisibleImage(false);
            if (showingChart) toggleHorizonChart(false);
        }

        upSyncThermalImages();

        return newIndex;
    }

    @Override
    public void deleteThermalDump() {
        if (tabResources.getCount() == 0)
            return;

        int currentIndex = tabResources.getCurrentIndex();
        ThermalDumpUtils.deleteThermalDumpBundle(activity, tabResources.getRawThermalDump())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(
                deletedPath -> AppUtils.sendBroadcastToMedia(activity, deletedPath),
                e -> getMvpView().showSnackBar(e.getMessage()),
                () -> {
                    getMvpView().showToast(R.string.dump_deleted);
                    closeThermalDump(currentIndex, true);
                }
        );
    }

    @NewThread
    @Override
    public void saveColoredThermalImage() {
        if (tabResources.getCount() == 0)
            return;

        String dumpPath = tabResources.getRawThermalDump().getFilepath();
        String exportPath = dumpPath.substring(0, dumpPath.lastIndexOf("_")) + Constants.POSTFIX_COLORED_IMAGE + ".png";

        Observable.create(emitter -> {
            if (ImageUtils.saveBitmap(tabResources.getThermalBitmap(contrastRatio, true), exportPath)) {
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

        final boolean saveAllMode = savingAllVisibleImages;
        final int currentIndex = tabResources.getCurrentIndex();
        final int tabCount = tabResources.getCount();

        String dumpPath = tabResources.getRawThermalDump().getFilepath();
        String exportPath = dumpPath.substring(0, dumpPath.lastIndexOf("_")) + Constants.POSTFIX_VISIBLE_IMAGE + ".png";

        Observable.create(emitter -> {
            VisibleImageMask visibleImageMask = tabResources.getRawThermalDump().getVisibleImageMask();
            Bitmap alignedVisibleImage;
            if (visibleImageMask == null || (alignedVisibleImage = visibleImageMask.getAlignedVisibleBitmap()) == null) {
                emitter.onError(new Error());
                return;
            }

            if (ImageUtils.saveBitmap(alignedVisibleImage, exportPath)) {
                emitter.onComplete();
            } else {
                emitter.onError(new Error());
            }
        }).subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                o -> {},
                e -> {
                    if (isViewAttached())
                        getMvpView().showToast(R.string.dump_failed);
                },
                () -> {
                    if (isViewAttached()) {
                        String prefix = saveAllMode ? "(" + (currentIndex + 1) + "/" + tabCount + ")" : "";
                        getMvpView().showToast(R.string.visible_image_dumped, prefix, new File(exportPath).getName());
                    }
                }
            );
    }

    @Override
    @NewThread
    public void updateVisibleLightImageOffset(final int imageViewOffsetX, final int imageViewOffsetY) {
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
            setThImageNotSynced(rawThermalDump);

            dataManager.pref.setDefaultVisibleOffset(
                    new android.graphics.Point(dumpPixelOffsetX, dumpPixelOffsetY));

        }).subscribeOn(Schedulers.io()).subscribe();
    }

    @Override
    public void saveAllVisibleLightImageFromOpened() {
        if (tabResources.getCount() == 0)
            return;

        savingAllVisibleImages = true;
        switchDumpTab(0);
    }

    @Override
    public boolean setThermalSpotsVisible(boolean visible) {
        if (tabResources.getThermalSpotHelper() == null)
            return false;

        showingThermalSpots = visible;
        tabResources.getThermalSpotHelper().setSpotsVisible(showingThermalSpots);
        return true;
    }

    @Override
    public boolean isSpotsVisible() {
        return showingThermalSpots;
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
            setThImageNotSynced(rawThermalDump);

            tabResources.setThermalSpotsHelper(
                getMvpView().createThermalSpotsHelper(rawThermalDump)
            );

        }).subscribeOn(Schedulers.computation()).subscribe();
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
    public void toggleVisibleImage(boolean show) {
        if (tabResources.getCount() == 0 && !showingVisibleImage)
            return;

        if (show && displayVisibleImage(tabResources.getRawThermalDump())) {
            getMvpView().setToggleVisibleChecked(showingVisibleImage = true);
        } else {
            getMvpView().setVisibleImageViewVisible(false, 0);
            getMvpView().setToggleVisibleChecked(showingVisibleImage = false);
            visibleImageAlignMode = false;
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
                toggleVisibleImage(true);
            }
        }

        float opacity = visibleImageAlignMode ? Config.VISIBLE_ALIGN_ALPHA / 255f : 1f;
        getMvpView().setVisibleImageViewVisible(true, opacity);
    }

    @Override
    public void toggleColoredMode(boolean colored) {
        getMvpView().setToggleColoredModeChecked(coloredMode = colored);
        getMvpView().updateThermalImageView(tabResources.getThermalBitmap(contrastRatio, coloredMode));
    }

    @Override
    public void toggleHorizonChart(boolean show) {
        if (horizontalLineY == -1 || tabResources.getCount() == 0 && !showingChart)
            return;

        if (show) {
            modifyChartParameter(thermalChartParameter, horizontalLineY);
            getMvpView().updateThermalChart(thermalChartParameter);
            getMvpView().setThermalChartVisible(true);
            getMvpView().setHorizontalLineVisible(true);
            getMvpView().setToggleHorizonChartChecked(showingChart = true);
        } else {
            getMvpView().setThermalChartVisible(false);
            getMvpView().setHorizontalLineVisible(false);
            getMvpView().setToggleHorizonChartChecked(showingChart = false);
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
    public boolean existCopiedSpots() {
        return copiedSpotMarkers != null;
    }

    /**
     * @param rawThermalDump rawThermalDump.captureRecordUuid should be set before calling
     */
    @NewThread
    @Override
    public void setThImageNotSynced(RawThermalDump rawThermalDump) {
        String uuid = rawThermalDump.getRecordUuid();
        if (uuid == null)
            throw new RuntimeException("rawThermalDump.captureRecordUuid is not set");

        Observable.create(emitter -> {
            CaptureRecord captureRecord = dataManager.db.captureRecordDAO().get(uuid);
            captureRecord.setSynced(false);
            dataManager.db.captureRecordDAO().update(captureRecord);
        }).subscribeOn(Schedulers.io()).subscribe();
    }

    @NewThread
    @Override
    public void upSyncThermalImages() {
        application.connectSyncService().subscribe(syncService ->
                syncService.scheduleNewTask(new UpSyncThImagesTask()));
    }


    @BgThreadCapable
    private synchronized void addThermalDump(final String filepath) {
        RawThermalDump thermalDump = RawThermalDump.readFromDumpFile(filepath);
        if (thermalDump == null) {
            getMvpView().showSnackBar("Failed reading thermal dump");
            return;
        }

        // Check and find uuid of the thermal dump
        thImagesHelper.findOrInsertRecordFromThermalDump(thermalDump)
            .observeOn(Schedulers.io())
            .blockingSubscribe(captureRecord -> {
                thermalDump.setRecordUuid(captureRecord.getUuid());
                thermalDump.saveAsync();
            });

        if (horizontalLineY == -1) {
            horizontalLineY = thermalDump.getHeight() / 2;
        }
        updateHorizontalLine(horizontalLineY);

        ThermalDumpProcessor thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
        tabResources.addResources(filepath, thermalDump, thermalDumpProcessor);
        addDumpDataToChartParameter(thermalChartParameter, thermalDump, horizontalLineY);
        getMvpView().updateThermalChart(thermalChartParameter);

        activity.runOnUiThread(() -> {
            final int newIndex = getMvpView().addDumpTab(thermalDump.getTitle());
            if (tabResources.getCurrentIndex() != newIndex) {
                switchDumpTab(newIndex);
            }
        });

        Log.d(TAG, "addThermalDump@end");
    }

    /**
     * Load thermal bitmap and create thermalSpotsHelper
     */
    private Observable<Boolean> loadThermalImageBitmap() {
        return Observable.create(emitter -> {

            Log.d(TAG, "loadThermalImageBitmap@start");
            Observable.<Bitmap>create(emitter1 -> {
                emitter1.onNext(tabResources.getThermalBitmap(contrastRatio, coloredMode));
                emitter1.onComplete();
            }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    bitmap -> {
                        if (isViewAttached()) {
                            getMvpView().updateThermalImageView(bitmap);
                        }
                    },
                    emitter::onError,
                    () -> {
                        if (!isViewAttached()) return;

                        // Create new thermalSpotsHelper if not existed
                        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
                        if (thermalSpotsHelper != null) {
                            thermalSpotsHelper.setSpotsVisible(showingThermalSpots);
                            emitter.onNext(true);
                            emitter.onComplete();
                            Log.d(TAG, "loadThermalImageBitmap@done - (thermalSpotsHelper existed)");
                            return;
                        }

                        // Wait measured width and height to be correct (while picking new dumps after the first time)
                        // TODO: better approach?
                        mainLooperHandler.postDelayed(() -> {
                            if (getMvpView().getThermalImageViewHeight() == 0) {
                                // This should be called after getMvpView().updateThermalImageView(), which was called in onNext() above
                                disposables.add(getMvpView().getThermalImageViewGlobalLayouts().take(1).subscribe(o -> {
                                    tabResources.setThermalSpotsHelper(
                                            getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump()));
                                    emitter.onNext(true);
                                    emitter.onComplete();
                                }));
                            } else {
                                tabResources.setThermalSpotsHelper(
                                        getMvpView().createThermalSpotsHelper(tabResources.getRawThermalDump()));
                                emitter.onNext(true);
                                emitter.onComplete();
                            }
                            Log.d(TAG, "loadThermalImageBitmap@done - (thermalSpotsHelper created)");
                        }, 150);
                    }
                );

        });
    }

    /**
     * Attach and load visible of rawThermalDump
     *
     * PS. The proceed mask can be retrieved by rawThermalDump.getVisibleImageMask()
     *
     * @return boolean: succeed or not
     */
    private Observable<Boolean> loadVisibleImage(final RawThermalDump rawThermalDump) {
        return Observable.create(emitter -> {
            Log.d(TAG, "loadVisibleImage@start - " + rawThermalDump.getTitle());

            if (rawThermalDump.getVisibleImageMask() != null) {
                emitter.onNext(true);
                emitter.onComplete();
                return;
            }

            visibleImageExtractor.extractImage(rawThermalDump.getFlirImagePath(), visibleImage -> {
                if (visibleImage == null) {
                    getMvpView().showSnackBar(R.string.failed_extract_visible);
                    emitter.onNext(false);
                } else {
                    rawThermalDump.attachVisibleImageMask(visibleImage, 0, 0);
                    emitter.onNext(true);
                }

                Log.d(TAG, "loadVisibleImage@done - " + rawThermalDump.getTitle());
                emitter.onComplete();
            });
        });
    }

    private boolean displayVisibleImage(RawThermalDump rawThermalDump) {
        if (rawThermalDump.getVisibleImageMask() != null) {
            getMvpView().updateVisibleImageView(rawThermalDump.getVisibleImageMask(), visibleImageAlignMode);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add thermal values on specific horizontal line on the thermal dump to the chart parameter.
     *
     * [Note] Not calling updateChartAxis() here because it will be called when all thermalDumps are added
     *
     */
    private synchronized void addDumpDataToChartParameter(ChartParameter<Float> chartParameter, RawThermalDump rawThermalDump, int y) {
        int width = rawThermalDump.getWidth();
        Float[] temperaturePoints = new Float[width];

        for (int i = 0; i < width; i++) {
            temperaturePoints[i] = rawThermalDump.getTemperatureAt(i, y);
        }
        chartParameter.addNumbersArray(rawThermalDump.getTitle(), temperaturePoints);
    }

    /**
     * Remove data (float array) from chart parameter by index.
     *
     * Note: Not calling updateChartAxis() here
     *
     */
    private synchronized void removeDataFromChartParameter(ChartParameter chartParameter, int index) {
        chartParameter.removeFloatArray(index);
    }

    private void modifyChartParameter(ChartParameter<Float> chartParameter, int y) {
        ArrayList<RawThermalDump> rawThermalDumps = tabResources.getRawThermalDumps();
        for (int i = 0; i < rawThermalDumps.size(); i++) {
            RawThermalDump rawThermalDump = rawThermalDumps.get(i);
            Float[] temperaturePoints = new Float[rawThermalDump.getWidth()];

            for (int j = 0; j < rawThermalDump.getWidth(); j++) {
                temperaturePoints[j] = rawThermalDump.getTemperatureAt(j, y);
            }

            chartParameter.updateNumbersArray(i, temperaturePoints);
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
        tabResources = null;
        thermalChartParameter = null;
        
        upSyncThermalImages();
    }
}
