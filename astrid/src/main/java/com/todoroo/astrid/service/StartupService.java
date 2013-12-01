/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.util.Log;
import android.widget.Toast;

import com.todoroo.andlib.data.DatabaseDao.ModelUpdateListener;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.andlib.service.ExceptionService;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.Query;
import com.todoroo.andlib.utility.AndroidUtilities;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.andlib.utility.DialogUtilities;
import com.todoroo.andlib.utility.Preferences;
import com.todoroo.astrid.activity.BeastModePreferences;
import com.todoroo.astrid.backup.BackupConstants;
import com.todoroo.astrid.backup.BackupService;
import com.todoroo.astrid.backup.TasksXmlImporter;
import com.todoroo.astrid.core.PluginServices;
import com.todoroo.astrid.dao.Database;
import com.todoroo.astrid.dao.MetadataDao.MetadataCriteria;
import com.todoroo.astrid.dao.TagDataDao;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.gcal.CalendarStartupReceiver;
import com.todoroo.astrid.gtasks.GtasksPreferenceService;
import com.todoroo.astrid.gtasks.sync.GtasksSyncService;
import com.todoroo.astrid.reminders.ReminderStartupReceiver;
import com.todoroo.astrid.tags.TaskToTagMetadata;
import com.todoroo.astrid.utility.AstridPreferences;
import com.todoroo.astrid.utility.Constants;

import org.tasks.R;
import org.tasks.dropbox.DropBoxSyncManager;

import java.io.File;
import java.util.List;

import static org.tasks.widget.WidgetHelper.startWidgetService;

