package tw.cchi.medthimager.component;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.R;

public class SpotsControlView extends RelativeLayout {

    private OnControlSpotsListener listener;
    private Animation fabOpen, fabClose, rotateForward, rotateBackward;
    private boolean isFabOpen = false;

    @BindView(R.id.fabSpotsControl) FloatingActionButton fabSpotsControl;
    @BindView(R.id.fabAddSpot) FloatingActionButton fabAddSpot;
    @BindView(R.id.fabRemoveSpot) FloatingActionButton fabRemoveSpot;

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
        fabOpen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open);
        fabClose = AnimationUtils.loadAnimation(getContext(),R.anim.fab_close);
        rotateForward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_forward);
        rotateBackward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_backward);
    }

    public void setOnControlSpotsListener(OnControlSpotsListener listener) {
        this.listener = listener;
    }

    @OnClick(R.id.fabSpotsControl)
    void onFabSpotsControlClick() {
        animateFab();
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

    private void animateFab() {
        if (isFabOpen) {
            fabSpotsControl.startAnimation(rotateBackward);
            fabAddSpot.startAnimation(fabClose);
            fabRemoveSpot.startAnimation(fabClose);
            fabAddSpot.setClickable(false);
            fabRemoveSpot.setClickable(false);
            isFabOpen = false;
        } else {
            fabSpotsControl.startAnimation(rotateForward);
            fabAddSpot.startAnimation(fabOpen);
            fabRemoveSpot.startAnimation(fabOpen);
            fabAddSpot.setClickable(true);
            fabRemoveSpot.setClickable(true);
            isFabOpen = true;
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
