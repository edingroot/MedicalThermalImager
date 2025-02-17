package tw.cchi.medthimager.model;

/**
 * This can be used to avoid reporting status 'not running' while switching between task A1 to task A2.
 *
 * i.e. Prevent things from going wrong on step 4:
 *   1. [running] start Task A1, setRunning(true)
 *   2. [running] Task A1 is done
 *   3. [stopped] setRunning(false)
 *   4. [  ???  ] client wants to know if task series A is running (expect answer is yes)
 *   5. [running] start Task A2, setRunning(true)
 *   6. [running] Task A2 is done
 *   7. [stopped] setRunning(false)
 */
public class RecurrentRunningStatus {
    private static final long MAX_RESTART_GAP = 20; // ms

    private volatile boolean running = false;
    private volatile long lastStopCalled = 0;

    public RecurrentRunningStatus() {
    }

    public boolean isRunning() {
        synchronized (RecurrentRunningStatus.class) {
            if (running) {
                return true;
            } else if (lastStopCalled != 0) {
                if (System.currentTimeMillis() - lastStopCalled <= MAX_RESTART_GAP) {
                    lastStopCalled = 0;
                    return true;
                } else {
                    lastStopCalled = 0;
                }
            }
            return false;
        }
    }

    public void setRunning(boolean running) {
        synchronized (RecurrentRunningStatus.class) {
            this.lastStopCalled = running ? 0 : System.currentTimeMillis();
            this.running = running;
        }
    }
}
