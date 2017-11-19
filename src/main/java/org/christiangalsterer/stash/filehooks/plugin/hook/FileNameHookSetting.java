package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.regex.Pattern;

class FileNameHookSetting {

    private Pattern includePattern;
    private Optional<Pattern> excludePattern;
    private Optional<Pattern> branchesPattern;

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
