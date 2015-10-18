package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.Settings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.hamcrest.core.Is.is;


public class FileNameHookTest {

    @Mock
    private ChangesetService changesetService;

    @Mock
    private I18nService i18n;

    @Mock
    private Repository repository;

    @Mock
    private Settings settings;

    private FileNameHook fileNameHook;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        fileNameHook = new FileNameHook(changesetService, i18n);
    }

    @Test
    public void validPush() throws Exception {
        assertThat(push(), is(Boolean.TRUE));
    }

    @Test
    public void invalidPush() throws Exception {
        assertThat(push(), is(Boolean.TRUE));
    }

    @Test
    public void testValidate() throws Exception {

    }

    private boolean push() {
        return true;
    }
}