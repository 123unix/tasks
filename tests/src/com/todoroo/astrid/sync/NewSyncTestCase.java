package com.todoroo.astrid.sync;

import com.todoroo.andlib.service.Autowired;
import com.todoroo.astrid.dao.MetadataDao;
import com.todoroo.astrid.dao.RemoteModelDao;
import com.todoroo.astrid.dao.TagDataDao;
import com.todoroo.astrid.dao.TagOutstandingDao;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.dao.TaskOutstandingDao;
import com.todoroo.astrid.data.SyncFlags;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.test.DatabaseTestCase;

public class NewSyncTestCase extends DatabaseTestCase {
    @Autowired
    protected TaskDao taskDao;
    @Autowired
    protected TagDataDao tagDataDao;

    @Autowired
    protected TaskOutstandingDao taskOutstandingDao;
    @Autowired
    protected TagOutstandingDao tagOutstandingDao;

    @Autowired
    protected MetadataDao metadataDao;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RemoteModelDao.setOutstandingEntryFlags(RemoteModelDao.OUTSTANDING_ENTRY_FLAG_RECORD_OUTSTANDING);
    }

    protected Task createTask(String title, boolean suppress) {
        Task task = new Task();
        task.setValue(Task.TITLE, title);
        task.setValue(Task.IMPORTANCE, SYNC_TASK_IMPORTANCE);

        if (suppress)
            task.putTransitory(SyncFlags.ACTFM_SUPPRESS_OUTSTANDING_ENTRIES, true);
        taskDao.createNew(task);
        return task;
    }

    protected Task createTask() {
        return createTask(false);
    }

    public static final String SYNC_TASK_TITLE = "new title";
    public static final int SYNC_TASK_IMPORTANCE = Task.IMPORTANCE_MUST_DO;

    protected Task createTask(boolean suppress) {
        return createTask(SYNC_TASK_TITLE, suppress);
    }

    protected TagData createTagData(String name, boolean suppress) {
        TagData tag = new TagData();
        tag.setValue(TagData.NAME, name);

        tagDataDao.createNew(tag);
        return tag;
    }

    protected TagData createTagData() {
        return createTagData(false);
    }

    protected TagData createTagData(boolean suppress) {
        return createTagData("new tag", suppress);
    }
}
