package tw.cchi.medthimager.di.module;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;

import dagger.Module;
import dagger.Provides;
import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.di.ActivityContext;
import tw.cchi.medthimager.ui.auth.LoginMvpPresenter;
import tw.cchi.medthimager.ui.auth.LoginMvpView;
import tw.cchi.medthimager.ui.auth.LoginPresenter;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.camera.CameraMvpPresenter;
import tw.cchi.medthimager.ui.camera.CameraMvpView;
import tw.cchi.medthimager.ui.camera.CameraPresenter;
import tw.cchi.medthimager.ui.camera.contishoot.ContiShootMvpPresenter;
import tw.cchi.medthimager.ui.camera.contishoot.ContiShootMvpView;
import tw.cchi.medthimager.ui.camera.contishoot.ContiShootPresenter;
import tw.cchi.medthimager.ui.camera.patientmgmt.PatientMgmtMvpPresenter;
import tw.cchi.medthimager.ui.camera.patientmgmt.PatientMgmtMvpView;
import tw.cchi.medthimager.ui.camera.patientmgmt.PatientMgmtPresenter;
import tw.cchi.medthimager.ui.camera.tagselection.TagSelectionMvpPresenter;
import tw.cchi.medthimager.ui.camera.tagselection.TagSelectionMvpView;
import tw.cchi.medthimager.ui.camera.tagselection.TagSelectionPresenter;
import tw.cchi.medthimager.ui.dialog.patientconflict.ConflictPatientMvpPresenter;
import tw.cchi.medthimager.ui.dialog.patientconflict.ConflictPatientMvpView;
import tw.cchi.medthimager.ui.dialog.patientconflict.ConflictPatientPresenter;
import tw.cchi.medthimager.ui.dialog.updateremider.UpdateReminderMvpPresenter;
import tw.cchi.medthimager.ui.dialog.updateremider.UpdateReminderMvpView;
import tw.cchi.medthimager.ui.dialog.updateremider.UpdateReminderPresenter;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerMvpPresenter;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerMvpView;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerPresenter;
import tw.cchi.medthimager.ui.settings.SettingsMvpPresenter;
import tw.cchi.medthimager.ui.settings.SettingsMvpView;
import tw.cchi.medthimager.ui.settings.SettingsPresenter;

@Module
public class ActivityModule {

    private BaseActivity mActivity;

    public ActivityModule(BaseActivity activity) {
        this.mActivity = activity;
    }

    @Provides
    Activity provideActivity() {
        return mActivity;
    }

    @Provides
    AppCompatActivity provideAppCompatActivity() {
        return mActivity;
    }

    @Provides
    @ActivityContext
    Context provideContext() {
        return mActivity;
    }

    @Provides
    CompositeDisposable provideCompositeDisposable() {
        return new CompositeDisposable();
    }

    @Provides
    LoginMvpPresenter<LoginMvpView> provideLoginMvpPresenter(LoginPresenter<LoginMvpView> presenter) {
        return presenter;
    }

    @Provides
    CameraMvpPresenter<CameraMvpView> provideCameraMvpPresenter(CameraPresenter<CameraMvpView> presenter) {
        return presenter;
    }

    @Provides
    PatientMgmtMvpPresenter<PatientMgmtMvpView> providePatientMgmtMvpPresenter(PatientMgmtPresenter<PatientMgmtMvpView> presenter) {
        return presenter;
    }

    @Provides
    ContiShootMvpPresenter<ContiShootMvpView> provideContiShotMvpPresenter(ContiShootPresenter<ContiShootMvpView> presenter) {
        return presenter;
    }

    @Provides
    SettingsMvpPresenter<SettingsMvpView> provideSettingsMvpPresenter(SettingsPresenter<SettingsMvpView> presenter) {
        return presenter;
    }

    @Provides
    DumpViewerMvpPresenter<DumpViewerMvpView> provideDumpViewerMvpPresenter(DumpViewerPresenter<DumpViewerMvpView> presenter) {
        return presenter;
    }


    // --------------- Dialogs --------------- //

    @Provides
    ConflictPatientMvpPresenter<ConflictPatientMvpView> provideConflictPatientMvpPresenter(ConflictPatientPresenter<ConflictPatientMvpView> presenter) {
        return presenter;
    }

    @Provides
    TagSelectionMvpPresenter<TagSelectionMvpView> provideTagSelectionMvpPresenter(TagSelectionPresenter<TagSelectionMvpView> presenter) {
        return presenter;
    }

    @Provides
    UpdateReminderMvpPresenter<UpdateReminderMvpView> provideUpdateReminderMvpPresenter(UpdateReminderPresenter<UpdateReminderMvpView> presenter) {
        return presenter;
    }

}
