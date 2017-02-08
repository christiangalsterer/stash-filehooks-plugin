package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.AbstractChangeCallback;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.GitScmConfig;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.setting.RepositorySettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.atlassian.fugue.Pair;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.*;

/**
 * Checks the name and path of a file in the pre-receive phase and rejects the push when the changeset contains files which match the configured file name pattern.
 */
public class FileNameHook implements PreReceiveRepositoryHook, RepositorySettingsValidator, RepositoryMergeRequestCheck {

    private static final String SETTINGS_INCLUDE_PATTERN = "pattern";
    private static final String SETTINGS_EXCLUDE_PATTERN = "pattern-exclude";
    private static final String SETTINGS_BRANCHES_PATTERN = "pattern-branches";
    private static final String TAG_REF = "refs/tags/";

    private final DiffTreeService diffTreeService;
    private final I18nService i18n;
    private final CommitService commitService;
    private final MergeBaseResolver mergeBaseResolver;

    public FileNameHook(GitCommandBuilderFactory builderFactory, CommitService commitService,
        DiffTreeService diffTreeService, I18nService i18n, GitScmConfig gitScmConfig) {
        this.diffTreeService = diffTreeService;
        this.i18n = i18n;
        this.commitService = commitService;
        this.mergeBaseResolver = new MergeBaseResolver(builderFactory, gitScmConfig, commitService);
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges,
        @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        FileNameHookSetting setting = getSettings(context.getSettings());
        Optional<Pattern> branchesPattern = setting.getBranchesPattern();

        Collection<RefChange> filteredRefChanges;

        if (branchesPattern.isPresent()) {
            filteredRefChanges = refChanges.stream().filter(REF_CHANGE_NOT_DELETED)
                .filter(refChange -> !refChange.getRef().getId().startsWith(TAG_REF))
                .filter(filterBranchesPredicate(branchesPattern.get())).collect(Collectors.toList());
        } else {
            filteredRefChanges = refChanges.stream().filter(REF_CHANGE_NOT_DELETED).filter(REF_CHANGE_IS_NOT_TAG)
                .collect(Collectors.toList());
        }

        Iterable<Pair<String, String>> changedFiles = diffTreeService.getChangedFiles(filteredRefChanges, repository);

        Collection<String> filteredPaths = StreamSupport.stream(changedFiles.spliterator(), false).map(Pair::right)
            .filter(setting.getIncludePattern().asPredicate()).collect(Collectors.toList());

        if (setting.getExcludePattern().isPresent()) {
            filteredPaths = filteredPaths.stream().filter(setting.getExcludePattern().get().asPredicate().negate())
                .collect(Collectors.toList());
        }

        if (filteredPaths.size() > 0) {
            hookResponse.out().println("=================================");
            for (String path : filteredPaths) {
                String msg;
                if (branchesPattern.isPresent()) {
                    msg = String.format("File [%s] violates file name pattern [%s] for branch [%s].", path,
                        setting.getIncludePattern().pattern(), branchesPattern.get());
                } else {
                    msg = String
                        .format("File [%s] violates file name pattern [%s].", path, setting.getIncludePattern().pattern());
                }
                hookResponse.out().println(msg);
            }
            hookResponse.out().println("=================================");
            return false;
        }
        return true;
    }

    private FileNameHookSetting getSettings(Settings settings) {
        String includeRegex = settings.getString(SETTINGS_INCLUDE_PATTERN);
        String excludeRegex = settings.getString(SETTINGS_EXCLUDE_PATTERN);
        String branchesRegex = settings.getString(SETTINGS_BRANCHES_PATTERN);

        return new FileNameHookSetting(includeRegex, excludeRegex, branchesRegex);
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {

        if (Strings.isNullOrEmpty(settings.getString(SETTINGS_INCLUDE_PATTERN))) {
            errors.addFieldError(SETTINGS_INCLUDE_PATTERN,
                i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
        } else {
            try {
                Pattern.compile(settings.getString(SETTINGS_INCLUDE_PATTERN, ""));
            } catch (PatternSyntaxException e) {
                errors.addFieldError(SETTINGS_INCLUDE_PATTERN,
                    i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }

        if (!Strings.isNullOrEmpty(settings.getString(SETTINGS_EXCLUDE_PATTERN))) {
            try {
                Pattern.compile(settings.getString(SETTINGS_EXCLUDE_PATTERN));
            } catch (PatternSyntaxException e) {
                errors.addFieldError(SETTINGS_EXCLUDE_PATTERN,
                    i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }

        if (!Strings.isNullOrEmpty(settings.getString(SETTINGS_BRANCHES_PATTERN))) {
            try {
                Pattern.compile(settings.getString(SETTINGS_BRANCHES_PATTERN));
            } catch (PatternSyntaxException e) {
                errors.addFieldError(SETTINGS_BRANCHES_PATTERN,
                    i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }
    }

    private String getPullRequestError(Collection<String> filteredFiles) {
        return filteredFiles.stream().collect(Collectors.joining(", "));
    }

    @Override
    public void check(RepositoryMergeRequestCheckContext context) {
        final MergeRequest request = context.getMergeRequest();
        final PullRequest pullRequest = request.getPullRequest();
        final Commit pullRequestFrom = getChangeSet(pullRequest.getFromRef());
        final Commit pullRequestTo = getChangeSet(pullRequest.getToRef());
        final Commit base = mergeBaseResolver.findMergeBase(pullRequestFrom, pullRequestTo);
        final FileNameHookSetting setting = getSettings(context.getSettings());

        final ChangesRequest pathsRequest =
            new ChangesRequest.Builder(pullRequestFrom.getRepository(), pullRequestFrom.getId()).sinceId(base.getId())
                .build();

        final ChangedPathsCollector pathsCallback = new ChangedPathsCollector();

        commitService.streamChanges(pathsRequest, pathsCallback);
        Collection<String> filteredFiles =
            pathsCallback.getChangedPaths().stream().filter(setting.getIncludePattern().asPredicate())
                .collect(Collectors.toList());

        if (setting.getExcludePattern().isPresent()) {
            filteredFiles = filteredFiles.stream().filter(setting.getExcludePattern().get().asPredicate().negate())
                .collect(Collectors.toList());
        }

        if (filteredFiles.size() > 0) {
            request.veto(i18n.getText("filename-hook.mergecheck.veto",
                "File Name Hook: The following files violate the file name pattern [{0}]:",
                setting.getIncludePattern().pattern()), getPullRequestError(filteredFiles));
        }
    }

    private Commit getChangeSet(PullRequestRef pullRequestRef) {
        final CommitRequest.Builder builder =
            new CommitRequest.Builder(pullRequestRef.getRepository(), pullRequestRef.getLatestCommit());
        return commitService.getCommit(builder.build());
    }

    /**
     * Callback, collecting all the paths, changed in the requested change
     * range.
     */
    private static class ChangedPathsCollector extends AbstractChangeCallback {

        private final Collection<String> changedPaths = new HashSet<String>();

        @Override
        public boolean onChange(Change change) throws IOException {
            if (change.getType() != ChangeType.DELETE) {
                changedPaths.add(change.getPath().toString());
            }
            return true;
        }

        public Collection<String> getChangedPaths() {
            return changedPaths;
        }

    }
}
