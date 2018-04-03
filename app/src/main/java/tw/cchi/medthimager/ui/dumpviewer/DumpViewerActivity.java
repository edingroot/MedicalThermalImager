package tw.cchi.medthimager.ui.dumpviewer;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;

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
import tw.cchi.medthimager.adapter.ThermalDumpsRecyclerAdapter;
import tw.cchi.medthimager.component.MultiChartView;
import tw.cchi.medthimager.di.BgThreadCapable;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.model.ChartParameter;
import tw.cchi.medthimager.thermalproc.RawThermalDump;
import tw.cchi.medthimager.thermalproc.VisibleImageMask;
import tw.cchi.medthimager.ui.base.BaseActivity;

@RuntimePermissions
public class DumpViewerActivity extends BaseActivity implements DumpViewerMvpView {
    private static final int MAX_OPEN_FILES = 18;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_viewer);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);

        thermalDumpsRecyclerAdapter = new ThermalDumpsRecyclerAdapter(this,
                new ThermalDumpsRecyclerAdapter.OnInteractionListener() {

            @Override
            public boolean onClick(View v, int position) {
                return presenter.switchDumpTab(position);
            }

            @Override
            public void onLongClick(View v, final int position) {
                // Show confirm dialog for closing this thermal dump
                showAlertDialog(
                    "Confirm",
                    "Confirm to remove " + presenter.getDumpTitle() + " from display?",
                    (dialog, which) -> presenter.removeThermalDump(position, true),
                    (dialog, which) -> {}
                );
            }
        });
        recyclerDumpSwitcher.setAdapter(thermalDumpsRecyclerAdapter);
        recyclerDumpSwitcher.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DumpViewerActivityPermissionsDispatcher.onRequestPermissionsResult(this,requestCode, grantResults);
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void onPickDoc(ArrayList<String> selectedFiles) {
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
                presenter.updateVisibleImageOffset(diffX, diffY);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                break;
        }
        return true;
    }

    @OnClick(R.id.btnPick)
    public void onImagePickClick(View v) {
        presenter.pickImages();
    }

    @OnClick(R.id.btnToggleVisible)
    public void onToggleVisibleImageClick(View v) {
        presenter.toggleVisibleImage();
    }

    @OnLongClick(R.id.btnToggleVisible)
    public boolean onToggleVisibleImageLongClick(View v) {
        presenter.toggleVisibleImageAlignMode();
        return true;
    }

    @OnClick(R.id.btnToggleColoredMode)
    public void onToggleColoredModeClick(@Nullable View v) {
        presenter.toggleColoredMode();
    }

    @OnClick(R.id.btnToggleHorizonChart)
    public void onToggleHorizonChartClick(@Nullable View v) {
        presenter.toggleHorizonChart();
    }

    @OnClick(R.id.btnEdit)
    public void onEditClick(View v) {
        if (presenter.getTabsCount() == 0)
            return;

        PopupMenu popupMenu = new PopupMenu(this, v);

        // Thermal image actions
        popupMenu.getMenu().add(Menu.NONE, 10, Menu.NONE, "Export colored thermal image");
        popupMenu.getMenu().add(Menu.NONE, 11, Menu.NONE, "Export visible light image");

        // Spot actions
        popupMenu.getMenu().add(Menu.NONE, 20, Menu.NONE, "Remove last spot");
        popupMenu.getMenu().add(Menu.NONE, 21, Menu.NONE, "Clear spots");
        popupMenu.getMenu().add(Menu.NONE, 22, Menu.NONE,
            presenter.isSpotsVisible() ? "Hide spots" : "Show spots"
        );
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

                case 20:
                    presenter.removeLastThermalSpot();
                    break;

                case 21:
                    presenter.clearThermalSpots();
                    break;

                case 22:
                    presenter.toggleThermalSpotsVisible();
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

    @OnClick(R.id.fabAddSpot)
    public void onFabAddSpotClick(View v) {
        presenter.addThermalSpot();
    }

    @OnLongClick(R.id.fabAddSpot)
    public boolean onFabAddSpotLongClick(View v) {
        if (presenter.getTabsCount() == 0)
            return true;

        presenter.removeLastThermalSpot();
        return true;
    }


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
        final ThermalSpotsHelper thermalSpotsHelper = new ThermalSpotsHelper(this, topView, rawThermalDump);
        thermalSpotsHelper.setImageViewMetrics(
                thermalImageView.getMeasuredWidth(),
                thermalImageView.getMeasuredHeight(),
                thermalImageView.getTop() + layoutThermalViews.getTop()
        );
        return thermalSpotsHelper;
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
    public void updateThermalChart(ChartParameter thermalChartParameter) {
        thermalChartView.updateChart(thermalChartParameter);
    }

    /**
     * @param title
     * @return Index of the new active tab
     */
    @Override
    public int addDumpTab(String title) {
        return thermalDumpsRecyclerAdapter.addDumpSwitch(title);
    }

    /**
     * @param index
     * @return Index of the new active tab or -1 for no tab after removing
     */
    @Override
    public int removeDumpTab(int index) {
        return thermalDumpsRecyclerAdapter.removeDumpSwitch(index);
    }

    @Override
    protected void onDestroy() {
        presenter.onDetach();
        super.onDestroy();
    }
}
