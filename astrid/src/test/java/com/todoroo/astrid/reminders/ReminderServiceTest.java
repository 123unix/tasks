/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.reminders;

import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.test.TodorooRobolectricTestCase;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.reminders.ReminderService.AlarmScheduler;

import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.tasks.Freeze.freezeClock;
import static org.tasks.Freeze.thaw;
import static org.tasks.date.DateTimeUtils.newDate;

@RunWith(RobolectricTestRunner.class)
public class ReminderServiceTest extends TodorooRobolectricTestCase {

    ReminderService service;

    @Autowired
    TaskDao taskDao;

    @Override
    public void before() {
        super.before();
        service = ReminderService.getInstance();
        freezeClock();
    }

    @After
    public void after() throws Exception {
        service.clearInstance();
        thaw();
    }

    @Test
    public void testNoReminders() {
        service.setScheduler(new NoAlarmExpected());

        Task task = new Task();
        task.setTitle("water");
        task.setReminderFlags(0);
        task.setReminderPeriod(0L);
        taskDao.save(task);
        service.scheduleAlarm(task);
    }

    @Test
    public void testDueDates() {
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertEquals((long)task.getDueDate(), time);
                assertEquals(type, ReminderService.TYPE_DUE);
            }
        });

        // test due date in the past
        final Task task = new Task();
        task.setTitle("water");
        task.setDueDate(DateUtilities.now() - DateUtilities.ONE_DAY);
        task.setReminderFlags(Task.NOTIFY_AT_DEADLINE);
        taskDao.save(task);

        // test due date in the future
        task.setDueDate(DateUtilities.now() + DateUtilities.ONE_DAY);
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);
    }

    @Test
    @Ignore
    public void testRandom() {
        // test random
        final Task task = new Task();
        task.setTitle("water");
        task.setReminderPeriod(DateUtilities.ONE_WEEK);
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > DateUtilities.now());
                assertTrue(time < DateUtilities.now() + 1.2 * DateUtilities.ONE_WEEK);
                assertEquals(type, ReminderService.TYPE_RANDOM);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);
    }

    @Test
    public void testOverdue() {
        // test due date in the future
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > task.getDueDate());
                assertTrue(time < task.getDueDate() + DateUtilities.ONE_DAY);
                assertEquals(type, ReminderService.TYPE_OVERDUE);
            }
        });
        final Task task = new Task();
        task.setTitle("water");
        task.setDueDate(DateUtilities.now() + DateUtilities.ONE_DAY);
        task.setReminderFlags(Task.NOTIFY_AFTER_DEADLINE);
        taskDao.save(task);

        // test due date in the past
        task.setDueDate(DateUtilities.now() - DateUtilities.ONE_DAY);
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > DateUtilities.now() - 1000L);
                assertTrue(time < DateUtilities.now() + 2 * DateUtilities.ONE_DAY);
                assertEquals(type, ReminderService.TYPE_OVERDUE);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);

        // test due date in the past, but recently notified
        task.setReminderLast(DateUtilities.now());
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > DateUtilities.now() + DateUtilities.ONE_HOUR);
                assertTrue(time < DateUtilities.now() + DateUtilities.ONE_DAY);
                assertEquals(type, ReminderService.TYPE_OVERDUE);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);
    }

    @Test
    public void testMultipleReminders() {
        // test due date in the future, enable random
        final Task task = new Task();
        task.setTitle("water");
        task.setDueDate(DateUtilities.now() + DateUtilities.ONE_WEEK);
        task.setReminderFlags(Task.NOTIFY_AT_DEADLINE);
        task.setReminderPeriod(DateUtilities.ONE_HOUR);
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > DateUtilities.now());
                assertTrue(time < DateUtilities.now() + DateUtilities.ONE_DAY);
                assertEquals(type, ReminderService.TYPE_RANDOM);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);

        // now set the due date in the past
        task.setDueDate(DateUtilities.now() - DateUtilities.ONE_WEEK);
        ((AlarmExpected)service.getScheduler()).alarmCreated = false;
        service.scheduleAlarm(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);

        // now set the due date before the random
        task.setDueDate(DateUtilities.now() + DateUtilities.ONE_HOUR);
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertEquals((long)task.getDueDate(), time);
                assertEquals(type, ReminderService.TYPE_DUE);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);
    }

    @Test
    public void testSnoozeReminders() {
        thaw(); // TODO: get rid of this

        // test due date and snooze in the future
        final Task task = new Task();
        task.setTitle("spacemen");
        task.setDueDate(DateUtilities.now() + 5000L);
        task.setReminderFlags(Task.NOTIFY_AT_DEADLINE);
        task.setReminderSnooze(DateUtilities.now() + DateUtilities.ONE_WEEK);
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > DateUtilities.now() + DateUtilities.ONE_WEEK - 1000L);
                assertTrue(time < DateUtilities.now() + DateUtilities.ONE_WEEK + 1000L);
                assertEquals(type, ReminderService.TYPE_SNOOZE);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);

        // snooze in the past
        task.setReminderSnooze(DateUtilities.now() - DateUtilities.ONE_WEEK);
        service.setScheduler(new AlarmExpected() {
            @Override
            public void createAlarm(Task task, long time, int type) {
                if (time == ReminderService.NO_ALARM)
                    return;
                super.createAlarm(task, time, type);
                assertTrue(time > DateUtilities.now() - 1000L);
                assertTrue(time < DateUtilities.now() + 5000L);
                assertEquals(type, ReminderService.TYPE_DUE);
            }
        });
        taskDao.save(task);
        assertTrue(((AlarmExpected)service.getScheduler()).alarmCreated);
    }

    // --- helper classes

    public class NoAlarmExpected implements AlarmScheduler {
        public void createAlarm(Task task, long time, int type) {
            if(time == 0 || time == Long.MAX_VALUE)
                return;
            fail("created alarm, no alarm expected (" + type + ": " + newDate(time));
        }
    }

    public class AlarmExpected implements AlarmScheduler {
        public boolean alarmCreated = false;
        public void createAlarm(Task task, long time, int type) {
            alarmCreated = true;
        }
    }
}
