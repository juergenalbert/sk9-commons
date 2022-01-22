package de.sk9.commons.fswatchdog.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;


public class FsWatchDogFactory {
	
	private FsWatchDogFactory() {
	}
	
	public static FsWatchDogFactory getInstance() {
		return new FsWatchDogFactory();
	}
	
	public FsWatchDog create(Path dir, Subscriber subscriber) throws IOException {
		return new FsWatchDogNative(dir, Executors.newSingleThreadExecutor(), subscriber);
		//return new FsWatchDogDirectoryWatcher(dir, subscriber);
	}
}
