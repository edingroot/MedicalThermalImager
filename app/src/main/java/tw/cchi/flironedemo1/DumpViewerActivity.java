package tw.cchi.flironedemo1;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.thermalproc.RawThermalDump;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpParser;
import tw.cchi.flironedemo1.thermalproc.ThermalDumpProcessor;


public class DumpViewerActivity extends Activity {
    public static final int ACTION_PICK_DUMP_FILE = 100;

    private String thermalDumpPath;
    private RawThermalDump rawThermalDump;
    ThermalDumpProcessor thermalDumpProcessor;
    private Bitmap thermalBitmap;

    @BindView(R.id.imageView) ImageView thermalImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_viewer);
        ButterKnife.bind(this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ACTION_PICK_DUMP_FILE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        openRawThermalDump(uri.getPath());
                    } else {
                        Toast.makeText(DumpViewerActivity.this, "Invalid file path", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }

    }

    public void onImagePickClicked(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        Intent destIntent = Intent.createChooser(intent, "Choose a thermal dump file (*.dat)");
        startActivityForResult(destIntent, ACTION_PICK_DUMP_FILE);
    }


    private void showToastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DumpViewerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openRawThermalDump(final String filepath) {
        (new Runnable() {
            @Override
            public void run() {
                rawThermalDump = ThermalDumpParser.readRawThermalDump(filepath);
                if (rawThermalDump != null) {
                    thermalDumpPath = filepath;
                    thermalDumpProcessor = new ThermalDumpProcessor(rawThermalDump);
                    thermalBitmap = thermalDumpProcessor.getBitmap(1);
                    updateThermalImageView(thermalBitmap);
                } else {
                    showToastMessage("Failed reading thermal dump");
                    thermalDumpPath = null;
                    thermalDumpProcessor = null;
                    thermalBitmap = null;
                    updateThermalImageView(null);
                }
            }
        }).run();

    }

    private void updateThermalImageView(final Bitmap frame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
            }
        });

        // Perform a native deep copy to avoid object referencing
        // thermalBitmap = frame.copy(frame.getConfig(), frame.isMutable());
    }

}
