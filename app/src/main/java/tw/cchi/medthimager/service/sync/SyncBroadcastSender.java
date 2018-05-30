package tw.cchi.medthimager.service.sync;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import tw.cchi.medthimager.Constants;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.model.api.SSPatient;

import static tw.cchi.medthimager.Constants.ACTION_SERVICE_BROADCAST;

public final class SyncBroadcastSender {
    public class EventName {
        public static final String SYNC_PATIENT_DONE = "SyncService/SYNC_PATIENT_DONE";
        public static final String SYNC_PATIENT_CONFLICT = "SyncService/SYNC_PATIENT_CONFLICT";
    }
    public class Extras {
        public static final String EXTRA_EVENT_NAME = "SyncService/SYNC_PATIENT_CONFLICT";
        public static final String EXTRA_CONFLICT_TYPE = "SyncService/EXTRA_CONFLICT_TYPE";
        public static final String EXTRA_PATIENT = "SyncService/EXTRA_PATIENT";
        public static final String EXTRA_SSPATIENT_LIST = "SyncService/EXTRA_SSPATIENT_LIST";
    }
    public enum ConflictType {FORCE_MERGE, CHECK }

    private SyncService syncService;

    public SyncBroadcastSender(SyncService syncService) {
        this.syncService = syncService;
    }

    public void sendSyncPatientsDone() {
        Intent intent = new Intent();
        intent.setAction(ACTION_SERVICE_BROADCAST);
        intent.putExtra(Extras.EXTRA_EVENT_NAME, EventName.SYNC_PATIENT_DONE);
        syncService.sendBroadcast(intent, Constants.INTERNAL_BROADCAST_PERMISSION);
    }

    public void sendSyncPatientConflict(ConflictType conflictType, Patient patient, List<SSPatient> conflictPatients) {
        Intent intent = new Intent();
        intent.setAction(ACTION_SERVICE_BROADCAST);
        intent.putExtra(Extras.EXTRA_EVENT_NAME, EventName.SYNC_PATIENT_CONFLICT);

        intent.putExtra(Extras.EXTRA_CONFLICT_TYPE, conflictType);
        intent.putExtra(Extras.EXTRA_PATIENT, patient);
        intent.putParcelableArrayListExtra(Extras.EXTRA_SSPATIENT_LIST, new ArrayList<>(conflictPatients));

        syncService.sendBroadcast(intent, Constants.INTERNAL_BROADCAST_PERMISSION);
    }

}
