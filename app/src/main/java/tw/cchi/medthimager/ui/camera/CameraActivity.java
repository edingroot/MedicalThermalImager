package tw.cchi.medthimager.ui.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.FlirUsbDevice;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.BindView;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.OnTouch;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.component.ThermalSpotView;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.camera.contishoot.ContiShootDialog;
import tw.cchi.medthimager.ui.camera.selectpatient.SelectPatientDialog;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerActivity;

@RuntimePermissions
public class CameraActivity extends BaseActivity implements CameraMvpView {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    public static final int ACTION_PICK_FROM_GALLERY = 100;

    @Inject CameraMvpPresenter<CameraMvpView> presenter;

    private SelectPatientDialog selectPatientDialog;
    private ScaleGestureDetector mScaleDetector;
    private ColorFilter originalChargingIndicatorColor;
    private int thermalViewOnTouchMoves = 0;

    @BindView(R.id.thermalImageView) ImageView thermalImageView;
    @BindView(R.id.pleaseConnect) TextView pleaseConnect;
    @BindView(R.id.batteryLevelTextView) TextView batteryLevelTextView;
    @BindView(R.id.batteryChargeIndicator) ImageView batteryChargeIndicator;
    @BindView(R.id.imgBtnCapture) ImageButton imgBtnCapture;

    @BindView(R.id.thermalSpotView) ThermalSpotView thermalSpotView;
    @BindView(R.id.txtTuningState) TextView txtTuningState;
    @BindView(R.id.tuningProgressBar) ProgressBar tuningProgressBar;
    @BindView(R.id.tuningTextView) TextView tuningTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);

        mScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                Log.d(TAG, "zoom ongoing, scale: " + detector.getScaleFactor());
