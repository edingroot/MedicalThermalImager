package tw.cchi.flironedemo1.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.models.sort.SortingTypes;
import droidninja.filepicker.utils.Orientation;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.Config;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.adapter.ThermalDumpsRecyclerAdapter;
import tw.cchi.flironedemo1.helper.ThermalSpotsHelper;
import tw.cchi.flironedemo1.helper.ViewerTabResourcesHelper;
import tw.cchi.flironedemo1.model.ChartParameter;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;
import tw.cchi.flironedemo1.thermalproc.VisibleImageMask;
import tw.cchi.flironedemo1.view.MultiChartView;

@RuntimePermissions
public class DumpViewerActivity extends BaseActivity {
    private static final int MAX_OPENING_FILES = 3;

    private volatile ViewerTabResourcesHelper tabResources = new ViewerTabResourcesHelper();
    private volatile ChartParameter thermalChartParameter;
    private ThermalDumpsRecyclerAdapter thermalDumpsRecyclerAdapter;

    private volatile ExecutorService addThermalDumpExecutor;
    private boolean showingVisibleImage = false;
    private volatile boolean visibleImageAlignMode = false;
    private boolean showingChart = false;

    private int horizontalLineY = -1; // pY (on the thermal dump) of horizontal indicator on showingChart mode
    private volatile float chartAxisMax = -1;
    private volatile float chartAxisMin = -1;

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

