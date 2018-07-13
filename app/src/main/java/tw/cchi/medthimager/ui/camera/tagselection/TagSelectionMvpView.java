package tw.cchi.medthimager.ui.camera.tagselection;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import tw.cchi.medthimager.ui.base.DialogMvpView;

public interface TagSelectionMvpView extends DialogMvpView {

    List<Object> getSelectedKeys();

    void setSelected(Object key, boolean isSelected);

    void setTags(ArrayList<Pair<Object, String>> listItems);

    TagSelectionDialog.OnResultListener getListener();

    void dismiss();

}
