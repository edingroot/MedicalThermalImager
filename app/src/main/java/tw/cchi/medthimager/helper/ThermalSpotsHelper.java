package tw.cchi.medthimager.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.flir.flironesdk.RenderedImage;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.component.ThermalSpotView;
import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.utils.CommonUtils;
import tw.cchi.medthimager.utils.ThermalDumpUtils;

public class ThermalSpotsHelper {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    public enum TempSource {ThermalDump, RenderedImage}

    private TempSource tempSource;
    private Context context;
    private ViewGroup parentView;

    private RawThermalDump rawThermalDump;
    private RenderedImage renderedImage;
    private final ArrayList<org.opencv.core.Point> preSelectedSpots = new ArrayList<>();

    private int tempSourceWidth;
    private int lastSpotId = -1;
    private boolean viewMetricsSet = false;
    private int imageViewWidth = -1;
    private int imageViewHeight = -1;
    private int imageViewRawY = -1;
    private boolean spotsVisible = true;

    private final ReentrantReadWriteLock spotViewsListLock = new ReentrantReadWriteLock();
    private SparseArray<ThermalSpotView> thermalSpotViews = new SparseArray<>(); // <spotId, ThermalSpotView>
    private Queue<Runnable> onSetViewMetricsRunnables = new LinkedList<>();

    private int spotDraggingDeltaX;
    private int spotDraggingDeltaY;

    @BgThreadCapable
    public ThermalSpotsHelper(Context context, ViewGroup parentView, RawThermalDump rawThermalDump) {
        this.tempSource = TempSource.ThermalDump;
        this.context = context;
        this.parentView = parentView;
        this.rawThermalDump = rawThermalDump;
        this.tempSourceWidth = rawThermalDump.getWidth();

        // Add thermal spots if exists
        ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
        if (spotMarkers.size() > 0) {
            for (int i = 0; i < spotMarkers.size(); i++) {
                org.opencv.core.Point spotPosition = spotMarkers.get(i);
                restoreSpot(i + 1, spotPosition.x, spotPosition.y);
            }
        } else {
            // If there is no spot position information in dump file, add one default spot.
            // run on the UI thread
            // parentView.post(() -> addSpot(1));
        }
    }

    @BgThreadCapable
    public ThermalSpotsHelper(Context context, ViewGroup parentView, RenderedImage renderedImage) {
        this.tempSource = TempSource.RenderedImage;
        this.context = context;
        this.parentView = parentView;
        this.renderedImage = renderedImage;
        this.tempSourceWidth = renderedImage.width();
    }

    public void setSpotsVisible(boolean spotsVisible) {
        if (this.spotsVisible == spotsVisible)
            return;

        for (int i = 0; i < thermalSpotViews.size(); i++) {
            int key = thermalSpotViews.keyAt(i);
            ThermalSpotView thermalSpotView = thermalSpotViews.get(key);
            thermalSpotView.setVisibility(spotsVisible ? View.VISIBLE : View.GONE);
        }
        this.spotsVisible = spotsVisible;
    }

    public int getCount() {
        spotViewsListLock.readLock().lock();
        int count = thermalSpotViews.size();
        spotViewsListLock.readLock().unlock();

        return count;
    }

    /**
     * @param imageViewWidth  thermalImageView.getMeasuredWidth()
     * @param imageViewHeight thermalImageView.getMeasuredHeight()
     * @param imageViewRawY   thermalImageView.getTop() + Sum(ALL_PARENTS_OF_thermalImageView .getTop())
     */
    public void setImageViewMetrics(int imageViewWidth, int imageViewHeight, int imageViewRawY) {
        Log.d(TAG, String.format("setImageViewMetrics: imageViewWidth=%d, imageViewHeight=%d, imageViewRawY=%d\n",
            imageViewWidth, imageViewHeight, imageViewRawY));
        
        this.imageViewWidth = imageViewWidth;
        this.imageViewHeight = imageViewHeight;
        this.imageViewRawY = imageViewRawY;
        this.viewMetricsSet = true;

        // Execute runnables
        while (!onSetViewMetricsRunnables.isEmpty()) {
            Completable.fromRunnable(onSetViewMetricsRunnables.poll())
                .subscribeOn(Schedulers.io()).subscribe();
        }
    }

    public synchronized void addSpot(int spotId) {
        addSpot(spotId, -1, -1, true);
    }

