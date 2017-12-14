package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.*;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.bitbucket.util.PagedIterable;
import com.google.common.collect.Iterables;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ChangesetServiceImpl implements ChangesetService {

    private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
    private static final int MAX_CHANGES_PER_COMMIT = 100;

    private final CommitService commitService;

    public ChangesetServiceImpl(CommitService commitService) {
        this.commitService = commitService;
    }

    @Override
    public Iterable<Change> getChanges(Iterable<RefChange> refChanges, final Repository repository) {
        List<Change> changes = new ArrayList<>();

        for (RefChange refChange : refChanges) {
            Iterable<Commit> commits = getCommitsBetween(repository, refChange);
            Iterables.addAll(changes, getChanges(repository, commits));
        }

        return changes;
    }

    @Override
    public Iterable<Change> getChanges(final Repository repository, Iterable<Commit> commits) {
        List<Change> changes = new ArrayList<>();

        Iterable<Changeset> changesets = getChangesets(repository, commits);
        for (Changeset changeset : changesets) {
            Iterable<Change> values = changeset.getChanges().getValues();
            for (Change change : values) {
                changes.add(change);
            }
        }

        return changes;
    }

    @Override
    public Iterable<Commit> getCommitsBetween(final Repository repository, final RefChange refChange) {
        List<Commit> commits = new LinkedList<>();
        CommitsBetweenRequest request = new CommitsBetweenRequest.Builder(repository)
                .exclude(refChange.getFromHash())
                .include(refChange.getToHash())
                .build();
        commitService.streamCommitsBetween(request, new AbstractCommitCallback() {
            @Override
            public boolean onCommit(@Nonnull Commit commit) throws IOException {
                commits.add(commit);
                return true;
            }
        });
        return commits;
    }

    private Iterable<Changeset> getChangesets(final Repository repository, Iterable<Commit> commits) {
        final Collection<String> commitIds = StreamSupport.stream(commits.spliterator(), false)
                .map(Commit::getId)
                .collect(Collectors.toSet());
        return new PagedIterable<>(pageRequest -> commitService.getChangesets(new ChangesetsRequest.Builder(repository)
                .commitIds(commitIds)
                .maxChangesPerCommit(MAX_CHANGES_PER_COMMIT)
                .build(), pageRequest), PAGE_REQUEST);
    }
}
