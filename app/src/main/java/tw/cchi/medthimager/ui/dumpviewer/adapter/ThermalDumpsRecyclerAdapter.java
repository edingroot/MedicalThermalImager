package tw.cchi.medthimager.ui.dumpviewer.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.medthimager.R;

public class ThermalDumpsRecyclerAdapter extends RecyclerView.Adapter<ThermalDumpsRecyclerAdapter.ViewHolder> {
    private final Context context;
    private OnInteractionListener onInteractionListener;
    private ArrayList<String> titles = new ArrayList<>();
    private int selectedPosition = -1;

    public ThermalDumpsRecyclerAdapter(Context context, OnInteractionListener onInteractionListener) {
        this.context = context;
        this.onInteractionListener = onInteractionListener;
    }

    public void setOnInteractionListener(OnInteractionListener onInteractionListener) {
        this.onInteractionListener = onInteractionListener;
    }

    /**
     * @return selected position
     */
    public int addDumpSwitch(String title) {
        titles.add(title);
        if (titles.size() == 1) {
            selectedPosition = 0;
        }
        notifyDataSetChanged();

        return selectedPosition;
    }

    /**
     * @return (new) selected position of -1 if the list is empty.
     */
    public int removeDumpSwitch(int position) {
        titles.remove(position);

        if (titles.size() == 0) {
            setSelectedPosition(-1);
        } else if (position <= selectedPosition) {
            setSelectedPosition(position > 0 ? position - 1 : 0);
        }

        notifyDataSetChanged();

        return selectedPosition;
    }

    private void setSelectedPosition(int selectedPosition) {
        this.selectedPosition = selectedPosition;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.listitem_thermaldump, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final int holderPosition = position;
        final String holderTitle = titles.get(position);
        int color = selectedPosition == position ? R.color.colorPrimary : R.color.tab_inactive;

        holder.button.setText(holderTitle);
        holder.button.setBackground(
            ResourcesCompat.getDrawable(context.getResources(), R.drawable.btn_dump_tab, null));
        holder.button.setBackgroundTintList(
            ColorStateList.valueOf(ResourcesCompat.getColor(context.getResources(), color, null)));
        // holder.button.setBackgroundResource(android.R.drawable.btn_default); // android default button style

        holder.button.setOnClickListener(v -> {
            if (holderPosition != selectedPosition) {
                if (onInteractionListener.onClick(v, holderPosition)) {
                    setSelectedPosition(holderPosition);
                }
            }
        });

        holder.button.setOnLongClickListener(v -> {
            onInteractionListener.onLongClick(v, holderPosition);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return titles.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        int index;
        String title;
        final View mView;

        @BindView(R.id.button) Button button;

        ViewHolder(View view) {
            super(view);
            mView = view;
            ButterKnife.bind(this, view);
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(), "%s: %d - %s", super.toString(), index, title);
        }
    }

    public interface OnInteractionListener {
        /**
         * @return false if reject tab switching
         */
        boolean onClick(View v, int position);

        void onLongClick(View v, int position);
    }

}
