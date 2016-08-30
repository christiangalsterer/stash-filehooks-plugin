package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.*;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.*;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.Collections;

import static com.google.common.collect.Iterables.transform;

public class ChangesetServiceImpl implements ChangesetService {

    private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
    private static final int MAX_CHANGES_PER_COMMIT = 100;

    private final CommitService commitService;

    public ChangesetServiceImpl(CommitService commitService) {
        this.commitService = commitService;
    }

    @Override
    public Iterable<Iterable<Change>> getChanges(Iterable<RefChange> refChanges, final Repository repository) {
        return Iterables.transform(refChanges, new Function<RefChange, Iterable<Change>>() {
            @Override
            public Iterable<Change> apply(RefChange refChange) {
                // TODO Ideally this is one diff-tree git call
                Iterable<String> csetss = transform(getCommitsBetween(repository, refChange), Functions.COMMIT_TO_ID);
                if (Iterables.size(csetss) == 0) {
                    // Changesets empty so return an empty set
                    return Collections.emptySet();
                }
                return Iterables.concat(Iterables.transform(getChangesets(repository, csetss), Functions.CHANGESET_TO_CHANGES));
            }
        });
    }

    private Iterable<Commit> getCommitsBetween(final Repository repository, final RefChange refChange) {
        return new PagedIterable<Commit>(new PageProvider<Commit>() {
            @Override
            public Page<Commit> get(PageRequest pageRequest) {
                return commitService.getCommitsBetween(new CommitsBetweenRequest.Builder(repository)
                        .exclude(refChange.getFromHash())
                        .include(refChange.getToHash())
                        .build(), pageRequest);
            }
        }, PAGE_REQUEST);
    }

    private Iterable<Changeset> getChangesets(final Repository repository, Iterable<String> changesets) {
        final Collection<String> csets = ImmutableSet.copyOf(changesets);
        return new PagedIterable<Changeset>(new PageProvider<Changeset>() {
            @Override
            public Page<Changeset> get(PageRequest pageRequest) {
                return commitService.getChangesets(new ChangesetsRequest.Builder(repository)
                        .commitIds(csets)
                        .maxChangesPerCommit(MAX_CHANGES_PER_COMMIT)
                        .build(), pageRequest);
            }
        }, PAGE_REQUEST);
    }

}