    /**
     * This should be run after viewMetrics set.
     *
     * @param spotId
     * @param viewX set to -1 to place in center
     * @param viewY set to -1 to place in center
     * @param appendToDump
     */
    @SuppressLint("ClickableViewAccessibility")
    @BgThreadCapable
    public synchronized void addSpot(final int spotId, int viewX, int viewY, boolean appendToDump) {
        final ThermalSpotView thermalSpotView = new ThermalSpotView(context, spotId, true);

        thermalSpotView.setOnTouchListener((view, motionEvent) -> {
            int x1 = (int) motionEvent.getRawX();
            int y1 = (int) motionEvent.getRawY();
            ThermalSpotView spotView = (ThermalSpotView) view;

            switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    Point centerPoint = thermalSpotView.getCenterPosition();
                    spotDraggingDeltaX = x1 - centerPoint.x;
                    spotDraggingDeltaY = y1 - centerPoint.y;

                case MotionEvent.ACTION_MOVE:
                    int centerX = CommonUtils.trimByRange(x1 - spotDraggingDeltaX, 0, imageViewWidth);
                    int centerY = CommonUtils.trimByRange(y1 - spotDraggingDeltaY, imageViewRawY, imageViewRawY + imageViewHeight);
                    thermalSpotView.setCenterPosition(centerX, centerY);
                    view.invalidate();
                    updateThermalValue(spotView);
                    break;

                case MotionEvent.ACTION_UP:
                    storeSpotPosition(thermalSpotView);
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    break;
            }

            return true;
        });

        if (viewX != -1 || viewY != -1) {
            thermalSpotView.setCenterPosition(viewX, viewY);
        } else {
            thermalSpotView.setCenterPosition(imageViewWidth / 2, imageViewRawY + imageViewHeight / 2);
        }

        spotViewsListLock.writeLock().lock();
        thermalSpotViews.append(spotId, thermalSpotView);
        spotViewsListLock.writeLock().unlock();
        if (spotId > lastSpotId)
            lastSpotId = spotId;

