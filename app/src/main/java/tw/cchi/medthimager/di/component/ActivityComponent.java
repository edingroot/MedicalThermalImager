package tw.cchi.medthimager.di.component;

import dagger.Component;
import tw.cchi.medthimager.di.PerActivity;
import tw.cchi.medthimager.di.module.ActivityModule;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerActivity;
import tw.cchi.medthimager.ui.PreviewActivity;

@PerActivity
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent {

    void inject(PreviewActivity activity);

    void inject(DumpViewerActivity activity);

}
