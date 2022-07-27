package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.io.LineReader;
import com.atlassian.bitbucket.io.LineReaderOutputHandler;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
 
/**
 * Returns the first line of output provided by the git process. 
 */ 
public class FirstLineOutputHandler extends LineReaderOutputHandler implements 
        CommandOutputHandler<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirstLineOutputHandler.class);
    private String sha; 
 
    FirstLineOutputHandler() {
        super(StandardCharsets.UTF_8);
    } 
 
    @Nullable 
    @Override 
    public String getOutput() { 
        return sha; 
    } 
 
    @Override 
    protected void processReader(LineReader lineReader) throws IOException { 
        sha = lineReader.readLine(); 
 
        // ignore the rest of the output 
        String nextLine = lineReader.readLine(); 
        while (nextLine != null) { 
            nextLine = lineReader.readLine(); 
        } 
    } 
 
}