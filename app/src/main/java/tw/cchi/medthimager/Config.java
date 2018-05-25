package tw.cchi.medthimager;

import okhttp3.logging.HttpLoggingInterceptor.Level;
import tw.cchi.medthimager.ui.auth.LoginActivity;

public final class Config {
    public static final String TAGPRE = "medthimager_";

    public static final boolean ENABLE_ANALYTICS_COLLECTION = true;

    public static final String API_BASE_URL = "https://medthimager.cchi.tw/api/";
//    public static final String API_BASE_URL = "http://192.168.1.102/thermal-imager-console/public/api/";
    public static final String PREFILLED_LOGIN_EMAIL = "test1@example.com";
    public static final String PREFILLED_LOGIN_PASSWORD = "example@test1";
    public static final Level API_LOGGING_LEVEL = Level.BASIC;

    public static final Class[] GUEST_ACTIVITIES = {
            LoginActivity.class
    };

    public static final int PREVIEW_MASK_ALPHA = 55; // 0 ~ 255
    public static final int VISIBLE_ALIGN_ALPHA = 55; // 0 ~ 255

    public static final int DUMP_ALL_VISIBLE_INTERVAL = 500; // ms
    public static final int CONTI_SHOOT_START_DELAY = 5; // sec

    public static final int INIT_DEFAULT_VISIBLE_OFFSET_X = 133;
    public static final int INIT_DEFAULT_VISIBLE_OFFSET_Y = -8;

}
