package tw.cchi.medthimager.util;

import java.io.File;
import java.util.ArrayList;

public final class FileUtils {

    private FileUtils() {
        // This utility class is not publicly instantiable
    }

    public static ArrayList<File> getAllFiles(String rootDir) {
        ArrayList<File> files = new ArrayList<>();
        getFiles(new File(rootDir).listFiles(), files);
        return files;
    }

    /**
     * @param filePath file path or name
     * @return extension without the leading dot | empty string for no extension
     */
    public static String getExtension(String filePath) {
        int index = filePath.lastIndexOf('.');
        if (index > 0)
            return filePath.substring(index + 1);
        else
            return "";
    }

    public static void waitUntilExists(String filePath) {
        while (!new File(filePath).exists()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {}
        }
    }


    public static String removeExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index > 0)
            return filename.substring(0, index);
        else
            return filename;
    }

    private static void getFiles(File[] files, ArrayList<File> result) {
        for (File file : files) {
            if (file.isDirectory())
                getFiles(file.listFiles(), result);
            else
                result.add(file);
        }
    }
}
