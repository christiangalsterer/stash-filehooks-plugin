package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.regex.Pattern;

class FileNameHookSetting {

    private final Pattern includePattern;
    private final Optional<Pattern> excludePattern;
    private final Optional<Pattern> branchesPattern;

    FileNameHookSetting(String includePattern, String excludePattern, String branchesPattern) {
        this.includePattern = Pattern.compile(includePattern);
        this.excludePattern = Strings.isNullOrEmpty(excludePattern) ? Optional.empty() : Optional.of(Pattern.compile(excludePattern));
        this.branchesPattern = Strings.isNullOrEmpty(branchesPattern) ? Optional.empty() : Optional.of(Pattern.compile(branchesPattern));
    }

    Pattern getIncludePattern() {
        return includePattern;
    }

    Optional<Pattern> getExcludePattern() {
        return excludePattern;
    }

    Optional<Pattern> getBranchesPattern() { return branchesPattern; }
}
