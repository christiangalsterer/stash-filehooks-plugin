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
import com.atlassian.fugue.Pair;
import com.google.common.collect.Iterables;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.collect.Iterables.addAll;
import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.*;

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
        Instant start = Instant.now();
        hookResponse.out().format("Hook start: %s\n", start);

        Repository repository = context.getRepository();
        List<FileSizeHookSetting> settings = getSettings(context.getSettings());

        Map<RefChange, Iterable<Commit>> commitsByRefChange = new HashMap<>();
        Map<Commit, Iterable<Change>> changesByCommit = new HashMap<>();

        Map<Long, Collection<String>> pathAndSizes = new HashMap<>();

        for (FileSizeHookSetting setting : settings) {
            Collection<String> violatingPaths = new ArrayList<>();
            Pattern includePattern = setting.getIncludePattern();
            Long maxFileSize = setting.getSize();
            Optional<Pattern> branchesPattern = setting.getBranchesPattern();

            hookResponse.out().format("Setting [%s]: %d ms\n", includePattern,
                    Duration.between(start, Instant.now()).toMillis());

            Stream<RefChange> filteredRefChanges = refChanges.stream()
                    .filter(isNotDeleteRefChange)
                    .filter(isNotTagRefChange);

            if (branchesPattern.isPresent()) {
                filteredRefChanges = filteredRefChanges
                        .filter(matchesBranchPattern(branchesPattern.get()));
            }

            hookResponse.out().format("Filtered changes: %d ms\n",
                    Duration.between(start, Instant.now()).toMillis());

            Set<Commit> commits = filteredRefChanges.flatMap(refChange -> {
                        hookResponse.out().format("Getting %s: %d ms\n", refChange.getRef().getId(),
                                Duration.between(start, Instant.now()).toMillis());
                        return StreamSupport.stream(
                                commitsByRefChange.computeIfAbsent(
                                        refChange,
                                        x -> {
                                            hookResponse.out().format("Getting commits: %d ms\n",
                                                    Duration.between(start, Instant.now()).toMillis());
                                            Iterable<Commit> refCommits = changesetService.getCommitsBetween(repository, x);
                                            hookResponse.out().format("Got commits: %d ms\n",
                                                    Duration.between(start, Instant.now()).toMillis());
                                            return refCommits;
                                        }
                                ).spliterator(), false);
                    }
            ).collect(Collectors.toSet());

            hookResponse.out().format("Unique commits: %d: %d ms\n",
                    commits.size(), Duration.between(start, Instant.now()).toMillis());

            Set<Commit> commitsToCheck = commits.stream()
                    .filter(commit -> !changesByCommit.containsKey(commit))
                    .collect(Collectors.toSet());

            if (!commitsToCheck.isEmpty()) {
                changesByCommit.putAll(changesetService.getChanges(repository, commitsToCheck));
            }

            List<Change> filteredChanges = commits.stream()
                    .flatMap(commit -> {
                        Iterable<Change> changes = changesByCommit.get(commit);
                        hookResponse.out().format("Commit %s, changes %d: %d ms\n",
                                commit.getId(), Iterables.size(changes),
                                Duration.between(start, Instant.now()).toMillis());
                        return StreamSupport.stream(changes.spliterator(), false);
                    })
                    .filter(isNotDeleteChange)
                    .filter(change -> {
                        String fullPath = change.getPath().toString();
                        return includePattern.matcher(fullPath).find()
                                && (!setting.getExcludePattern().isPresent()
                                || !setting.getExcludePattern().get().matcher(fullPath).find());
                    })
                    .collect(Collectors.toList());

            hookResponse.out().format("Filtered changes: %d: %d ms\n",
                    filteredChanges.size(), Duration.between(start, Instant.now()).toMillis());

            List<String> filteredPaths =
                    StreamSupport.stream(zipWithSize(repository, filteredChanges).spliterator(), false)
                            .filter(p -> p.right() > maxFileSize)
                            .map(p -> p.left().getPath().toString())
                            .collect(Collectors.toList());

            hookResponse.out().format("Filtered paths %d: %d ms\n",
                    filteredPaths.size(), Duration.between(start, Instant.now()).toMillis());

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
                hookResponse.out().println("=================================");
                for (String path : paths) {
                    hookResponse.out().println(String.format("File [%s] is too large. Maximum allowed file size is %s bytes.", path, maxFileSize));
                }
                hookResponse.out().println("=================================");
            }
        }

        Instant finish = Instant.now();
        hookResponse.out().format("Hook finish: %s\n", finish);
        hookResponse.out().format("Elapsed: %d ms\n", Duration.between(start, finish).toMillis());

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

    private Iterable<Pair<Change, Long>> zipWithSize(Repository repository, Iterable<Change> changes) {
        Map<String, List<Change>> contentIdsToChanges = new HashMap<>();
        for (Change change : changes) {
            contentIdsToChanges.computeIfAbsent(change.getContentId(), c -> new ArrayList<>()).add(change);
        }

        CatFileBatchCheckHandler handler = new CatFileBatchCheckHandler(contentIdsToChanges.keySet());
        Command<List<Pair<String, Long>>> cmd = commandFactory.builder(repository).command("cat-file").argument("--batch-check").inputHandler(handler).build(handler);

        return cmd.call().stream()
                .map(p -> Pair.pair(contentIdsToChanges.get(p.left()), p.right()))
                .flatMap(i -> i.left().stream().map(c -> Pair.pair(c, i.right())))
                .collect(Collectors.toList());
    }
}
