package tw.cchi.medthimager.ui.camera.contishoot;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class ContiShootPresenter<V extends ContiShootMvpView> extends BasePresenter<V> implements ContiShootMvpPresenter<V> {


    @Inject
    public ContiShootPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
    }

    @Override
    public boolean parseResult(String interval, String times) {
        int intervalVal, timesVal;

        try {
            intervalVal = Integer.parseInt(interval);
            timesVal = Integer.parseInt(times);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return false;
        }

        getMvpView().getListener().onStart((ContiShootDialog) getMvpView(), intervalVal, timesVal);
        getMvpView().dismissDialog();

        return true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
