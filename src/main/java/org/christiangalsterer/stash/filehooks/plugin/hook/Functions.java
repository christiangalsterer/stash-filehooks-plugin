package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.Changeset;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.google.common.base.Function;

public class Functions {

    public static final Function<Commit, String> COMMIT_TO_ID = new Function<Commit, String>() {
        @Override
        public String apply(Commit input) {
            return input.getId();
        }
    };

    public static final Function<Changeset, Iterable<Change>> CHANGESET_TO_CHANGES = new Function<Changeset, Iterable<Change>>() {
        @SuppressWarnings({ "ConstantConditions", "unchecked" })
        @Override
        public Iterable<Change> apply(Changeset input) {
            return (Iterable<Change>) input.getChanges().getValues();
        }
    };

    public static final Function<Change, String> CHANGE_TO_CONTENTID = new Function<Change, String>() {
        @Override
        public String apply(Change input) {
            return input.getContentId();
        }
    };

    public static final Function<Change, String> CHANGE_TO_PATH = new Function<Change, String>() {
        @Override
        public String apply(Change change) {
            return change.getPath().toString();
        }
    };

    public static final Function<RefChange, Boolean> REF_CHANGE_IS_NOT_DELETE = new Function<RefChange, Boolean>() {
        @Override
        public Boolean apply(RefChange refChange) {
            return !refChange.getType().equals(RefChangeType.DELETE);
        }
    };

}
