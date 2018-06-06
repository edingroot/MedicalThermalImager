package tw.cchi.medthimager.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

public final class CommonUtils {
    private static Gson gson;

    private CommonUtils() {
        // This utility class is not publicly instantiable
    }

    public static Gson getGsonInstance() {
        if (gson == null) {
            synchronized (CommonUtils.class) {
                if (gson == null) {
                    gson = new GsonBuilder()
                            .setDateFormat("yyyy-MM-dd HH:mm:ss")
                            .create();
                }
            }
        }

        return gson;
    }

    public static <T> ArrayList<T> cloneArrayList(ArrayList<T> list) {
        ArrayList<T> clonedList = new ArrayList<>(list.size());
        clonedList.addAll(list);
        return clonedList;
    }

    public static void removeListDuplication(List<String> list, List<String> compareTarget) {
        for (String str : list) {
            if (compareTarget.contains(str)) {
                list.remove(str);
            }
        }
    }

    /**
     * Effective range: min <= val < max
     */
    public static int trimByRange(int val, int min, int max) {
        if (val < min)
            return min;
        else if (val >= max)
            return max - 1;
        else
            return val;
    }

    public static String padLeft(String input, char padChar, int length) {
        StringBuilder stringBuilder = new StringBuilder(input);
        for (int i = input.length(); i < length; i++)
            stringBuilder.insert(0, padChar);
        return stringBuilder.toString();
    }

    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void delayExecute(Runnable runnable, long delayMillis) {
        Observable.create(emitter -> {
            try {
                Thread.sleep(delayMillis);
                runnable.run();
            } catch (InterruptedException ignored) {
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.io()).subscribe();
    }
}
