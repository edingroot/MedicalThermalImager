package tw.cchi.medthimager.di.component;

import dagger.Component;
import tw.cchi.medthimager.di.PerService;
import tw.cchi.medthimager.di.module.ServiceModule;
import tw.cchi.medthimager.service.sync.SyncService;
import tw.cchi.medthimager.service.sync.task.UpSyncThImagesTask;

@PerService
@Component(dependencies = ApplicationComponent.class, modules = ServiceModule.class)
public interface ServiceComponent {

    void inject(SyncService service);

    void inject(UpSyncThImagesTask task);

}
