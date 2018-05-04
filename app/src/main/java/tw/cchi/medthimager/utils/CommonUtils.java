package tw.cchi.medthimager.utils;

import java.util.ArrayList;
import java.util.List;

public final class CommonUtils {

    private CommonUtils() {
        // This utility class is not publicly instantiable
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
     *
     * @param val
     * @param min
     * @param max
     * @return
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

}
