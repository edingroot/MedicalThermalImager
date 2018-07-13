package tw.cchi.medthimager.ui.camera.tagselection;

import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.data.network.ApiHelper;
import tw.cchi.medthimager.model.api.Tag;
import tw.cchi.medthimager.ui.base.BasePresenter;

public class TagSelectionPresenter<V extends TagSelectionMvpView> extends BasePresenter<V> implements TagSelectionMvpPresenter<V> {

    private HashMap<String, Tag> tags = new HashMap<>();

    @Inject ApiHelper apiHelper;

    @Inject
    public TagSelectionPresenter(CompositeDisposable compositeDisposable) {
        super(compositeDisposable);
    }

    @Override
    public void onAttach(V mvpView) {
        super.onAttach(mvpView);

        // Update tags
        tags = dataManager.pref.getCachedTags();
        if (tags == null || tags.size() == 0) {
            apiHelper.refreshTags().blockingSubscribe(success -> handleTagsUpdate());
        } else {
            getMvpView().setTags(createListItems(tags));
            apiHelper.refreshTags().subscribe(success -> handleTagsUpdate());
        }
    }

    @Override
    public void setSelectedTags(Set<Tag> tags) {
        for (Tag tag : tags)
            getMvpView().setSelected(tag.getUuid(), true);
    }

    @Override
    public void confirm() {
        List<Tag> selectedTags = new ArrayList<>();
        List<Object> selectedKeys = getMvpView().getSelectedKeys();

        for (Object key : selectedKeys)
            selectedTags.add(tags.get(key.toString()));

        getMvpView().getListener().onResult(selectedTags);
        getMvpView().dismiss();
    }

    private void handleTagsUpdate() {
        if (!isViewAttached())
            return;

        HashMap<String, Tag> tags = dataManager.pref.getCachedTags();
        if (tags == null || tags.size() == 0) {
            getMvpView().showToast(R.string.no_tags_avail);
            getMvpView().dismiss();
        } else {
            getMvpView().setTags(createListItems(tags));
        }
    }

    private ArrayList<Pair<Object, String>> createListItems(HashMap<String, Tag> tags) {
        // Sort tags by name
        SortedMap<String, Tag> tagNameMap = new TreeMap<>();
        for (Tag tag : tags.values())
            tagNameMap.put(tag.getName(), tag);

        // Generate result
        ArrayList<Pair<Object, String>> items = new ArrayList<>();
        for (Tag tag : tagNameMap.values())
            items.add(new Pair<>(tag.getUuid(), tag.getName()));

        return items;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
