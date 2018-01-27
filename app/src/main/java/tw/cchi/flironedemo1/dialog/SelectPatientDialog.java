package tw.cchi.flironedemo1.dialog;

import android.app.Dialog;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.flironedemo1.R;

public class SelectPatientDialog {
    private Context context;
    private OnInteractionListener onInteractionListener;
    private Dialog dialog;
    private String selectedPatientUUID;

    @BindView(R.id.editPatientName) EditText editPatientName;
    @BindView(R.id.btnAddPatient) Button btnAddPatient;
    @BindView(R.id.recyclerPatientList) RecyclerView recyclerPatientList;
    @BindView(R.id.btnOk) Button btnOk;

    public SelectPatientDialog(Context context, OnInteractionListener onInteractionListener) {
        this.context = context;
        this.onInteractionListener = onInteractionListener;
    }

    public SelectPatientDialog show() {
        dialog = new Dialog(context, R.style.DialogTheme);
        dialog.setContentView(R.layout.dialog_select_patient);
        ButterKnife.bind(this, dialog);
        initComponents();

        dialog.setTitle(context.getString(R.string.select_patient));
        dialog.show();

        return this;
    }

    private void initComponents() {
        btnAddPatient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String patientName = editPatientName.getText().toString();
                // TODO
            }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInteractionListener.onOkClicked(selectedPatientUUID);
                dismiss();
            }
        });
    }

    public void setSelectedPatientUUID(String selectedPatientUUID) {
        this.selectedPatientUUID = selectedPatientUUID;
        // TODO
    }

    public void dismiss() {
        if (dialog != null)
            dialog.dismiss();
    }

    public interface OnInteractionListener {
        void onOkClicked(String selectedPatientUUID);
    }

}
