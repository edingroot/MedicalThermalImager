package tw.cchi.medthimager.ui.settings;

import android.support.v7.app.AppCompatActivity;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class SettingsPresenter<V extends SettingsMvpView> extends BasePresenter<V> implements SettingsMvpPresenter<V> {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    @Inject AppCompatActivity activity;

    @Inject
    public SettingsPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

}
