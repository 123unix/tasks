/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.gtasks;

import android.content.Intent;
import android.os.Bundle;

import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.astrid.gtasks.auth.GtasksLoginActivity;
import com.todoroo.astrid.gtasks.sync.GtasksSyncV2Provider;
import com.todoroo.astrid.sync.SyncProviderPreferences;
import com.todoroo.astrid.sync.SyncProviderUtilities;
import com.todoroo.astrid.tags.TagService;

import org.astrid.R;

/**
 * Displays synchronization preferences and an action panel so users can
 * initiate actions from the menu.
 *
 * @author Tim Su <tim@todoroo.com>
 */
public class GtasksPreferences extends SyncProviderPreferences {

    @Autowired
    private GtasksPreferenceService gtasksPreferenceService;

    @Autowired
    private TagService tagService;

    public GtasksPreferences() {
        DependencyInjectionService.getInstance().inject(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public int getPreferenceResource() {
        return R.xml.preferences_gtasks;
    }

    @Override
    public void startSync() {
        if (!gtasksPreferenceService.isLoggedIn()) {
            startLogin();
        } else {
            syncOrImport();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK) {
            syncOrImport();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void syncOrImport() {
        setResultForSynchronize();
    }

    private void setResultForSynchronize() {
        setResult(RESULT_CODE_SYNCHRONIZE);
        finish();
    }

    private void startLogin() {
        Intent intent = new Intent(this, GtasksLoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    @Override
    public void logOut() {
        GtasksSyncV2Provider.getInstance().signOut(this);
    }

    @Override
    public SyncProviderUtilities getUtilities() {
        return gtasksPreferenceService;
    }

    @Override
    protected void onPause() {
        super.onPause();
        new GtasksBackgroundService().scheduleService();
    }
}
