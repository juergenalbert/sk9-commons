package de.sk9.commons.fswatchdog.reactive;

import java.nio.file.Path;

public record FsEvent (Type type, Path path) {
	
	public enum Type { CREATED, MODIFIED, DELETED }
}
