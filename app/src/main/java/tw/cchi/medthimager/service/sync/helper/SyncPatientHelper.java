package tw.cchi.medthimager.service.sync.helper;

import java.util.List;

import io.reactivex.Observable;
import tw.cchi.medthimager.data.DataManager;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.SSPatient;

public class SyncPatientHelper {
    private DataManager dataManager;
    private ApiHelper apiHelper;

    public SyncPatientHelper(DataManager dataManager, ApiHelper apiHelper) {
        this.dataManager = dataManager;
        this.apiHelper = apiHelper;
    }

    public Observable<String> syncPatient(String patientCuid) {
        return Observable.create(emitter -> {
            // Patient not yet synced
            Patient patient = dataManager.db.patientDAO().get(patientCuid);
            apiHelper.upSyncPatient(new SSPatient(patient), null, false,
                    true, new ApiHelper.OnPatientSyncListener() {
                        @Override
                        public void onSuccess(SSPatient ssPatient) {
                            emitter.onNext(ssPatient.getUuid());
                            emitter.onComplete();
                        }

                        @Override
                        public void onConflictForceMerge(List<SSPatient> conflictPatients, String message) {
                            emitter.onNext("");
                            emitter.onComplete();
                        }

                        @Override
                        public void onConflictCheck(List<SSPatient> conflictPatients, String message) {
                            emitter.onNext("");
                            emitter.onComplete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            emitter.onNext("");
                            emitter.onComplete();
                        }
                    });
        });
    }

}
