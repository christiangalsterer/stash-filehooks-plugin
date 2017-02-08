package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.CommandBuilderSupport;
import com.atlassian.bitbucket.scm.git.GitScmConfig;

public class GitUtils {

    private GitUtils() {
    }

    public static void setAlternateIfCrossRepository(CommandBuilderSupport<?> builder, Repository repository,
        Repository secondRepository, GitScmConfig config) {
        if (repository.getId() != secondRepository.getId()) {
            builder
                .environment("GIT_ALTERNATE_OBJECT_DIRECTORIES", config.getObjectsDir(secondRepository).getAbsolutePath());
        }
    }
}