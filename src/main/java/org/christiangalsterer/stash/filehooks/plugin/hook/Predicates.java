package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.google.common.base.Predicate;

public class Predicates {

    private final static String TAG_REF = "refs/tags/";

    /**
     * Predicate to check that a ref change is not a delete operation.
     */
    public static final Predicate<RefChange> isNotDeleteRefChange = new Predicate<RefChange>() {

        @Override
        public boolean apply(RefChange refChange) {
            return !refChange.getType().equals(RefChangeType.DELETE);
        }
    };

    /**
     * Predicate to check that a ref change is not a tag operation.
     */
    public static final Predicate<RefChange> isNotTagRefChange = new Predicate<RefChange>() {

        @Override
        public boolean apply(RefChange refChange) {
            return !refChange.getRefId().startsWith(TAG_REF);
        }
    };

    /**
     * Predicate to check that a change is not a delete operation.
     */
    public static final Predicate<Change> isNotDeleteChange = new Predicate<Change>() {

        @Override
        public boolean apply(Change change) {
            return !change.getType().equals(ChangeType.DELETE);
        }
    };
}
