package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scope.Scope;
import com.atlassian.bitbucket.setting.SettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FileSizeHookValidator implements SettingsValidator {

    private static final int MAX_SETTINGS = 5;
    private static final String SETTINGS_INCLUDE_PATTERN_PREFIX = "pattern-";
    private static final String SETTINGS_EXCLUDE_PATTERN_PREFIX = "pattern-exclude-";
    private static final String SETTINGS_SIZE_PREFIX = "size-";
    private static final String SETTINGS_BRANCHES_PATTERN_PREFIX = "pattern-branches-";

    private final I18nService i18n;

    public FileSizeHookValidator(I18nService i18n) {
        this.i18n = i18n;
    }

    @Override
    public void validate(@Nonnull Settings settings, @Nonnull SettingsValidationErrors errors, @Nonnull Scope scope) {

        int patternParams = 0;

        final Set<String> params = settings.asMap().keySet();
        for (String param : params) {
            if (param.matches(SETTINGS_INCLUDE_PATTERN_PREFIX + "[1-5]$")) {
                patternParams++;
                continue;
            }
        }

        if (patternParams > MAX_SETTINGS)
            errors.addFormError(i18n.getText("filesize-hook.error.max.settings", "Only {0} settings are allowed.", MAX_SETTINGS));

        for (int i = 1; i <= patternParams; i++) {
           try {
                int size = Integer.parseInt(settings.getString(SETTINGS_SIZE_PREFIX + i, ""));
                if (size < 1) {
                    errors.addFieldError("size-" + i, i18n.getText("filesize-hook.error.size", "Size must be an integer value larger than 0"));
                }
            } catch (NumberFormatException e) {
                errors.addFieldError(SETTINGS_SIZE_PREFIX + i, i18n.getText("filesize-hook.error.size", "Size must be an integer value larger than 0"));
            }

            if (Strings.isNullOrEmpty(settings.getString(SETTINGS_INCLUDE_PATTERN_PREFIX + i))) {
                errors.addFieldError(SETTINGS_INCLUDE_PATTERN_PREFIX + i, i18n.getText("filesize-hook.error.pattern", "Pattern is not a valid regular expression"));
            } else {
                try {
                    Pattern.compile(settings.getString(SETTINGS_INCLUDE_PATTERN_PREFIX + i, ""));
                } catch (PatternSyntaxException e) {
                    errors.addFieldError(SETTINGS_INCLUDE_PATTERN_PREFIX + i, i18n.getText("filesize-hook.error.pattern", "Pattern is not a valid regular expression"));
                }
            }

            if (!Strings.isNullOrEmpty(settings.getString(SETTINGS_EXCLUDE_PATTERN_PREFIX + i))) {
                try {
                    Pattern.compile(settings.getString(SETTINGS_EXCLUDE_PATTERN_PREFIX + i));
                } catch (PatternSyntaxException e) {
                    errors.addFieldError(SETTINGS_EXCLUDE_PATTERN_PREFIX + i, i18n.getText("filesize-hook.error.pattern", "Pattern is not a valid regular expression"));
                }
            }

            if (!Strings.isNullOrEmpty(settings.getString(SETTINGS_BRANCHES_PATTERN_PREFIX + i))) {
                try {
                    Pattern.compile(settings.getString(SETTINGS_BRANCHES_PATTERN_PREFIX + i));
                } catch (PatternSyntaxException e) {
                    errors.addFieldError(SETTINGS_BRANCHES_PATTERN_PREFIX + i, i18n.getText("filesize-hook.error.pattern", "Pattern is not a valid regular expression"));
                }
            }
        }
    }
}
