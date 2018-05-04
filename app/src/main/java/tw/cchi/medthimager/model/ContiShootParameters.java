package tw.cchi.medthimager.model;

import java.util.Date;

public class ContiShootParameters {
    public Date timeStart;
    public int interval;
    public int capturedCount;
    public int totalCaptures;

    public int secondsToNextTick = 0;

    public ContiShootParameters(int interval, int totalCaptures) {
        this.interval = interval;
        this.totalCaptures = totalCaptures;
    }

}
