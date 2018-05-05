package tw.cchi.medthimager.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.NumberFormat;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import tw.cchi.medthimager.R;

public class ThermalSpotView extends RelativeLayout {
    private Unbinder unbinder;

    private int spotId;
    private boolean showId;

    private final int width;
    private final int height;
    private boolean moved = false;

    @BindView(R.id.txtSpotId) TextView txtSpotId;
    @BindView(R.id.txtSpotValue) TextView txtSpotValue;

    public ThermalSpotView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        View rootView = inflate(context, R.layout.view_thermal_spot, this);
        unbinder = ButterKnife.bind(this, rootView);

        // Load view attributes
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ThermalSpotView, 0, 0);
        try {
            spotId = typedArray.getInt(R.styleable.ThermalSpotView_spotId, 0);
            showId = typedArray.getBoolean(R.styleable.ThermalSpotView_showId, true);
        } finally {
            typedArray.recycle();
        }

        this.width = (int) getResources().getDimension(R.dimen.thermal_spot_width);
        this.height = (int) getResources().getDimension(R.dimen.thermal_spot_height);

        initLayoutParams();
        onAttrsUpdate();
    }

    public ThermalSpotView(Context context, int spotId, boolean showId) {
        super(context);
        View rootView = inflate(context, R.layout.view_thermal_spot, this);
        unbinder = ButterKnife.bind(this, rootView);

        this.spotId = spotId;
        this.showId = showId;
        this.width = (int) getResources().getDimension(R.dimen.thermal_spot_width);
        this.height = (int) getResources().getDimension(R.dimen.thermal_spot_height);

        initLayoutParams();
        onAttrsUpdate();
    }

    /**
     * By default, the centerPosition is centered vertical & horizontally
     */
    private void initLayoutParams() {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        params.addRule(RelativeLayout.CENTER_HORIZONTAL, TRUE);
        params.addRule(RelativeLayout.CENTER_VERTICAL, TRUE);

        setLayoutParams(params);
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

    @SuppressLint("SetTextI18n")
    public void setTemperature(double spotValue) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        txtSpotValue.setText(" " + numberFormat.format(spotValue) + "ºC");
    }

    /**
     * Set the centerPosition on the screen, use the center point of this view as reference.
     */
    public synchronized void setCenterPosition(final int x, final int y) {
        final LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
        params.leftMargin = x - width / 2;
        params.topMargin = y - height / 2;

        if (!moved) {
            params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);

            // Prevent the view from being compressed when moving right or down
            params.rightMargin = -width;
            params.bottomMargin = -height;

            moved = true;
        }

        setLayoutParams(params);
        invalidate();
    }

    public Point getCenterPosition() {
        if (!moved) {
            return new Point(
                    getLeft() + width / 2,
                    getTop() + height / 2
            );
        } else {
            LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
            return new Point(
                    params.leftMargin + width / 2,
                    params.topMargin + height / 2
            );
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (unbinder != null)
            unbinder.unbind();
        super.onDetachedFromWindow();
    }
}
