package tw.cchi.medthimager.di.module;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.db.AppDatabase;
import tw.cchi.medthimager.db.helper.PatientThermalDumpsHelper;
import tw.cchi.medthimager.di.ApplicationContext;

@Module
public class ApplicationModule {

    private final MvpApplication mvpApplication;

    public ApplicationModule(MvpApplication mvpApplication) {
        this.mvpApplication = mvpApplication;
    }

    @Provides
    @ApplicationContext
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

    @Provides
    @Singleton
    AppDatabase provideAppDatabase() {
        return AppDatabase.getInstance(mvpApplication);
    }

}