//                frameProcessor.setMSXDistance(detector.getScaleFactor());
                return false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Device.getSupportedDeviceClasses(this).contains(FlirUsbDevice.class)) {
            pleaseConnect.setVisibility(View.VISIBLE);
        }

        CameraActivityPermissionsDispatcher.enableDeviceDiscoveryWithPermissionCheck(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (selectPatientDialog != null)
            selectPatientDialog.dismiss();

        if (presenter.isContiShootingMode()) {
            presenter.finishContiShooting(true);
        }

        presenter.frameStreamControl(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        presenter.frameStreamControl(true);
    }

    @Override
    public void onStop() {
        presenter.onActivityStop();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        CameraActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ACTION_PICK_FROM_GALLERY:
                if (resultCode == Activity.RESULT_OK) {
                    // Pick image from gallery
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    // Get the cursor
                    Cursor cursor = this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                    // Move to first row
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String filepath = cursor.getString(columnIndex);
                    cursor.close();

                    presenter.setOpacityMask(filepath);
                    presenter.checkReconnectSimDevice();
                }
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (checkContiShootBlocking())
                    return true;
                presenter.triggerImageCapture();
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }


    @OnClick(R.id.imgBtnSelectPatient)
    public void onSelectPatientClick(View v) {
        if (checkContiShootBlocking())
            return;

        if (selectPatientDialog == null) {
            selectPatientDialog = new SelectPatientDialog(this, presenter::setCurrentPatient);
        }
        selectPatientDialog.setSelectedPatientUUID(presenter.getCurrentPatient());
        selectPatientDialog.show();
    }

    @OnClick(R.id.btnTools)
    public void onToolsClick(View v) {
        if (checkContiShootBlocking())
            return;

        PopupMenu popup = new PopupMenu(this, v);
        popup.inflate(R.menu.preview_tools_menu);

        String pickMaskTitle = getString(presenter.isOpacityMaskAttached() ? R.string.unset_mask : R.string.pick_mask);
        popup.getMenu().findItem(R.id.action_pick_mask).setTitle(pickMaskTitle);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_dump_viewer:
                    startActivity(new Intent(CameraActivity.this, DumpViewerActivity.class));
                    return true;

                case R.id.action_pick_mask:
                    if (!presenter.isOpacityMaskAttached()) {
                        Intent galleryIntent = new Intent(
                                Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(galleryIntent, ACTION_PICK_FROM_GALLERY);
                    } else {
                        presenter.setOpacityMask(null);
                    }
                    return true;

                case R.id.export_csv:
                    presenter.exportAllRecordsToCSV();
                    return true;

                case R.id.action_switch_rotate:
                    // TODO: also rotate the capturing image?
                    if (thermalImageView.getRotation() == 0f) {
                        thermalImageView.setRotation(180f);
                    } else {
                        thermalImageView.setRotation(0f);
                    }
                    return true;

                case R.id.action_toggle_sim:
                    CameraActivityPermissionsDispatcher.checkAndConnectSimDeviceWithPermissionCheck(this);
                    return true;

                default:
                    return false;
            }
        });
        popup.show();
    }

    @OnClick(R.id.imgBtnCapture)
    public void onCaptureImageClick(View v) {
        if (!presenter.isDeviceAttached())
            return;

        if (presenter.isContiShootingMode()) {
            showAlertDialog(
                getString(R.string.confirm),
                getString(R.string.conti_shoot_confirm_abort),
                (dialog, which) -> {
                    presenter.finishContiShooting(false);
                    dialog.dismiss();
                },
                (dialog, which) -> dialog.dismiss()
            );
        } else {
            presenter.triggerImageCapture();
        }
    }

    @OnClick(R.id.btnTune)
    public void onTuneClick(View v) {
        presenter.performTune();
    }

    @OnClick(R.id.btnContiShot)
    public void onContiShotClick(View v) {
        if (!presenter.isDeviceAttached())
            return;

        if (checkContiShootBlocking())
            return;

        ContiShootDialog.newInstance().show(getSupportFragmentManager(), (dialog, params) -> {
            presenter.startContiShooting(params);
        });
    }

    @OnTouch(R.id.thermalImageView)
    public boolean onThermalImageViewTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                thermalViewOnTouchMoves = 0;
                break;

            case MotionEvent.ACTION_MOVE:
                thermalViewOnTouchMoves++;
                break;
        }

        if (thermalImageView.getMeasuredHeight() > 0) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (y >= 0 && y < thermalImageView.getMeasuredHeight()) {
                presenter.updateThermalSpotTemp(x, y);
                thermalSpotView.setCenterPosition(x, y + thermalImageView.getTop());
            }
            thermalViewOnTouchMoves++;
        }

        mScaleDetector.onTouchEvent(event);

        return false;
    }

    @OnLongClick(R.id.thermalImageView)
    public boolean onThermalImageViewLongClick(View v) {
        // if (thermalViewOnTouchMoves >= 10)
            // return false;

        if (checkContiShootBlocking())
            return true;

        presenter.performTune();
        return true;
    }


    @Override
    public void updateForDeviceConnected() {
        pleaseConnect.setVisibility(View.GONE);
        thermalSpotView.setVisibility(View.VISIBLE);
    }

    @Override
    public void updateForDeviceDisconnected() {
        pleaseConnect.setVisibility(View.VISIBLE);
        thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565));
        thermalImageView.clearColorFilter();
        thermalImageView.setImageResource(android.R.color.transparent);
        batteryLevelTextView.setText("--");
        batteryChargeIndicator.setVisibility(View.GONE);
        thermalSpotView.setVisibility(View.GONE);
        findViewById(R.id.tuningProgressBar).setVisibility(View.GONE);
        findViewById(R.id.tuningTextView).setVisibility(View.GONE);
    }

    @Override
    public void updateForDeviceChargingState(Device.BatteryChargingState batteryChargingState) {
        if (originalChargingIndicatorColor == null) {  // TODO: which color?
            originalChargingIndicatorColor = batteryChargeIndicator.getColorFilter();
        }
        switch (batteryChargingState) {
            case FAULT:
            case FAULT_HEAT:
                batteryChargeIndicator.setColorFilter(Color.RED);
                batteryChargeIndicator.setVisibility(View.VISIBLE);
                break;
            case FAULT_BAD_CHARGER:
                batteryChargeIndicator.setColorFilter(Color.DKGRAY);
                batteryChargeIndicator.setVisibility(View.VISIBLE);
            case MANAGED_CHARGING:
                batteryChargeIndicator.setColorFilter(originalChargingIndicatorColor);
                batteryChargeIndicator.setVisibility(View.VISIBLE);
                break;
            case NO_CHARGING:
            default:
                batteryChargeIndicator.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void updateForDeviceTuningState(Device.TuningState tuningState) {
        if (tuningState == Device.TuningState.InProgress) {
            txtTuningState.setText(tuningState.name());
            thermalImageView.setColorFilter(Color.DKGRAY, PorterDuff.Mode.DARKEN);
            tuningProgressBar.setVisibility(View.VISIBLE);
            tuningTextView.setVisibility(View.VISIBLE);
        } else {
            txtTuningState.setText(tuningState.name());
            thermalImageView.clearColorFilter();
            tuningProgressBar.setVisibility(View.GONE);
            tuningTextView.setVisibility(View.GONE);
        }
    }

    @Override
    public void updateDeviceBatteryPercentage(byte percentage) {
        batteryLevelTextView.setText(String.valueOf((int) percentage) + "%");
    }

    @Override
    public void setThermalImageViewBitmap(final Bitmap frame) {
        thermalImageView.setImageBitmap(frame);
    }

    @Override
    public void setThermalSpotTemp(double temperature) {
        thermalSpotView.setTemperature(temperature);
    }

    @Override
    public void animateFlash() {
        thermalImageView.animate().setDuration(50).scaleY(0).withEndAction((
            () -> thermalImageView.animate().setDuration(50).scaleY(1))
        );
    }

    @Override
    public void setCameraMode() {
        imgBtnCapture.setImageResource(android.R.drawable.ic_menu_camera);
    }

    @Override
    public void setContinuousShootMode() {
        imgBtnCapture.setImageResource(R.drawable.ic_camera_automation);
    }

    @Override
    public int getThermalImageViewWidth() {
        return thermalImageView.getMeasuredWidth();
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void enableDeviceDiscovery() {
        if (!presenter.startDeviceDiscovery()) {
            // On some platforms, we need the user to select the app to give us permission to the USB device.
            showToast(R.string.insert_flirone, getString(R.string.app_name));
            // There is likely a cleaner way to recover, but for now, exit the activity and
            // wait for user to follow the instructions;
            finish();
        }
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void checkAndConnectSimDevice() {
        presenter.checkAndConnectSimDevice();
    }

    private boolean checkContiShootBlocking() {
        if (presenter.isContiShootingMode()) {
            showSnackBar(R.string.conti_shoot_blocking);
            return true;
        } else {
            return false;
        }
    }
}

// Notes:
// Device OnFrameProcessedListener methods
// Called during device discovery, when a device is connected
// During this callback, you should save a reference to device
// You should also set the power update delegate for the device if you have one
// Go ahead and start frame stream as soon as connected, in this use case
// Finally we create a frame processor for rendering frames
