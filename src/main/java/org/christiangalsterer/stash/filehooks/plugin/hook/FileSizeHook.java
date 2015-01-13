package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.fugue.Pair;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.*;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.Command;
import com.atlassian.stash.scm.PluginCommandBuilderFactory;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.setting.RepositorySettingsValidator;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;
import com.atlassian.stash.util.*;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.google.common.base.Functions.compose;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

/**
 * Checks the size of a file in the pre-receive phase and rejects the push when the changeset contains files which exceed the configured file size limit.
 */
public class FileSizeHook implements PreReceiveRepositoryHook, RepositorySettingsValidator {

    private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
    private static final String SETTINGS_PATTERN = "pattern-1";
    private static final String SETTINGS_SIZE = "size-1";
    private static final int MAX_CHANGES = 100;
    private static final Function<Changeset, String> PLUCK_ID = new Function<Changeset, String>() {
        @Override
        public String apply(Changeset input) {
            return input.getId();
        }
    };
    private static final Function<DetailedChangeset, Iterable<Change>> PLUCK_CHANGES = new Function<DetailedChangeset, Iterable<Change>>() {
        @SuppressWarnings({ "ConstantConditions", "unchecked" })
        @Override
        public Iterable<Change> apply(DetailedChangeset input) {
            return (Iterable<Change>) input.getChanges().getValues();
        }
    };
    private static final Function<Change, String> PLUCK_CONTENT_ID = new Function<Change, String>() {
        @Override
        public String apply(Change input) {
            return input.getContentId();
        }
    };
    private static final Function<Change, String> PLUCK_PATH = new Function<Change, String>() {
        @Override
        public String apply(Change change) {
            return change.getPath().toString();
        }
    };

    private final CommitService commitService;
    private final PluginCommandBuilderFactory commandFactory;

    public FileSizeHook(CommitService commitService, GitCommandBuilderFactory commandFactory) {
        this.commitService = commitService;
        this.commandFactory = commandFactory;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        Pattern pattern = Pattern.compile(context.getSettings().getString(SETTINGS_PATTERN));
        long maxFileSize = Long.parseLong(context.getSettings().getString(SETTINGS_SIZE));
        Collection<String> sizePaths = Multimaps.index(
                filter(getChanges(repository, refChanges),
                        Predicates.compose(Ranges.greaterThan(maxFileSize), Pair.<Long>rightValue())),
                compose(PLUCK_PATH, Pair.<Change>leftValue())).keySet();

        Collection<String> paths = new ArrayList<String>();

        if (sizePaths.size() > 0) {
            for (String path : sizePaths) {
                if (pattern.matcher(path).matches())
                    paths.add(path);
            }
        }

        if (paths.size() > 0) {
            hookResponse.out().println("=================================");
            for (String path : sizePaths) {
                hookResponse.out().println("File too large: " + path);
            }
            hookResponse.out().println("=================================");
            return false;
        }
        return true;
    }

    private Iterable<Pair<Change, Long>> getChanges(Repository repository, Collection<RefChange> refChanges) {
        return zipWithSize(Iterables.concat(getChanges(refChanges, repository)), repository);
    }

    private Iterable<Pair<Change, Long>> zipWithSize(Iterable<Change> changes, Repository repository) {
        // TODO We really shouldn't need a multimap
        Multimap<String, Change> commit2changes = Multimaps.index(changes, PLUCK_CONTENT_ID);
        CatFileBatchCheckHandler handler = new CatFileBatchCheckHandler(commit2changes.keySet());
        Command<List<Pair<String, Long>>> cmd = commandFactory.builder(repository).command("cat-file").argument("--batch-check").inputHandler(handler).build(handler);
        return transform(cmd.call(), this.<String, Change, Long>getFromMap(commit2changes));
    }

    private Iterable<Iterable<Change>> getChanges(Iterable<RefChange> refChanges, final Repository repository) {
        return Iterables.transform(refChanges, new Function<RefChange, Iterable<Change>>() {
            @Override
            public Iterable<Change> apply(RefChange refChange) {
                // TODO Ideally this is one diff-tree git call
                Iterable<String> csetss = transform(getChangesetsBetween(repository, refChange), PLUCK_ID);
                return Iterables.concat(Iterables.transform(getDetailedChangesets(repository, csetss), FileSizeHook.PLUCK_CHANGES));
            }
        });
    }

    private Iterable<Changeset> getChangesetsBetween(final Repository repository, final RefChange refChange) {
        return new PagedIterable<Changeset>(new PageProvider<Changeset>() {
            @Override
            public Page<Changeset> get(PageRequest pageRequest) {
                return commitService.getChangesetsBetween(new ChangesetsBetweenRequest.Builder(repository)
                        .exclude(refChange.getFromHash())
                        .include(refChange.getToHash())
                        .build(), pageRequest);
            }
        }, PAGE_REQUEST);
    }

    private Iterable<DetailedChangeset> getDetailedChangesets(final Repository repository, Iterable<String> changesets) {
        final Collection<String> csets = ImmutableSet.copyOf(changesets);
        return new PagedIterable<DetailedChangeset>(new PageProvider<DetailedChangeset>() {
            @Override
            public Page<DetailedChangeset> get(PageRequest pageRequest) {
                return commitService.getDetailedChangesets(new DetailedChangesetsRequest.Builder(repository)
                        .changesetIds(csets)
                        .maxChangesPerCommit(MAX_CHANGES)
                        .build(), pageRequest);
            }
        }, PAGE_REQUEST);
    }

    private <K, T, R> Function<Pair<K, R>, Pair<T, R>> getFromMap(final Multimap<K, T> commit2changes) {
        return new Function<Pair<K, R>, Pair<T, R>>() {
            @Override
            public Pair<T, R> apply(Pair<K, R> input) {
                return Pair.pair(Iterables.getFirst(commit2changes.get(input.left()), null), input.right());
            }
        };
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {
        try {
            int size = Integer.parseInt(settings.getString(SETTINGS_SIZE, ""));
            if (size < 1) {
                errors.addFieldError("size-1", "Size must be larger than 0");
            }
        } catch (NumberFormatException e) {
            errors.addFieldError("size-1", "Size must be an integer value larger than 0");
        }

        try {
            Pattern.compile(settings.getString(SETTINGS_PATTERN, ""));
        } catch (PatternSyntaxException e) {
            errors.addFieldError("pattern-1", "Pattern is not a valid regular expression");
        }
    }
}
