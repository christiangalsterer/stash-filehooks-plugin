package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;

import java.util.function.Predicate;
import java.util.regex.Pattern;

class Predicates {

    private final static String TAG_REF = "refs/tags/";

    /**
     * Predicate to check that a ref change is not a delete operation.
     */
    static final Predicate<RefChange> isNotDeleteRefChange = refChange -> !refChange.getType().equals(RefChangeType.DELETE);

    /**
     * Predicate to check that a ref change is not a tag operation.
     */
    static final Predicate<RefChange> isNotTagRefChange = refChange -> !refChange.getRef().getId().startsWith(TAG_REF);

    /**
     * Predicate to check that a change is not a delete operation.
     */
    static final Predicate<Change> isNotDeleteChange = change -> !change.getType().equals(ChangeType.DELETE);

    /**
     * Predicate to check if the RefChange is matched by the @param branchesPattern
     */
    static Predicate<RefChange> matchesBranchPattern (final Pattern branchesPattern) {
        return refChange -> branchesPattern.matcher(refChange.getRef().getDisplayId()).matches();
    }
}
