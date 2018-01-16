package tw.cchi.flironedemo1.helper;

import android.content.Context;
import android.graphics.Point;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.view.ThermalSpotView;

public class ThermalSpotsHelper {
    private Context context;
    private ViewGroup parentView;
    private RawThermalDump rawThermalDump;
    private boolean spotsVisible = true;

    private SparseArray<ThermalSpotView> thermalSpotViews = new SparseArray<>(); // <spotId, ThermalSpotView>
    private SparseArray<Point> thermalPixelPositions = new SparseArray<>(); // <spotId, actual position on the thermal dump>
    private SparseArray<Pair<Integer, Integer>> imageViewMetrics = new SparseArray<>(); // <spotId, Pair<width, pY + parentsY>>

    private int spotStartDraggingX;
    private int spotStartDraggingY;

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

    /**
     *
     * @param spotId
     * @param imageViewWidth thermalImageView.getMeasuredWidth()
     * @param imageViewY (int)thermalImageView.getY()
     */
    public synchronized void addThermalSpot(int spotId, int imageViewWidth, int imageViewY) {
        final ThermalSpotView thermalSpotView = new ThermalSpotView(context, parentView, spotId, true);

        thermalSpotView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int x = (int) motionEvent.getRawX();
                int y = (int) motionEvent.getRawY();
                ThermalSpotView spotView = (ThermalSpotView) view;
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();

                // TODO
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        spotStartDraggingX = x;
                        spotStartDraggingY = y;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        layoutParams.leftMargin = x - spotStartDraggingX;
                        layoutParams.topMargin = y - spotStartDraggingY;
                        view.setLayoutParams(layoutParams);
                        updateThermalValue(spotView);
                        break;

                    case MotionEvent.ACTION_UP:
                        updateThermalValue(spotView);

                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_POINTER_UP:
                        break;
                }
                return true;
            }
        });

        thermalSpotViews.append(spotId, thermalSpotView);
        parentView.addView(thermalSpotView);
        imageViewMetrics.append(spotId, new Pair(imageViewWidth, imageViewY));

        // After view is rendered, run on UI thread
        thermalSpotView.post(new Runnable() {
            @Override
            public void run() {
                updateThermalValue(thermalSpotView);
            }
        });
    }

    private void updateThermalValue(ThermalSpotView spotView) {
        int imageViewWidth = imageViewMetrics.get(spotView.getSpotId()).first;
        int offsetY = imageViewMetrics.get(spotView.getSpotId()).second;
        double ratio = (double) rawThermalDump.getWidth() / imageViewWidth;

        Point viewPosition = spotView.getCenterPosition();
        viewPosition.y -= offsetY;
        Point thermalPosition = new Point(
                thermalViewPositionConversion(viewPosition.x, viewPosition.y, ratio)
        );
        thermalPixelPositions.setValueAt(spotView.getSpotId(), thermalPosition);

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
