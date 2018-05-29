package tw.cchi.medthimager.ui.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.db.model.Patient;
import tw.cchi.medthimager.model.api.SSPatient;

/**
 * We can set either Patient or SSPatient as list entries.
 */
public class PatientSelectRecyclerAdapter extends RecyclerView.Adapter<PatientSelectRecyclerAdapter.ViewHolder> {
    private OnInteractionListener onInteractionListener;
    private boolean showRemove = true;

    private List<Patient> patients = new ArrayList<>();
    private List<SSPatient> ssPatients = new ArrayList<>();
    private int selectedPosition = -1;

    public PatientSelectRecyclerAdapter() {
    }

    public void setOnInteractionListener(OnInteractionListener onInteractionListener) {
        this.onInteractionListener = onInteractionListener;
    }

    public void setShowRemove(boolean showRemove) {
        this.showRemove = showRemove;
        notifyDataSetChanged();
    }

    public void setPatients(ArrayList<Patient> patients) {
        this.patients = patients;
        this.ssPatients = null;
        notifyDataSetChanged();
    }

    public void setSSPatients(List<SSPatient> ssPatients) {
        this.ssPatients = ssPatients;
        this.patients = null;
        notifyDataSetChanged();
    }

    /**
     * @param selectedPosition set as -1 if no item selected
     */
    public void setSelectedPosition(int selectedPosition) {
        this.selectedPosition = selectedPosition;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.listitem_patient_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final int holderPosition = position;
        final boolean selected = position == selectedPosition;

        boolean isDefaultPatient = false;
        String patientName = "";

        if (patients != null) {
            Patient patient = patients.get(position);
            patientName = patient.getName();
            isDefaultPatient = patient.isDefualtPatient();
        } else if (ssPatients != null) {
            SSPatient ssPatient = ssPatients.get(position);
            patientName = ssPatient.getName();
        }

        holder.txtRemove.setVisibility(showRemove ? View.VISIBLE : View.INVISIBLE);
        holder.txtChecked.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        holder.txtPatientName.setText(patientName);

        holder.layoutRoot.setOnClickListener(v -> {
            // If no patient selected, auto select the default patient
            int positionToSet = holderPosition != selectedPosition ? holderPosition : 0;
            setSelectedPosition(positionToSet);

            if (onInteractionListener != null)
                onInteractionListener.onSelected(v, positionToSet);
        });

        if (!isDefaultPatient) {
            holder.txtRemove.setOnClickListener(v -> {
                if (onInteractionListener != null)
                    onInteractionListener.onRemoveClicked(v, holderPosition);
            });
        } else {
            holder.txtRemove.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        if (patients != null)
            return patients.size();
        else if (ssPatients != null)
            return ssPatients.size();
        else
            return 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final View mView;

        @BindView(R.id.layoutRoot) RelativeLayout layoutRoot;
        @BindView(R.id.txtChecked) TextView txtChecked;
        @BindView(R.id.txtPatientName) TextView txtPatientName;
        @BindView(R.id.txtRemove) TextView txtRemove;

        ViewHolder(View view) {
            super(view);
            mView = view;
            ButterKnife.bind(this, view);
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(), "%s - %s", super.toString(), txtPatientName.getText());
        }
    }

    public interface OnInteractionListener {
        /**
         * @param v
         * @param position -1 if no item selected (the original item deselected)
         */
        void onSelected(View v, int position);

        void onRemoveClicked(View v, int position);
    }

}
