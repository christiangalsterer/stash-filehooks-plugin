package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.Command;
import com.atlassian.bitbucket.scm.PluginCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.setting.Settings;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Iterables.addAll;
import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.isNotDeleteChange;
import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.matchesBranchPattern;

/**
 * Checks the size of a file in the pre-receive phase and rejects the push when the changeset contains files which exceed the configured file size limit.
 */
public class FileSizeHook implements PreReceiveRepositoryHook {

    private static final int MAX_SETTINGS = 5;
    private static final String SETTINGS_INCLUDE_PATTERN_PREFIX = "pattern-";
    private static final String SETTINGS_EXCLUDE_PATTERN_PREFIX = "pattern-exclude-";
    private static final String SETTINGS_SIZE_PREFIX = "size-";
    private static final String SETTINGS_BRANCHES_PATTERN_PREFIX = "pattern-branches-";

    private final ChangesetService changesetService;
    private final PluginCommandBuilderFactory commandFactory;

    public FileSizeHook(ChangesetService changesetService, GitCommandBuilderFactory commandFactory) {
        this.changesetService = changesetService;
        this.commandFactory = commandFactory;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        List<FileSizeHookSetting> settings = getSettings(context.getSettings());

        FlatteningCachingResolver<Commit, Change> changesByCommit = new FlatteningCachingResolver<>();
        CachingResolver<String, Long> sizesByContentId = new CachingResolver<>();

        Map<Long, Collection<String>> pathAndSizes = new HashMap<>();

        for (FileSizeHookSetting setting : settings) {
            Collection<String> violatingPaths = new ArrayList<>();
            Pattern includePattern = setting.getIncludePattern();
            Long maxFileSize = setting.getSize();
            Optional<Pattern> branchesPattern = setting.getBranchesPattern();

            Stream<RefChange> filteredRefChanges = refChanges.stream();

            if (branchesPattern.isPresent()) {
                filteredRefChanges = filteredRefChanges
                        .filter(matchesBranchPattern(branchesPattern.get()));
            }

            Set<Commit> commits =
                    changesetService.getCommitsBetween(repository, filteredRefChanges.collect(Collectors.toSet()));

            Set<Change> filteredChanges =
                    changesByCommit.flatBatchResolve(commits, x -> changesetService.getChanges(repository, x)).stream()
                            .filter(isNotDeleteChange)
                            .filter(change -> {
                                String fullPath = change.getPath().toString();
                                return includePattern.matcher(fullPath).find()
                                        && (!setting.getExcludePattern().isPresent()
                                        || !setting.getExcludePattern().get().matcher(fullPath).find());
                            })
                            .collect(Collectors.toSet());

            // Pre-populate cache by resolving all required changes at once
            sizesByContentId.batchResolve(
                    filteredChanges.stream().map(Change::getContentId).collect(Collectors.toSet()),
                    contentIds -> getSizeForContentIds(repository, contentIds));

            List<String> filteredPaths = filteredChanges.stream()
                    .filter(change -> sizesByContentId.resolve(change.getContentId(), 0L) > maxFileSize)
                    .map(change -> change.getPath().toString())
                    .collect(Collectors.toList());

            addAll(violatingPaths, filteredPaths);

            if (pathAndSizes.containsKey(maxFileSize))
                addAll(violatingPaths, pathAndSizes.get(maxFileSize));

            pathAndSizes.put(maxFileSize, violatingPaths);
        }

        boolean hookPassed = true;

        for (Long maxFileSize : pathAndSizes.keySet()) {
            Collection<String> paths = pathAndSizes.get(maxFileSize);
            if (paths.size() > 0) {
                hookPassed = false;
                hookResponse.out().println("=== File Size Hook ===");
                hookResponse.out().println("");
                for (String path : paths) {
                    hookResponse.out().println(String.format("File [%s] is too large. Maximum allowed file size is %s bytes.", path, maxFileSize));
                }
                hookResponse.out().println("");
                hookResponse.out().println("You may to consider to use Git Large File Storage in Bitbucket, see https://confluence.atlassian.com/bitbucket/git-large-file-storage-in-bitbucket-829078514.html");
                hookResponse.out().println("======================");
            }
        }

        return hookPassed;
    }

    private List<FileSizeHookSetting> getSettings(Settings settings) {
        List<FileSizeHookSetting> configurations = new ArrayList<>();
        String includeRegex;
        Long size;
        String excludeRegex;
        String branchesRegex;

        for (int i = 1; i <= MAX_SETTINGS; i++) {
            includeRegex = settings.getString(SETTINGS_INCLUDE_PATTERN_PREFIX + i);
            if (includeRegex != null) {
                excludeRegex = settings.getString(SETTINGS_EXCLUDE_PATTERN_PREFIX + i);
                size = settings.getLong(SETTINGS_SIZE_PREFIX + i);
                branchesRegex = settings.getString(SETTINGS_BRANCHES_PATTERN_PREFIX + i);
                configurations.add(new FileSizeHookSetting(size, includeRegex, excludeRegex, branchesRegex));
            }
        }

        return configurations;
    }

    private Map<String, Long> getSizeForContentIds(final Repository repository, Iterable<String> contentIds) {
        CatFileBatchCheckHandler handler = new CatFileBatchCheckHandler(contentIds);
        Command<Map<String, Long>> cmd = commandFactory.builder(repository)
                .command("cat-file")
                .argument("--batch-check")
                .inputHandler(handler)
                .build(handler);
        return filterOutNullSizes(cmd.call());
    }

    private Map<String, Long> filterOutNullSizes(Map<String, Long> sizes) {
        return sizes.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
