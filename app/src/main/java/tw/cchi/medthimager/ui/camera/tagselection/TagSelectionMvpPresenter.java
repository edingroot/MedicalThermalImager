package tw.cchi.medthimager.ui.camera.tagselection;

import java.util.Set;

import tw.cchi.medthimager.model.api.Tag;
import tw.cchi.medthimager.ui.base.MvpPresenter;

public interface TagSelectionMvpPresenter<V extends TagSelectionMvpView> extends MvpPresenter<V> {

    void setSelectedTags(Set<Tag> tagUuids);

    void confirm();

}
