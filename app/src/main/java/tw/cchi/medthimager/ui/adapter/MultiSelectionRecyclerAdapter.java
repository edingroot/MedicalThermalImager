package tw.cchi.medthimager.ui.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import tw.cchi.medthimager.R;

public class MultiSelectionRecyclerAdapter extends RecyclerView.Adapter<MultiSelectionRecyclerAdapter.ViewHolder> {
    private OnInteractionListener onInteractionListener;
    private ArrayList<Pair<Object, String>> items = new ArrayList<>(); // Pair<key, title>
    private HashMap<Object, Boolean> isKeySelected = new HashMap<>();
    private boolean showRemove = true;

    @Inject
    public MultiSelectionRecyclerAdapter() {
    }

    public void setItems(ArrayList<Pair<Object, String>> items) {
        this.items = items;

        for (Pair pair : items) {
            if (!isKeySelected.containsKey(pair.first))
                isKeySelected.put(pair.first, false);
        }

        notifyDataSetChangedOnUI();
    }

    public void setOnInteractionListener(OnInteractionListener onInteractionListener) {
        this.onInteractionListener = onInteractionListener;
    }

    public void setShowRemove(boolean showRemove) {
        this.showRemove = showRemove;
        notifyDataSetChangedOnUI();
    }

    public void setSelected(Object key, boolean isSelected) {
        if (isKeySelected.containsKey(key))
            isKeySelected.put(key, isSelected);

        notifyDataSetChangedOnUI();
    }

    public List<Object> getSelectedKeys() {
        List<Object> selectedKeys = new ArrayList<>();

        for (Pair<Object, String> item : items) {
            if (isKeySelected.get(item.first))
                selectedKeys.add(item.first);
        }

        return selectedKeys;
    }

    // Prevents "IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling"
    private void notifyDataSetChangedOnUI() {
        Observable.create(emitter -> {
            notifyDataSetChanged();
            emitter.onComplete();
        }).subscribeOn(AndroidSchedulers.mainThread()).subscribe();
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.listitem_multi_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Object key = items.get(position).first;
        final boolean selected = isKeySelected.get(key);

        holder.txtRemove.setVisibility(showRemove ? View.VISIBLE : View.INVISIBLE);
        holder.txtChecked.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        holder.txtTitle.setText(items.get(position).second);

        holder.layoutRoot.setOnClickListener(v -> {
            setSelected(key, !selected);

            if (onInteractionListener != null)
                onInteractionListener.onChange(getSelectedKeys());
        });

        holder.txtRemove.setOnClickListener(v -> {
            if (onInteractionListener != null)
                onInteractionListener.onRemoveClicked(key);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final View mView;

        @BindView(R.id.layoutRoot) RelativeLayout layoutRoot;
        @BindView(R.id.txtChecked) TextView txtChecked;
        @BindView(R.id.txtTitle) TextView txtTitle;
        @BindView(R.id.txtRemove) TextView txtRemove;

        ViewHolder(View view) {
            super(view);
            mView = view;
            ButterKnife.bind(this, view);
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(), "%s: %s", super.toString(), txtTitle.getText());
        }
    }

    public interface OnInteractionListener {
        void onChange(List<Object> selectedKeys);

        void onRemoveClicked(Object key);
    }

}
