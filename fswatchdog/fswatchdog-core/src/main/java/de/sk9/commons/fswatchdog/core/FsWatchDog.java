package de.sk9.commons.fswatchdog.core;

import java.io.IOException;

public interface FsWatchDog {

	void close() throws InterruptedException, IOException;

}
