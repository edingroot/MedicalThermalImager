package tw.cchi.medthimager.adapter;

import android.app.Activity;
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
    private final Activity activity;
    private final OnInteractionListener onInteractionListener;
    private ArrayList<String> titles = new ArrayList<>();
    private int selectedPosition = -1;

    public ThermalDumpsRecyclerAdapter(Activity activity, OnInteractionListener onInteractionListener) {
        this.activity = activity;
        this.onInteractionListener = onInteractionListener;
    }

    /**
     *
     * @param title
     * @return selected position
     */
    public int addDumpSwitch(String title) {
        titles.add(title);
        if (titles.size() == 1) {
            selectedPosition = 0;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });

        return selectedPosition;
    }

    /**
     *
     * @param position
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
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.listitem_thermaldump, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final int holderPosition = position;
        final String holderTitle = titles.get(position);

        holder.button.setText(holderTitle);

        if (selectedPosition == position) {
            holder.button.setBackground(
                    ResourcesCompat.getDrawable(activity.getResources(), R.drawable.dumpbtn_selected, null)
            );
        } else {
            // holder.button.setBackgroundResource(android.R.drawable.btn_default); // android default button style
            holder.button.setBackground(
                    ResourcesCompat.getDrawable(activity.getResources(), R.drawable.dumpbtn_normal, null)
            );
        }

        holder.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holderPosition != selectedPosition) {
                    setSelectedPosition(holderPosition);
                    onInteractionListener.onClick(v, holderPosition);
                }
            }
        });

        holder.button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onInteractionListener.onLongClick(v, holderPosition);
                return true;
            }
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
        void onClick(View v, int position);
        void onLongClick(View v, int position);
    }

}
