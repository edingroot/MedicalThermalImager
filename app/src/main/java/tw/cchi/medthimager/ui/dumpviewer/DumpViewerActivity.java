package tw.cchi.medthimager.ui.dumpviewer;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.ToggleButton;

import com.jakewharton.rxbinding2.view.RxView;

import java.util.ArrayList;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.OnTouch;
import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.models.sort.SortingTypes;
import droidninja.filepicker.utils.Orientation;
import io.reactivex.Observable;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.component.MultiChartView;
import tw.cchi.medthimager.component.SpotsControlView;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.ChartParameter;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.VisibleImageMask;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.dumpviewer.adapter.ThermalDumpsRecyclerAdapter;
import tw.cchi.medthimager.util.annotation.BgThreadCapable;

@RuntimePermissions
public class DumpViewerActivity extends BaseActivity
    implements DumpViewerMvpView, SpotsControlView.OnControlSpotsListener {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private static final int MAX_OPEN_FILES = 16;

    @Inject DumpViewerMvpPresenter<DumpViewerMvpView> presenter;

    private ThermalDumpsRecyclerAdapter thermalDumpsRecyclerAdapter;

    // VisibleImageView dragging
    private int startDraggingX;
    private int startDraggingY;

    @BindView(R.id.topView) RelativeLayout topView;
    @BindView(R.id.layoutThermalViews) FrameLayout layoutThermalViews;
    @BindView(R.id.recyclerDumpSwitcher) RecyclerView recyclerDumpSwitcher;

    @BindView(R.id.thermalImageView) ImageView thermalImageView;
    @BindView(R.id.visibleImageView) ImageView visibleImageView;
    @BindView(R.id.thermalChartView) MultiChartView thermalChartView;
    @BindView(R.id.horizontalLine) View horizontalLine;
    
    @BindView(R.id.toggleVisible) ToggleButton toggleVisible;
    @BindView(R.id.toggleColoredMode) ToggleButton toggleColoredMode;
    @BindView(R.id.toggleHorizonChart) ToggleButton toggleHorizonChart;
    @BindView(R.id.spotsControl) SpotsControlView spotsControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_viewer);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);

        thermalDumpsRecyclerAdapter = new ThermalDumpsRecyclerAdapter(
                this, new ThermalDumpsRecyclerAdapter.OnInteractionListener() {
            @Override
            public boolean onClick(View v, int position) {
                return presenter.switchDumpTab(position);
            }

            @Override
            public void onLongClick(View v, final int position) {
                // Show confirm dialog for closing this thermal dump
                showAlertDialog(
                    getString(R.string.confirm),
                    getString(R.string.confirm_close_dump, presenter.getDumpTitle()),
                    (dialog, which) -> presenter.closeThermalDump(position, true),
                    (dialog, which) -> {}
                );
            }
        });
        recyclerDumpSwitcher.setAdapter(thermalDumpsRecyclerAdapter);
        recyclerDumpSwitcher.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        spotsControl.setOnControlSpotsListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DumpViewerActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void onPickDoc(ArrayList<String> selectedFiles) {
        String[] thermalDumpExts = {".dat"};

        FilePickerBuilder.getInstance().setMaxCount(MAX_OPEN_FILES)
                .setSelectedFiles(selectedFiles)
                .setActivityTheme(R.style.FilePickerTheme)
                .addFileSupport("ThermalDump", thermalDumpExts)
                .sortDocumentsBy(SortingTypes.name)
                .enableDocSupport(false)
                .withOrientation(Orientation.PORTRAIT_ONLY)
                .pickFile(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case FilePickerConst.REQUEST_CODE_DOC:
                if (resultCode == RESULT_OK && data != null) {
                    presenter.updateDumpsAfterPick(data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS));
                }
                break;
        }
    }


    @OnTouch(R.id.thermalImageView)
    public boolean onThermalImageViewTouch(View v, MotionEvent event) {
        if (thermalImageView.getMeasuredHeight() > 0) {
            int y = (int) event.getY();
            if (y < 0) {
                y = 0;
            } else if (y >= thermalImageView.getMeasuredHeight()) {
                y = thermalImageView.getMeasuredHeight() - 1;
            }
            presenter.updateHorizontalLine(y);
        }

        // Consume the onTouch event in order to capture future movement
        return true;
    }

    @OnTouch(R.id.visibleImageView)
    public boolean onVisibleImageViewTouch(View view, MotionEvent motionEvent) {
        if (!presenter.isVisibleImageAlignMode())
            return false;

        final int x = (int) motionEvent.getRawX();
        final int y = (int) motionEvent.getRawY();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();

        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                startDraggingX = x - layoutParams.leftMargin;
                startDraggingY = y - layoutParams.topMargin;
                break;

            case MotionEvent.ACTION_MOVE:
                layoutParams.leftMargin = x - startDraggingX;
                layoutParams.topMargin = y - startDraggingY;
                // Prevent the view from being compressed when moving right or down
                layoutParams.rightMargin = -500;
                layoutParams.bottomMargin = -500;
                view.setLayoutParams(layoutParams);
                view.invalidate();
                break;

            case MotionEvent.ACTION_UP:
                int diffX = (int) (visibleImageView.getX() - thermalImageView.getX());
                int diffY = (int) (visibleImageView.getY() - thermalImageView.getY());
                presenter.updateVisibleLightImageOffset(diffX, diffY);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                break;
        }
        return true;
    }

    @OnClick(R.id.btnPick)
    public void onImagePickClick() {
        presenter.pickImages();
    }

    @OnClick(R.id.toggleVisible)
    public void onToggleVisibleImageClick(ToggleButton b) {
        presenter.toggleVisibleImage(b.isChecked());
    }

    @OnLongClick(R.id.toggleVisible)
    public boolean onToggleVisibleImageLongClick() {
        presenter.toggleVisibleImageAlignMode();
        return true;
    }

    @OnClick(R.id.toggleColoredMode)
    public void onToggleColoredModeClick(ToggleButton b) {
        presenter.toggleColoredMode(b.isChecked());
    }

    @OnClick(R.id.toggleHorizonChart)
    public void onToggleHorizonChartClick(ToggleButton b) {
        presenter.toggleHorizonChart(b.isChecked());
    }

    @OnClick(R.id.btnDelete)
    public void onDeleteClick(ToggleButton b) {
        if (presenter.getTabsCount() == 0)
            return;

        showAlertDialog(
            getString(R.string.confirm),
            getString(R.string.confirm_delete_dump, presenter.getDumpTitle()),
            (dialog, which) -> presenter.deleteThermalDump(),
            (dialog, which) -> {}
        );
    }

    @OnClick(R.id.btnMenu)
    public void onEditClick(View v) {
        if (presenter.getTabsCount() == 0)
            return;

        PopupMenu popupMenu = new PopupMenu(this, v);

        // Thermal image actions
        popupMenu.getMenu().add(Menu.NONE, 10, Menu.NONE, "Export colored thermal image");
        popupMenu.getMenu().add(Menu.NONE, 11, Menu.NONE, "Export visible light (VL) image");
        popupMenu.getMenu().add(Menu.NONE, 12, Menu.NONE, "Batch export opened (VL) images");

        // Spot actions
        popupMenu.getMenu().add(Menu.NONE, 23, Menu.NONE, "Copy spots");
        if (presenter.existCopiedSpots())
            popupMenu.getMenu().add(Menu.NONE, 24, Menu.NONE, "Paste spots");


        popupMenu.setOnMenuItemClickListener(clickedItem -> {
            switch (clickedItem.getItemId()) {
                case 10:
                    presenter.saveColoredThermalImage();
                    break;

                case 11:
                    presenter.saveVisibleLightImage();
                    break;

                case 12:
                    presenter.saveAllVisibleLightImageFromOpened();
                    break;

                case 23:
                    presenter.copyThermalSpots();
                    break;

                case 24:
                    presenter.pasteThermalSpots();
                    break;
            }
            return true;
        });

        popupMenu.show();
    }


    // -------------------------- SpotsControlView.OnControlSpotsListener ------------------------ //
    @Override
    public void onAddSpot() {
        presenter.addThermalSpot();
    }

    @Override
    public void onRemoveSpot() {
        presenter.removeLastThermalSpot();
    }

    @Override
    public void onClearSpots() {
        presenter.clearThermalSpots();
    }

    @Override
    public void onHideSpots() {
        presenter.setThermalSpotsVisible(false);
    }

    @Override
    public void onShowSpots() {
        presenter.setThermalSpotsVisible(true);
    }
    // -------------------------- /SpotsControlView.OnControlSpotsListener ------------------------ //


    @Override
    public Observable<Object> getThermalImageViewGlobalLayouts() {
        return RxView.globalLayouts(thermalImageView);
    }

    @Override
    public Observable<Object> getVisibleImageViewLayoutObservable() {
        return RxView.globalLayouts(visibleImageView);
    }

    /**
     * This should be called after thermalImageView measured (global layouted).
     */
    @Override
    public ThermalSpotsHelper createThermalSpotsHelper(RawThermalDump rawThermalDump) {
        Log.i(TAG, String.format("[createThermalSpotsHelper] thermalImageView.getMeasuredHeight()=%d, thermalImageView.getTop()=%d, layoutThermalViews.getTop()=%d\n",
            thermalImageView.getMeasuredHeight(), thermalImageView.getTop(), layoutThermalViews.getTop()));

        final ThermalSpotsHelper thermalSpotsHelper = new ThermalSpotsHelper(DumpViewerActivity.this,
                topView, rawThermalDump, () -> presenter.setThImageNotSynced(rawThermalDump));
        thermalSpotsHelper.setImageViewMetrics(
                thermalImageView.getMeasuredWidth(),
                thermalImageView.getMeasuredHeight(),
                thermalImageView.getTop() + layoutThermalViews.getTop());
        return thermalSpotsHelper;
    }

    @Override
    public void setToggleVisibleChecked(boolean checked) {
        toggleVisible.setChecked(checked);
    }

    @Override
    public void setToggleColoredModeChecked(boolean checked) {
        toggleColoredMode.setChecked(checked);
    }

    @Override
    public void setToggleHorizonChartChecked(boolean checked) {
        toggleHorizonChart.setChecked(checked);
    }

    @Override
    public void resizeVisibleImageViewToThermalImage() {
        if (visibleImageView.getMeasuredWidth() != thermalImageView.getMeasuredWidth()) {
            visibleImageView.getLayoutParams().width = thermalImageView.getMeasuredWidth();
            visibleImageView.getLayoutParams().height = thermalImageView.getMeasuredHeight();
            visibleImageView.requestLayout();
        }
    }

    @Override
    public void launchImagePicker(ArrayList<String> pickedFiles) {
        DumpViewerActivityPermissionsDispatcher.onPickDocWithPermissionCheck(this, pickedFiles);
    }

    @Override
    public void updateThermalImageView(@Nullable Bitmap frame) {
        thermalImageView.setImageBitmap(frame);
    }

    @Override
    public int getThermalImageViewHeight() {
        return thermalImageView.getMeasuredHeight();
    }

    /**
     * @param opacity Works only when visible is true
     */
    @Override
    public void setVisibleImageViewVisible(boolean visible, float opacity) {
        visibleImageView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        if (visible)
            visibleImageView.setAlpha(opacity);
    }

    @Override
    public void updateVisibleImageView(@Nullable VisibleImageMask mask, boolean visibleImageAlignMode) {
        if (mask == null) {
            visibleImageView.setImageBitmap(null);
            return;
        }

        visibleImageView.setImageBitmap(mask.getVisibleBitmap());
        visibleImageView.setAlpha(visibleImageAlignMode ? Config.VISIBLE_ALIGN_ALPHA / 255f : 1f);
        visibleImageView.setVisibility(View.VISIBLE);

        // Set initial params
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) visibleImageView.getLayoutParams();
        // height of visibleImageView will be set to same as thermalImageView after layouted
        double ratio = 0.1 * thermalImageView.getMeasuredHeight() / mask.getLinkedRawThermalDump().getHeight();
        layoutParams.leftMargin = (int) (mask.getLinkedRawThermalDump().getVisibleOffsetX() * ratio + thermalImageView.getX());
        layoutParams.topMargin = (int) (mask.getLinkedRawThermalDump().getVisibleOffsetY() * ratio + thermalImageView.getY());

        // Prevent the view from being compressed when moving right or down
        layoutParams.rightMargin = -500;
        layoutParams.bottomMargin = -500;

        visibleImageView.setLayoutParams(layoutParams);
    }

    @Override
    public void setHorizontalLineVisible(boolean visible) {
        horizontalLine.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setHorizontalLineY(int y) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) horizontalLine.getLayoutParams();
        params.topMargin = y + layoutThermalViews.getTop() + thermalImageView.getTop();
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        horizontalLine.setLayoutParams(params);
    }

    @Override
    public void setThermalChartVisible(boolean visible) {
        thermalChartView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    @BgThreadCapable
    public void updateThermalChart(ChartParameter<? extends Number> thermalChartParameter) {
        thermalChartView.updateChart(thermalChartParameter);
    }

    /**
     * @return Index of the new active tab
     */
    @Override
    public int addDumpTab(String title) {
        spotsControl.setEnabled(true);
        return thermalDumpsRecyclerAdapter.addDumpSwitch(title);
    }

    /**
     * @return Index of the new active tab or -1 for no tab after removing
     */
    @Override
    public int removeDumpTab(int index) {
        int activePosition = thermalDumpsRecyclerAdapter.removeDumpSwitch(index);
        if (activePosition == -1)
            spotsControl.setEnabled(false);
        return activePosition;
    }

    @Override
    protected void onDestroy() {
        if (presenter != null)
            presenter.onDetach();

        recyclerDumpSwitcher.setAdapter(null);
        thermalDumpsRecyclerAdapter.setOnInteractionListener(null);
        thermalDumpsRecyclerAdapter = null;

        super.onDestroy();
    }
}
