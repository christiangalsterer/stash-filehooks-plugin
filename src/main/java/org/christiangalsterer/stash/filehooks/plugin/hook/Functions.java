package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.Changeset;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;

import java.util.function.Function;

class Functions {

    static final Function<Commit, String> COMMIT_TO_COMMIT_ID = commit -> commit.getId();

    static final Function<Change, String> CHANGE_TO_PATH = change -> change.getPath().toString();
}
