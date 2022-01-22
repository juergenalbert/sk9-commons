package de.sk9.commons.fswatchdog.core;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//@SuppressWarnings("java:S125")
class FsWatchDogNative implements FsWatchDog {
	private static Logger log = LogManager.getLogger(FsWatchDogNative.class);

	private WatchService watcher;
	private final Map<WatchKey, Path> keys;
	private Subscriber subscriber;
	Thread thread;

	private boolean configChanged;

	public FsWatchDogNative(Path dir, Executor executor, Subscriber subscriber) {
		this.subscriber = subscriber;
		keys = new HashMap<>();

		try {
			watcher = FileSystems.getDefault().newWatchService();
			registerAll(dir);

			executor.execute(new Runnable() {
				@Override
				public void run() {
					processEvents();
				}
			});
		} catch (IOException ex) {
			subscriber.onError(ex);
		}
	}
	
	public void close() throws InterruptedException {
		if (thread != null) {
			thread.interrupt();
			thread.join();
		}
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		Path prev = keys.get(key);
		if (prev == null) {
			log.debug(() -> "registering directory " + dir);
		} else {
			if (!dir.equals(prev)) {
				log.debug(() -> "updating registration for directory " + dir);
			}
		}
		keys.put(key, dir);
		configChanged = true;
	}

	private void registerAll(final Path start) throws IOException {
		// register directory and sub-directories
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException ex) {
				log.warn(() -> "failed watching file/directory " + file + ", cause: " + ex);
				subscriber.onError(ex);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@SuppressWarnings("java:S3776")
	private void processEvents() {
		while (true) {
			if (configChanged) {
				log.debug(() -> "watching " + keys.size() + " directories:");
				keys.entrySet().stream().forEach(e -> log.debug(() -> "  " + e.getValue()));
				configChanged = false;
			}

			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				Thread.currentThread().interrupt();
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				log.warn(() -> "unknown watch key " + key);
			} else {

				for (WatchEvent<?> event : key.pollEvents()) {
					Kind<?> kind = event.kind();

					if (kind == OVERFLOW) {
						log.warn(() -> "overflow");
						subscriber.onOverflow();
					} else if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY || kind == ENTRY_DELETE) {
						// Context for directory entry event is the file name of entry
						WatchEvent<Path> ev = cast(event);
						Path name = ev.context();
						Path child = dir.resolve(name);

						log.debug(() -> "event " + kind.name() + ": " + child);
						
						if (kind == ENTRY_CREATE) {
							try {
								if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
									try (Stream<Path> walk = Files.walk(child)) {
										walk.forEach(p -> {
											log.debug(() -> "created: " + p);
											subscriber.onCreate(p);
										});
									}
									registerAll(child);
								} else {
									log.debug(() -> "created: " + child);
									subscriber.onCreate(child);
								}
							} catch (IOException ioe) {
								subscriber.onError(ioe);
							}
						} else if (kind == ENTRY_MODIFY) {
							log.debug(() -> "modified: " + child);
							subscriber.onModify(child);
						} else if (kind == ENTRY_DELETE) {
							log.debug(() -> "deleted: " + child);
							subscriber.onDelete(child);
						}
					}
				}

				// reset key and remove from set if directory no longer accessible
				boolean valid = key.reset();
				if (!valid) {
					keys.remove(key);

					// all directories are inaccessible
					if (keys.isEmpty()) {
						break;
					}
				}
			}
		}
	}
}
