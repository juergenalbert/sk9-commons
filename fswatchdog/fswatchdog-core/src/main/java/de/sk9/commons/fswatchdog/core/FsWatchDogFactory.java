package de.sk9.commons.fswatchdog.core;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;


public class FsWatchDogFactory {
	
	private FsWatchDogFactory() {
	}
	
	public static FsWatchDogFactory getInstance() {
		return new FsWatchDogFactory();
	}
	
	public FsWatchDog create(Path dir, Subscriber subscriber) throws IOException {
		String implClassname = System.getProperty(FsWatchDog.class.getCanonicalName());
		if (null == implClassname || implClassname.isEmpty()) {
			return new FsWatchDogDirectoryWatcher(dir, subscriber);
		} else {
			try {
				return (FsWatchDog) Class.forName(implClassname).getDeclaredConstructor(Path.class, Subscriber.class).newInstance(dir, subscriber);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException
					| ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
