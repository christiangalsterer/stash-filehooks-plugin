package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.bitbucket.io.LineReader;
import com.atlassian.bitbucket.io.LineReaderOutputHandler;
import com.atlassian.bitbucket.scm.CommandInputHandler;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.utils.process.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CatFileBatchCheckHandler extends LineReaderOutputHandler implements CommandInputHandler, CommandOutputHandler<Map<String, Long>> {

	private final Iterable<String> changesets;
    private final Map<String, Long> values = new HashMap<>();

	CatFileBatchCheckHandler(Iterable<String> changesets) {
		super(StandardCharsets.UTF_8);
		this.changesets = changesets;
	}

	@Override
	public Map<String, Long> getOutput() {
		return values;
	}

	@Override
	public void complete() {
		try {
			super.complete();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void processReader(LineReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			String[] split = line.split(" ");
			// Only process blobs (ie files), ignore folders
			if (split.length == 3 && split[1].equals("blob")) {
				values.put(split[0], Long.parseLong(split[2]));
			}
		}
	}

	@Override
	public void process(OutputStream input) {
		try {
			for (String c : changesets) {
				input.write(c.getBytes(StandardCharsets.UTF_8.name()));
				input.write("\n".getBytes(StandardCharsets.UTF_8.name()));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		IOUtils.closeQuietly(input);
	}
}
