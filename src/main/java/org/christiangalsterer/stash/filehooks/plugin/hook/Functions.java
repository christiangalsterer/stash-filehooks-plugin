package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.DetailedChangeset;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.google.common.base.Function;
import org.apache.xpath.operations.Bool;

public class Functions {

    public static final Function<Changeset, String> CHANGESET_TO_ID = new Function<Changeset, String>() {
        @Override
        public String apply(Changeset input) {
            return input.getId();
        }
    };

    public static final Function<DetailedChangeset, Iterable<Change>> DETAILED_CHANGESET_TO_CHANGES = new Function<DetailedChangeset, Iterable<Change>>() {
        @SuppressWarnings({ "ConstantConditions", "unchecked" })
        @Override
        public Iterable<Change> apply(DetailedChangeset input) {
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
