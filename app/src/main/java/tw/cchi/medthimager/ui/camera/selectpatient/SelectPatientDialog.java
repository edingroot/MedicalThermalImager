package tw.cchi.medthimager.ui.camera.selectpatient;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.medthimager.MvpApplication;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.AppDatabase;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.service.sync.task.SyncPatientsTask;
import tw.cchi.medthimager.service.sync.task.SyncSinglePatientTask;
import tw.cchi.medthimager.ui.adapter.PatientSelectRecyclerAdapter;

public class SelectPatientDialog {
    private static final int UPDATE_PATIENTS = 1;

    private final Activity activity;
    private OnInteractionListener onInteractionListener;
    private Dialog dialog;
    private Handler handler;
    private AppDatabase database;
    private PatientSelectRecyclerAdapter patientRecyclerAdapter;

    private List<Patient> patients;
    private String selectedPatientCuid;

    @BindView(R.id.editPatientName) EditText editPatientName;
    @BindView(R.id.btnAddPatient) Button btnAddPatient;
    @BindView(R.id.progressBarLoading) ProgressBar progressBarLoading;
    @BindView(R.id.recyclerPatientList) RecyclerView recyclerPatientList;
    @BindView(R.id.btnStart) Button btnOk;

    public SelectPatientDialog(Activity activity, OnInteractionListener onInteractionListener) {
        this.activity = activity;
        this.onInteractionListener = onInteractionListener;
        this.database = AppDatabase.getInstance(activity.getApplicationContext());

        this.handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case UPDATE_PATIENTS:
                        patientRecyclerAdapter.setPatients((ArrayList<Patient>) patients);
                        progressBarLoading.setVisibility(View.INVISIBLE);
                        recyclerPatientList.setVisibility(View.VISIBLE);
                        updateSelectedPatientByCuid(selectedPatientCuid);
                        break;
                }
            }
        };
    }

    /**
     * @param patientCuid null if no patient selected
     */
    public void setSelectedPatientCuid(@Nullable String patientCuid) {
        if (patientCuid == null)
            selectedPatientCuid = Patient.DEFAULT_PATIENT_CUID;
        else
            selectedPatientCuid = patientCuid;

        if (patientRecyclerAdapter != null)
            updateSelectedPatientByCuid(patientCuid);
    }

    public SelectPatientDialog show() {
        dialog = new Dialog(activity, R.style.DialogTheme);
        dialog.setContentView(R.layout.dialog_select_patient);
        ButterKnife.bind(this, dialog);
        dialog.setTitle(activity.getString(R.string.select_patient));
        initComponents();

        // Load data from database
        setUILoading();
        new Thread(() -> {
            patients = database.patientDAO().getAll();
            handler.sendEmptyMessage(UPDATE_PATIENTS);
        }).start();

        dialog.show();
        return this;
    }

    private void initComponents() {
        editPatientName.setOnEditorActionListener((v, actionId, event) -> {
            handleAddPatient();

            // Hide virtual keyboard
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.hideSoftInputFromWindow(editPatientName.getWindowToken(), 0);
            return true;
        });

        btnAddPatient.setOnClickListener(v -> handleAddPatient());

        btnOk.setOnClickListener(v -> {
            onInteractionListener.onOkClicked(selectedPatientCuid);
            syncPatients();
            dismiss();
        });

        patientRecyclerAdapter = new PatientSelectRecyclerAdapter();
        // TODO: temporary disable the remove function
        patientRecyclerAdapter.setShowRemove(false);
        patientRecyclerAdapter.setOnInteractionListener(new PatientSelectRecyclerAdapter.OnInteractionListener() {
            @Override
            public void onSelected(View v, int position) {
                // Check if there isn't only the default patient: "Not Specified"
                if (patients.size() != 1) {
                    selectedPatientCuid = patients.get(position).getCuid();
                }
            }

            @Override
            public void onRemoveClicked(View v, final int position) {
                // Confirm before removal
                final Patient patientRemoving = patients.get(position);
                new AlertDialog.Builder(activity, R.style.MyAlertDialog)
                        .setTitle("Confirm")
                        .setMessage(
                                "Confirm to remove " + patientRemoving.getName() +
                                        " and all related capture record (list for exporting CSV, not thermal data) from database?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // Remove patient data from database
                            setUILoading();
                            new Thread(() -> {
                                if (selectedPatientCuid != null && selectedPatientCuid.equals(patientRemoving.getCuid()))
                                    patientRecyclerAdapter.setSelectedPosition(-1);

                                database.patientDAO().delete(patientRemoving);
                                patients = database.patientDAO().getAll();
                                handler.sendEmptyMessage(UPDATE_PATIENTS);
                            }).start();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                        }).show();
            }
        });
        recyclerPatientList.setLayoutManager(
                new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        recyclerPatientList.setAdapter(patientRecyclerAdapter);
    }

    private void updateSelectedPatientByCuid(String patientCuid) {
        if (patientRecyclerAdapter == null) return;

        int selectedPatientIndex = -1;
        if (patientCuid != null) {
            for (int i = 0; i < patients.size(); i++) {
                if (patients.get(i).getCuid().equals(patientCuid)) {
                    selectedPatientIndex = i;
                    break;
                }
            }
        }
        patientRecyclerAdapter.setSelectedPosition(selectedPatientIndex);
    }

    private void handleAddPatient() {
        final String patientName = editPatientName.getText().toString();
        editPatientName.setText("");

        setUILoading();
        new Thread(() -> {
            Patient patient = new Patient(patientName);
            database.patientDAO().insertAll(patient);
            patients = database.patientDAO().getAll();

            upSyncPatient(patient);
            handler.sendEmptyMessage(UPDATE_PATIENTS);
        }).start();
    }

    private void setUILoading() {
        progressBarLoading.setVisibility(View.VISIBLE);
        recyclerPatientList.setVisibility(View.GONE);
    }


    private void upSyncPatient(Patient patient) {
        MvpApplication application = (MvpApplication) activity.getApplication();
        application.getSyncService().subscribe(syncService ->
                syncService.scheduleNewTask(new SyncSinglePatientTask(patient)));
    }

    private void syncPatients() {
        MvpApplication application = (MvpApplication) activity.getApplication();
        application.getSyncService().subscribe(syncService ->
                syncService.scheduleNewTask(new SyncPatientsTask()));
    }


    public void dismiss() {
        if (dialog != null)
            dialog.dismiss();
    }

    public interface OnInteractionListener {
        void onOkClicked(String selectedPatientCuid);
    }
}
