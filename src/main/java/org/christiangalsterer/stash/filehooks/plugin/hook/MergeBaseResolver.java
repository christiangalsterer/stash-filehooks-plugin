package org.christiangalsterer.stash.filehooks.plugin.hook;


import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;


/**
 * Determines the merge base of a pair of commits.
 */

/**
class MergeBaseResolver {

    private final GitCommandBuilderFactory builderFactory;
    private final GitScmConfig gitScmConfig;
    private final CommitService commitService;

    MergeBaseResolver(GitCommandBuilderFactory builderFactory, GitScmConfig gitScmConfig, CommitService commitService) {
        this.builderFactory = builderFactory;
        this.gitScmConfig = gitScmConfig;
        this.commitService = commitService;
    }

    Commit findMergeBase(Commit a, Commit b) {
        if (a.equals(b)) {
            return a;
        }
        final GitMergeBaseBuilder builder = builderFactory.builder(a.getRepository()).mergeBase().between(a.getId(), b.getId());
        GitUtils.setAlternateIfCrossRepository(builder, a.getRepository(), b.getRepository(), gitScmConfig);

        final String sha = builder.build(new FirstLineOutputHandler()).call();
        if (sha == null) {
            return null;
        }
        final CommitRequest commitRequest = new CommitRequest.Builder(a.getRepository(), sha).build();

        return commitService.getCommit(commitRequest);
    }
}

*/