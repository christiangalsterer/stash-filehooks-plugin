package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.hook.repository.*;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.Command;
import com.atlassian.bitbucket.scm.ScmCommandBuilder;
import com.atlassian.bitbucket.scm.ScmService;
import com.atlassian.bitbucket.setting.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Iterables.addAll;
import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.isNotDeleteChange;
import static org.christiangalsterer.stash.filehooks.plugin.hook.Predicates.matchesBranchPattern;

/**
 * Checks the size of a file in the pre-receive phase and rejects the push when the changeset contains files which exceed the configured file size limit.
 */
public class FileSizeHook implements PreRepositoryHook<RepositoryHookRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSizeHook.class);
    private static final int MAX_SETTINGS = 5;
    private static final String SETTINGS_INCLUDE_PATTERN_PREFIX = "pattern-";
    private static final String SETTINGS_EXCLUDE_PATTERN_PREFIX = "pattern-exclude-";
    private static final String SETTINGS_SIZE_PREFIX = "size-";
    private static final String SETTINGS_BRANCHES_PATTERN_PREFIX = "pattern-branches-";
    private final ChangesetService changesetService;
    private final ScmService scmService;


    public FileSizeHook(ChangesetService changesetService, ScmService scmService) {
        this.changesetService = changesetService;
        this.scmService = scmService;
    }


    private List<FileSizeHookSetting> getSettings(Settings settings) {
        LOGGER.info("Get hook configuration settings");
        List<FileSizeHookSetting> configurations = new ArrayList<>();
        String includeRegex;
        Long size;
        String excludeRegex;
        String branchesRegex;

        for (int i = 1; i <= MAX_SETTINGS; i++) {
            includeRegex = settings.getString(SETTINGS_INCLUDE_PATTERN_PREFIX + i);
            if (includeRegex != null) {
                excludeRegex = settings.getString(SETTINGS_EXCLUDE_PATTERN_PREFIX + i);
                size = settings.getLong(SETTINGS_SIZE_PREFIX + i);
                branchesRegex = settings.getString(SETTINGS_BRANCHES_PATTERN_PREFIX + i);
                configurations.add(new FileSizeHookSetting(size, includeRegex, excludeRegex, branchesRegex));
            }
        }
        LOGGER.info("Return hook configuration settings");
        return configurations;
    }


    private Map<String, Long> getSizeForContentIds(final Repository repository, Iterable<String> contentIds) {
        LOGGER.info("Get size for content ids");
        ScmCommandBuilder<?> scmCommandBuilder = scmService.createBuilder(repository);
        CatFileBatchCheckHandler handler = new CatFileBatchCheckHandler(contentIds);
        Command<Map<String, Long>> cmd = scmCommandBuilder
                .command("cat-file")
                .argument("--batch-check")
                .inputHandler(handler)
                .build(handler);
        return filterOutNullSizes(Objects.requireNonNull(cmd.call()));
    }


    private Map<String, Long> filterOutNullSizes(Map<String, Long> sizes) {
        LOGGER.info("Filter null sizes");
        return sizes.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext context,
                                          @Nonnull RepositoryHookRequest request) {
        LOGGER.info("Start of preUpdate hook event");
        Repository repository = request.getRepository();
        List<FileSizeHookSetting> settings = getSettings(context.getSettings());

        FlatteningCachingResolver<Commit, Change> changesByCommit = new FlatteningCachingResolver<>();
        CachingResolver<String, Long> sizesByContentId = new CachingResolver<>();

        Map<Long, Collection<String>> pathAndSizes = new HashMap<>();

        for (FileSizeHookSetting setting : settings) {
            Collection<String> violatingPaths = new ArrayList<>();
            Pattern includePattern = setting.getIncludePattern();
            Long maxFileSize = setting.getSize();
            Optional<Pattern> branchesPattern = setting.getBranchesPattern();

            Stream<RefChange> filteredRefChanges = request.getRefChanges().stream();

            if (branchesPattern.isPresent()) {
                filteredRefChanges = filteredRefChanges
                        .filter(matchesBranchPattern(branchesPattern.get()));
            }

            Set<Commit> commits =
                    changesetService.getCommitsBetween(repository, filteredRefChanges.collect(Collectors.toSet()));
            LOGGER.info("Number of commits: " + commits.size());

            Set<Change> filteredChanges =
                    changesByCommit.flatBatchResolve(commits, x -> changesetService.getChanges(repository, x)).stream()
                            .filter(isNotDeleteChange)
                            .filter(change -> {
                                String fullPath = change.getPath().toString();
                                return includePattern.matcher(fullPath).find()
                                        && (!setting.getExcludePattern().isPresent()
                                        || !setting.getExcludePattern().get().matcher(fullPath).find());
                            })
                            .collect(Collectors.toSet());
            LOGGER.info("Number of filtered changes: " + filteredChanges.size());
            // Pre-populate cache by resolving all required changes at once
            sizesByContentId.batchResolve(
                    filteredChanges.stream().map(Change::getContentId).collect(Collectors.toSet()),
                    contentIds -> getSizeForContentIds(repository, contentIds));

            List<String> filteredPaths = filteredChanges.stream()
                    .filter(change -> sizesByContentId.resolve(change.getContentId()) > maxFileSize)
                    .map(change -> change.getPath().toString())
                    .collect(Collectors.toList());
            LOGGER.info("Violating paths: " + violatingPaths.size() + ", filtered paths: " + filteredPaths.size());
            addAll(violatingPaths, filteredPaths);

            if (pathAndSizes.containsKey(maxFileSize))
                addAll(violatingPaths, pathAndSizes.get(maxFileSize));

            pathAndSizes.put(maxFileSize, violatingPaths);

            for (Map.Entry<Long, Collection<String>> entry : pathAndSizes.entrySet()) {
                LOGGER.info("File size : " + entry.getKey() + ", file path: " + entry.getValue());
            }
        }

        ArrayList<String> resultList = new ArrayList<>();
        boolean hookPassed = true;

        for (Long maxFileSize : pathAndSizes.keySet()) {
            Collection<String> paths = pathAndSizes.get(maxFileSize);
            if (paths.size() > 0) {
                hookPassed = false;
                for (String path : paths)
                    resultList.add(String.format("File [%s] is too large. Maximum allowed file size is %s bytes.", path, maxFileSize));
            }
        }
        LOGGER.info("End of preUpdate repo hook event, hook passed: " + hookPassed);

        if (hookPassed) {
            return RepositoryHookResult.accepted();
        } else {
            return RepositoryHookResult.rejected("Files are too large", Arrays.toString(resultList.toArray()));
        }

    }

}