    @BindView(R.id.btnToggleVisible) ImageView btnToggleVisible;
    @BindView(R.id.fabAddSpot) FloatingActionButton fabAddSpot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_viewer);
        ButterKnife.bind(this);
        thermalChartParameter = new ChartParameter(ChartParameter.ChartType.MULTI_LINE_CURVE);
        thermalChartParameter.setAlpha(0.6f);

        // Launch image picker on activity first started
        onImagePickClicked(findViewById(R.id.btnPick));
        showToastMessage(getString(R.string.pick_thermal_images));

        thermalImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (thermalImageView.getMeasuredHeight() > 0) {
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    if (y < 0) {
                        y = 0;
                    } else if (y >= thermalImageView.getMeasuredHeight()) {
                        y = thermalImageView.getMeasuredHeight() - 1;
                    }
                    handleThermalImageTouch(x, y);
                }

                // Consuming the onTouch event in order to capture future movement
                return true;
            }
        });

        // Wait until the view have been measured (visibility state considered)
        // Ref: https://stackoverflow.com/questions/36586146/ongloballayoutlistener-vs-postrunnable
        visibleImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int width = thermalImageView.getMeasuredWidth();
                if (showingVisibleImage && visibleImageView.getMeasuredWidth() != width) {
                    visibleImageView.getLayoutParams().width = thermalImageView.getMeasuredWidth();
                    visibleImageView.getLayoutParams().height = thermalImageView.getMeasuredHeight();
                    visibleImageView.requestLayout();
                }
            }
        });

        visibleImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (!visibleImageAlignMode)
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
                        handleVisibleImageDragged();
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_POINTER_UP:
                        break;
                }
                return true;
            }
        });

        // Pass touch event to the underlying layout
        thermalChartView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });

        thermalDumpsRecyclerAdapter = new ThermalDumpsRecyclerAdapter(this,
                new ThermalDumpsRecyclerAdapter.OnInteractionListener() {

            @Override
            public void onClick(View v, int position) {
                ThermalSpotsHelper thermalSpotsHelper;

                // Hide all spots of the old dump, switch to new tab resources, show spots of the new dump
                thermalSpotsHelper = tabResources.getThermalSpotHelper();
                if (thermalSpotsHelper != null) thermalSpotsHelper.setSpotsVisible(false);
                tabResources.setCurrentIndex(position);
                thermalSpotsHelper = tabResources.getThermalSpotHelper();
                if (thermalSpotsHelper != null) thermalSpotsHelper.setSpotsVisible(true);

                if (showingVisibleImage) {
                    visibleImageAlignMode = false;
                    showVisibleImage(tabResources.getRawThermalDump());
                }

                updateThermalImageView(tabResources.getThermalBitmap());
            }

            @Override
            public void onLongClick(View v, int position) {
                // Show confirm dialog for closing this thermal dump
                final int dumpPosition = position;
                new AlertDialog.Builder(DumpViewerActivity.this, R.style.MyAlertDialog)
                        .setTitle("Confirm")
                        .setMessage("Confirm to remove " + tabResources.getRawThermalDump().getTitle() + " from display?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeThermalDump(dumpPosition, false);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        }).show();
            }
        });
        recyclerDumpSwitcher.setAdapter(thermalDumpsRecyclerAdapter);
        recyclerDumpSwitcher.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        btnToggleVisible.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                onToggleVisibleImageLongClicked(view);
                return true;
            }
        });

        fabAddSpot.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onFabAddSpotLongClicked(v);
                return true;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DumpViewerActivityPermissionsDispatcher.onRequestPermissionsResult(this,requestCode, grantResults);
    }

    public void onImagePickClicked(View v) {
        DumpViewerActivityPermissionsDispatcher.onPickDocWithPermissionCheck(this);
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void onPickDoc() {
        String[] thermalDumpExts = {".dat"};

        if(tabResources.getCount() > MAX_OPENING_FILES) {
            showToastMessage("Cannot select more than " + MAX_OPENING_FILES + " items");
        }  else {
            FilePickerBuilder.getInstance().setMaxCount(3)
                    .setSelectedFiles(tabResources.getThermalDumpPaths())
                    .setActivityTheme(R.style.FilePickerTheme)
                    .addFileSupport("ThermalDump", thermalDumpExts)
                    .sortDocumentsBy(SortingTypes.name)
                    .enableDocSupport(false)
                    .withOrientation(Orientation.PORTRAIT_ONLY)
                    .pickFile(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case FilePickerConst.REQUEST_CODE_DOC:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> addPaths = new ArrayList<>();
                    ArrayList<String> removePaths = new ArrayList<>(tabResources.getThermalDumpPaths());
                    ArrayList<String> currentPaths = new ArrayList<>(tabResources.getThermalDumpPaths());

                    addPaths.addAll(data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS));

                    for (String path : currentPaths) {
                        // Selected file has already been added
                        if (addPaths.contains(path)) {
                            addPaths.remove(path);
                            removePaths.remove(path);
                        }
                    }

                    // Update thermalImageView just on the last time
                    for (int i = 0; i < removePaths.size(); i++)
                        removeThermalDump(tabResources.indexOf(removePaths.get(i)), (i != removePaths.size() - 1));

                    if (addPaths.size() > 0) {
                        if (addThermalDumpExecutor == null || addThermalDumpExecutor.isShutdown() || addThermalDumpExecutor.isTerminated())
                            addThermalDumpExecutor = Executors.newCachedThreadPool();

                        // Make a short delay to avoid various async problems :P
                        // and also make dumps added sequentially
                        int delay = 0;
                        for (String path : addPaths) {
                            addThermalDump(path, 50 * delay++);
                        }
                    } else {
                        if (tabResources.getCount() == 0)
                            thermalImageView.setImageBitmap(null);
                    }
                    updateChartAxis();
                }
                break;
        }
    }

    public void onToggleVisibleImageClicked(View v) {
        if (tabResources.getCount() == 0)
            return;

        if (showingVisibleImage) {
            visibleImageView.setVisibility(View.GONE);
            showingVisibleImage = visibleImageAlignMode = false;
        } else {
            if (showVisibleImage(tabResources.getRawThermalDump())) {
                showingVisibleImage = true;
            } else {
                showingVisibleImage = visibleImageAlignMode = false;
            }
        }
    }

    public void onToggleVisibleImageLongClicked(View v) {
        if (tabResources.getCount() == 0)
            return;

        if (visibleImageAlignMode) {
            visibleImageAlignMode = false;
        } else {
            visibleImageAlignMode = true;
            if (!showingVisibleImage) {
                // Show visible image first
                onToggleVisibleImageClicked(btnToggleVisible);
            } else {
                visibleImageView.setAlpha(visibleImageAlignMode ? Config.DUMP_VISUAL_MASK_ALPHA / 255f : 1f);
            }
        }
    }

    public void onToggleHorizonChartClicked(View v) {
        if (tabResources.getCount() == 0 || horizontalLineY == -1)
            return;

        if (showingChart) {
            thermalChartView.setVisibility(View.GONE);
            horizontalLine.setVisibility(View.GONE);
            showingChart = false;
        } else {
            updateChartParameter(thermalChartParameter, horizontalLineY);
            thermalChartView.updateChart(thermalChartParameter);
            thermalChartView.setVisibility(View.VISIBLE);
            horizontalLine.setVisibility(View.VISIBLE);
            showingChart = true;
        }
    }

    public void onFabAddSpotClicked(View v) {
        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        int lastSpotId = thermalSpotsHelper.getLastSpotId();
        thermalSpotsHelper.addThermalSpot(lastSpotId == -1 ? 1 : lastSpotId + 1);
    }

    public void onFabAddSpotLongClicked(View v) {
        ThermalSpotsHelper thermalSpotsHelper = tabResources.getThermalSpotHelper();
        thermalSpotsHelper.removeLastThermalSpot();
    }

    private void showToastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DumpViewerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addThermalDump(final String filepath, final long delay) {
        addThermalDumpExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {}

                RawThermalDump thermalDump = RawThermalDump.readFromDumpFile(filepath);

                if (thermalDump != null) {
                    ThermalDumpProcessor thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                    Bitmap thermalBitmap = thermalDumpProcessor.getBitmap(1);

                    if (horizontalLineY == -1) {
                        horizontalLineY = thermalDump.getHeight() / 2;
                    }

                    tabResources.addResources(filepath, thermalDump, thermalDumpProcessor, thermalBitmap);
                    addToChartParameter(thermalChartParameter, thermalDump, horizontalLineY);
                    thermalChartView.updateChart(thermalChartParameter);

                    int newIndex = thermalDumpsRecyclerAdapter.addDumpSwitch(thermalDump.getTitle());
                    System.out.printf("addThermalDump: currIndex=%d, newIndex=%d\n", tabResources.getCurrentIndex(), newIndex);
                    if (tabResources.getCurrentIndex() != newIndex) {
                        tabResources.setCurrentIndex(newIndex);
                        updateThermalImageView(thermalBitmap);
                    }
                } else {
                    showToastMessage("Failed reading thermal dump");
                }
            }
        });
    }

    private void removeThermalDump(int index, boolean ignoreImageViewUpdate) {
        int currentIndex = tabResources.getCurrentIndex();
        int newIndex = thermalDumpsRecyclerAdapter.removeDumpSwitch(index);

        tabResources.removeResources(index, newIndex);
        removeDataFromChartParameter(thermalChartParameter, index);
        thermalChartView.updateChart(thermalChartParameter);

        // If the dump removing is the one that currently displaying & still have some other dumps opened
        if (currentIndex == index && newIndex != -1 && !ignoreImageViewUpdate) {
            System.out.printf("removeThermalDump: update thermal image view - currIndex=%d, newIndex=%d\n", currentIndex, newIndex);
            ThermalSpotsHelper thermalSpotsHelper;

            updateThermalImageView(tabResources.getThermalBitmap());
            thermalSpotsHelper = tabResources.getThermalSpotHelper();
            if (thermalSpotsHelper != null)
                thermalSpotsHelper.setSpotsVisible(true);
        }

        if (tabResources.getCount() == 0) {
            thermalImageView.setImageBitmap(null);

            if (showingChart) {
                thermalChartView.setVisibility(View.GONE);
                horizontalLine.setVisibility(View.GONE);
                showingChart = false;
            }

            if (showingVisibleImage) {
                visibleImageView.setImageBitmap(null);
                visibleImageView.setVisibility(View.GONE);
                showingVisibleImage = visibleImageAlignMode = false;
            }
        } else {
            if (showingVisibleImage) {
                visibleImageAlignMode = false;
                showVisibleImage(tabResources.getRawThermalDump());
            }
        }
        System.gc();
    }

    private void updateThermalImageView(final Bitmap frame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
            }
        });

        // Create new thermalSpotsHelper if not existed after view measured
        if (frame != null && tabResources.getCount() != 0 && tabResources.getThermalSpotHelper() == null) {
            thermalImageView.post(new Runnable() {
                @Override
                public void run() {
                    System.out.printf("In thermalImageView.post: tabResources.getCount=%d, tabResources.currIndex=%d\n",
                            tabResources.getCount(),
                            tabResources.getCurrentIndex()
                    );

                    // Make a short delay (at least 20ms) to wait ui thread for correct thermalImageView.getTop() value
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {}

                            final ThermalSpotsHelper thermalSpotsHelper = new ThermalSpotsHelper(
                                    DumpViewerActivity.this, topView, tabResources.getRawThermalDump()
                            );
                            thermalSpotsHelper.setImageViewMetrics(
                                    thermalImageView.getMeasuredWidth(),
                                    thermalImageView.getMeasuredHeight(),
                                    thermalImageView.getTop() + layoutThermalViews.getTop()
                            );
                            tabResources.addThermalSpotsHelper(thermalSpotsHelper);
                        }
                    }).start();
                }
            });
        }
    }

    private void handleThermalImageTouch(int x, int y) {
        if (tabResources.getCount() == 0)
            return;

        // Set horizontal line location
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) horizontalLine.getLayoutParams();
        params.topMargin = y + layoutThermalViews.getTop() + thermalImageView.getTop();
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        horizontalLine.setLayoutParams(params);

        // Calculate the correspondent point on the thermal image
        int thermalDumpHeight = tabResources.getRawThermalDump().getHeight();
        double ratio = (double) thermalDumpHeight / thermalImageView.getMeasuredHeight();
        horizontalLineY = AppUtils.trimByRange((int) (y * ratio), 1, thermalDumpHeight - 1);

        if (showingChart) {
            updateChartParameter(thermalChartParameter, horizontalLineY);
            thermalChartView.updateChart(thermalChartParameter);
        }
    }

    /**
     * Add thermal values on specific horizontal line on the thermal dump to the chart parameter.
     * [Note] Not calling updateChartAxis() here because it will be called when all thermalDumps are added
     *
     * @param chartParameter
     * @param rawThermalDump
     * @param y
     */
    private synchronized void addToChartParameter(ChartParameter chartParameter, RawThermalDump rawThermalDump, int y) {
        int width = rawThermalDump.getWidth();
        float[] temperaturePoints = new float[width];

        for (int i = 0; i < width; i++) {
            temperaturePoints[i] = rawThermalDump.getTemperatureAt(i, y);
        }
        chartParameter.addFloatArray(rawThermalDump.getTitle(), temperaturePoints);
    }

    /**
     * Remove data (float array) from chart parameter by index.
     *  Note: Not calling updateChartAxis() here
     *
     * @param chartParameter
     * @param index
     */
    private void removeDataFromChartParameter(ChartParameter chartParameter, int index) {
        chartParameter.removeFloatArray(index);
    }

    private void updateChartParameter(ChartParameter chartParameter, int y) {
        ArrayList<RawThermalDump> rawThermalDumps = tabResources.getRawThermalDumps();
        for (int i = 0; i < rawThermalDumps.size(); i++) {
            RawThermalDump rawThermalDump = rawThermalDumps.get(i);
            float[] temperaturePoints = new float[rawThermalDump.getWidth()];

            for (int j = 0; j < rawThermalDump.getWidth(); j++) {
                temperaturePoints[j] = rawThermalDump.getTemperatureAt(j, y);
            }

            chartParameter.updateFloatArray(i, temperaturePoints);
        }
    }

    /**
     * Calculate max and min temperature among all thermal dumps and update chart axis.
     */
    private void updateChartAxis() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("updateChartAxis: waiting for all addThermalDump threads finished");

                // Stop accepting new tasks (prepare for termination)
                addThermalDumpExecutor.shutdown();
                try {
                    // Wait until all tasks have finished or the time has been reached
                    addThermalDumpExecutor.awaitTermination(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {}
                if (DumpViewerActivity.this.isDestroyed())
                    return;

                System.out.println("updateChartAxis: addThermalDumpExecutor terminated");

                float max = Float.MIN_VALUE;
                float min = Float.MAX_VALUE;

                for (RawThermalDump rawThermalDump : tabResources.getRawThermalDumps()) {
                    if (rawThermalDump.getMaxTemperature() > max)
                        max = rawThermalDump.getMaxTemperature();
                    if (rawThermalDump.getMinTemperature() < min)
                        min = rawThermalDump.getMinTemperature();
                }

                if (max != chartAxisMax || min != chartAxisMin) {
                    /* max = (float) Math.ceil(max) + 9;
                    max -= max % 10;
                    min = (float) Math.floor(min);
                    min -= min % 10; */
                    chartAxisMax = max;
                    chartAxisMin = min;

                    thermalChartParameter.setAxisMax(chartAxisMax);
                    thermalChartParameter.setAxisMin(chartAxisMin);
                    if (showingChart) {
                        thermalChartView.updateChart(thermalChartParameter);
                    }
                }
            }
        }).start();
    }

    private boolean showVisibleImage(RawThermalDump rawThermalDump) {
        if (!rawThermalDump.isVisibleImageAttached()) {
            if (!rawThermalDump.attachVisibleImageMask()) {
                showToastMessage("Failed to read visible image. Does the jpg file with same name exist?");
                return false;
            }
            rawThermalDump.getVisibleImageMask().processFrame(this, new VisibleImageMask.BitmapUpdateListener() {
                @Override
                public void onBitmapUpdate(VisibleImageMask maskInstance) {
                    final VisibleImageMask mask = maskInstance;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateVisibleImageView(mask);
                        }
                    });
                }
            });
        } else {
            updateVisibleImageView(rawThermalDump.getVisibleImageMask());
        }
        return true;
    }

    private void updateVisibleImageView(VisibleImageMask mask) {
        visibleImageView.setImageBitmap(mask.getVisibleBitmap());
        visibleImageView.setAlpha(visibleImageAlignMode ? Config.DUMP_VISUAL_MASK_ALPHA / 255f : 1f);
        visibleImageView.setVisibility(View.VISIBLE);

        // Set initial params
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) visibleImageView.getLayoutParams();
        layoutParams.leftMargin = mask.getLinkedRawThermalDump().getVisibleOffsetX() + (int) thermalImageView.getX();
        layoutParams.topMargin = mask.getLinkedRawThermalDump().getVisibleOffsetY() + (int) thermalImageView.getY();

        // Prevent the view from being compressed when moving right or down
        layoutParams.rightMargin = -500;
        layoutParams.bottomMargin = -500;

        visibleImageView.setLayoutParams(layoutParams);
    }

    private void handleVisibleImageDragged() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RawThermalDump rawThermalDump = tabResources.getRawThermalDump();
                int diffX = (int) (visibleImageView.getX() - thermalImageView.getX());
                int diffY = (int) (visibleImageView.getY() - thermalImageView.getY());
                rawThermalDump.setVisibleOffsetX(diffX);
                rawThermalDump.setVisibleOffsetY(diffY);
                rawThermalDump.save();
            }
        }).start();
    }

}
