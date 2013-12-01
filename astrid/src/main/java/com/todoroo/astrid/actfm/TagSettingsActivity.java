/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.actfm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.andlib.utility.AndroidUtilities;
import com.todoroo.andlib.utility.Preferences;
import com.todoroo.astrid.actfm.ActFmCameraModule.CameraResultCallback;
import com.todoroo.astrid.dao.TagMetadataDao;
import com.todoroo.astrid.data.RemoteModel;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.helper.UUIDHelper;
import com.todoroo.astrid.service.TagDataService;
import com.todoroo.astrid.service.ThemeService;
import com.todoroo.astrid.tags.TagFilterExposer;
import com.todoroo.astrid.tags.TagService;
import com.todoroo.astrid.utility.AstridPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tasks.R;

import static android.support.v4.view.MenuItemCompat.setShowAsAction;

public class TagSettingsActivity extends ActionBarActivity {

    public static final String TOKEN_NEW_FILTER = "newFilter"; //$NON-NLS-1$

    private static final int MENU_SAVE_ID = R.string.TEA_menu_save;
    private static final int MENU_DISCARD_ID = R.string.TEA_menu_discard;

    public static final int REQUEST_ACTFM_LOGIN = 3;

    public static final String TOKEN_AUTOPOPULATE_MEMBERS = "autopopulateMembers"; //$NON-NLS-1$

    public static final String TOKEN_AUTOPOPULATE_NAME = "autopopulateName"; //$NON-NLS-1$

    private TagData tagData;

    @Autowired TagService tagService;

    @Autowired TagDataService tagDataService;

    @Autowired TagMetadataDao tagMetadataDao;

    private EditText tagName;
    private Bitmap setBitmap;

    private boolean isNewTag = false;
    private boolean isDialog;

    public TagSettingsActivity() {
        DependencyInjectionService.getInstance().inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setupForDialogOrFullscreen();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_settings_activity);

        if (isDialog) {
            LayoutParams params = getWindow().getAttributes();
            params.width = LayoutParams.FILL_PARENT;
            params.height = LayoutParams.WRAP_CONTENT;

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            if ((metrics.widthPixels / metrics.density) >= AndroidUtilities.MIN_TABLET_HEIGHT) {
                params.width = (3 * metrics.widthPixels) / 5;
            } else if ((metrics.widthPixels / metrics.density) >= AndroidUtilities.MIN_TABLET_WIDTH) {
                params.width = (4 * metrics.widthPixels) / 5;
            }
            getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
        }

        tagData = getIntent().getParcelableExtra(TagViewFragment.EXTRA_TAG_DATA);
        if (tagData == null) {
            isNewTag = true;
            tagData = new TagData();
            tagData.setValue(TagData.UUID, UUIDHelper.newUUID());
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setUpSettingsPage();
    }

    private void setupForDialogOrFullscreen() {
        isDialog = AstridPreferences.useTabletLayout(this);
        if (isDialog) {
            setTheme(ThemeService.getDialogTheme());
            if (AndroidUtilities.getSdkVersion() < 14) {
                requestWindowFeature(Window.FEATURE_NO_TITLE);
            }
        } else {
            ThemeService.applyTheme(this);
        }
    }

