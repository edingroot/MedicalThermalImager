package tw.cchi.medthimager.helper;

import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import tw.cchi.medthimager.component.ThermalSpotView;
import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.utils.CommonUtils;

public class ThermalSpotsHelper {
    private Context context;
    private ViewGroup parentView;
    private RawThermalDump rawThermalDump;
    private volatile int lastSpotId = -1;
    private boolean viewMetricsSet = false;
    private int imageViewWidth = -1;
    private int imageViewHeight = -1;
    private int imageViewRawY = -1;
    private boolean spotsVisible = true;

    private SparseArray<ThermalSpotView> thermalSpotViews = new SparseArray<>(); // <spotId, ThermalSpotView>
    private Queue<Runnable> onSetViewMetricsRunnables = new LinkedList<>();

    private int spotDraggingDeltaX;
    private int spotDraggingDeltaY;

    /**
     * Constructor: auto add thermal spots specified in thermal dump file
     *
     * @param context
     * @param parentView
     * @param rawThermalDump
     */
    @BgThreadCapable
    public ThermalSpotsHelper(Context context, ViewGroup parentView, RawThermalDump rawThermalDump) {
        this.context = context;
        this.parentView = parentView;
        this.rawThermalDump = rawThermalDump;

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
            parentView.post(() -> addSpot(1));
        }
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
        return thermalSpotViews.size();
    }

    /**
     * @param imageViewWidth  thermalImageView.getMeasuredWidth()
     * @param imageViewHeight thermalImageView.getMeasuredHeight()
     * @param imageViewRawY   thermalImageView.getTop() + Sum(ALL_PARENTS_OF_thermalImageView .getTop())
     */
    public void setImageViewMetrics(int imageViewWidth, int imageViewHeight, int imageViewRawY) {
        System.out.printf("setImageViewMetrics: imageViewWidth=%d, imageViewHeight=%d, imageViewRawY=%d\n",
                imageViewWidth, imageViewHeight, imageViewRawY);
        
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
     * No need to run on UI thread.
     *
     * @param spotId
     * @param viewX
     * @param viewY
     * @param appendToDump
     */
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

        updateThermalValue(thermalSpotView);

        if (viewX != -1 && viewY != -1) {
            thermalSpotView.setCenterPosition(viewX, viewY);
        } else {
            thermalSpotView.setCenterPosition(imageViewWidth / 2, imageViewRawY + imageViewHeight / 2);
        }

        thermalSpotViews.append(spotId, thermalSpotView);
        if (spotId > lastSpotId)
            lastSpotId = spotId;

        // Add and save to thermal dump file
        if (appendToDump) {
            Point position = thermalSpotView.getCenterPosition();
            ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
            spotMarkers.add(new org.opencv.core.Point(position.x, position.y));
            rawThermalDump.setSpotMarkers(spotMarkers);
            rawThermalDump.saveAsync();
        }

        // After view is rendered, run on UI thread
        parentView.post(() -> parentView.addView(thermalSpotView));
    }

    public int getLastSpotId() {
        return lastSpotId;
    }

    public void removeLastSpot() {
        if (lastSpotId == -1)
            return;

        // Remove and save to thermal dump file
        ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
        spotMarkers.remove(thermalSpotViews.indexOfKey(lastSpotId));
        rawThermalDump.setSpotMarkers(spotMarkers);
        rawThermalDump.saveAsync();

        ThermalSpotView thermalSpotView = thermalSpotViews.get(lastSpotId);
        parentView.removeView(thermalSpotView);
        thermalSpotViews.remove(lastSpotId);
        if (thermalSpotViews.size() > 0) {
            lastSpotId = thermalSpotViews.keyAt(thermalSpotViews.size() - 1);
        } else {
            lastSpotId = -1;
        }
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

            rawThermalDump.setSpotMarkers(new ArrayList<>());
            rawThermalDump.save();
        });
    }

    public void dispose() {
        setSpotsVisible(false);
    }


    /**
     * Store spot position in thermal dump files
     *
     * @param spotView
     */
    private void storeSpotPosition(final ThermalSpotView spotView) {
        double ratio = (double) rawThermalDump.getWidth() / imageViewWidth;

        Point viewPosition = spotView.getCenterPosition();
        ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();

        System.out.printf("Store2dump@BfConv id=%d, pos=(%d, %d)\n", spotView.getSpotId(), viewPosition.x, viewPosition.y);

        Point rawPosition = thermalViewPositionConversion(viewPosition.x, viewPosition.y - imageViewRawY, ratio);

        System.out.printf("Store2dump@AfConv id=%d, pos=(%d, %d), temp=%.2f\n", spotView.getSpotId(), rawPosition.x, rawPosition.y, rawThermalDump.getTemperature9Average(rawPosition.x, rawPosition.y));

        spotMarkers.get(spotView.getSpotId() - 1).set(new double[]{rawPosition.x, rawPosition.y});
        rawThermalDump.setSpotMarkers(spotMarkers);
        rawThermalDump.saveAsync();
    }

    /**
     * Spots may not restored in order due to each runnable will parallel run on different thread.
     */
    @BgThreadCapable
    private void restoreSpot(final int spotId, final double dumpX, final double dumpY) {
        proceedViewMetricsRunnable(() -> {
            System.out.printf("RestoreThermalSpot@BfConv: spotId=%s, x=%.0f, y=%.0f\n", spotId, dumpX, dumpY);

            // Convert position on rawThermalImage to position on the imageView
            double ratio = (double) imageViewWidth / rawThermalDump.getWidth();
            Point viewPosition = thermalViewPositionConversion((int) dumpX, (int) dumpY, ratio);
            viewPosition.y += imageViewRawY;

            System.out.printf("RestoreThermalSpot@AfConv: spotId=%s, x=%d, y=%d, lastSpotId=%d\n",
                spotId, viewPosition.x, viewPosition.y, lastSpotId
            );

            addSpot(spotId, viewPosition.x, viewPosition.y, false);
        });
    }

    /**
     * Note: This method should be called after {@link #setImageViewMetrics(int, int, int)} called
     *
     * @param spotView
     */
    @BgThreadCapable
    private void updateThermalValue(final ThermalSpotView spotView) {
        proceedViewMetricsRunnable(() -> {
            double ratio = (double) rawThermalDump.getWidth() / imageViewWidth;
            Point viewPosition = spotView.getCenterPosition();
            viewPosition.y -= imageViewRawY;
            final Point thermalPosition = new Point(
                    thermalViewPositionConversion(viewPosition.x, viewPosition.y, ratio)
            );

            // System.out.printf("updateThermalValue, %d - viewPos=(%d, %d)\n", spotView.getSpotId(), viewPosition.x, viewPosition.y);
            // System.out.printf("updateThermalValue, %d - dumpPos=(%d, %d)\n", spotView.getSpotId(), thermalPosition.x, thermalPosition.y);

            // Run on UI thread
            new Handler(Looper.getMainLooper()).post(() -> spotView.setTemperature(rawThermalDump.getTemperature9Average(thermalPosition.x, thermalPosition.y)));
        });
    }

    /**
     * View position <=> Thermal pixel position
     */
    private Point thermalViewPositionConversion(int x, int y, double ratio) {
        x = (int) (x * ratio);
        y = (int) (y * ratio);
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
