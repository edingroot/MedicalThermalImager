package tw.cchi.flironedemo1.thermalproc;

import java.io.File;

public class RawThermalDump {
    public int width;
    public int height;
    public int[] thermalValues;
    private String filepath;
    private String title;

    public RawThermalDump(int width, int height, int[] thermalValues) {
        this.width = width;
        this.height = height;
        this.thermalValues = thermalValues;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;

        // Generate title; filenameEx: 2017-10-08-16-10-08_rawThermal.dat
        String filename = new File(filepath).getName();
        title = filename.substring("2017-".length(), "_rawThermal.dat".length());
    }

    public String getTitle() {
        return title;
    }

    public float getTemperatureAt(int x, int y) {
        int index = y * width + x;

        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        return (float) (thermalValues[index] - 27315) / 100;
    }

    public float getTemperatureKAt(int x, int y) {
        int index = y * width + x;

        if (index >= thermalValues.length)
            throw new RuntimeException("index < thermalValues.length");

        return thermalValues[index] / 100.0f;
    }

    /**
     * Get the average of the 9 pixels around the point.
     *  - Note: this code is not optimized.
     * @param x
     * @param y
     * @return The average temperature in degree Celsius
     */
    public double getTemperature9Average(int x, int y) {
        int centerPixelIndex = width * y + x;

        int[] centerPixelIndexes = new int[]{
                centerPixelIndex, centerPixelIndex - 1, centerPixelIndex + 1,
                centerPixelIndex - width,
                centerPixelIndex - width - 1,
                centerPixelIndex - width + 1,
                centerPixelIndex + width,
                centerPixelIndex + width - 1,
                centerPixelIndex + width + 1
        };

        double averageTemp = 0;
        for (int i = 0; i < centerPixelIndexes.length; i++) {
            // Remember: all primitives are signed, we want the unsigned value,
            // we've used renderedImage.thermalPixelValues() to get unsigned values
            int pixelValue = thermalValues[centerPixelIndexes[i]];
            averageTemp += (((double) pixelValue) - averageTemp) / ((double) i + 1);
        }
        return (averageTemp / 100) - 273.15;

    }
}
