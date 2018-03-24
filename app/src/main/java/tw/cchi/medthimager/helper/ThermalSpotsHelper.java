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

import tw.cchi.medthimager.AppUtils;
import tw.cchi.medthimager.component.ThermalSpotView;
import tw.cchi.medthimager.di.BgThreadAvail;
import tw.cchi.medthimager.thermalproc.RawThermalDump;

public class ThermalSpotsHelper {
    private Context context;
    private ViewGroup parentView;
    private RawThermalDump rawThermalDump;
    private int lastSpotId = -1;
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
    @BgThreadAvail
    public ThermalSpotsHelper(Context context, ViewGroup parentView, RawThermalDump rawThermalDump) {
        this.context = context;
        this.parentView = parentView;
        this.rawThermalDump = rawThermalDump;

        // Add thermal spots if exists
        ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
        if (spotMarkers.size() > 0) {
            for (int i = 0; i < spotMarkers.size(); i++) {
                org.opencv.core.Point spotPosition = spotMarkers.get(i);
                restoreThermalSpot(i + 1, spotPosition.x, spotPosition.y);
            }
        } else {
            // If there is no spot position information in dump file, add one default spot.
            // run on the UI thread
            parentView.post(() -> addThermalSpot(1));
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
        this.imageViewRawY =imageViewRawY;
        this.viewMetricsSet = true;

        // Execute runnables
        while (!onSetViewMetricsRunnables.isEmpty()) {
            new Thread(onSetViewMetricsRunnables.poll()).start();
        }
    }

    public synchronized void addThermalSpot(int spotId) {
        addThermalSpot(spotId, -1, -1, true);
    }

    /**
     * No need to run on UI thread.
     *
     * @param spotId
     * @param x
     * @param y
     * @param appendToDump
     */
    public synchronized void addThermalSpot(final int spotId, int x, int y, boolean appendToDump) {
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
                    int centerX = AppUtils.trimByRange(x1 - spotDraggingDeltaX, 0, imageViewWidth);
                    int centerY = AppUtils.trimByRange(y1 - spotDraggingDeltaY, imageViewRawY, imageViewRawY + imageViewHeight);
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

        if (x != -1 && y != -1) {
            thermalSpotView.setCenterPosition(x, y);
        } else {
            thermalSpotView.setCenterPosition(imageViewWidth / 2, imageViewRawY + imageViewHeight / 2);
        }

        thermalSpotViews.append(spotId, thermalSpotView);
        lastSpotId = spotId;

        // Add and save to thermal dump file
        if (appendToDump) {
            Point position = thermalSpotView.getCenterPosition();
            ArrayList<org.opencv.core.Point> spotMarkers = rawThermalDump.getSpotMarkers();
            spotMarkers.add(new org.opencv.core.Point(position.x, position.y));
            rawThermalDump.setSpotMarkers(spotMarkers);
            rawThermalDump.saveAsync();
        }

        parentView.addView(thermalSpotView);
    }

    public int getLastSpotId() {
        return lastSpotId;
    }

    public void removeLastThermalSpot() {
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

    @BgThreadAvail
    private void restoreThermalSpot(final int spotId, final double rawX, final double rawY) {
        proceedViewMetricsRunnable(() -> {
            System.out.printf("RestoreThermalSpot@BfConv: spotId=%s, x=%.0f, y=%.0f\n", spotId, rawX, rawY);

            // Convert position on rawThermalImage to position on the imageView
            double ratio = (double) imageViewWidth / rawThermalDump.getWidth();
            Point viewPosition = thermalViewPositionConversion((int) rawX, (int) rawY, ratio);
            viewPosition.y += imageViewRawY;

            System.out.printf("RestoreThermalSpot@AfConv: spotId=%s, x=%d, y=%d\n", spotId, viewPosition.x, viewPosition.y);

            addThermalSpot(spotId, viewPosition.x, viewPosition.y, false);
        });
    }

    /**
     * Note: This method should be called after {@link #setImageViewMetrics(int, int, int)} called
     *
     * @param spotView
     */
    @BgThreadAvail
    private void updateThermalValue(final ThermalSpotView spotView) {
        proceedViewMetricsRunnable(() -> {
            double ratio = (double) rawThermalDump.getWidth() / imageViewWidth;
            Point viewPosition = spotView.getCenterPosition();
            viewPosition.y -= imageViewRawY;
            final Point thermalPosition = new Point(
                    thermalViewPositionConversion(viewPosition.x, viewPosition.y, ratio)
            );

            System.out.printf("updateThermalValue, %d - viewPos=(%d, %d)\n", spotView.getSpotId(), viewPosition.x, viewPosition.y);
            System.out.printf("updateThermalValue, %d - dumpPos=(%d, %d)\n", spotView.getSpotId(), thermalPosition.x, thermalPosition.y);

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

    private void proceedViewMetricsRunnable(Runnable runnable) {
        if (viewMetricsSet) {
            new Thread(runnable).start();
        } else {
            onSetViewMetricsRunnables.add(runnable);
        }
    }

}
