package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.fugue.Pair;

import java.util.Collection;
import java.util.stream.Collectors;

public class DiffTreeServiceImpl implements DiffTreeService {

    public static final String ZERO_REF = "0000000000000000000000000000000000000000";

    private final GitCommandBuilderFactory commandBuilderFactory;

    public DiffTreeServiceImpl(GitCommandBuilderFactory gitCommandBuilderFactory) {
        this.commandBuilderFactory = gitCommandBuilderFactory;
    }

    @Override
    public Collection<Pair<String, String>> getChangedFiles(Collection<RefChange> refChanges, final Repository repository) {
        return refChanges.stream().flatMap(refChange -> {
            String fromHash = refChange.getFromHash();
            if (ZERO_REF.equals(fromHash)) {
                fromHash = getNullHash(repository);
            }
            DiffTreeHandler diffTreeHandler = new DiffTreeHandler();
            commandBuilderFactory.builder(repository).command("diff-tree").clearArguments().argument("-r").argument(fromHash)
                .argument(refChange.getToHash()).build(diffTreeHandler).call();
            return diffTreeHandler.getOutput().stream();
        }).collect(Collectors.toSet());
    }

    private String getNullHash(Repository repository) {
        String fromHash;
        final FirstLineOutputHandler outputHandler = new FirstLineOutputHandler();
        commandBuilderFactory.builder(repository).command("hash-object").clearArguments().argument("-t").argument("tree")
            .argument("/dev/null").build(outputHandler).call();
        fromHash = outputHandler.getOutput();
        return fromHash;
    }
}
