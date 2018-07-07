package tw.cchi.medthimager.ui.settings;

import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.helper.ThImagesHelper;
import tw.cchi.medthimager.helper.session.Session;
import tw.cchi.medthimager.service.sync.task.SyncPatientsTask;
import tw.cchi.medthimager.service.sync.task.UpSyncThImagesTask;
import tw.cchi.medthimager.ui.base.BasePresenter;
import tw.cchi.medthimager.util.DateTimeUtils;

public class SettingsPresenter<V extends SettingsMvpView> extends BasePresenter<V> implements SettingsMvpPresenter<V> {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;
    @Inject ApiHelper apiHelper;
    @Inject ThImagesHelper thImagesHelper;

    private Session currentSession;

    @Inject
    public SettingsPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
        loadSettings();
    }

    private void loadSettings() {
        currentSession = application.getSession();

        getMvpView().setAuthState(currentSession.isActive(), currentSession.getUser());
        getMvpView().setSwClearSpotsOnDisconn(dataManager.pref.getClearSpotsOnDisconnectEnabled());
        getMvpView().setSwAutoApplyVisibleOffset(dataManager.pref.getAutoApplyVisibleOffsetEnabled());

        application.connectSyncService().subscribe(syncService -> {
            getMvpView().setSyncPatientsStatus(syncService.isTaskRunning(SyncPatientsTask.class), getLastSyncPatients());
            getMvpView().setSyncThImagesStatus(syncService.isTaskRunning(UpSyncThImagesTask.class), getLastSyncThImages());
        });

        // Refresh data asynchronously
        apiHelper.refreshUserProfile().observeOn(AndroidSchedulers.mainThread()).subscribe(success -> {
            if (isViewAttached()) {
                if (success) {
                    getMvpView().setAuthState(currentSession.isActive(), currentSession.getUser());
                }
            }
        });
    }

    @Override
    public void login() {
        // Unreachable state currently
    }

    @Override
    public void logout() {
        if (!currentSession.isActive()) {
            return;
        }

        currentSession.getApiClient().logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(TAG, "onLogout: got status code " + response.code());

                // Logout anyway
                // activity will be notified to finish by invalidateSession()
                currentSession.invalidate();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                call.cancel();
                t.printStackTrace();

                // Logout anyway
                currentSession.invalidate();
            }
        });
    }

    @Override
    public void setClearSpotsOnDisconnect(boolean enable) {
        dataManager.pref.setClearSpotsOnDisconnect(enable);
    }

    @Override
    public void setAutoSetVisibleOffset(boolean enable) {
        dataManager.pref.setAutoApplyVisibleOffsetEnabled(enable);
    }

    @Override
    public void syncPatients() {
        getMvpView().setSyncPatientsStatus(true, "");

        application.connectSyncService().subscribe(syncService -> {
            if (application.checkNetworkAuthed(true) && !syncService.isTaskRunning(SyncPatientsTask.class)) {
                syncService.scheduleNewTask(new SyncPatientsTask());
            }
        });
    }

    @Override
    public void syncThImages() {
        getMvpView().setSyncThImagesStatus(true, "");

        Observable.create(emitter -> {
            thImagesHelper.deleteInvalidCaptureRecords().blockingSubscribe();
            thImagesHelper.updateRecordsFromDumpFiles().blockingSubscribe();

            application.connectSyncService().subscribe(syncService -> {
                if (application.checkNetworkAuthed(true) && !syncService.isTaskRunning(UpSyncThImagesTask.class)) {
                    syncService.scheduleNewTask(new UpSyncThImagesTask());
                }
            });
        }).subscribeOn(Schedulers.io()).subscribe();
    }

    @Override
    public void onSyncPatientsDone() {
        if (isViewAttached()) {
            getMvpView().setSyncPatientsStatus(false, getLastSyncPatients());
        }
    }

    @Override
    public void onSyncThImagesDone() {
        if (isViewAttached()) {
            getMvpView().setSyncThImagesStatus(false, getLastSyncThImages());
        }
    }

    private String getLastSyncPatients() {
        return DateTimeUtils.timestampToString(dataManager.pref.getLastSyncPatients().getTime());
    }

    private String getLastSyncThImages() {
        return DateTimeUtils.timestampToString(dataManager.pref.getLastSyncThImages().getTime());
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
