package tw.cchi.medthimager.service.sync.task;

public class SyncSingleThImageTask extends SyncTask {
    private boolean createNew = false;

    public SyncSingleThImageTask() {
        super();
    }

    @Override
    void doWork() {
        if (!checkNetworkAndAuthed())
            return;

        // TODO
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
