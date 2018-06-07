package tw.cchi.medthimager.di.module;

import android.app.Application;
import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.fanalytics.FirebaseAnalyticsHelper;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.data.pref.AppPreferencesHelper;
import tw.cchi.medthimager.data.pref.PreferencesHelper;
import tw.cchi.medthimager.di.ApplicationContext;
import tw.cchi.medthimager.di.PreferenceInfo;
import tw.cchi.medthimager.thermalproc.VisibleImageExtractor;

@Module
public class ApplicationModule {
    private final MvpApplication mvpApplication;
    private final FirebaseAnalytics firebaseAnalytics;

    public ApplicationModule(MvpApplication mvpApplication) {
        this.mvpApplication = mvpApplication;
        this.firebaseAnalytics = FirebaseAnalytics.getInstance(mvpApplication);
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
    @PreferenceInfo
    String providePreferenceName() {
        return Constants.PREF_NAME;
    }

    @Provides
    @Singleton
    PreferencesHelper providePreferencesHelper(AppPreferencesHelper appPreferencesHelper) {
        return appPreferencesHelper;
    }

    @Provides
    @Singleton
    ApiHelper provideApiHelper(ApiHelper apiHelper) {
        return apiHelper;
    }

    @Provides
    @Singleton
    AppDatabase provideAppDatabase() {
        return AppDatabase.getInstance(mvpApplication);
    }

    @Provides
    @Singleton
    FirebaseAnalyticsHelper provideFirebaseAnalyticsHelper() {
        return new FirebaseAnalyticsHelper(mvpApplication, firebaseAnalytics);
    }

    @Provides
    @Singleton
    VisibleImageExtractor provideVisibleImageExtractor() {
        return new VisibleImageExtractor(mvpApplication);
    }

}
