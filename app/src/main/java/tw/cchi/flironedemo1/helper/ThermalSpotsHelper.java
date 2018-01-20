package tw.cchi.flironedemo1.helper;

import android.content.Context;
import android.graphics.Point;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.view.ThermalSpotView;

public class ThermalSpotsHelper {
    private Context context;
    private ViewGroup parentView;
    private RawThermalDump rawThermalDump;
    private int lastSpotId = -1;
    private int imageViewWidth = -1;
    private int imageViewHeight = -1;
    private int imageViewRawY = -1;
    private boolean spotsVisible = true;

    private SparseArray<ThermalSpotView> thermalSpotViews = new SparseArray<>(); // <spotId, ThermalSpotView>
//    private SparseArray<Point> actualThermalPositions = new SparseArray<>(); // <spotId, actual position on the thermal dump>

    private int spotDraggingDeltaX;
    private int spotDraggingDeltaY;

    public ThermalSpotsHelper(Context context, ViewGroup parentView, RawThermalDump rawThermalDump) {
        this.context = context;
        this.parentView = parentView;
        this.rawThermalDump = rawThermalDump;
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
    }

    public synchronized void addThermalSpot(int spotId) {
        System.out.printf("addThermalSpot: spotId=%d\n", spotId);
        final ThermalSpotView thermalSpotView = new ThermalSpotView(context, spotId, true);

        thermalSpotView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int x = (int) motionEvent.getRawX();
                int y = (int) motionEvent.getRawY();
                ThermalSpotView spotView = (ThermalSpotView) view;

                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        Point centerPoint = thermalSpotView.getCenterPosition();
                        spotDraggingDeltaX = x - centerPoint.x;
                        spotDraggingDeltaY = y - centerPoint.y;

                    case MotionEvent.ACTION_MOVE:
                        int centerX = AppUtils.trimByRange(x - spotDraggingDeltaX, 0, imageViewWidth);
                        int centerY = AppUtils.trimByRange(y - spotDraggingDeltaY, imageViewRawY, imageViewRawY + imageViewHeight);
                        thermalSpotView.setCenterPosition(centerX, centerY);
                        view.invalidate();
                        updateThermalValue(spotView);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_POINTER_UP:
                        break;
                }
                return true;
            }
        });

        thermalSpotViews.append(spotId, thermalSpotView);
        parentView.addView(thermalSpotView);
        lastSpotId = spotId;

        // After view is rendered, run on UI thread
        thermalSpotView.post(new Runnable() {
            @Override
            public void run() {
                updateThermalValue(thermalSpotView);
            }
        });
    }

    public int getLastSpotId() {
        return lastSpotId;
    }

    public void removeLastThermalSpot() {
        ThermalSpotView thermalSpotView = thermalSpotViews.get(lastSpotId);
        parentView.removeView(thermalSpotView);
        thermalSpotViews.remove(lastSpotId);
        lastSpotId = thermalSpotViews.keyAt(thermalSpotViews.size() - 1);
    }

    public void dispose() {
        setSpotsVisible(false);
    }

    /**
     * Note: This method should be called after {@link #setImageViewMetrics(int, int)} called
     *
     * @param spotView
     */
    private void updateThermalValue(ThermalSpotView spotView) {
        if (imageViewWidth == -1 || imageViewRawY == -1) {
            throw new RuntimeException("Error: cannot calculate position conversion ratio due to image view metrics not set");
        }
        double ratio = (double) rawThermalDump.getWidth() / imageViewWidth;

        Point viewPosition = spotView.getCenterPosition();
        viewPosition.y -= imageViewRawY;
        Point thermalPosition = new Point(
                thermalViewPositionConversion(viewPosition.x, viewPosition.y, ratio)
        );
//        actualThermalPositions.append(spotView.getSpotId(), thermalPosition);

        double averageC = rawThermalDump.getTemperature9Average(thermalPosition.x, thermalPosition.y);
        spotView.setTemperature(averageC);
    }

    /**
     * View position => Thermal pixel position
     */
    private Point thermalViewPositionConversion(int x, int y, double ratio) {
        x = (int) (x * ratio);
        y = (int) (y * ratio);
        return new Point(x, y);
    }

}
