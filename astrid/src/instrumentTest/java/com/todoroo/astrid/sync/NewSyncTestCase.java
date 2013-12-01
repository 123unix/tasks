package com.todoroo.astrid.sync;

import com.todoroo.andlib.service.Autowired;
import com.todoroo.astrid.dao.TagDataDao;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.test.DatabaseTestCase;

public class NewSyncTestCase extends DatabaseTestCase {
	@Autowired
	protected TaskDao taskDao;
	@Autowired
	protected TagDataDao tagDataDao;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	protected Task createTask(String title) {
		Task task = new Task();
		task.setValue(Task.TITLE, title);
		task.setValue(Task.IMPORTANCE, SYNC_TASK_IMPORTANCE);

        taskDao.createNew(task);
		return task;
	}

	public static final String SYNC_TASK_TITLE = "new title";
	public static final int SYNC_TASK_IMPORTANCE = Task.IMPORTANCE_MUST_DO;

	protected Task createTask() {
		return createTask(SYNC_TASK_TITLE);
	}

	protected TagData createTagData(String name) {
		TagData tag = new TagData();
		tag.setValue(TagData.NAME, name);

		tagDataDao.createNew(tag);
		return tag;
	}

	protected TagData createTagData() {
		return createTagData("new tag");
	}
}
