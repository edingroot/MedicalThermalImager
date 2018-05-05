package tw.cchi.medthimager.component;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import tw.cchi.medthimager.R;

public class SpotsControlView extends RelativeLayout {
    private Unbinder unbinder;
    private OnControlSpotsListener listener;

    private final boolean toggleVisibility;
    private Animation animFabOpen, animFabClose, animRotateForward, animRotateBackward;
    private ArrayList<FloatingActionButton> childFabs = new ArrayList<>();

    private boolean enabled = true;
    private boolean fabOpen = false;
    private boolean spotsVisible = true;

    @BindView(R.id.fabAddSpot) FloatingActionButton fabAddSpot;
    @BindView(R.id.fabRemoveSpot) FloatingActionButton fabRemoveSpot;
    @BindView(R.id.fabClearSpots) FloatingActionButton fabClearSpots;
    @BindView(R.id.fabToggleVisibility) FloatingActionButton fabToggleVisibility;
    @BindView(R.id.fabSpotsControl) FloatingActionButton fabSpotsControl;

    public SpotsControlView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        View rootView = inflate(context, R.layout.view_spots_control, this);
        unbinder = ButterKnife.bind(this, rootView);

        // Load view attributes
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
            attrs, R.styleable.SpotsControlView, 0, 0);
        try {
            enabled = typedArray.getBoolean(R.styleable.SpotsControlView_enabled, true);
            toggleVisibility = typedArray.getBoolean(R.styleable.SpotsControlView_toggleVisibility, true);
        } finally {
            typedArray.recycle();
        }

        initialize();
    }

    private void initialize() {
        animFabOpen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open);
        animFabClose = AnimationUtils.loadAnimation(getContext(),R.anim.fab_close);
        animRotateForward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_forward);
        animRotateBackward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_backward);

        // Primary fab
        int color = enabled ? R.color.spotsFab : R.color.spotsFabDisabled;
        fabSpotsControl.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(color)));

        // Children fabs
        childFabs.add(fabAddSpot);
        childFabs.add(fabRemoveSpot);
        childFabs.add(fabClearSpots);
        if (toggleVisibility)
            childFabs.add(fabToggleVisibility);
        else
            fabToggleVisibility.setVisibility(GONE);

        for (FloatingActionButton fab : childFabs) {
            fab.setVisibility(INVISIBLE);
        }
    }

    public void setOnControlSpotsListener(OnControlSpotsListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && fabOpen)
            animateFab();

        this.enabled = enabled;
        int color = this.enabled ? R.color.spotsFab : R.color.spotsFabDisabled;
        fabSpotsControl.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(color)));
    }

    @OnClick(R.id.fabAddSpot)
    void onFabAddSpotClick() {
        if (enabled && listener != null)
            listener.onAddSpot();
    }

    @OnClick(R.id.fabRemoveSpot)
    void onFabRemoveSpotClick() {
        if (enabled && listener != null)
            listener.onRemoveSpot();
    }

    @OnClick(R.id.fabClearSpots)
    void onFabClearSpotsClick() {
        if (enabled && listener != null)
            listener.onClearSpots();
    }

    @OnClick(R.id.fabToggleVisibility)
    void onFabToggleVisibilityClick(FloatingActionButton fab) {
        if (enabled && listener != null) {
            if (spotsVisible) {
                listener.onHideSpots();
                fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_visible));
                spotsVisible = false;
            } else {
                listener.onShowSpots();
                fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_not_visible));
                spotsVisible = true;
            }
        }
    }

    @OnClick(R.id.fabSpotsControl)
    void onFabSpotsControlClick() {
        if (enabled)
            animateFab();
    }

    private void animateFab() {
        if (fabOpen) {
            fabSpotsControl.startAnimation(animRotateBackward);

            for (FloatingActionButton fab : childFabs) {
                fab.startAnimation(animFabClose);
                fab.setClickable(false);
            }

            fabOpen = false;
        } else {
            fabSpotsControl.startAnimation(animRotateForward);

            fabAddSpot.startAnimation(animFabOpen);
            fabRemoveSpot.startAnimation(animFabOpen);
            fabClearSpots.startAnimation(animFabOpen);
            fabToggleVisibility.startAnimation(animFabOpen);

            for (FloatingActionButton fab : childFabs) {
                fab.startAnimation(animFabOpen);
                fab.setClickable(true);
            }

            fabOpen = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (unbinder != null)
            unbinder.unbind();
        super.onDetachedFromWindow();
    }

    public interface OnControlSpotsListener {
        void onAddSpot();

        void onRemoveSpot();

        void onClearSpots();

        void onHideSpots();

        void onShowSpots();
    }
}
