package tw.cchi.medthimager.data;

import javax.inject.Inject;

import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.fanalytics.FirebaseAnalyticsHelper;
import tw.cchi.medthimager.data.frconfig.FirebaseRemoteConfigHelper;
import tw.cchi.medthimager.data.pref.PreferencesHelper;

public class DataManager {
    public AppDatabase db;
    public PreferencesHelper pref;
    public FirebaseAnalyticsHelper analytics;
    public FirebaseRemoteConfigHelper rconfig;

    @Inject
    public DataManager(AppDatabase db, PreferencesHelper pref,
                       FirebaseAnalyticsHelper analytics, FirebaseRemoteConfigHelper rconfig) {
        this.db = db;
        this.pref = pref;
        this.analytics = analytics;
        this.rconfig = rconfig;
    }
}
