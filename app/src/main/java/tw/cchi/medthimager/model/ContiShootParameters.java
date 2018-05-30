package tw.cchi.medthimager.model;

import java.util.Date;
import java.util.UUID;

public class ContiShootParameters {
    public final String groupUuid;
    public Date timeStart;
    public int interval;
    public int capturedCount;
    public int totalCaptures;

    public int secondsToNextTick = 0;

    public ContiShootParameters(int interval, int totalCaptures) {
        this.groupUuid = UUID.randomUUID().toString();
        this.interval = interval;
        this.totalCaptures = totalCaptures;
    }

}
