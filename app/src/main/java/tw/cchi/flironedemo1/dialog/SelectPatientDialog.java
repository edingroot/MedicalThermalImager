package tw.cchi.flironedemo1.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.R;
import tw.cchi.flironedemo1.adapter.PatientSelectsRecyclerAdapter;
import tw.cchi.flironedemo1.db.AppDatabase;
import tw.cchi.flironedemo1.db.Patient;

public class SelectPatientDialog {
    private static final int UPDATE_PATIENT = 1;

    private final Context context;
    private OnInteractionListener onInteractionListener;
    private Dialog dialog;
    private Handler handler;
    private AppDatabase database;
    private PatientSelectsRecyclerAdapter patientRecyclerAdapter;

    private List<Patient> patients;
    private String selectedPatientUUID = null;

    @BindView(R.id.editPatientName) EditText editPatientName;
    @BindView(R.id.btnAddPatient) Button btnAddPatient;
    @BindView(R.id.progressBarLoading) ProgressBar progressBarLoading;
    @BindView(R.id.recyclerPatientList) RecyclerView recyclerPatientList;
    @BindView(R.id.btnOk) Button btnOk;

    public SelectPatientDialog(Context context, OnInteractionListener onInteractionListener) {
        this.context = context;
        this.onInteractionListener = onInteractionListener;
        this.database = AppDatabase.getInstance(context);

        this.handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case UPDATE_PATIENT:
                        ArrayList<String> patientNames = new ArrayList<>();
                        for (Patient patient : patients)
                            patientNames.add(patient.getName());
                        patientRecyclerAdapter.setPatientNames(patientNames);
                        progressBarLoading.setVisibility(View.GONE);
                        recyclerPatientList.setVisibility(View.VISIBLE);
                        break;
                }
            }
        };
    }

    /**
     * @param patientUUID null if no patient selected
     */
    public void setSelectedPatientUUID(String patientUUID) {
        this.selectedPatientUUID = patientUUID;

        int selectedPatientIndex = -1;
        if (patientUUID != null) {
            for (int i = 0; i < patients.size(); i++) {
                if (patients.get(i).getUuid().equals(patientUUID)) {
                    selectedPatientIndex = i;
                    break;
                }
            }
        }
        patientRecyclerAdapter.setSelectedPosition(selectedPatientIndex);
    }

    public SelectPatientDialog show() {
        dialog = new Dialog(context, R.style.DialogTheme);
        dialog.setContentView(R.layout.dialog_select_patient);
        ButterKnife.bind(this, dialog);
        dialog.setTitle(context.getString(R.string.select_patient));
        initComponents();

        // Load data from database
        setUILoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                patients = database.patientDAO().getAll();
                handler.sendEmptyMessage(UPDATE_PATIENT);
            }
        }).start();

        dialog.show();
        return this;
    }

    private void initComponents() {
        editPatientName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                handleAddPatient();

                // Hide virtual keyboard
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.hideSoftInputFromWindow(editPatientName.getWindowToken(), 0);
                return true;
            }
        });

        btnAddPatient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleAddPatient();
            }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInteractionListener.onOkClicked(selectedPatientUUID);
                dismiss();
            }
        });

        patientRecyclerAdapter = new PatientSelectsRecyclerAdapter(context, new PatientSelectsRecyclerAdapter.OnInteractionListener() {
            @Override
            public void onSelected(View v, int position) {
                selectedPatientUUID = position == -1 ? null : patients.get(position).getUuid();
            }

            @Override
            public void onRemoveClicked(View v, final int position) {
                // TODO: confirm before removal

                // Remove patient data from database
                setUILoading();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Patient patientRemoving = patients.get(position);
                        if (selectedPatientUUID != null && selectedPatientUUID.equals(patientRemoving.getUuid()))
                            patientRecyclerAdapter.setSelectedPosition(-1);

                        database.patientDAO().delete(patientRemoving);
                        patients = database.patientDAO().getAll();
                        handler.sendEmptyMessage(UPDATE_PATIENT);
                    }
                }).start();
            }
        });
        recyclerPatientList.setAdapter(patientRecyclerAdapter);
        recyclerPatientList.setLayoutManager(
                new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        );
    }

    private void handleAddPatient() {
        final String patientName = editPatientName.getText().toString();
        editPatientName.setText("");

        setUILoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                database.patientDAO().insertAll(new Patient(patientName));
                patients = database.patientDAO().getAll();
                handler.sendEmptyMessage(UPDATE_PATIENT);
            }
        }).start();
    }

    private void setUILoading() {
        progressBarLoading.setVisibility(View.VISIBLE);
        recyclerPatientList.setVisibility(View.GONE);
    }

    public void dismiss() {
        if (dialog != null)
            dialog.dismiss();
    }

    public interface OnInteractionListener {
        void onOkClicked(String selectedPatientUUID);
    }
}