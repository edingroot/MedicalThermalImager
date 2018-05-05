package tw.cchi.medthimager.component;

import android.content.Context;
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
import tw.cchi.medthimager.R;

public class SpotsControlView extends RelativeLayout {

    private OnControlSpotsListener listener;
    private Animation animFabOpen, animFabClose, animRotateForward, animRotateBackward;
    private ArrayList<FloatingActionButton> childSpots = new ArrayList<>();
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
        ButterKnife.bind(this, rootView);
        initialize();
    }

    public SpotsControlView(Context context) {
        super(context);
        View rootView = inflate(context, R.layout.view_spots_control, this);
        ButterKnife.bind(this, rootView);
        initialize();
    }

    private void initialize() {
        animFabOpen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open);
        animFabClose = AnimationUtils.loadAnimation(getContext(),R.anim.fab_close);
        animRotateForward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_forward);
        animRotateBackward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_backward);

        childSpots.add(fabAddSpot);
        childSpots.add(fabRemoveSpot);
        childSpots.add(fabClearSpots);
        childSpots.add(fabToggleVisibility);

        for (FloatingActionButton fab : childSpots) {
            fab.setVisibility(INVISIBLE);
        }
    }

    public void setOnControlSpotsListener(OnControlSpotsListener listener) {
        this.listener = listener;
    }

    @OnClick(R.id.fabAddSpot)
    void onFabAddSpotClick() {
        if (listener != null)
            listener.onAddSpot();
    }

    @OnClick(R.id.fabRemoveSpot)
    void onFabRemoveSpotClick() {
        if (listener != null)
            listener.onRemoveSpot();
    }

    @OnClick(R.id.fabClearSpots)
    void onFabClearSpotsClick() {
        if (listener != null)
            listener.onClearSpots();
    }

    @OnClick(R.id.fabToggleVisibility)
    void onFabToggleVisibilityClick(FloatingActionButton fab) {
        if (listener != null) {
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
        animateFab();
    }

    private void animateFab() {
        if (fabOpen) {
            fabSpotsControl.startAnimation(animRotateBackward);

            for (FloatingActionButton fab : childSpots) {
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

            for (FloatingActionButton fab : childSpots) {
                fab.startAnimation(animFabOpen);
                fab.setClickable(true);
            }

            fabOpen = true;
        }
    }

    public interface OnControlSpotsListener {
        void onAddSpot();

        void onRemoveSpot();

        void onClearSpots();

        void onHideSpots();

        void onShowSpots();
    }
}
