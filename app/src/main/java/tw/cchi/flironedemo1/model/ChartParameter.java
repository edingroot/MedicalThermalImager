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
    private ArrayList<float[]> floatArrays;

    public ChartParameter(ChartType chartType) {
        this.chartType = chartType;
        this.floatArrays = new ArrayList<>();
    }

    public ChartType getChartType() {
        return chartType;
    }

    public void addFloatArray(float[] floatArray) {
        floatArrays.add(floatArray);
    }

    public ArrayList<float[]> getFloatArrays() {
        return floatArrays;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
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
