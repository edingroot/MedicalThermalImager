package tw.cchi.flironedemo1.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.AppUtils;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.model.ChartParameter;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpParser;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;
import tw.cchi.flironedemo1.view.MultiChartView;


public class DumpViewerActivity extends Activity {
    public static final int ACTION_PICK_DUMP_FILE = 100;

    private String thermalDumpPath;
    private RawThermalDump rawThermalDump;
    private ThermalDumpProcessor thermalDumpProcessor;
    private Bitmap thermalBitmap;
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
            case ACTION_PICK_DUMP_FILE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        openRawThermalDump(uri.getPath());
                    } else {
                        Toast.makeText(DumpViewerActivity.this, "Invalid file path", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }

    }

    public void onImagePickClicked(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        Intent destIntent = Intent.createChooser(intent, getString(R.string.pick_thermal_image));
        startActivityForResult(destIntent, ACTION_PICK_DUMP_FILE);
    }

    public void onVisualizeHorizonClicked(View v) {
        if (rawThermalDump == null || thermalSpotY == -1)
            return;

        if (displayingChart) {
            thermalChartView.setVisibility(View.GONE);
            displayingChart = false;
        } else {
            updateLineChart();
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

    private void openRawThermalDump(final String filepath) {
        (new Runnable() {
            @Override
            public void run() {
                rawThermalDump = ThermalDumpParser.readRawThermalDump(filepath);
                if (rawThermalDump != null) {
                    thermalDumpPath = filepath;
                    thermalDumpProcessor = new ThermalDumpProcessor(rawThermalDump);
                    thermalBitmap = thermalDumpProcessor.getBitmap(1);
                    updateThermalImageView(thermalBitmap);
                } else {
                    showToastMessage("Failed reading thermal dump");
                    thermalDumpPath = null;
                    thermalDumpProcessor = null;
                    thermalBitmap = null;
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

    private void updateThermalSpotValue() {
        if (rawThermalDump == null)
            return;

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

        updateLineChart();
    }

    /**
     *
     * @param x the pX value on the imageView
     * @param y the pY value on the imageView
     */
    private void handleThermalImageTouch(int x, int y) {
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

        updateThermalSpotValue();
    }

    private void updateLineChart() {
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
