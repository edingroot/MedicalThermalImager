package tw.cchi.flironedemo1.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.Queue;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.R;

public class ThermalSpotView extends RelativeLayout {
    private int spotId;
    private boolean showId;
    private boolean moved;
    private Queue<Runnable> beforeMeasureQueue = new LinkedList<>();
    private OnPlacedListener onPlacedListener;

    @BindView(R.id.txtSpotId) TextView txtSpotId;
    @BindView(R.id.txtSpotValue) TextView txtSpotValue;

    public ThermalSpotView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        View rootView = inflate(context, R.layout.view_thermal_spot, this);
        ButterKnife.bind(this, rootView);

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ThermalSpotView, 0, 0
        );
        try {
            spotId = typedArray.getInt(R.styleable.ThermalSpotView_spotId, 0);
            showId = typedArray.getBoolean(R.styleable.ThermalSpotView_showId, true);
        } finally {
            typedArray.recycle();
        }

        initLayoutParams();
        onAttrsUpdate();
    }

    public ThermalSpotView(Context context, int spotId, boolean showId) {
        super(context);
        View rootView = inflate(context, R.layout.view_thermal_spot, this);
        ButterKnife.bind(this, rootView);

        this.spotId = spotId;
        this.showId = showId;

        initLayoutParams();
        onAttrsUpdate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        while (!beforeMeasureQueue.isEmpty()) {
            new Thread(beforeMeasureQueue.poll()).start();
        }

        if (moved && onPlacedListener != null) {
            moved = false;
            onPlacedListener.onPlaced();
        }
    }

    /**
     * By default, the position is centered vertical & horizontally
     */
    private void initLayoutParams() {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        params.addRule(RelativeLayout.CENTER_HORIZONTAL, TRUE);
        params.addRule(RelativeLayout.CENTER_VERTICAL, TRUE);

        setLayoutParams(params);
        moved = false;
    }

    private void onAttrsUpdate() {
        txtSpotId.setText(String.valueOf(spotId));
        txtSpotId.setVisibility(showId ? VISIBLE : GONE);
    }

    public int getSpotId() {
        return spotId;
    }

    public void setSpotId(int spotId) {
        this.spotId = spotId;
    }

    public void setOnPlacedListener(OnPlacedListener onPlacedListener) {
        this.onPlacedListener = onPlacedListener;
    }

    @SuppressLint("SetTextI18n")
    public void setTemperature(double spotValue) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        txtSpotValue.setText(" " + numberFormat.format(spotValue) + "ºC");
    }

    /**
     * Set the position on the screen, use the center point of this view as reference.
     */
    public synchronized void setCenterPosition(final int x, final int y) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.printf("setCenterPosition: (%d,%d)\n", x, y);
                final LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
                params.leftMargin = x - getMeasuredWidth() / 2;
                params.topMargin = y - getMeasuredHeight() / 2;
                System.out.printf("setCenterPosition: margins=(%d,%d)\n", params.leftMargin, params.topMargin);

                if (!moved) {
                    params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
                    params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);

                    // Prevent the view from being compressed when moving right or down
                    params.rightMargin = -getMeasuredWidth();
                    params.bottomMargin = -getMeasuredHeight();

                    moved = true;
                }

                // Run on UI thread
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        setLayoutParams(params);
                        invalidate();
                    }
                });
            }
        };

        // Queue runnable if view not measured
        if (getMeasuredWidth() == 0 || getMeasuredHeight() == 0) {
            beforeMeasureQueue.add(runnable);
        } else {
            new Thread(runnable).start();
        }
    }

    public Point getCenterPosition() {
        LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
        Point point;
        if (!moved) {
            point = new Point(
                    getLeft() + getMeasuredWidth() / 2,
                    getTop() + getMeasuredHeight() / 2
            );
        } else {
            point = new Point(
                    params.leftMargin + this.getMeasuredWidth() / 2,
                    params.topMargin + this.getMeasuredHeight() / 2
            );
        }

        System.out.printf("getCenterPosition: (%d,%d)\n", point.x, point.y);

        return point;
    }

    public interface OnPlacedListener {
        // Called after view is measured and spot is placed to the correct position
        void onPlaced();
    }

}
