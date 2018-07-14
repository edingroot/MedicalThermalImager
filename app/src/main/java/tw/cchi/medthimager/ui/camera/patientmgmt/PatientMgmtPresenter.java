package tw.cchi.medthimager.ui.camera.patientmgmt;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.service.sync.task.SyncPatientsTask;
import tw.cchi.medthimager.service.sync.task.SyncSinglePatientTask;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class PatientMgmtPresenter<V extends PatientMgmtMvpView> extends BasePresenter<V> implements PatientMgmtMvpPresenter<V> {

    @Inject MvpApplication application;

    private PublishSubject<Boolean> patientsFirstUpdatePub = PublishSubject.create();
    private List<Patient> patients;

    @Inject
    public PatientMgmtPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);
        loadPatientListFromDB();
        syncPatients();
    }

    @Override
    public void onSyncPatientsDone() {
        loadPatientListFromDB();
    }

    @Override
    public void addPatient(String caseId, String bed, String name) {
        if (bed.isEmpty() && name.isEmpty()) {
            getMvpView().showToast(R.string.error_both_bed_name_empty);
        } else {
            Observable.<List<Patient>>create(emitter -> {
                Patient patient = new Patient(caseId, bed, name);
                dataManager.db.patientDAO().insertAll(patient);
                upSyncPatient(patient);

                loadPatientListFromDB();
                emitter.onComplete();
            }).subscribeOn(Schedulers.io()).subscribe();
        }
    }

    @Override
    public void removePatient(int position) {
        Patient patient = getPatientByPosition(position);
        if (patient.isDefaultPatient())
            return;

        if (position == getMvpView().getSelectedPosition()) {
            // Set default patient selected
            getMvpView().setSelectedPosition(0);
        }

        dataManager.db.patientDAO().delete(patient);
        loadPatientListFromDB();
    }

    @Override
    public void setSelected(String patientCuid) {
        Runnable runnable = () -> {
            int selectedPosition = -1;
            for (int i = 0; i < patients.size(); i++) {
                if (patients.get(i).getCuid().equals(patientCuid)) {
                    selectedPosition = i;
                    break;
                }
            }

            if (selectedPosition != -1 && isViewAttached()) {
                getMvpView().setSelectedPosition(selectedPosition);
            }
        };

        if (patients == null) {
            getCompositeDisposable().add(
                patientsFirstUpdatePub.subscribe(b -> runnable.run()));
        } else {
            runnable.run();
        }
    }

    @Override
    public void processSelectPatient(Patient patient) {
        syncPatients();
        getMvpView().dismiss();
    }

    @Override
    public Patient getPatientByPosition(int position) {
        return patients.get(position);
    }

    private void loadPatientListFromDB() {
        Observable.<List<Patient>>create(emitter -> {
            emitter.onNext(dataManager.db.patientDAO().getAll());
            emitter.onComplete();
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(patients -> {
                boolean firstUpdated = this.patients == null;
                this.patients = patients;

                if (isViewAttached())
                    getMvpView().setPatients(patients);

                if (firstUpdated)
                    patientsFirstUpdatePub.onNext(true);
            });
    }

    private void upSyncPatient(Patient patient) {
        application.connectSyncService().subscribe(syncService ->
                syncService.scheduleNewTask(new SyncSinglePatientTask(patient)));
    }

    private void syncPatients() {
        application.connectSyncService().subscribe(syncService ->
                syncService.scheduleNewTask(new SyncPatientsTask()));
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
