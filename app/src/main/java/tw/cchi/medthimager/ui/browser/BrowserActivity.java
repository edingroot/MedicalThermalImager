package tw.cchi.medthimager.ui.browser;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.widget.Button;
import android.widget.TextView;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import tw.cchi.medthimager.R;
import tw.cchi.medthimager.ui.base.BaseActivity;

public class BrowserActivity extends BaseActivity implements BrowserMvpView {

    @Inject BrowserPresenter<BrowserMvpView> presenter;

    @BindView(R.id.btnPatientName) Button btnPatientName;
    @BindView(R.id.btnDates) Button btnDates;
    @BindView(R.id.txtSelectionStatus) TextView txtSelectionStatus;
    @BindView(R.id.recyclerDumps) RecyclerView recyclerDumps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        getActivityComponent().inject(this);
        setUnBinder(ButterKnife.bind(this));
        presenter.onAttach(this);
    }

    @Override
    protected void onDestroy() {
        presenter.onDetach();
        super.onDestroy();
    }
}
