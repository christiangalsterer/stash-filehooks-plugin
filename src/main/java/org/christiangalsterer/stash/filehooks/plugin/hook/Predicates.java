package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.google.common.base.Predicate;

public class Predicates {

    /**
     * Predicate to check that a change is not a delete operation.
     */
    public static final Predicate<RefChange> isNotDeleteChange = new Predicate<RefChange>() {

        @Override
        public boolean apply(RefChange refChange) {
            return !refChange.getType().equals(RefChangeType.DELETE);
        }
    };

}
