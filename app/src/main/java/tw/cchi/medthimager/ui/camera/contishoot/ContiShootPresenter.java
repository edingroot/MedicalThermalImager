package tw.cchi.medthimager.ui.camera.contishoot;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.model.ContiShootParameters;
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
    public boolean parseResult(String period, String captureCount) {
        int periodVal, captureCountVal;

        try {
            periodVal = Integer.parseInt(period);
            captureCountVal = Integer.parseInt(captureCount);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return false;
        }

        ContiShootParameters parameters = new ContiShootParameters(periodVal, captureCountVal);

        getMvpView().getListener().onResult((ContiShootDialog) getMvpView(), parameters);
        getMvpView().dismissDialog();

        return true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
