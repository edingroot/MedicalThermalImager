package tw.cchi.medthimager.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import java.util.ArrayList;

import tw.cchi.medthimager.R;

public final class CommonUtils {

    private CommonUtils() {
        // This utility class is not publicly instantiable
    }

    public static ProgressDialog showLoadingDialog(final Activity activity) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.show();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        progressDialog.setContentView(R.layout.progress_dialog);
        progressDialog.setIndeterminate(true);

        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnCancelListener(dialog -> activity.finish());

        return progressDialog;
    }

    public static <T> ArrayList<T> cloneArrayList(ArrayList<T> list) {
        ArrayList<T> clonedList = new ArrayList<>(list.size());
        clonedList.addAll(list);
        return clonedList;
    }

}
