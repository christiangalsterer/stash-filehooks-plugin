package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.io.LineReader;
import com.atlassian.bitbucket.io.LineReaderOutputHandler;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.fugue.Pair;
import com.atlassian.utils.process.ProcessException;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiffTreeHandler extends LineReaderOutputHandler implements CommandOutputHandler<List<Pair<String, String>>> {

    private static final String UTF_8 = "UTF-8";
    private static final Pattern pattern =
        Pattern.compile("^:\\d{6}\\s\\d{6}\\s[0-9a-f]{40}\\s([0-9a-f]{40})\\s([ACDMRTUX])\\d{0,3}\\s+(.*)$");
    private static final String D = "D";
    private final List<Pair<String, String>> values = Lists.newArrayList();

    public DiffTreeHandler() {
        super(UTF_8);
    }

    @Override
    public List<Pair<String, String>> getOutput() {
        return values;
    }

    @Override
    public void complete() {
        try {
            super.complete();
        } catch (ProcessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void processReader(LineReader reader) throws IOException {
        String line;
        while ((line = resetWatchdogAndReadLine(reader)) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                //omit deleted files
                if (!D.equals(matcher.group(2))) {
                    values.add(Pair.pair(matcher.group(1), matcher.group(3)));
                }
            }
        }
    }
}
