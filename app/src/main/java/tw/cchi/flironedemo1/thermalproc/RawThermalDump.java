package tw.cchi.flironedemo1.thermalproc;

public class RawThermalDump {
    public int width;
    public int height;
    public int[] thermalValues;

    public RawThermalDump(int width, int height, int[] thermalValues) {
        this.width = width;
        this.height = height;
        this.thermalValues = thermalValues;
    }

    public double getTemperatureAt(int x, int y) {
        int index = y * width + x;

        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        return (double) (thermalValues[index] - 27315) / 100;
    }

    public double getTemperatureKAt(int x, int y) {
        int index = y * width + x;

        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        return thermalValues[index] / 100.0;
    }
}
