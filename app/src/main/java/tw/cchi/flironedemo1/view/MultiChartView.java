package tw.cchi.flironedemo1.view;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.model.ChartParameter;

public class MultiChartView extends RelativeLayout {
    private ChartParameter chartParameter;

    @BindView(R.id.chart1) LineChart lineChart;

    public MultiChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.view_multi_thermalcharts, this, true);
        ButterKnife.bind(this);
        initLineChart();
    }

    public void setChartParameter(ChartParameter chartParameter) {
        this.chartParameter = chartParameter;
        updateChart();
    }

    private void updateChart() {
        // TODO: auto calculate and set axis max/min values

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        int[] colors = ColorTemplate.VORDIPLOM_COLORS;
        ArrayList<float[]> valueArrays = chartParameter.getFloatArrays();

        for (int i = 0; i < valueArrays.size(); i++) {
            float[] valueArray = valueArrays.get(i);
            ArrayList<Entry> values = new ArrayList<>();

            for (int j = 0; j < valueArray.length; j++) {
                values.add(new Entry(j, valueArray[j]));
            }

            LineDataSet lineDataSet = new LineDataSet(values, "ThermalGraph " + (i + 1));
            lineDataSet.setLineWidth(2.5f);
            lineDataSet.setCircleRadius(4f);

            int color = colors[i % colors.length];
            lineDataSet.setColor(color);
            lineDataSet.setDrawCircles(false);
            lineDataSet.setDrawCircles(false);
            dataSets.add(lineDataSet);
        }

        LineData data = new LineData(dataSets);
        lineChart.setData(data);
        lineChart.setAlpha(chartParameter.getAlpha());
        lineChart.invalidate();
    }

    private void initLineChart() {
        lineChart.setBackgroundColor(Color.rgb(104, 241, 175));

        // no description text
        lineChart.getDescription().setEnabled(false);

        // Disable touch gestures in order to pass touch event to the underlying view
        lineChart.setTouchEnabled(false);

        lineChart.setDrawGridBackground(false);

        YAxis leftAxis = lineChart.getAxisLeft();
        // TODO: remove fixed axis max/min values
        leftAxis.setAxisMaximum(45f);
        leftAxis.setAxisMinimum(30f);
        //leftAxis.setYOffset(20f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setEnabled(true);
        lineChart.getAxisRight().setEnabled(false);
    }
}
