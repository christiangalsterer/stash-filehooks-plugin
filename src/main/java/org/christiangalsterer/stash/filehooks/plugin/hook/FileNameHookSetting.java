package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.regex.Pattern;

public class FileNameHookSetting {

    private Pattern includePattern;
    private Optional<Pattern> excludePattern;
    private Optional<Pattern> affectedBranches;

    public FileNameHookSetting(String includePattern, String excludePattern, String affectedBranches) {
        this.includePattern = Pattern.compile(includePattern);
        this.excludePattern = Strings.isNullOrEmpty(excludePattern) ? Optional.empty() : Optional.of(Pattern.compile(excludePattern));
        this.affectedBranches = Strings.isNullOrEmpty(affectedBranches) ?
                Optional.empty() : Optional.of(Pattern.compile(affectedBranches));
    }

    public Pattern getIncludePattern() {
        return includePattern;
    }

    public Optional<Pattern> getExcludePattern() {
        return excludePattern;
    }

    public Optional<Pattern> getAffectedBranches() { return affectedBranches; }
}
