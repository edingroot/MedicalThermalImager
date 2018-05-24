package tw.cchi.medthimager.di.component;

import dagger.Component;
import tw.cchi.medthimager.di.PerActivity;
import tw.cchi.medthimager.di.module.ActivityModule;
import tw.cchi.medthimager.ui.auth.LoginActivity;
import tw.cchi.medthimager.ui.camera.CameraActivity;
import tw.cchi.medthimager.ui.camera.contishoot.ContiShootDialog;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerActivity;
import tw.cchi.medthimager.ui.settings.SettingsActivity;

@PerActivity
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent {

    void inject(LoginActivity activity);

    void inject(CameraActivity activity);

    void inject(ContiShootDialog dialog);

    void inject(SettingsActivity activity);

    void inject(DumpViewerActivity activity);

}
