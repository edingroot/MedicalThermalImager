package tw.cchi.medthimager.ui.dialog.patientconflict;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class ConflictPatientPresenter<V extends ConflictPatientMvpView> extends BasePresenter<V> implements ConflictPatientMvpPresenter<V> {

    @Inject
    public ConflictPatientPresenter(CompositeDisposable compositeDisposable) {
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
