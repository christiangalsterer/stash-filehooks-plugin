package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.stash.content.Change;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;

public interface ChangesetService {

    Iterable<Iterable<Change>> getChanges(Iterable<RefChange> refChanges, final Repository repository);
}
