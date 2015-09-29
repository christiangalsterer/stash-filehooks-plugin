package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;

public interface ChangesetService {

    Iterable<Iterable<Change>> getChanges(Iterable<RefChange> refChanges, final Repository repository);
}
