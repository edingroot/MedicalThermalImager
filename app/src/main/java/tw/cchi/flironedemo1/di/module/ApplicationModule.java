package tw.cchi.flironedemo1.di.module;

import android.app.Application;
import android.content.Context;

import dagger.Module;
import dagger.Provides;
import tw.cchi.flironedemo1.MvpApplication;

@Module
public class ApplicationModule {

    private final MvpApplication mvpApplication;

    public ApplicationModule(MvpApplication mvpApplication) {
        this.mvpApplication = mvpApplication;
    }

    @Provides
    Context provideContext() {
        return mvpApplication;
    }

    @Provides
    Application provideApplication() {
        return mvpApplication;
    }

    @Provides
    MvpApplication provideMvpApplication() {
        return mvpApplication;
    }

}
