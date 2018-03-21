package tw.cchi.medthimager.di.module;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;

import dagger.Module;
import dagger.Provides;
import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.di.ActivityContext;
import tw.cchi.medthimager.ui.base.BaseActivity;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerMvpPresenter;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerMvpView;
import tw.cchi.medthimager.ui.dumpviewer.DumpViewerPresenter;

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
