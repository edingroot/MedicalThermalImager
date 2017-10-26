package tw.cchi.flironedemo1.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class ChartParameter implements Parcelable {
    public enum ChartType {
        MULTI_LINE_CURVE
    }
    private ChartType chartType;
    private float alpha = 1.0f;
    private float axisMin = -1;
    private float axisMax = -1;
    private ArrayList<String> titles;
    private ArrayList<float[]> floatArrays;

    public ChartParameter(ChartType chartType) {
        this.chartType = chartType;
        this.titles = new ArrayList<>();
        this.floatArrays = new ArrayList<>();
    }

    public ChartType getChartType() {
        return chartType;
    }

    public float getAxisMin() {
        return axisMin;
    }

    public void setAxisMin(float axisMin) {
        this.axisMin = axisMin;
    }

    public float getAxisMax() {
        return axisMax;
    }

    public void setAxisMax(float axisMax) {
        this.axisMax = axisMax;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public String getTitle(int index) {
        return titles.get(index);
    }

    public void addFloatArray(String title, float[] floatArray) {
        titles.add(title);
        floatArrays.add(floatArray);
    }

    public boolean updateFloatArray(int index, float[] floatArray) {
        if (index >= floatArrays.size())
            return false;

        floatArrays.set(index, floatArray);
        return true;
    }

    public boolean removeFloatArray(int index) {
        if (index >= titles.size() || index >= floatArrays.size())
            return false;

        titles.remove(index);
        floatArrays.remove(index);
        return true;
    }

    public ArrayList<float[]> getFloatArrays() {
        return floatArrays;
    }

    protected ChartParameter(Parcel in) {
        chartType = ChartType.values()[in.readInt()];
        in.readArrayList(float[].class.getClassLoader());
    }

    public static final Creator<ChartParameter> CREATOR = new Creator<ChartParameter>() {
        @Override
        public ChartParameter createFromParcel(Parcel in) {
            return new ChartParameter(in);
        }

        @Override
        public ChartParameter[] newArray(int size) {
            return new ChartParameter[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(chartType.ordinal());
        dest.writeList(floatArrays);
    }
}
