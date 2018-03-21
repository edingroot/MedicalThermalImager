package tw.cchi.medthimager.component;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.BgThreadAvail;
import tw.cchi.medthimager.model.ChartParameter;

public class MultiChartView extends RelativeLayout {
    private Unbinder unbinder;

    private View rootView;
    private ChartParameter chartParameter;

    @BindView(R.id.chart1) LineChart lineChart;

    public MultiChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        rootView = inflate(context, R.layout.view_multi_thermalcharts, this);

        unbinder = ButterKnife.bind(this, rootView);

        initLineChart();
    }

    @BgThreadAvail
    public synchronized void updateChart(ChartParameter chartParameter) {
        this.chartParameter = chartParameter;

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        int[] colors = ColorTemplate.PASTEL_COLORS;
        ArrayList<float[]> valueArrays = chartParameter.getFloatArrays();

        for (int i = 0; i < valueArrays.size(); i++) {
            float[] valueArray = valueArrays.get(i);
            ArrayList<Entry> values = new ArrayList<>();

            for (int j = 0; j < valueArray.length; j++) {
                values.add(new Entry(j, valueArray[j]));
            }

            LineDataSet lineDataSet = new LineDataSet(values, chartParameter.getTitle(i));
            lineDataSet.setLineWidth(2.5f);
            lineDataSet.setCircleRadius(4f);

            int color = colors[i % colors.length];
            lineDataSet.setColor(color);
            lineDataSet.setDrawCircles(false);
            dataSets.add(lineDataSet);
        }

        lineChart.setData(new LineData(dataSets));
        lineChart.setAlpha(chartParameter.getAlpha());

        // Calculate and set max, min of the left axis
        YAxis leftAxis = lineChart.getAxisLeft();
        if (chartParameter.getAxisMax() != -1) {
            leftAxis.setAxisMaximum(chartParameter.getAxisMax());
        }
        if (chartParameter.getAxisMin() != -1) {
            leftAxis.setAxisMinimum(chartParameter.getAxisMin());
        }

        // Run on the ui thread
        this.post(new Runnable() {
            @Override
            public void run() {
                lineChart.invalidate();
            }
        });
    }

    private void initLineChart() {
        lineChart.setBackgroundColor(Color.rgb(104, 241, 175));

        // no description text
        lineChart.getDescription().setEnabled(false);

        // Disable touch gestures in order to pass touch event to the underlying view
        lineChart.setTouchEnabled(false);

        lineChart.setDrawGridBackground(false);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setEnabled(true);
        lineChart.getAxisRight().setEnabled(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (unbinder != null)
            unbinder.unbind();

        super.onDetachedFromWindow();
    }
}
