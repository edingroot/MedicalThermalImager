package tw.cchi.medthimager.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class ChartParameter<T extends Number> implements Parcelable {
    public enum ChartType {MULTI_LINE_CURVE}

    private ChartType chartType;
    private float alpha = 1.0f;
    private float axisMin = -1;
    private float axisMax = -1;
    private ArrayList<String> titles;
    private ArrayList<T[]> numbersArrays;

    public ChartParameter(ChartType chartType) {
        this.chartType = chartType;
        this.titles = new ArrayList<>();
        this.numbersArrays = new ArrayList<>();
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

    public void addNumbersArray(String title, T[] numbersArray) {
        titles.add(title);
        numbersArrays.add(numbersArray);
    }

    public void updateNumbersArray(int index, T[] numbers) {
        numbersArrays.set(index, numbers);
    }

    public void removeFloatArray(int index) {
        titles.remove(index);
        numbersArrays.remove(index);
    }

    public ArrayList<T[]> getNumbersArrays() {
        return numbersArrays;
    }

    protected ChartParameter(Parcel in) {
        chartType = ChartType.values()[in.readInt()];
        numbersArrays = in.readArrayList(numbersArrays.getClass().getClassLoader());
    }

    public static final Creator<ChartParameter> CREATOR = new Creator<ChartParameter>() {
        @Override
        public ChartParameter createFromParcel(Parcel in) {
            return new ChartParameter<>(in);
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
        dest.writeList(numbersArrays);
    }
}
