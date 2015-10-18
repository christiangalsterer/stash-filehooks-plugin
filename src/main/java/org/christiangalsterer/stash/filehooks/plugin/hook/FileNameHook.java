package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.content.*;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.RepositorySettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

/**
 * Checks the name and path of a file in the pre-receive phase and rejects the push when the changeset contains files which match the configured file name pattern.
 */
public class FileNameHook implements PreReceiveRepositoryHook, RepositorySettingsValidator {

    private static final String SETTINGS_INCLUDE_PATTERN = "pattern";
    private static final String SETTINGS_EXCLUDE_PATTERN = "pattern-exclude";

    private final ChangesetService changesetService;
    private final I18nService i18n;

    public FileNameHook(ChangesetService changesetService, I18nService i18n) {
        this.changesetService = changesetService;
        this.i18n = i18n;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        FileNameHookSetting setting = getSettings(context.getSettings());

        Collection<String> paths = new ArrayList<String>();
        Iterable<String> filteredPaths = filter(transform(getChanges(repository, filter(refChanges, org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.isNotDeleteChange)), Functions.CHANGE_TO_PATH), Predicates.contains(setting.getIncludePattern()));

        if (setting.getExcludePattern().isPresent())
            filteredPaths = filter(filteredPaths, Predicates.not(Predicates.contains(setting.getExcludePattern().get())));

        addAll(paths, filteredPaths);

        if (paths.size() > 0) {
            hookResponse.out().println("=================================");
            for (String path : paths) {
                hookResponse.out().println(String.format("File [%s] violates file name pattern [%s].", path, setting.getIncludePattern().pattern()));
            }
            hookResponse.out().println("=================================");
            return false;
        }
        return true;
    }

    private Iterable<Change> getChanges(Repository repository, Iterable<RefChange> refChanges) {
        return Iterables.concat(changesetService.getChanges(refChanges, repository));
    }

    private FileNameHookSetting getSettings(Settings settings) {
        String includeRegex = settings.getString(SETTINGS_INCLUDE_PATTERN);
        String excludeRegex = settings.getString(SETTINGS_EXCLUDE_PATTERN);

        return new FileNameHookSetting(includeRegex, excludeRegex);
    }
    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {

        if (Strings.isNullOrEmpty(settings.getString(SETTINGS_INCLUDE_PATTERN))){
            errors.addFieldError(SETTINGS_INCLUDE_PATTERN, i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
        } else {
            try {
                Pattern.compile(settings.getString(SETTINGS_INCLUDE_PATTERN, ""));
            } catch (PatternSyntaxException e) {
                errors.addFieldError(SETTINGS_INCLUDE_PATTERN, i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }

        if (!Strings.isNullOrEmpty(settings.getString(SETTINGS_EXCLUDE_PATTERN))){
            try {
                Pattern.compile(settings.getString(SETTINGS_EXCLUDE_PATTERN));
            } catch (PatternSyntaxException e) {
                errors.addFieldError(SETTINGS_EXCLUDE_PATTERN, i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }
    }
}
