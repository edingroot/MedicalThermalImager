package tw.cchi.medthimager.utils;

import android.content.Context;

import java.io.File;

import io.reactivex.Observable;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.thermalproc.RawThermalDump;

public class ThermalDumpUtils {

    public static Observable<String> deleteThermalDumpBundle(Context context, RawThermalDump rawThermalDump) {
        String dumpPath = rawThermalDump.getFilepath();
        String flirImagePath = rawThermalDump.getFlirImagePath();
        String coloredImagePath = rawThermalDump.getColoredImagePath();
        String visibleImagePath = rawThermalDump.getVisibleImagePath();

        return Observable.create(emitter -> {
            File file;

            // Delete flir image file
            file = new File(flirImagePath);
            if (file.exists() && !file.delete()) {
                emitter.onError(new Error(context.getString(R.string.error_delete_flir)));
                emitter.onComplete();
                return;
            } else {
                emitter.onNext(flirImagePath);
            }

            // Delete exported color image file (if exists)
            file = new File(coloredImagePath);
            if (file.exists() && !file.delete()) {
                emitter.onError(new Error(context.getString(R.string.error_delete_color)));
                emitter.onComplete();
                return;
            } else {
                emitter.onNext(coloredImagePath);
            }

            // Delete exported visible light image file (if exists)
            file = new File(visibleImagePath);
            if (file.exists() && !file.delete()) {
                emitter.onError(new Error(context.getString(R.string.error_delete_visible)));
                emitter.onComplete();
                return;
            } else {
                emitter.onNext(visibleImagePath);
            }

            // Delete thermal dump file (.dat)
            if (!new File(dumpPath).delete()) {
                emitter.onError(new Error(context.getString(R.string.error_delete_dump)));
                emitter.onComplete();
                return;
            } else {
                emitter.onNext(dumpPath);
            }

            // TODO: remove related records from database as well?

            emitter.onComplete();
        });
    }

}
