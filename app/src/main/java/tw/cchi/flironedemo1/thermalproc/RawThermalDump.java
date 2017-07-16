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
}
