package tw.cchi.flironedemo1.di.module;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;

import dagger.Module;
import dagger.Provides;
import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.flironedemo1.di.ActivityContext;
import tw.cchi.flironedemo1.ui.base.BaseActivity;
import tw.cchi.flironedemo1.ui.dumpviewer.DumpViewerMvpPresenter;
import tw.cchi.flironedemo1.ui.dumpviewer.DumpViewerMvpView;
import tw.cchi.flironedemo1.ui.dumpviewer.DumpViewerPresenter;

@Module
public class ActivityModule {

    private BaseActivity mActivity;

    public ActivityModule(BaseActivity activity) {
        this.mActivity = activity;
    }

    @Provides
    AppCompatActivity provideActivity() {
        return mActivity;
    }

    @Provides
    @ActivityContext
    Context provideContext() {
        return mActivity;
    }

    @Provides
    CompositeDisposable provideCompositeDisposable() {
        return new CompositeDisposable();
    }

    @Provides
    DumpViewerMvpPresenter<DumpViewerMvpView> provideDumpViewerMvpPresenter(DumpViewerPresenter<DumpViewerMvpView> presenter) {
        return presenter;
    }

}
