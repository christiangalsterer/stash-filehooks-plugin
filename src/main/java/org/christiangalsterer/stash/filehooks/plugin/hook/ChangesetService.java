package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;

import java.util.Map;

public interface ChangesetService {
    Iterable<Change> getChanges(Iterable<RefChange> refChanges, final Repository repository);
    Map<Commit, Iterable<Change>> getChanges(final Repository repository, Iterable<Commit> commits);
    Iterable<Commit> getCommitsBetween(final Repository repository, final RefChange refChange);
}