        // Adding view on UI thread after view render
        parentView.post(() -> {
            parentView.addView(thermalSpotView);
            updateThermalValue(thermalSpotView);

            // Add and save to thermal dump file
            if (appendToDump) {
                Point viewPosition = thermalSpotView.getCenterPosition();
                Point thermalPosition = view2thermalPosition(viewPosition.x, viewPosition.y);
                org.opencv.core.Point thermalPositionCV = new org.opencv.core.Point(thermalPosition.x, thermalPosition.y);

                if (tempSource == TempSource.ThermalDump) {
                    rawThermalDump.getSpotMarkers().add(thermalPositionCV);
                    rawThermalDump.saveAsync();
                } else if (tempSource == TempSource.RenderedImage) {
                    synchronized (preSelectedSpots) {
                        preSelectedSpots.add(thermalPositionCV);
                    }
                }
            }
        });
    }

    public int getLastSpotId() {
        return lastSpotId;
    }

    public void removeLastSpot() {
        if (lastSpotId == -1)
            return;

        if (tempSource == TempSource.ThermalDump) {
            // Remove and save to thermal dump file
            ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
            spotMarkers.remove(thermalSpotViews.indexOfKey(lastSpotId));
            rawThermalDump.setSpotMarkers(spotMarkers);
            rawThermalDump.saveAsync();
        } else if (tempSource == TempSource.RenderedImage) {
            synchronized (preSelectedSpots) {
                preSelectedSpots.remove(thermalSpotViews.indexOfKey(lastSpotId));
            }
        }

        spotViewsListLock.writeLock().lock();
        ThermalSpotView thermalSpotView = thermalSpotViews.get(lastSpotId);
        parentView.removeView(thermalSpotView);
        thermalSpotViews.remove(lastSpotId);
        if (thermalSpotViews.size() > 0) {
            lastSpotId = thermalSpotViews.keyAt(thermalSpotViews.size() - 1);
        } else {
            lastSpotId = -1;
        }
        spotViewsListLock.writeLock().unlock();
    }

    @BgThreadCapable
    public void clearAllSpots() {
        proceedViewMetricsRunnable(() -> {
            lastSpotId = -1;

            // Run on UI thread
            new Handler(Looper.getMainLooper()).post(() -> {
                for (int i = 0; i < thermalSpotViews.size(); i++) {
                    View spotView = thermalSpotViews.valueAt(i);
                    parentView.removeView(spotView);
                }
                thermalSpotViews.clear();
            });

            if (tempSource == TempSource.ThermalDump) {
                rawThermalDump.getSpotMarkers().clear();
                rawThermalDump.save();
            } else if (tempSource == TempSource.RenderedImage) {
                synchronized (preSelectedSpots) {
                    preSelectedSpots.clear();
                }
            }
        });
    }

    /**
     * Update thermal value of all spots based on the view position of those spotViews.
     */
    public void updateThermalValuesFromImage(RenderedImage renderedImage) {
        if (tempSource != TempSource.RenderedImage) {
            throw new RuntimeException("should be called only if TempSource is RenderedImage");
        }

        this.renderedImage = renderedImage;

        spotViewsListLock.readLock().lock();
        for (int i = 0; i < thermalSpotViews.size(); i++) {
            updateThermalValue(thermalSpotViews.valueAt(i));
        }
        spotViewsListLock.readLock().unlock();
    }

    public ArrayList<org.opencv.core.Point> getPreSelectedSpots() {
        synchronized (preSelectedSpots) {
            return preSelectedSpots;
        }
    }

    public void dispose() {
        setSpotsVisible(false);
    }


    /**
     * Store spot position in thermal dump files
     */
    private void storeSpotPosition(final ThermalSpotView spotView) {
        Point viewPosition = spotView.getCenterPosition();
        Point rawPosition = view2thermalPosition(viewPosition.x, viewPosition.y);

        Log.d(TAG, String.format("storeSpotPosition(id=%d) BfConv=(%d, %d), AfConv=(%d, %d)\n",
            spotView.getSpotId(), viewPosition.x, viewPosition.y, rawPosition.x, rawPosition.y
        ));

        if (tempSource == TempSource.ThermalDump) {
            ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
            spotMarkers.get(spotView.getSpotId() - 1).set(new double[]{rawPosition.x, rawPosition.y});
            rawThermalDump.setSpotMarkers(spotMarkers);
            rawThermalDump.saveAsync();
        } else if (tempSource == TempSource.RenderedImage) {
            synchronized (preSelectedSpots) {
                preSelectedSpots.get(spotView.getSpotId() - 1).set(new double[]{rawPosition.x, rawPosition.y});
            }
        }
    }

    /**
     * Spots may not restored in order due to each runnable will parallel run on different thread.
     */
    @BgThreadCapable
    private void restoreSpot(final int spotId, final double dumpX, final double dumpY) {
        proceedViewMetricsRunnable(() -> {
            // Convert position on rawThermalImage to position on the imageView
            Point viewPosition = thermal2viewPosition((int) dumpX, (int) dumpY);

            Log.d(TAG, String.format("RestoreThermalSpot@BfConv: spotId=%s, x=%.0f, y=%.0f\n", spotId, dumpX, dumpY));
            Log.d(TAG, String.format("RestoreThermalSpot@AfConv: spotId=%s, x=%d, y=%d, lastSpotId=%d\n",
                spotId, viewPosition.x, viewPosition.y, lastSpotId
            ));

            addSpot(spotId, viewPosition.x, viewPosition.y, false);
        });
    }

    /**
     * Update thermal value based on the view position of the spotView.
     *
     * Note: This method should be called after {@link #setImageViewMetrics(int, int, int)} called.
     */
    @BgThreadCapable
    private void updateThermalValue(final ThermalSpotView spotView) {
        proceedViewMetricsRunnable(() -> {
            Point viewPosition = spotView.getCenterPosition();
            final Point thermalPosition = view2thermalPosition(viewPosition.x, viewPosition.y);

            Log.d(TAG, String.format("updateThermalValue, %d - viewPos=(%d, %d), dumpPos=(%d, %d)\n",
                spotView.getSpotId(), viewPosition.x, viewPosition.y, thermalPosition.x, thermalPosition.y
            ));

            // Run on UI thread
            new Handler(Looper.getMainLooper()).post(() -> {
                if (tempSource == TempSource.ThermalDump) {
                    spotView.setTemperature(
                        rawThermalDump.getTemperature9Average(thermalPosition.x, thermalPosition.y));
                } else if (tempSource == TempSource.RenderedImage) {
                    spotView.setTemperature(
                        ThermalDumpUtils.getTemperature9Average(renderedImage, thermalPosition.x, thermalPosition.y));
                }
            });
        });
    }

    private Point view2thermalPosition(int x, int y) {
        if (!viewMetricsSet)
            throw new RuntimeException("Convert spot position before view metrics set.");

        double ratio = (double) tempSourceWidth / imageViewWidth;
        y -= imageViewRawY;
        x = (int) (x * ratio);
        y = (int) (y * ratio);
        return new Point(x, y);
    }

    private Point thermal2viewPosition(int x, int y) {
        if (!viewMetricsSet)
            throw new RuntimeException("Convert spot position before view metrics set.");

        double ratio = (double) imageViewWidth / tempSourceWidth;
        x = (int) (x * ratio);
        y = (int) (y * ratio) + imageViewRawY;
        return new Point(x, y);
    }

    /**
     * @param runnable the runnable should be capable of executing on a background thread
     */
    private void proceedViewMetricsRunnable(Runnable runnable) {
        if (viewMetricsSet) {
            Completable.fromRunnable(runnable).subscribeOn(Schedulers.io()).subscribe();
        } else {
            onSetViewMetricsRunnables.add(runnable);
        }
    }

}
