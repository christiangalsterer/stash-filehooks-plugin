package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.scm.git.GitRefPattern;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Predicates {

    /**
     * Predicate to check that a ref change is not a delete operation.
     */
    public static final Predicate<RefChange> REF_CHANGE_NOT_DELETED =
        refChange -> !refChange.getType().equals(RefChangeType.DELETE);
    private final static String TAG_REF = "refs/tags/";
    /**
     * Predicate to check that a ref change is not a tag operation.
     */
    public static final Predicate<RefChange> REF_CHANGE_IS_NOT_TAG =
        refChange -> !refChange.getRef().getId().startsWith(TAG_REF);

    /**
     * Predicate to check if the RefChange is matched by the @param branchesPattern
     */

    public static final Predicate<RefChange> filterBranchesPredicate(final Pattern branchesPattern) {
        return refChange -> branchesPattern.matcher(GitRefPattern.HEADS.unqualify(refChange.getRef().getId())).matches();
    }
}
