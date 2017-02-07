package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.fugue.Pair;

import java.util.Collection;

public interface DiffTreeService {

    Collection<Pair<String, String>> getChangedFiles(Collection<RefChange> refChanges, final Repository repository);
}
