package tw.cchi.flironedemo1.di.component;

import dagger.Component;
import tw.cchi.flironedemo1.di.PerActivity;
import tw.cchi.flironedemo1.di.module.ActivityModule;
import tw.cchi.flironedemo1.ui.dumpviewer.DumpViewerActivity;
import tw.cchi.flironedemo1.ui.PreviewActivity;

@PerActivity
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent {

    void inject(PreviewActivity activity);

    void inject(DumpViewerActivity activity);

}
