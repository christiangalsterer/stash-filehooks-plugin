package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.content.Change;

import java.util.function.Function;

class Functions {

    static final Function<Commit, String> COMMIT_TO_COMMIT_ID = MinimalCommit::getId;

    static final Function<Change, String> CHANGE_TO_PATH = change -> change.getPath().toString();
}
