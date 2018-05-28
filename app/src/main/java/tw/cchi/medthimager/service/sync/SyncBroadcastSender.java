package tw.cchi.medthimager.service.sync;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.model.api.SSPatient;

public final class SyncBroadcastSender {
    public class Actions {
        public static final String SYNC_PATIENT_CONFLICT = "SyncService/SYNC_PATIENT_CONFLICT";
    }
    public class Extras {
        public static final String EXTRA_CONFLICT_TYPE = "SyncService/EXTRA_CONFLICT_TYPE";
        public static final String EXTRA_PATIENT = "SyncService/EXTRA_PATIENT";
        public static final String EXTRA_SSPATIENT_LIST = "SyncService/EXTRA_SSPATIENT_LIST";
    }
    public enum ConflictType { STRICT, CHECK }

    private SyncService syncService;

    public SyncBroadcastSender(SyncService syncService) {
        this.syncService = syncService;
    }

    public void sendSyncPatientConflict(ConflictType conflictType, Patient patient, List<SSPatient> conflictPatients) {
        Intent intent = new Intent();
        intent.setAction(Actions.SYNC_PATIENT_CONFLICT);
        intent.putExtra(Extras.EXTRA_CONFLICT_TYPE, conflictType);
        intent.putExtra(Extras.EXTRA_PATIENT, patient);
        intent.putParcelableArrayListExtra(Extras.EXTRA_SSPATIENT_LIST, new ArrayList<>(conflictPatients));
        syncService.sendBroadcast(intent, Constants.INTERNAL_BROADCAST_PERMISSION);
    }

}
