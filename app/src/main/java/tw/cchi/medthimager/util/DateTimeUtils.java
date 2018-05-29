package tw.cchi.medthimager.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeUtils {
    private DateTimeUtils() {
        // This utility class is not publicly instantiable
    }

    public static String timestampToString(long timestamp, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());
        return dateFormat.format(new Date(timestamp));
    }

    public static String timestampToString(long timestamp) {
        return timestampToString(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    public static String timestampToDateString(long timestamp) {
        return timestampToString(timestamp, "yyyy/MM/dd");
    }

    public static Date stringToDate(String string) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            return dateFormat.parse(string);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse timestamp string from API
     *
     * @param timeString JavaScript ISOTimestamp string (UTC), e.g."2011-11-11T00:00:01.000Z"
     * @return Date
     */
    public static Date stringISOToDate(String timeString) {
        if (timeString.equals("null"))
            return null;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-d'T'hh:mm:ss.SSS'Z'", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat.parse(timeString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
