package tw.cchi.medthimager.data;

import javax.inject.Inject;

import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.fanalytics.FirebaseAnalyticsHelper;
import tw.cchi.medthimager.data.pref.PreferencesHelper;

public class DataManager {
    public AppDatabase db;
    public FirebaseAnalyticsHelper analytics;
    public PreferencesHelper pref;

    @Inject
    public DataManager(AppDatabase db, FirebaseAnalyticsHelper analytics, PreferencesHelper pref) {
        this.db = db;
        this.analytics = analytics;
        this.pref = pref;
    }
}
