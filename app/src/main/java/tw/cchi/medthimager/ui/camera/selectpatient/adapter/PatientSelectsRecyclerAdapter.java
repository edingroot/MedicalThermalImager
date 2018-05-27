package tw.cchi.medthimager.ui.camera.selectpatient.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.db.Patient;

public class PatientSelectsRecyclerAdapter extends RecyclerView.Adapter<PatientSelectsRecyclerAdapter.ViewHolder> {
    private final Context context;
    private final OnInteractionListener onInteractionListener;
    private ArrayList<Patient> patients = new ArrayList<>();
    private int selectedPosition = -1;

    public PatientSelectsRecyclerAdapter(Context context, OnInteractionListener onInteractionListener) {
        this.context = context;
        this.onInteractionListener = onInteractionListener;
    }

    public void setPatients(ArrayList<Patient> patients) {
        this.patients = patients;
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
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.listitem_patient_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final int holderPosition = position;
        final boolean selected = position == selectedPosition;
        final Patient patient = patients.get(position);

        holder.txtChecked.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);

        holder.txtPatientName.setText(patient.getName());

        holder.layoutRoot.setOnClickListener(v -> {
            // If no patient selected, auto select the default patient
            int positionToSet = holderPosition != selectedPosition ? holderPosition : 0;
            setSelectedPosition(positionToSet);
            onInteractionListener.onSelected(v, positionToSet);
        });

        if (!patient.getCuid().equals(Patient.DEFAULT_PATIENT_CUID)) {
            holder.txtRemove.setOnClickListener(v -> onInteractionListener.onRemoveClicked(v, holderPosition));
        } else {
            holder.txtRemove.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return patients.size();
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
