package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.fugue.Pair;
import com.atlassian.bitbucket.content.*;
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
import java.util.stream.StreamSupport;

import static com.google.common.collect.Iterables.*;
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
        Repository repository = context.getRepository();
        List<FileSizeHookSetting> settings = getSettings(context.getSettings());
        Optional<Pattern> branchesPattern = Optional.empty();

        Map<Long, Collection<String>> pathAndSizes = new HashMap<>();

        for (FileSizeHookSetting setting : settings) {
            Collection<String> violatingPaths = new ArrayList<>();
            Pattern includePattern = setting.getIncludePattern();
            Long maxFileSize = setting.getSize();
            branchesPattern = setting.getBranchesPattern();

            Collection<RefChange> filteredRefChanges = refChanges.stream().filter(isNotDeleteRefChange).filter(isNotTagRefChange).collect(Collectors.toList());

            if(branchesPattern.isPresent()) {
                filteredRefChanges = filteredRefChanges.stream().filter(matchesBranchPattern(branchesPattern.get())).collect(Collectors.toList());
            }

            Iterable<String> filteredPaths = StreamSupport.stream(getChanges(repository, filteredRefChanges).spliterator(), false).filter(p -> p.right() > maxFileSize).filter(p -> p.left().getPath().toString().matches(includePattern.pattern())).map(p -> p.left().getPath().toString()).collect(Collectors.toList());

            if (setting.getExcludePattern().isPresent())
                filteredPaths = StreamSupport.stream(filteredPaths.spliterator(), false).filter(setting.getExcludePattern().get().asPredicate().negate()).collect(Collectors.toList());

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

        return hookPassed;
    }

    private List<FileSizeHookSetting> getSettings(Settings settings) {
        List<FileSizeHookSetting> configurations = new ArrayList<>();
        String includeRegex;
        Long size;
        String excludeRegex;
        String branchesRegex;

        for (int i = 1; i <= MAX_SETTINGS ; i++) {
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

    private Iterable<Pair<Change, Long>> getChanges(Repository repository, Iterable<RefChange> refChanges) {
        List<Change> changes = StreamSupport.stream(changesetService.getChanges(refChanges, repository).spliterator(), false).filter(isNotDeleteChange).collect(Collectors.toList());
        return zipWithSize(changes, repository);
    }

    private Iterable<Pair<Change, Long>> zipWithSize(Iterable<Change> changes, Repository repository) {
        Map<String, List<Change>> contentIdsToChanges = new HashMap<>();
        for (Change change : changes) {
            contentIdsToChanges.computeIfAbsent(change.getContentId(), c -> new ArrayList<>()).add(change);
        }

        CatFileBatchCheckHandler handler = new CatFileBatchCheckHandler(contentIdsToChanges.keySet());
        Command<List<Pair<String, Long>>> cmd = commandFactory.builder(repository).command("cat-file").argument("--batch-check").inputHandler(handler).build(handler);

        return cmd.call().stream().map(p -> Pair.pair(contentIdsToChanges.get(p.left()), p.right())).collect(Collectors.toList()).stream().flatMap(i -> i.left().stream().map(c -> Pair.pair(c, i.right()))).collect(Collectors.toList());
    }
}
