package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Changeset;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.*;
import com.atlassian.bitbucket.scm.ChangesetsCommandParameters;
import com.atlassian.bitbucket.scm.CommitsCommandParameters;
import com.atlassian.bitbucket.scm.RefsCommandParameters;
import com.atlassian.bitbucket.scm.ScmService;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageUtils;
import com.atlassian.bitbucket.util.PagedIterable;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ChangesetServiceImpl implements ChangesetService {

    private static final PageRequest PAGE_REQUEST = PageUtils.newRequest(0, PageRequest.MAX_PAGE_LIMIT);
    private static final int MAX_CHANGES_PER_COMMIT = PageRequest.MAX_PAGE_LIMIT;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangesetServiceImpl.class);
    private final ScmService scmService;

    public ChangesetServiceImpl(ScmService scmService) {
        this.scmService = scmService;
    }

    @Override
    public Iterable<Change> getChanges(Iterable<RefChange> refChanges, final Repository repository) {
        LOGGER.info("Getting changes as iterable");
        List<Change> changes = new ArrayList<>();

        Iterable<Commit> commits = getCommitsBetween(repository, refChanges);
        for (Iterable<Change> values : getChanges(repository, commits).values()) {
            Iterables.addAll(changes, values);
        }

        LOGGER.info("Returning iterable changes");
        return changes;
    }

    @Override
    public Map<Commit, Iterable<Change>> getChanges(final Repository repository, Iterable<Commit> commits) {
        LOGGER.info("Getting changes as map");
        Map<Commit, Iterable<Change>> changesByCommit = new HashMap<>();

        Iterable<Changeset> changesets = getChangesets(repository, commits);
        for (Changeset changeset : changesets) {
            changesByCommit.put(changeset.getToCommit(), Objects.requireNonNull(changeset.getChanges()).getValues());
        }
        LOGGER.info("Changes by commit : " + changesByCommit.size());
        LOGGER.info("Returning map of changes");
        return changesByCommit;
    }

    @Override
    public Set<Commit> getCommitsBetween(final Repository repository, Iterable<RefChange> refChanges) {
        LOGGER.info("Getting commits between");
        Set<Commit> commits = new HashSet<>();
        CommitsCommandParameters.Builder builder = new CommitsCommandParameters.Builder().withMessages(false);

        for (RefChange refChange : refChanges) {
            switch (refChange.getType()) {
                case UPDATE:
                    builder = builder.include(refChange.getToHash());
                    builder = builder.exclude(refChange.getFromHash());
                    break;
                case ADD:
                    builder = builder.include(refChange.getToHash());
                    break;
                case DELETE:
                    // Deleting branch means that its commits were already in repository,
                    // excluding them may reduce amount of commits to inspect if other ref changes exist.
                    builder = builder.exclude(refChange.getFromHash());
                    break;
            }
        }

        Set<String> existingHeads = getExistingRefs(repository).stream()
                .map(Ref::getLatestCommit)
                .collect(Collectors.toSet());

        builder = builder.exclude(existingHeads);

        CommitsCommandParameters parameters = builder.build();
        if (parameters.hasIncludes()) {
            scmService.getCommandFactory(repository).commits(parameters, commits::add).call();
        }
        LOGGER.info("Returning " + commits.size() + " commits");
        return commits;
    }

    private Set<Ref> getExistingRefs(final Repository repository) {
        LOGGER.info("Getting refs");
        Set<Ref> refs = new HashSet<>();
        RefCallback refCallback = new RefCallback() {
            @Override
            public boolean onRef(@Nonnull Ref ref) throws IOException {
                refs.add(ref);
                return true;
            }
        };
        scmService.getCommandFactory(repository).refs(new RefsCommandParameters.Builder().build(), refCallback).call();

        LOGGER.info("Returning refs, count: " + refs.size());
        return refs;
    }

    private Iterable<Changeset> getChangesets(final Repository repository, Iterable<Commit> commits) {
        LOGGER.info("Getting change sets");
        Iterable<Changeset> changesets = new ArrayList<>();

        final Collection<String> commitIds = StreamSupport.stream(commits.spliterator(), false)
                .map(Commit::getId)
                .collect(Collectors.toSet());
        LOGGER.info("Number of commit ids: " + commitIds.size());
        if (!commitIds.isEmpty()) {
            changesets = new PagedIterable<>(pageRequest -> Objects.requireNonNull(scmService.getCommandFactory(repository).changesets(
                    new ChangesetsCommandParameters.Builder()
                            .commitIds(commitIds)
                            .maxChangesPerCommit(MAX_CHANGES_PER_COMMIT)
                            .maxMessageLength(0)
                            .build(),
                    pageRequest).call()), PAGE_REQUEST);
        }
        LOGGER.info("Returning change sets");
        return changesets;
    }
}
