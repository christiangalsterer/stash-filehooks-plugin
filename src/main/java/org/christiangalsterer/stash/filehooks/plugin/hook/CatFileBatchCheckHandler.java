package org.christiangalsterer.stash.filehooks.plugin.hook;

import com.atlassian.fugue.Pair;
import com.atlassian.bitbucket.io.LineReader;
import com.atlassian.bitbucket.io.LineReaderOutputHandler;
import com.atlassian.bitbucket.scm.CommandInputHandler;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.utils.process.IOUtils;
import com.atlassian.utils.process.ProcessException;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class CatFileBatchCheckHandler extends LineReaderOutputHandler implements CommandInputHandler, CommandOutputHandler<List<Pair<String, Long>>> {

	private final Iterable<String> changesets;
	private final List<Pair<String, Long>> values = Lists.newArrayList();

	public CatFileBatchCheckHandler(Iterable<String> changesets) {
		super("UTF-8");
		this.changesets = changesets;
	}

	@Override
	public List<Pair<String, Long>> getOutput() {
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
			String[] split = line.split(" ");
			// Only process blobs (ie files), ignore folders
			if (split.length == 3 && split[1].equals("blob")) {
				values.add(Pair.pair(split[0], Long.parseLong(split[2])));
			}
		}
	}

	@Override
	public void process(OutputStream input) {
		try {
			for (String c : changesets) {
				input.write(c.getBytes("UTF-8"));
				input.write("\n".getBytes("UTF-8"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		IOUtils.closeQuietly(input);
	}
}
