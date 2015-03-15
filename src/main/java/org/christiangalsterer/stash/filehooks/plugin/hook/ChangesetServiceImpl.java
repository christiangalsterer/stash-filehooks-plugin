package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.*;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.util.*;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.util.Collection;

import static com.google.common.collect.Iterables.transform;

public class ChangesetServiceImpl implements ChangesetService {

    private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
    private static final int MAX_CHANGES = 100;

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
                Iterable<String> csetss = transform(getChangesetsBetween(repository, refChange), Functions.CHANGESET_TO_ID);
                return Iterables.concat(Iterables.transform(getDetailedChangesets(repository, csetss), Functions.DETAILED_CHANGESET_TO_CHANGES));
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

}
