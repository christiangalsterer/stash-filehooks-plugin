package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.regex.Pattern;

class FileSizeHookSetting {

    private final Long size;
    private final Pattern includePattern;
    private Optional<Pattern> excludePattern;
    private Optional<Pattern> branchesPattern;

    FileSizeHookSetting(Long size, String includePattern, String excludePattern, String branchesPattern) {
        this.size = size;
        this.includePattern = Pattern.compile(includePattern);
        this.excludePattern = Strings.isNullOrEmpty(excludePattern) ? Optional.empty() : Optional.of(Pattern.compile(excludePattern));
        this.branchesPattern = Strings.isNullOrEmpty(branchesPattern) ? Optional.empty() : Optional.of(Pattern.compile(branchesPattern));

    }

    Long getSize() {
        return size;
    }

    Pattern getIncludePattern() {
        return includePattern;
    }

    Optional<Pattern> getExcludePattern() {
        return excludePattern;
    }

    Optional<Pattern> getBranchesPattern() { return branchesPattern; }

}
