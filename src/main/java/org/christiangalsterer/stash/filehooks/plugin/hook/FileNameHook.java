package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.AbstractChangeCallback;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.google.common.base.Strings;

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

/**
public class FileNameHook implements PreReceiveRepositoryHook, RepositorySettingsValidator, RepositoryMergeRequestCheck {

    private static final String SETTINGS_INCLUDE_PATTERN = "pattern";
    private static final String SETTINGS_EXCLUDE_PATTERN = "pattern-exclude";
    private static final String SETTINGS_BRANCHES_PATTERN = "pattern-branches";

    private final ChangesetService changesetService;
    private final I18nService i18n;
    private final CommitService commitService;
    private final MergeBaseResolver mergeBaseResolver;

    public FileNameHook(GitCommandBuilderFactory builderFactory, CommitService commitService, ChangesetService changesetService, I18nService i18n, GitScmConfig gitScmConfig) {
        this.changesetService = changesetService;
        this.i18n = i18n;
        this.commitService = commitService;
        this.mergeBaseResolver = new MergeBaseResolver(builderFactory, gitScmConfig, commitService);
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        FileNameHookSetting setting = getSettings(context.getSettings());
        Optional<Pattern> branchesPattern = setting.getBranchesPattern();

        Collection<RefChange> filteredRefChanges = refChanges.stream().filter(isNotDeleteRefChange).filter(isNotTagRefChange).collect(Collectors.toList());

        if(branchesPattern.isPresent()) {
            filteredRefChanges = filteredRefChanges.stream().filter(matchesBranchPattern(branchesPattern.get())).collect(Collectors.toList());
        }

        Iterable<Change> changes = changesetService.getChanges(filteredRefChanges, repository);

        Collection<String> filteredPaths = StreamSupport.stream(changes.spliterator(), false).filter(isNotDeleteChange).map(Functions.CHANGE_TO_PATH).filter(setting.getIncludePattern().asPredicate()).collect(Collectors.toList());

        if(setting.getExcludePattern().isPresent()) {
            Pattern excludePattern = setting.getExcludePattern().get();
            filteredPaths = filteredPaths.stream().filter(excludePattern.asPredicate().negate()).collect(Collectors.toList());
        }

        if (filteredPaths.size() > 0) {
            hookResponse.out().println("=================================");
            for (String path : filteredPaths) {
                String msg;
                if(branchesPattern.isPresent()) {
                    msg = String.format("File [%s] violates file name pattern [%s] for branch [%s].", path, setting.getIncludePattern().pattern(), branchesPattern.get());
                } else {
                    msg = String.format("File [%s] violates file name pattern [%s].", path, setting.getIncludePattern().pattern());
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

        if (!Strings.isNullOrEmpty(settings.getString(SETTINGS_BRANCHES_PATTERN))) {
            try {
                Pattern.compile(settings.getString(SETTINGS_BRANCHES_PATTERN));
            } catch (PatternSyntaxException e) {
                errors.addFieldError(SETTINGS_BRANCHES_PATTERN, i18n.getText("filename-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }
    }

    private static class ChangedPathsCollector extends AbstractChangeCallback {
        private final Collection<String> changedPaths = new HashSet<>();

        @Override
        public boolean onChange(Change change) throws IOException {
            if (change.getType() != ChangeType.DELETE) {
                changedPaths.add(change.getPath().toString());
            }
            return true;
        }

        Collection<String> getChangedPaths() {
            return changedPaths;
        }

    }

    private String getPullRequestError(Collection<String> filteredFiles) {
        final StringBuilder sb = new StringBuilder();
        final Iterator<String> iter = filteredFiles.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    @Override
    public void check(RepositoryMergeRequestCheckContext context) {
        final MergeRequest request = context.getMergeRequest();
        final PullRequest pr = request.getPullRequest();
        final Commit prFrom = getChangeSet(pr.getFromRef());
        final Commit prTo = getChangeSet(pr.getToRef());
        final Commit base = mergeBaseResolver.findMergeBase(prFrom, prTo);
        final FileNameHookSetting setting = getSettings(context.getSettings());

        final ChangesRequest.Builder builder = new ChangesRequest.Builder(prFrom.getRepository(), prFrom.getId());
        if (base.getId() != null) {
            builder.sinceId(base.getId());
        }
        final ChangesRequest pathsRequest = builder.build();
        final ChangedPathsCollector pathsCallback = new ChangedPathsCollector();
        commitService.streamChanges(pathsRequest, pathsCallback);
        Collection<String> filteredFiles = pathsCallback.getChangedPaths();
        filteredFiles = filteredFiles.stream().filter(setting.getIncludePattern().asPredicate()).collect(Collectors.toList());

        if(setting.getExcludePattern().isPresent()) {
            Pattern excludePattern = setting.getExcludePattern().get();
            filteredFiles = filteredFiles.stream().filter(excludePattern.asPredicate().negate()).collect(Collectors.toList());
        }

        if (filteredFiles.size() > 0) {
            request.veto(i18n.getText("filename-hook.mergecheck.veto", "File Name Hook: The following files violate the file name pattern [{0}]:", setting.getIncludePattern().pattern()), getPullRequestError(filteredFiles));
        }
    }

    private Commit getChangeSet(PullRequestRef prRef) {
        final CommitRequest.Builder builder = new CommitRequest.Builder(prRef.getRepository(), prRef.getLatestCommit());
        return commitService.getCommit(builder.build());
    }
}

*/