    protected void setUpSettingsPage() {
        if (isDialog) {
            findViewById(R.id.save_and_cancel).setVisibility(View.VISIBLE);
            findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            findViewById(R.id.save).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveSettings();
                }
            });
        }

        tagName = (EditText) findViewById(R.id.tag_name);

        refreshSettingsPage();

        String autopopulateMembers = getIntent().getStringExtra(TOKEN_AUTOPOPULATE_MEMBERS);
        if (!TextUtils.isEmpty(autopopulateMembers)) {
            getIntent().removeExtra(TOKEN_AUTOPOPULATE_MEMBERS);
        }

        String autopopulateName = getIntent().getStringExtra(TOKEN_AUTOPOPULATE_NAME);
        if (!TextUtils.isEmpty(autopopulateName)) {
            tagName.setText(autopopulateName);
            getIntent().removeExtra(TOKEN_AUTOPOPULATE_NAME);
        }
    }

    private void saveSettings() {
        String oldName = tagData.getValue(TagData.NAME);
        String newName = tagName.getText().toString().trim();

        if (TextUtils.isEmpty(newName)) {
            return;
        }

        boolean nameChanged = !oldName.equals(newName);
        TagService service = TagService.getInstance();
        if (nameChanged) {
            if (oldName.equalsIgnoreCase(newName)) { // Change the capitalization of a list manually
                tagData.setValue(TagData.NAME, newName);
                service.rename(tagData.getUuid(), newName);
            } else { // Rename list--check for existing name
                newName = service.getTagWithCase(newName);
                tagName.setText(newName);
                if (!newName.equals(oldName)) {
                    tagData.setValue(TagData.NAME, newName);
                    service.rename(tagData.getUuid(), newName);
                }
            }
        }

        if (setBitmap != null) {
            JSONObject pictureJson = RemoteModel.PictureHelper.savePictureJson(this, setBitmap);
            if (pictureJson != null) {
                tagData.setValue(TagData.PICTURE, pictureJson.toString());
            }
        }

        JSONArray members = new JSONArray();

        tagData.setValue(TagData.MEMBER_COUNT, members.length());

        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(tagName.getWindowToken(), 0);

        tagDataService.save(tagData);
        tagMetadataDao.synchronizeMembers(tagData, tagData.getValue(TagData.MEMBERS), tagData.getUuid(), members);

        if (isNewTag) {
            setResult(RESULT_OK, new Intent().putExtra(TOKEN_NEW_FILTER,
                    TagFilterExposer.filterFromTagData(TagSettingsActivity.this, tagData)));
        } else {
            setResult(RESULT_OK);
        }

        refreshSettingsPage();
        finish();
    }

    private void saveTagPictureLocally(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        try {
            String tagPicture = RemoteModel.PictureHelper.getPictureHash(tagData);
            tagData.setValue(TagData.PICTURE, tagPicture);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void finish() {
        finishWithAnimation(!isDialog);
    }

    private void finishWithAnimation(boolean backAnimation) {
        super.finish();
        if (backAnimation) {
            AndroidUtilities.callOverridePendingTransition(this, R.anim.slide_right_in, R.anim.slide_right_out);
        }
    }

    private void refreshSettingsPage() {
        tagName.setText(tagData.getValue(TagData.NAME));
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            if (isNewTag) {
                ab.setTitle(getString(R.string.tag_new_list));
            } else {
                ab.setTitle(getString(R.string.tag_settings_title));
            }
        } else {
            if (isNewTag) {
                setTitle(getString(R.string.tag_new_list));
            } else {
                setTitle(getString(R.string.tag_settings_title));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        CameraResultCallback callback = new CameraResultCallback() {
            @Override
            public void handleCameraResult(Bitmap bitmap) {
                setBitmap = bitmap;
                saveTagPictureLocally(bitmap);
            }
        };
        if (!ActFmCameraModule.activityResult(this, requestCode, resultCode, data, callback)) {
            if(requestCode == REQUEST_ACTFM_LOGIN && resultCode == Activity.RESULT_OK) {
                saveSettings();
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        if (Preferences.getBoolean(R.string.p_save_and_cancel, false)) {
            item = menu.add(Menu.NONE, MENU_DISCARD_ID, 0, R.string.TEA_menu_discard);
            item.setIcon(ThemeService.getDrawable(R.drawable.ic_action_cancel));
            setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        if (isDialog) {
            item = menu.add(Menu.NONE, MENU_SAVE_ID, 0, R.string.TEA_menu_save);
            item.setIcon(ThemeService.getDrawable(R.drawable.ic_action_save));
            setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (tagName.getText().length() == 0) {
            finish();
        } else {
            saveSettings();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case MENU_DISCARD_ID:
            finish();
            break;
        case MENU_SAVE_ID:
            saveSettings();
            break;
        case android.R.id.home:
            saveSettings();
            if (!isFinishing()) {
                finish();
            }
            break;
        }
        return super.onOptionsItemSelected(item);
    }
}
