package tw.cchi.medthimager.ui.camera.tagselection;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.di.component.ActivityComponent;
import tw.cchi.medthimager.model.api.Tag;
import tw.cchi.medthimager.ui.adapter.MultiSelectionRecyclerAdapter;
import tw.cchi.medthimager.ui.base.BaseDialog;

public class TagSelectionDialog extends BaseDialog
        implements TagSelectionMvpView, MultiSelectionRecyclerAdapter.OnInteractionListener {
    private final String FRAGMENT_TAG = "TagSelectionDialog";

    @Inject TagSelectionMvpPresenter<TagSelectionMvpView> presenter;

    private OnResultListener onResultListener;
    private Set<Tag> preSelectedTags;

    @Inject MultiSelectionRecyclerAdapter tagsRecyclerAdapter;

    @BindView(R.id.recyclerTagsList) RecyclerView recyclerTagsList;

    public static TagSelectionDialog newInstance() {
        return new TagSelectionDialog();
    }

    public void show(FragmentManager fragmentManager, OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
        super.show(fragmentManager, FRAGMENT_TAG);
    }

    public void setSelectedTags(Set<Tag> tags) {
        if (presenter == null)
            this.preSelectedTags = tags;
        else
            presenter.setSelectedTags(tags);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_tag_selection, container, false);

        ActivityComponent component = getActivityComponent();
        if (component != null) {
            component.inject(this);
            setUnBinder(ButterKnife.bind(this, view));
            presenter.onAttach(this);
        }

        getDialog().setCanceledOnTouchOutside(true);

        // Tags list
        tagsRecyclerAdapter.setOnInteractionListener(this);
        tagsRecyclerAdapter.setShowRemove(false);
        recyclerTagsList.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false));
        recyclerTagsList.setAdapter(tagsRecyclerAdapter);

        if (preSelectedTags != null)
            setSelectedTags(preSelectedTags);

        return view;
    }


    @OnClick(R.id.btnConfirm)
    public void onConfirmClick() {
        presenter.confirm();
    }

    @OnClick(R.id.btnCancel)
    public void onCancelClick() {
        dismiss();
    }

    @Override
    public List<Object> getSelectedKeys() {
        return tagsRecyclerAdapter.getSelectedKeys();
    }

    @Override
    public void setSelected(Object key, boolean isSelected) {
        tagsRecyclerAdapter.setSelected(key, isSelected);
    }

    @Override
    public void setTags(ArrayList<Pair<Object, String>> listItems) {
        tagsRecyclerAdapter.setItems(listItems);
    }

    @Override
    public OnResultListener getListener() {
        return onResultListener;
    }

    @Override
    public void dismiss() {
        super.dismiss(FRAGMENT_TAG);
    }


    // MultiSelectionRecyclerAdapter.OnInteractionListener
    @Override
    public void onChange(List<Object> selectedKeys) {
    }

    // MultiSelectionRecyclerAdapter.OnInteractionListener
    @Override
    public void onRemoveClicked(Object key) {
    }


    @Override
    public void onDestroyView() {
        presenter.onDetach();
        super.onDestroyView();
    }

    public interface OnResultListener {
        void onResult(List<Tag> tags);
    }
}
