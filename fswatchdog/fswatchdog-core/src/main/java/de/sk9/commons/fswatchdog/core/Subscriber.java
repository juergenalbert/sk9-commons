package de.sk9.commons.fswatchdog.core;

import java.io.IOException;
import java.nio.file.Path;

public interface Subscriber {
	void onCreate(Path path);
	void onModify(Path path);
	void onDelete(Path path);
	void onOverflow();
	void onError(IOException ioe);
}
