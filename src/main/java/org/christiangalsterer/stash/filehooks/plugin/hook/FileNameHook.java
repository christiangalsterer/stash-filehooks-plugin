package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.fugue.Pair;
import com.atlassian.bitbucket.content.*;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.PluginCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.setting.RepositorySettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

/**
 * Checks the name and path of a file in the pre-receive phase and rejects the push when the changeset contains files which match the configured file name pattern.
 */
public class FileNameHook implements PreReceiveRepositoryHook, RepositorySettingsValidator {

    private static final String SETTINGS_PATTERN = "pattern";

    private final ChangesetService changesetService;

    public FileNameHook(ChangesetService changesetService) {
        this.changesetService = changesetService;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        Pattern pattern = Pattern.compile(context.getSettings().getString(SETTINGS_PATTERN));

        Collection<String> paths = new ArrayList<String>();
        addAll(paths, filter(transform(getChanges(repository, filter(refChanges, org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.isNotDeleteChange)), Functions.CHANGE_TO_PATH), Predicates.contains(pattern)));

        if (paths.size() > 0) {
            hookResponse.out().println("=================================");
            for (String path : paths) {
                hookResponse.out().println(String.format("File [%s] violates file name pattern [%s].", path, pattern.pattern()));
            }
            hookResponse.out().println("=================================");
            return false;
        }
        return true;
    }

    private Iterable<Change> getChanges(Repository repository, Iterable<RefChange> refChanges) {
        return Iterables.concat(changesetService.getChanges(refChanges, repository));
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {
        try {
            Pattern.compile(settings.getString(SETTINGS_PATTERN, ""));
        } catch (PatternSyntaxException e) {
            errors.addFieldError(SETTINGS_PATTERN, "Pattern is not a valid regular expression");
        }
    }
}
