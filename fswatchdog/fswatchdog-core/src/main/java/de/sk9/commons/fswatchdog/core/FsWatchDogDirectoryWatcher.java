package de.sk9.commons.fswatchdog.core;

import java.io.IOException;
import java.nio.file.Path;

import io.methvin.watcher.DirectoryWatcher;

public class FsWatchDogDirectoryWatcher implements FsWatchDog {

	private DirectoryWatcher watcher;

	public FsWatchDogDirectoryWatcher(Path dir, Subscriber subscriber) throws IOException {
		this.watcher = DirectoryWatcher.builder().path(dir) // or use paths(directoriesToWatch)
				.listener(event -> {
					switch (event.eventType()) {
					case CREATE:
						subscriber.onCreate(event.path());
						break;
					case MODIFY:
						subscriber.onModify(event.path());
						break;
					case DELETE:
						subscriber.onDelete(event.path());
						break;
					case OVERFLOW:
						subscriber.onOverflow();
						break;
					}
				})
				// .fileHashing(false) // defaults to true
				// .fileHasher(FileHasher.LAST_MODIFIED_TIME)
				// .logger(logger) // defaults to
				// LoggerFactory.getLogger(DirectoryWatcher.class)
				// .watchService(watchService) // defaults based on OS to either JVM
				// WatchService or the JNA macOS WatchService
				.build();
		watcher.watchAsync();
	}

	@Override
	public void close() throws InterruptedException, IOException {
		watcher.close();
	}

}
