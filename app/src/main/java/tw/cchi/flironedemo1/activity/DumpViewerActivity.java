package tw.cchi.flironedemo1.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.utils.Orientation;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.model.ChartParameter;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpParser;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;
import tw.cchi.flironedemo1.view.MultiChartView;

@RuntimePermissions
public class DumpViewerActivity extends Activity {
    private ArrayList<String> thermalDumpPaths = new ArrayList<>();
    private ArrayList<RawThermalDump> rawThermalDumps = new ArrayList<>();
    private ArrayList<ThermalDumpProcessor> thermalDumpProcessors = new ArrayList<>();
    private ArrayList<Bitmap> thermalBitmaps = new ArrayList<>();
    private int selectedThermalDumpIndex = -1;
    private boolean displayingChart = false;

    private int thermalSpotX = -1;
    private int thermalSpotY = -1;

    @BindView(R.id.layoutThermalViews) FrameLayout layoutThermalViews;
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

        // Launch image picker on activity first started
        onImagePickClicked(findViewById(R.id.imgBtnPick));
        showToastMessage(getString(R.string.pick_thermal_image));

        thermalImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (thermalImageView.getMeasuredHeight() > 0) {
                    // Calculate actual touched position on the thermal image
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    if (y >= 0 && y < thermalImageView.getMeasuredHeight()) {
                        handleThermalImageTouch(x, y);
                    }
                }

                // Consume the event, which onClick event will not triggered
                return true;
            }
        });

        thermalChartView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case FilePickerConst.REQUEST_CODE_DOC:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> docPaths = new ArrayList<>();

                    docPaths.addAll(data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS));
                    AppUtils.removeListDuplication(docPaths, thermalDumpPaths);
                    for (String path : docPaths) {
                        openRawThermalDump(path);
                    }
                }
                break;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DumpViewerActivityPermissionsDispatcher.onRequestPermissionsResult(this,requestCode, grantResults);
    }

    public void onImagePickClicked(View v) {
        DumpViewerActivityPermissionsDispatcher.onPickDocWithPermissionCheck(this);
    }

    public void onVisualizeHorizonClicked(View v) {
        if (rawThermalDumps.size() == 0 || selectedThermalDumpIndex == -1 || thermalSpotY == -1)
            return;

        RawThermalDump rawThermalDump = rawThermalDumps.get(selectedThermalDumpIndex);

        if (displayingChart) {
            thermalChartView.setVisibility(View.GONE);
            displayingChart = false;
        } else {
            updateLineChart(rawThermalDump);
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

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void onPickDoc() {
        String[] thermalDumpExts = {".dat"};
        int MAX_FILES_COUNT = 3;

        if(thermalDumpPaths.size() > MAX_FILES_COUNT )
            showToastMessage("Cannot select more than " + MAX_FILES_COUNT + " items");
        else
            FilePickerBuilder.getInstance().setMaxCount(3)
                    .setSelectedFiles(thermalDumpPaths)
                    .setActivityTheme(R.style.FilePickerTheme)
                    .addFileSupport("ThermalDump", thermalDumpExts)
                    .enableDocSupport(false)
                    .withOrientation(Orientation.PORTRAIT_ONLY)
                    .pickFile(this);
    }

    private void openRawThermalDump(final String filepath) {
        (new Runnable() {
            @Override
            public void run() {
                RawThermalDump thermalDump = ThermalDumpParser.readRawThermalDump(filepath);

                if (thermalDump != null) {
                    ThermalDumpProcessor thermalDumpProcessor = new ThermalDumpProcessor(thermalDump);
                    Bitmap thermalBitmap = thermalDumpProcessor.getBitmap(1);

                    rawThermalDumps.add(thermalDump);
                    thermalDumpProcessors.add(thermalDumpProcessor);
                    thermalBitmaps.add(thermalBitmap);

                    setSelectedThermalDump(rawThermalDumps.size() - 1);
                    // TODO: handle multiple thermal images at a same time
                    updateThermalImageView(thermalBitmap);

                } else {
                    showToastMessage("Failed reading thermal dump");
                    thermalDumpProcessors = null;
                    thermalBitmaps = null;
                    updateThermalImageView(null);
                }
            }
        }).run();
    }

    private void updateThermalImageView(final Bitmap frame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
                thermalSpotX = layoutThermalViews.getMeasuredWidth() / 2;
                thermalSpotY = layoutThermalViews.getMeasuredHeight() / 2;
                handleThermalImageTouch(thermalSpotX, thermalSpotY);
            }
        });
    }

    private boolean setSelectedThermalDump(int selectedIndex) {
        if (selectedIndex >= rawThermalDumps.size())
            return false;

        this.selectedThermalDumpIndex = selectedIndex;
        // TODO: display selected status on UI

        return true;
    }

    /**
     *
     * @param x the pX value on the imageView
     * @param y the pY value on the imageView
     */
    private void handleThermalImageTouch(int x, int y) {
        if (rawThermalDumps.size() == 0 || selectedThermalDumpIndex == -1)
            return;

        RawThermalDump rawThermalDump = rawThermalDumps.get(selectedThermalDumpIndex);

        // Calculate the correspondent point on the thermal image
        double ratio = (double) rawThermalDump.width / thermalImageView.getMeasuredWidth();
        int imgX = (int) (x * ratio);
        int imgY = (int) (y * ratio);
        thermalSpotX = AppUtils.trimByRange(imgX, 1, rawThermalDump.width - 1);
        thermalSpotY = AppUtils.trimByRange(imgY, 1, rawThermalDump.height - 1);

        // Set indication spot location
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutTempSpot.getLayoutParams();
        params.leftMargin = x - layoutTempSpot.getMeasuredWidth() / 2;
        params.topMargin = y - layoutTempSpot.getMeasuredHeight() / 2 + layoutThermalViews.getTop();
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        layoutTempSpot.setLayoutParams(params);

        // Set horizontal line location
        params = (RelativeLayout.LayoutParams) horizontalLine.getLayoutParams();
        params.topMargin = y + layoutThermalViews.getTop();
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        horizontalLine.setLayoutParams(params);

        updateThermalSpotValue(rawThermalDump);
    }

    private void updateThermalSpotValue(RawThermalDump rawThermalDump) {
        double averageC;
        if (thermalSpotX == -1) {
            averageC = rawThermalDump.getTemperature9Average(rawThermalDump.width / 2, rawThermalDump.height / 2);
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
            updateLineChart(rawThermalDump);
        }
    }

    private void updateLineChart(RawThermalDump rawThermalDump) {
        ChartParameter chartParameter = new ChartParameter(ChartParameter.ChartType.MULTI_LINE_CURVE);
        chartParameter.setAlpha(0.6f);

        int width = rawThermalDump.width;
        float[] temperaturePoints = new float[width];
        for (int i = 0; i < width; i++) {
            temperaturePoints[i] = rawThermalDump.getTemperatureAt(i, thermalSpotY);
        }
        chartParameter.addFloatArray(temperaturePoints);
        thermalChartView.setChartParameter(chartParameter);
    }

}
