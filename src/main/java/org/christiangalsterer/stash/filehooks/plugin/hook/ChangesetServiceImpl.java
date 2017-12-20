package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.AbstractCommitCallback;
import com.atlassian.bitbucket.commit.Changeset;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.ChangesetsCommandParameters;
import com.atlassian.bitbucket.scm.CommitsCommandParameters;
import com.atlassian.bitbucket.scm.ScmService;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageUtils;
import com.atlassian.bitbucket.util.PagedIterable;
import com.google.common.collect.Iterables;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ChangesetServiceImpl implements ChangesetService {

    private static final PageRequest PAGE_REQUEST = PageUtils.newRequest(0, PageRequest.MAX_PAGE_LIMIT);
    private static final int MAX_CHANGES_PER_COMMIT = PageRequest.MAX_PAGE_LIMIT;

    private final ScmService scmService;

    public ChangesetServiceImpl(ScmService scmService) {
        this.scmService = scmService;
    }

    @Override
    public Iterable<Change> getChanges(Iterable<RefChange> refChanges, final Repository repository) {
        List<Change> changes = new ArrayList<>();

        for (RefChange refChange : refChanges) {
            Iterable<Commit> commits = getCommitsBetween(repository, refChange);
            for (Iterable<Change> values : getChanges(repository, commits).values()) {
                Iterables.addAll(changes, values);
            }
        }

        return changes;
    }

    @Override
    public Map<Commit, Iterable<Change>> getChanges(final Repository repository, Iterable<Commit> commits) {
        Map<Commit, Iterable<Change>> changesByCommit = new HashMap<>();

        Iterable<Changeset> changesets = getChangesets(repository, commits);
        for (Changeset changeset : changesets) {
            changesByCommit.put(changeset.getToCommit(), changeset.getChanges().getValues());
        }

        return changesByCommit;
    }

    @Override
    public Iterable<Commit> getCommitsBetween(final Repository repository, final RefChange refChange) {
        List<Commit> commits = new LinkedList<>();
        CommitsCommandParameters.Builder parameters = new CommitsCommandParameters.Builder()
                .include(refChange.getToHash())
                .withMessages(false);

        if (refChange.getType() == RefChangeType.UPDATE) {
            parameters = parameters.exclude(refChange.getFromHash());
        }

        scmService.getCommandFactory(repository).commits(parameters.build(), new AbstractCommitCallback() {
            @Override
            public boolean onCommit(@Nonnull Commit commit) {
                return commits.add(commit);
            }
        }).call();
        return commits;
    }

    private Iterable<Changeset> getChangesets(final Repository repository, Iterable<Commit> commits) {
        final Collection<String> commitIds = StreamSupport.stream(commits.spliterator(), false)
                .map(Commit::getId)
                .collect(Collectors.toSet());
        return new PagedIterable<>(pageRequest -> scmService.getCommandFactory(repository).changesets(
                new ChangesetsCommandParameters.Builder()
                        .commitIds(commitIds)
                        .maxChangesPerCommit(MAX_CHANGES_PER_COMMIT)
                        .maxMessageLength(0)
                        .build(),
                pageRequest).call(), PAGE_REQUEST);
    }
}
