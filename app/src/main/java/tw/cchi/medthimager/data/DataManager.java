package tw.cchi.medthimager.data;

import javax.inject.Inject;

import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.fanalytics.FirebaseAnalyticsHelper;
import tw.cchi.medthimager.data.pref.PreferencesHelper;

public class DataManager {
    @Inject public AppDatabase db;
    @Inject public FirebaseAnalyticsHelper analytics;
    @Inject public PreferencesHelper pref;

    @Inject
    public DataManager(AppDatabase db, FirebaseAnalyticsHelper analytics, PreferencesHelper pref) {
        this.db = db;
        this.analytics = analytics;
        this.pref = pref;
    }
}