/**
 * Service which handles jobs that need to be run when Astrid starts up.
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class StartupService {


    static {
        AstridDependencyInjector.initialize();
    }

    public StartupService() {
        DependencyInjectionService.getInstance().inject(this);
    }

    // --- application startup

    @Autowired ExceptionService exceptionService;

    @Autowired UpgradeService upgradeService;

    @Autowired TaskService taskService;

    @Autowired TagDataDao tagDataDao;

    @Autowired MetadataService metadataService;

    @Autowired Database database;

    @Autowired GtasksPreferenceService gtasksPreferenceService;

    @Autowired GtasksSyncService gtasksSyncService;

    @Autowired
    private DropBoxSyncManager dropboxSyncManager;

    /**
     * bit to prevent multiple initializations
     */
    private static boolean hasStartedUp = false;

    /** Called when this application is started up */
    public synchronized void onStartupApplication(final Activity context) {
        if(hasStartedUp || context == null) {
            return;
        }

        // sets up context manager
        ContextManager.setContext(context);

        try {
            database.openForWriting();
            checkForMissingColumns();
        } catch (SQLiteException e) {
            handleSQLiteError(context, e);
            return;
        }

        // show notification if reminders are silenced
        if(context instanceof Activity) {
            AudioManager audioManager = (AudioManager)context.getSystemService(
                Context.AUDIO_SERVICE);
            if(!Preferences.getBoolean(R.string.p_rmd_enabled, true)) {
                Toast.makeText(context, R.string.TLA_notification_disabled, Toast.LENGTH_LONG).show();
            } else if(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) {
                Toast.makeText(context, R.string.TLA_notification_volume_low, Toast.LENGTH_LONG).show();
            }
        }

        // read current version
        int latestSetVersion = 0;
        try {
            latestSetVersion = AstridPreferences.getCurrentVersion();
        } catch (Exception e) {
            exceptionService.reportError("astrid-startup-version-read", e); //$NON-NLS-1$
        }

        if (latestSetVersion == 0) {
            if (Preferences.getLong(AstridPreferences.P_FIRST_LAUNCH, -1) < 0) {
                Preferences.setLong(AstridPreferences.P_FIRST_LAUNCH, DateUtilities.now());
            }
        }

        BeastModePreferences.assertHideUntilSectionExists(context, latestSetVersion);

        int version = 0;
        String versionName = "0"; //$NON-NLS-1$
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(Constants.PACKAGE, PackageManager.GET_META_DATA);
            version = pi.versionCode;
            versionName = pi.versionName;
        } catch (Exception e) {
            exceptionService.reportError("astrid-startup-package-read", e); //$NON-NLS-1$
        }

        Log.i("astrid", "Astrid Startup. " + latestSetVersion + //$NON-NLS-1$ //$NON-NLS-2$
                " => " + version); //$NON-NLS-1$

        databaseRestoreIfEmpty(context);

        // invoke upgrade service
        boolean justUpgraded = latestSetVersion != version;
        if(justUpgraded && version > 0) {
            if(latestSetVersion > 0) {
                upgradeService.performUpgrade(context, latestSetVersion);
            }
            AstridPreferences.setCurrentVersion(version);
            AstridPreferences.setCurrentVersionName(versionName);
        }

        final int finalLatestVersion = latestSetVersion;

        initializeDatabaseListeners();

        // perform startup activities in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                startWidgetService(context);

                taskService.cleanup();

                // if sync ongoing flag was set, clear it
                gtasksPreferenceService.stopOngoing();

                dropboxSyncManager.resumeLink(context);

                // perform initialization
                ReminderStartupReceiver.startReminderSchedulingService(context);
                BackupService.scheduleService(context);

                gtasksSyncService.initialize();

                // get and display update messages
                if (finalLatestVersion != 0) {
//                    new UpdateMessageService(context).processUpdates();
                }
            }
        }).start();

        AstridPreferences.setPreferenceDefaults();
        CalendarStartupReceiver.scheduleCalendarAlarms(context, false); // This needs to be after set preference defaults for the purposes of ab testing

        showTaskKillerHelp(context);

        hasStartedUp = true;
    }

    private void initializeDatabaseListeners() {
        // This listener makes sure that when a tag's name is created or changed,
        // the corresponding metadata will also update
        tagDataDao.addListener(new ModelUpdateListener<TagData>() {
            @Override
            public void onModelUpdated(TagData model) {
                ContentValues values = model.getSetValues();
                Metadata m = new Metadata();
                if (values != null) {
                    if (values.containsKey(TagData.NAME.name)) {
                        m.setValue(TaskToTagMetadata.TAG_NAME, model.getValue(TagData.NAME));
                        PluginServices.getMetadataService().update(Criterion.and(MetadataCriteria.withKey(TaskToTagMetadata.KEY),
                                TaskToTagMetadata.TAG_UUID.eq(model.getValue(TagData.UUID))), m);
                    }
                }
            }
        });
    }

    public static void handleSQLiteError(Context context, final SQLiteException e) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            DialogUtilities.okDialog(activity, activity.getString(R.string.DB_corrupted_title),
                    0, activity.getString(R.string.DB_corrupted_body));
        }
        e.printStackTrace();
    }

    private void checkForMissingColumns() {
        // For some reason these properties are missing for some users.
        // Make them exist!
        try {
            TodorooCursor<Task> tasks = taskService.query(Query.select(Task.UUID, Task.USER_ID).limit(1));
            try {
                System.err.println(tasks.getCount());
            } finally {
                tasks.close();
            }
        } catch (SQLiteException e) {
            database.tryAddColumn(Task.TABLE, Task.UUID, "'0'"); //$NON-NLS-1$
            database.tryAddColumn(Task.TABLE, Task.USER_ID, "0"); //$NON-NLS-1$
        }
    }

    /**
     * If database exists, no tasks but metadata, and a backup file exists, restore it
     */
    private void databaseRestoreIfEmpty(Context context) {
        try {
            if(AstridPreferences.getCurrentVersion() >= UpgradeService.V3_0_0 &&
                    !context.getDatabasePath(database.getName()).exists()) {
                // we didn't have a database! restore latest file
                File directory = BackupConstants.defaultExportDirectory();
                if(!directory.exists()) {
                    return;
                }
                File[] children = directory.listFiles();
                AndroidUtilities.sortFilesByDateDesc(children);
                if(children.length > 0) {
                    TasksXmlImporter.importTasks(context, children[0].getAbsolutePath(), null);
                }
            }
        } catch (Exception e) {
            Log.w("astrid-database-restore", e); //$NON-NLS-1$
        }
    }

    private static final String P_TASK_KILLER_HELP = "taskkiller"; //$NON-NLS-1$

    /**
     * Show task killer helper
     */
    private static void showTaskKillerHelp(final Context context) {
        if(!Preferences.getBoolean(P_TASK_KILLER_HELP, false)) {
            return;
        }

        // search for task killers. if they exist, show the help!
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> apps = pm
                .getInstalledPackages(PackageManager.GET_PERMISSIONS);
        outer: for (PackageInfo app : apps) {
            if(app == null || app.requestedPermissions == null) {
                continue;
            }
            if(app.packageName.startsWith("com.android")) //$NON-NLS-1$
            {
                continue;
            }
            for (String permission : app.requestedPermissions) {
                if (Manifest.permission.RESTART_PACKAGES.equals(permission)) {
                    CharSequence appName = app.applicationInfo.loadLabel(pm);
                    OnClickListener listener = new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0,
                                int arg1) {
                            Preferences.setBoolean(P_TASK_KILLER_HELP, true);
                        }
                    };

                    new AlertDialog.Builder(context)
                    .setTitle(R.string.DLG_information_title)
                    .setMessage(context.getString(R.string.task_killer_help,
                            appName))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.task_killer_help_ok, listener)
                    .show();

                    break outer;
                }
            }
        }
    }

}
