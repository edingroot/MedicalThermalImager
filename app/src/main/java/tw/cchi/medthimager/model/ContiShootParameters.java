package tw.cchi.medthimager.model;

import java.util.Date;

public class ContiShootParameters {

    public int period;
    public int totalCaptures;

    public Date timeStart;
    public int capturedCount;

    public ContiShootParameters(int period, int totalCaptures) {
        this.period = period;
        this.totalCaptures = totalCaptures;
    }

}
