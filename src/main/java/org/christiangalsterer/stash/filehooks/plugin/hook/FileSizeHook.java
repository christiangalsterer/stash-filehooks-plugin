package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.fugue.Pair;
import com.atlassian.stash.content.*;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.Command;
import com.atlassian.stash.scm.PluginCommandBuilderFactory;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.setting.Settings;
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

/**
 * Checks the size of a file in the pre-receive phase and rejects the push when the changeset contains files which exceed the configured file size limit.
 */
public class FileSizeHook implements PreReceiveRepositoryHook {

    private static final int MAX_SETTINGS = 5;
    private static final String SETTINGS_PATTERN_PREFIX = "pattern-";
    private static final String SETTINGS_SIZE_PREFIX = "size-";

    private final ChangesetService changesetService;
    private final PluginCommandBuilderFactory commandFactory;

    public FileSizeHook(ChangesetService changesetService, GitCommandBuilderFactory commandFactory) {
        this.changesetService = changesetService;
        this.commandFactory = commandFactory;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        SortedMap<String, Long> regexAndSizes = getSettings(context.getSettings());

        Map<Long, Collection<String>> pathAndSizes = new HashMap<Long, Collection<String>>();

        for (String regex : regexAndSizes.keySet()) {
            Collection<String> paths = new ArrayList<String>();
            Pattern pattern = Pattern.compile(regex);
            Long maxFileSize = regexAndSizes.get(regex);

            addAll(paths, filter((Multimaps.index(
                    filter(getChanges(repository, filter(refChanges, org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.isNotDeleteChange)),
                            Predicates.compose(Ranges.greaterThan(maxFileSize), Pair.<Long>rightValue())),
                    compose(Functions.CHANGE_TO_PATH, Pair.<Change>leftValue())).keySet()), Predicates.contains(pattern)));
            pathAndSizes.put(maxFileSize, paths);
        }

        boolean hookPassed = true;

        for (Long maxFileSize : pathAndSizes.keySet()) {
            Collection<String> paths = pathAndSizes.get(maxFileSize);
            if (paths.size() > 0) {
                hookPassed = false;
                hookResponse.out().println("=================================");
                for (String path : paths) {
                    hookResponse.out().println(String.format("File [%s] is too large. Maximum allowed file size is %s bytes", path, maxFileSize));
                }
                hookResponse.out().println("=================================");
            }
        }

        return hookPassed;
    }

    private SortedMap<String, Long> getSettings(Settings settings) {
        String regex;
        Long size;
        Map<String, Long> regexAndSizes = new HashMap<String, Long>();

        for (int i = 1; i <= MAX_SETTINGS ; i++) {
            regex = settings.getString(SETTINGS_PATTERN_PREFIX + i);
            if (regex != null) {
                size = settings.getLong(SETTINGS_SIZE_PREFIX + i);
                regexAndSizes.put(regex, size);
            }
        }

        return ImmutableSortedMap.copyOf(regexAndSizes);
    }

    private Iterable<Pair<Change, Long>> getChanges(Repository repository, Iterable<RefChange> refChanges) {
        return zipWithSize(Iterables.concat(changesetService.getChanges(refChanges, repository)), repository);
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
