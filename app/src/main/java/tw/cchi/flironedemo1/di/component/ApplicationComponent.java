package tw.cchi.flironedemo1.di.component;

import android.app.Application;
import android.content.Context;

import javax.inject.Singleton;

import dagger.Component;
import tw.cchi.flironedemo1.MvpApplication;
import tw.cchi.flironedemo1.di.module.ApplicationModule;

@Singleton
@Component(modules = ApplicationModule.class)
public interface ApplicationComponent {

    void inject(MvpApplication app);

    // ----- Methods below are used by Dagger implementation of ActivityComponent ----- //
    Context context();

    Application application();

    MvpApplication mvpApp();

}
