package tw.cchi.medthimager;

public final class Config {
    public static final String TAG = "appdebug";

    public static final int PREVIEW_MASK_ALPHA = 55; // 0 ~ 255
    public static final int VISIBLE_ALIGN_ALPHA = 55; // 0 ~ 255

    public static final String POSTFIX_FLIR_IMAGE = "_flir";
    public static final String POSTFIX_THERMAL_DUMP = "_raw";
    public static final String POSTFIX_COLORED_IMAGE = "_color";
    public static final String POSTFIX_VISIBLE_IMAGE = "_vis";

    public static final int DUMP_ALL_VISIBLE_INTERVAL = 500; // ms

}
