package tw.cchi.medthimager.di.component;

import android.app.Application;
import android.content.Context;

import javax.inject.Singleton;

import dagger.Component;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.di.module.ApplicationModule;
import tw.cchi.medthimager.data.fanalytics.FirebaseAnalyticsHelper;
import tw.cchi.medthimager.data.pref.PreferencesHelper;

@Singleton
@Component(modules = ApplicationModule.class)
public interface ApplicationComponent {

    void inject(MvpApplication app);

    // ---------- Methods below are used by Dagger implementation of ActivityComponent ---------- //

    @ApplicationContext
    Context context();

    Application application();

    MvpApplication mvpApp();

    PreferencesHelper preferenceHelper();

    AppDatabase appDatabase();

    FirebaseAnalyticsHelper firebaseAnalyticsHelper();

}
