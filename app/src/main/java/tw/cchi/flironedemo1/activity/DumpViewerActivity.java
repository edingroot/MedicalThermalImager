package tw.cchi.flironedemo1.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.utils.Orientation;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.Config;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.adapter.ThermalDumpsRecyclerAdapter;
import tw.cchi.flironedemo1.model.ChartParameter;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;
import tw.cchi.flironedemo1.view.MultiChartView;

@RuntimePermissions
public class DumpViewerActivity extends BaseActivity {

    private final Object dumpListLock = new Object();
    private volatile ArrayList<String> thermalDumpPaths = new ArrayList<>();
    private volatile ArrayList<RawThermalDump> rawThermalDumps = new ArrayList<>();
    private volatile ArrayList<ThermalDumpProcessor> thermalDumpProcessors = new ArrayList<>();
    private volatile ArrayList<Bitmap> thermalBitmaps = new ArrayList<>();
    private volatile ChartParameter thermalChartParameter;

    private ThermalDumpsRecyclerAdapter thermalDumpsRecyclerAdapter;
    private volatile ExecutorService addThermalDumpExecutorService;
    private int selectedThermalDumpIndex = -1;
    private boolean displayingChart = false;

    private int thermalSpotX = -1;
    private int thermalSpotY = -1;
    private volatile float chartAxisMax = -1;
    private volatile float chartAxisMin = -1;

    @BindView(R.id.layoutThermalViews) FrameLayout layoutThermalViews;
    @BindView(R.id.recyclerDumpSwitcher) RecyclerView recyclerDumpSwitcher;

    @BindView(R.id.thermalImageView) ImageView thermalImageView;
    @BindView(R.id.thermalChartView) MultiChartView thermalChartView;

