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
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.regex.Pattern;

import static com.google.common.base.Functions.compose;
import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
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

        Map<Long, Collection<String>> pathAndSizes = new HashMap<Long, Collection<String>>();

        for (FileSizeHookSetting setting : settings) {
            Collection<String> paths = new ArrayList<String>();
            Pattern includePattern = setting.getIncludePattern();
            Long maxFileSize = setting.getSize();
            branchesPattern = setting.getBranchesPattern();

            Collection<RefChange> filteredRefChanges = FluentIterable.from(refChanges)
                    .filter(isNotDeleteRefChange)
                    .filter(isNotTagRefChange)
                    .toList();

            if(branchesPattern.isPresent()) {
                filteredRefChanges = Collections2.filter(filteredRefChanges, filterBranchesPredicate(branchesPattern.get()));
            }

            Iterable<String> filteredPaths = filter((Multimaps.index(
                    filter(getChanges(repository, filteredRefChanges),
                            Predicates.compose(Range.greaterThan(maxFileSize), Pair.<Long>rightValue())),
                            compose(Functions.CHANGE_TO_PATH, Pair.<Change>leftValue())).keySet()), Predicates.contains(includePattern));

            if (setting.getExcludePattern().isPresent())
                filteredPaths = filter(filteredPaths, Predicates.not(Predicates.contains(setting.getExcludePattern().get())));

            addAll(paths, filteredPaths);
            pathAndSizes.put(maxFileSize, paths);
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
        List<FileSizeHookSetting> configurations = new ArrayList<FileSizeHookSetting>();
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
        return zipWithSize(filter(Iterables.concat(changesetService.getChanges(refChanges, repository)), isNotDeleteChange), repository);
    }

    private Iterable<Pair<Change, Long>> zipWithSize(Iterable<Change> changes, Repository repository) {
        // TODO We really shouldn't need a multimap
        Multimap<String, Change> commit2changes = Multimaps.index(changes, Functions.CHANGE_TO_CONTENTID);
        CatFileBatchCheckHandler handler = new CatFileBatchCheckHandler(commit2changes.keySet());
        Command<List<Pair<String, Long>>> cmd = commandFactory.builder(repository).command("cat-file").argument("--batch-check").inputHandler(handler).build(handler);
        return transform(cmd.call(), this.<String, Change, Long>getFromMap(commit2changes));
    }

    private <K, T, R> Function<Pair<K, R>, Pair<T, R>> getFromMap(final Multimap<K, T> commit2changes) {
        return new Function<Pair<K, R>, Pair<T, R>>() {
            @Override
            public Pair<T, R> apply(Pair<K, R> input) {
                return Pair.pair(Iterables.getFirst(commit2changes.get(input.left()), null), input.right());
            }
        };
    }
}
