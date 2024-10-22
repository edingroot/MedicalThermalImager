package tw.cchi.medthimager.data.fanalytics;

public final class Event {
    public static final String PREFIX = "medth_";

    public static final String SIMPLE_EVENT = "simple_event";

    public static final String CAMERA_CONNECTED = "camera_connected";
    public static final String CAMERA_DISCONNECTED = "camera_disconnected";
    public static final String CONNECT_SIMULATED_DEVICE = "connect_simulated_device";
    public static final String CAMERA_CHARGING_STATE_CHANGED = "camera_charging_state_changed";
    public static final String TUNING_STATE_CHANGED = "tuning_state_changed";
    public static final String AUTOMATIC_TUNING_CHANGED = "automatic_tuning_changed";
    public static final String MANUALLY_TUNE = "manually_tune";

    public static final String CAMERA_CAPTURE = "camera_capture";
    public static final String CONTISHOOT_START = "contishoot_start";
    public static final String CONTISHOOT_FINISH = "contishoot_finish";
    public static final String TUNING_WHILE_CONTI_SHOOT = "tuning_while_conti_shoot";

    public static final String SET_CURRENT_PATIENT = "set_current_patient";

}
