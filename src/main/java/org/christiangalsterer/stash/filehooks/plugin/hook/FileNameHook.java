package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.AbstractChangeCallback;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PostRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PostRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeCheck;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.Scm;
import com.atlassian.bitbucket.scm.ScmCommandBuilder;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.setting.SettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.google.common.base.Strings;
import com.google.common.collect.*;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.*;

/**
 * Checks the name and path of a file in the pre-receive phase and rejects the push when the changeset contains files which match the configured file name pattern.
 */
public class FileNameHook implements PostRepositoryHook<RepositoryHookRequest> {

    private static final Logger log = LoggerFactory.getLogger(LoggingPostRepositoryHook.class);

    @Override
    public void postUpdate(@Nonnull PostRepositoryHookContext context,
                           @Nonnull RepositoryHookRequest hookRequest) {
        log.info("[{}] {} updated [{}]",
                hookRequest.getRepository(),
                hookRequest.getTrigger().getId(),
                hookRequest.getRefChanges().stream()
                        .map(change -> change.getRef().getId())
                        .collect(Collectors.joining(", ")));
    }
}
