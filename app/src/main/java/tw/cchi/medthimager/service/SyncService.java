package tw.cchi.medthimager.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import javax.inject.Inject;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.di.component.DaggerServiceComponent;
import tw.cchi.medthimager.di.component.ServiceComponent;
import tw.cchi.medthimager.util.NetworkUtils;

public class SyncService extends Service {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    private NetworkStateBroadcastReceiver networkStateReceiver;

    @Inject MvpApplication application;

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceComponent component = DaggerServiceComponent.builder()
                .applicationComponent(((MvpApplication) getApplication()).getComponent())
                .build();
        component.inject(this);

        networkStateReceiver = new NetworkStateBroadcastReceiver();
        registerReceiver(networkStateReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "SyncService started");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SyncService stopped");
        unregisterReceiver(networkStateReceiver);
        super.onDestroy();
    }


    public static Intent getStartIntent(Context context) {
        return new Intent(context, SyncService.class);
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, SyncService.class);
        context.startService(starter);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, SyncService.class));
    }


    private class NetworkStateBroadcastReceiver extends BroadcastReceiver {
        public NetworkStateBroadcastReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (NetworkUtils.isNetworkConnected(context)) {
                // TODO
            }
        }
    }

    public class ServiceBinder extends Binder {
        public SyncService getService() {
            return SyncService.this;
        }
    }
}
