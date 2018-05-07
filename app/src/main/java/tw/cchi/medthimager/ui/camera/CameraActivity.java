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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.FlirUsbDevice;
import com.flir.flironesdk.RenderedImage;
import com.jakewharton.rxbinding2.view.RxView;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;
import io.reactivex.Observable;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.component.SpotsControlView;
import tw.cchi.medthimager.component.ThermalSpotView;
import tw.cchi.medthimager.helper.ThermalSpotsHelper;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.camera.contishoot.ContiShootDialog;
import tw.cchi.medthimager.ui.camera.selectpatient.SelectPatientDialog;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerActivity;
import tw.cchi.medthimager.ui.settings.SettingsActivity;
import tw.cchi.medthimager.utils.CommonUtils;

@RuntimePermissions
public class CameraActivity extends BaseActivity implements
    CameraMvpView, PopupMenu.OnMenuItemClickListener, SpotsControlView.OnControlSpotsListener {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    public static final int ACTION_PICK_FROM_GALLERY = 100;

    @Inject CameraMvpPresenter<CameraMvpView> presenter;

    private SelectPatientDialog selectPatientDialog;
    private ScaleGestureDetector mScaleDetector;
    private ColorFilter originalChargingIndicatorColor;
    // private int thermalViewOnTouchMoves = 0;

    @BindView(R.id.topView) RelativeLayout topView;
    @BindView(R.id.batteryLevelTextView) TextView batteryLevelTextView;
    @BindView(R.id.batteryChargeIndicator) ImageView batteryChargeIndicator;

    @BindView(R.id.txtPatientName) TextView txtPatientName;
    @BindView(R.id.txtShootInfo) TextView txtShootInfo;
    @BindView(R.id.txtTuningState) TextView txtTuningState;

    @BindView(R.id.thermalImageView) ImageView thermalImageView;
    @BindView(R.id.pleaseConnect) TextView pleaseConnect;
    @BindView(R.id.thermalSpotView) ThermalSpotView thermalSpotView; // TODO
    @BindView(R.id.tuningProgressBar) ProgressBar tuningProgressBar;
    @BindView(R.id.tuningTextView) TextView tuningTextView;

    @BindView(R.id.spotsControl) SpotsControlView spotsControl;
    @BindView(R.id.imgBtnCapture) ImageButton imgBtnCapture;
    @BindView(R.id.txtShootCountdown) TextView txtShootCountdown;

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

        spotsControl.setOnControlSpotsListener(this);
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
        presenter.loadSettings();
        presenter.frameStreamControl(true);
    }

    @Override
    public void onStop() {
        presenter.unregisterFlir();
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
        selectPatientDialog.setSelectedPatientUUID(presenter.getCurrentPatientUuid());
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

        popup.setOnMenuItemClickListener(this);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
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

            case R.id.action_open_settings:
                startActivity(new Intent(CameraActivity.this, SettingsActivity.class));
                return true;
        }

        return false;
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
                // thermalViewOnTouchMoves = 0;
                break;

            case MotionEvent.ACTION_MOVE:
                break;
        }

        if (thermalImageView.getMeasuredHeight() > 0) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (y >= 0 && y < thermalImageView.getMeasuredHeight()) {
                // TODO
                // presenter.updateThermalSpotTemp(x, y);
            }
            // thermalViewOnTouchMoves++;
        }

        mScaleDetector.onTouchEvent(event);

        return true;
    }

    /* @OnLongClick(R.id.thermalImageView)
    public boolean onThermalImageViewLongClick(View v) {
        // if (thermalViewOnTouchMoves >= 10)
            // return false;

        presenter.performTune();
        return true;
    } */


    // -------------------------- SpotsControlView.OnControlSpotsListener ------------------------ //
    @Override
    public void onAddSpot() {
        presenter.addThermalSpot();
    }

    @Override
    public void onRemoveSpot() {
        presenter.removeLastThermalSpot();
    }

    @Override
    public void onClearSpots() {
        presenter.clearThermalSpots();
    }

    @Override
    public void onHideSpots() {
        // never called due to function disabled
    }

    @Override
    public void onShowSpots() {
        // never called due to function disabled
    }
    // -------------------------- /SpotsControlView.OnControlSpotsListener ------------------------ //


    @Override
    public Observable<Object> getThermalImageViewGlobalLayouts() {
        return RxView.globalLayouts(thermalImageView);
    }

    /**
     * This should be called after thermalImageView measured (global layouted).
     */
    @Override
    public ThermalSpotsHelper createThermalSpotsHelper(RenderedImage renderedImage) {
        Log.i(TAG, String.format("[createThermalSpotsHelper] thermalImageView.getMeasuredHeight()=%d, thermalImageView.getTop()=%d, layoutThermalViews.getTop()=%d\n",
            thermalImageView.getMeasuredHeight(), thermalImageView.getTop(), topView.getTop()
        ));

        final ThermalSpotsHelper thermalSpotsHelper = new ThermalSpotsHelper(this, topView, renderedImage);
        thermalSpotsHelper.setImageViewMetrics(
            thermalImageView.getMeasuredWidth(),
            thermalImageView.getMeasuredHeight(),
            thermalImageView.getTop() + topView.getTop()
        );
        return thermalSpotsHelper;
    }

    @Override
    public void setPatientStatusText(String patientName) {
        txtPatientName.setText(getString(R.string.patient_, patientName));
    }

    @Override
    public void setDeviceConnected() {
        pleaseConnect.setVisibility(View.GONE);
    }

    @Override
    public void setDeviceDisconnected() {
        pleaseConnect.setVisibility(View.VISIBLE);
        setSpotsControlEnabled(false);

        thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565));
        thermalImageView.clearColorFilter();
        thermalImageView.setImageResource(android.R.color.transparent);
        batteryLevelTextView.setText("--");
        batteryChargeIndicator.setVisibility(View.GONE);
        tuningProgressBar.setVisibility(View.GONE);
        tuningTextView.setVisibility(View.GONE);
    }

    @Override
    public void setDeviceChargingState(Device.BatteryChargingState batteryChargingState) {
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
    public void setDeviceTuningState(Device.TuningState tuningState) {
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
    public void setDeviceBatteryPercentage(byte percentage) {
        batteryLevelTextView.setText(String.valueOf((int) percentage) + "%");
    }

    @Override
    public void setSpotsControlEnabled(boolean enabled) {
        if (spotsControl.isEnabled() != enabled)
            spotsControl.setEnabled(enabled);
    }

    @Override
    public void updateThermalImageView(final Bitmap frame) {
        thermalImageView.setImageBitmap(frame);
    }

    @Override
    public int getThermalImageViewHeight() {
        return thermalImageView.getMeasuredHeight();
    }

    @Override
    public void animateFlash() {
        thermalImageView.animate().setDuration(50).scaleY(0).withEndAction((
            () -> thermalImageView.animate().setDuration(50).scaleY(1))
        );
    }

    @Override
    public void setSingleShootMode() {
        imgBtnCapture.setImageResource(android.R.drawable.ic_menu_camera);
        txtShootCountdown.setVisibility(View.INVISIBLE);

        txtShootInfo.setText(R.string.single_shoot);
        txtShootInfo.setTextColor(getResources().getColor(R.color.contentText));
    }

    @Override
    public void setContinuousShootMode(int capturedCount, int totalCaptures) {
        imgBtnCapture.setImageResource(R.drawable.ic_camera_automation);
        txtShootCountdown.setVisibility(View.VISIBLE);

        txtShootInfo.setText(getString(R.string.conti_shoot_counts, capturedCount, totalCaptures));
        txtShootInfo.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
    }

    @Override
    public void updateContinuousShootCountdown(int value) {
        txtShootCountdown.setText(CommonUtils.padLeft(String.valueOf(value), '0', 3));
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

    @OnPermissionDenied({Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void onWriteStoragePermissionDenied() {
        showToast(R.string.write_storage_required);
        finish();
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
