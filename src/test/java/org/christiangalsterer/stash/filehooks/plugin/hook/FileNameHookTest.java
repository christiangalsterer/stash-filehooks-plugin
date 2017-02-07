package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.GitScmConfig;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.setting.Settings;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.hamcrest.core.Is.is;

public class FileNameHookTest {

    @Mock
    private DiffTreeService diffTreeService;

    @Mock
    private I18nService i18n;

    @Mock
    private Repository repository;

    @Mock
    private Settings settings;
    
    @Mock
    private GitCommandBuilderFactory builderFactory;
    
    @Mock
    private CommitService commitService;
    
    @Mock
    private GitScmConfig gitScmConfig;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
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