    @BindView(R.id.layoutTempSpot) RelativeLayout layoutTempSpot;
    @BindView(R.id.spotMeterValue) TextView spotMeterValue;
    @BindView(R.id.horizontalLine) View horizontalLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_viewer);
        ButterKnife.bind(this);
        thermalChartParameter = new ChartParameter(ChartParameter.ChartType.MULTI_LINE_CURVE);
        thermalChartParameter.setAlpha(0.6f);

        // Launch image picker on activity first started
        onImagePickClicked(findViewById(R.id.imgBtnPick));
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
                    handleThermalImageTouch(x, y, true);
                }

                // Consume the event, which onClick event will not triggered
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

        thermalDumpsRecyclerAdapter = new ThermalDumpsRecyclerAdapter(this, new ThermalDumpsRecyclerAdapter.OnInteractionListener() {
            @Override
            public void onClick(int position) {
                selectedThermalDumpIndex = position;
                updateThermalImageView(thermalBitmaps.get(position));
            }

            @Override
            public void onLongClick(int position) {
                final int dumpPosition = position;

                new AlertDialog.Builder(DumpViewerActivity.this, R.style.MyAlertDialog)
                        .setTitle("Confirm")
                        .setMessage("Confirm to remove " + rawThermalDumps.get(position).getTitle() + " from display?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeThermalDump(dumpPosition);
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
        int MAX_FILES_COUNT = 3;

        if(rawThermalDumps.size() > MAX_FILES_COUNT) {
            showToastMessage("Cannot select more than " + MAX_FILES_COUNT + " items");
        }  else {
            FilePickerBuilder.getInstance().setMaxCount(3)
                    .setSelectedFiles(thermalDumpPaths)
                    .setActivityTheme(R.style.FilePickerTheme)
                    .addFileSupport("ThermalDump", thermalDumpExts)
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
                    ArrayList<String> removePaths = new ArrayList<>(thermalDumpPaths);
                    ArrayList<String> currentPaths = new ArrayList<>(thermalDumpPaths);

                    addPaths.addAll(data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS));

                    for (String path : currentPaths) {
                        // Selected file has already been added
                        if (addPaths.contains(path)) {
                            addPaths.remove(path);
                            removePaths.remove(path);
                        }
                    }

                    for (String path : removePaths) {
                        removeThermalDump(thermalDumpPaths.indexOf(path));
                    }

                    if (addPaths.size() > 0) {
                        if (addThermalDumpExecutorService == null || addThermalDumpExecutorService.isShutdown() || addThermalDumpExecutorService.isTerminated())
                            addThermalDumpExecutorService = Executors.newCachedThreadPool();
                        for (String path : addPaths) {
                            addThermalDump(path);
                        }
                    } else {
                        if (thermalDumpPaths.size() == 0) {
                            thermalImageView.setImageBitmap(null);
                        }
                    }
                    updateChartAxis();
                }
                break;
        }
    }

    public void onVisualizeHorizonClicked(View v) {
        if (rawThermalDumps.size() == 0 || selectedThermalDumpIndex == -1 || thermalSpotY == -1)
            return;

        if (displayingChart) {
            thermalChartView.setVisibility(View.GONE);
            displayingChart = false;
        } else {
            updateChartParameter(thermalChartParameter, thermalSpotY);
            thermalChartView.updateChart(thermalChartParameter);
            thermalChartView.setVisibility(View.VISIBLE);
            displayingChart = true;
        }
    }

    private void showToastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DumpViewerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addThermalDump(final String filepath) {
        addThermalDumpExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                RawThermalDump thermalDump = RawThermalDump.readFromDumpFile(filepath);

                if (thermalDump != null) {
                    ThermalDumpProcessor thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                    Bitmap thermalBitmap = thermalDumpProcessor.getBitmap(1);

                    if (thermalSpotX == -1 || thermalSpotY == -1) {
                        thermalSpotX = thermalDump.getWidth() / 2;
                        thermalSpotY = thermalDump.getHeight() / 2;
                    }

                    int newIndex;
                    synchronized (dumpListLock) {
                        thermalDumpPaths.add(filepath);
                        rawThermalDumps.add(thermalDump);
                        thermalDumpProcessors.add(thermalDumpProcessor);
                        thermalBitmaps.add(thermalBitmap);
                        addToChartParameter(thermalChartParameter, thermalDump, thermalSpotY);
                        newIndex = thermalDumpsRecyclerAdapter.addDumpSwitch(thermalDump.getTitle());
                    }

                    if (selectedThermalDumpIndex != newIndex) {
                        selectedThermalDumpIndex = newIndex;
                        updateThermalImageView(thermalBitmap);
                    }

                } else {
                    showToastMessage("Failed reading thermal dump");
                    thermalDumpProcessors = null;
                    thermalBitmaps = null;
                    updateThermalImageView(null);
                }
            }
        });
    }

    private void removeThermalDump(int index) {
        synchronized (dumpListLock) {
            int newIndex = thermalDumpsRecyclerAdapter.removeDumpSwitch(index);

            if (selectedThermalDumpIndex == index) {
                updateThermalImageView(thermalBitmaps.get(index));
            }
            selectedThermalDumpIndex = newIndex;
            thermalDumpPaths.remove(index);
            rawThermalDumps.remove(index);
            thermalDumpProcessors.remove(index);
            thermalBitmaps.remove(index);
            removeFromChartParameter(thermalChartParameter, index);
        }

        System.gc();
    }

    private void updateThermalImageView(final Bitmap frame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
                // Execute after view measuring and layouting
                thermalImageView.post(new Runnable() {
                    @Override
                    public void run() {
                        handleThermalImageTouch(thermalSpotX, thermalSpotY, false);
                    }
                });
            }
        });
    }

    /**
     *
     * @param x
     * @param y
     * @param isImageViewCoordinates true: (x, y) on the imageView; false: (x, y) on the thermalImage (not scaled)
     */
    private void handleThermalImageTouch(int x, int y, boolean isImageViewCoordinates) {
        if (rawThermalDumps.size() == 0 || selectedThermalDumpIndex == -1)
            return;

        RawThermalDump rawThermalDump = rawThermalDumps.get(selectedThermalDumpIndex);

        // Calculate the correspondent point on the thermal image
        double ratio = (double) rawThermalDump.getWidth() / thermalImageView.getMeasuredWidth();
        if (isImageViewCoordinates) {
            thermalSpotX = AppUtils.trimByRange((int) (x * ratio), 1, rawThermalDump.getWidth() - 1);
            thermalSpotY = AppUtils.trimByRange((int) (y * ratio), 1, rawThermalDump.getHeight() - 1);
        } else {
            thermalSpotX = x;
            thermalSpotY = y;
            // Make x and y be the actual coordinates on the imageView which will be used below
            x = (int) (x / ratio);
            y = (int) (y / ratio);
        }

        // Set indication spot location
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutTempSpot.getLayoutParams();
        params.leftMargin = x - layoutTempSpot.getMeasuredWidth() / 2;
        params.topMargin = y - layoutTempSpot.getMeasuredHeight() / 2 + layoutThermalViews.getTop() + thermalImageView.getTop();
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        layoutTempSpot.setLayoutParams(params);

        // Set horizontal line location
        params = (RelativeLayout.LayoutParams) horizontalLine.getLayoutParams();
        params.topMargin = y + layoutThermalViews.getTop() + thermalImageView.getTop();
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        horizontalLine.setLayoutParams(params);

        updateThermalSpotValue();
    }

    private void updateThermalSpotValue() {
        RawThermalDump rawThermalDump = rawThermalDumps.get(selectedThermalDumpIndex);

        double averageC;
        if (thermalSpotX == -1) {
            averageC = rawThermalDump.getTemperature9Average(rawThermalDump.getWidth() / 2, rawThermalDump.getHeight() / 2);
        } else {
            averageC = rawThermalDump.getTemperature9Average(thermalSpotX, thermalSpotY);
        }

        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        final String spotMeterValue = numberFormat.format(averageC) + "ºC";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DumpViewerActivity.this.spotMeterValue.setText(spotMeterValue);
            }
        });

        if (displayingChart) {
            updateChartParameter(thermalChartParameter, thermalSpotY);
            thermalChartView.updateChart(thermalChartParameter);
        }
    }

    /**
     * Note: Not calling updateChartAxis() here because it will be called when all thermalDumps are added
     *
     * @param chartParameter
     * @param rawThermalDump
     * @param y
     */
    private void addToChartParameter(ChartParameter chartParameter, RawThermalDump rawThermalDump, int y) {
        int width = rawThermalDump.getWidth();
        float[] temperaturePoints = new float[width];

        for (int i = 0; i < width; i++) {
            temperaturePoints[i] = rawThermalDump.getTemperatureAt(i, y);
        }
        chartParameter.addFloatArray(rawThermalDump.getTitle(), temperaturePoints);
    }

    /**
     * Note: Not calling updateChartAxis() here
     *
     * @param chartParameter
     * @param index
     */
    private void removeFromChartParameter(ChartParameter chartParameter, int index) {
        chartParameter.removeFloatArray(index);
    }

    private void updateChartParameter(ChartParameter chartParameter, int y) {
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
                Log.i(Config.TAG, "updateChartAxis: waiting for all addThermalDump threads finished");

                // Wait until all addThermalDump threads finished
                addThermalDumpExecutorService.shutdown();
                try {
                    // All tasks have finished or the time has been reached
                    addThermalDumpExecutorService.awaitTermination(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {}
                if (DumpViewerActivity.this.isDestroyed())
                    return;

                Log.i(Config.TAG, "updateChartAxis: addThermalDumpExecutorService terminated");

                float max = Float.MIN_VALUE;
                float min = Float.MAX_VALUE;

                for (RawThermalDump rawThermalDump : rawThermalDumps) {
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
                    if (displayingChart) {
                        thermalChartView.updateChart(thermalChartParameter);
                    }
                }
            }
        }).start();
    }

}
