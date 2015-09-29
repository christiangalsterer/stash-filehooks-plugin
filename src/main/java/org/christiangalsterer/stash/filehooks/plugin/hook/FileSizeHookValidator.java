package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.RepositorySettingsValidator;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FileSizeHookValidator implements RepositorySettingsValidator {

    private static final int MAX_SETTINGS = 5;
    private static final String SETTINGS_PATTERN_PREFIX = "pattern-";
    private static final String SETTINGS_SIZE_PREFIX = "size-";
    private final I18nService i18n;

    public FileSizeHookValidator(I18nService i18n) {
        this.i18n = i18n;
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {

        int patternParams = 0;

        final Set<String> params = settings.asMap().keySet();
        for (String param : params) {
            if (param.startsWith(SETTINGS_PATTERN_PREFIX)) {
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
                errors.addFieldError("size-" + i, i18n.getText("filesize-hook.error.size", "Size must be an integer value larger than 0"));
            }

            try {
                Pattern.compile(settings.getString(SETTINGS_PATTERN_PREFIX + i, ""));
            } catch (PatternSyntaxException e) {
                errors.addFieldError("pattern-" + i, i18n.getText("filesize-hook.error.pattern", "Pattern is not a valid regular expression"));
            }
        }
    }
}
