/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.andlib.test;

import android.content.res.Configuration;
import android.test.AndroidTestCase;
import android.util.DisplayMetrics;

import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.astrid.service.AstridDependencyInjector;

import java.util.Locale;

/**
 * Base test case for Astrid tests
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class TodorooTestCase extends AndroidTestCase {

    static {
        AstridDependencyInjector.initialize();
    }

	@Override
	protected void setUp() throws Exception {
	    super.setUp();

	    ContextManager.setContext(this.getContext());
	    AstridDependencyInjector.flush();
	    DependencyInjectionService.getInstance().inject(this);
	    setLocale(Locale.ENGLISH);
	}

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        setLocale(Locale.getDefault());
    }

    /**
     * Sets locale
     */
    private void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        getContext().getResources().updateConfiguration(config, metrics);
    }

}
