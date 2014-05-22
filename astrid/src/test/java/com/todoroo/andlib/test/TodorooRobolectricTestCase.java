/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.andlib.test;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.andlib.service.RobolectricTestDependencyInjector;
import com.todoroo.astrid.service.AstridDependencyInjector;

import org.junit.Before;
import org.robolectric.Robolectric;
import org.tasks.Broadcaster;

import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.tasks.TestUtilities.resetPreferences;

public class TodorooRobolectricTestCase {

    private RobolectricTestDependencyInjector testInjector;

    protected <T> T addInjectable(String name, T object) {
        testInjector.addInjectable(name, object);
        return object;
    }

    @Before
    public void before() {
        ContextManager.setContext(getRobolectricContext());
        resetPreferences();
        AstridDependencyInjector.reset();
        testInjector = RobolectricTestDependencyInjector.initialize("test");
        addInjectable("broadcaster", mock(Broadcaster.class));
        DependencyInjectionService.getInstance().inject(this);
    }

    /**
     * Sets locale
     */
    private static void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        DisplayMetrics metrics = getRobolectricContext().getResources().getDisplayMetrics();
        getRobolectricContext().getResources().updateConfiguration(config, metrics);
    }

    public static Context getRobolectricContext() {
        return Robolectric.getShadowApplication().getApplicationContext();
    }
}
