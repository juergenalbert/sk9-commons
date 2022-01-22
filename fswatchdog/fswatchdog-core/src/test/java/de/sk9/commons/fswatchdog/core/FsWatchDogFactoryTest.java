package de.sk9.commons.fswatchdog.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class FsWatchDogFactoryTest {
	private Path testDir;

	@BeforeEach
	void beforeEach() throws IOException {
		testDir = Files.createTempDirectory(FsWatchDogTest.class.getSimpleName());
	}

	@Test
	@Order(0)
	void testImplClassNotSet() throws IOException {
		FsWatchDog fsWatchDog = FsWatchDogFactory.getInstance().create(testDir, null);
		assertTrue(fsWatchDog instanceof FsWatchDogDirectoryWatcher);
	}

	@Test
	@Order(1)
	void testWrongImplClassSet() throws IOException {
		System.setProperty(FsWatchDog.class.getCanonicalName(), "xxx");

		assertThrows(RuntimeException.class, () -> {
			FsWatchDogFactory.getInstance().create(testDir, null);
		});
	}

	@Test
	@Order(2)
	void testImplClassSet() throws IOException {
		System.setProperty(FsWatchDog.class.getCanonicalName(), FsWatchDogNative.class.getCanonicalName());

		FsWatchDog fsWatchDog = FsWatchDogFactory.getInstance().create(testDir, null);
		assertTrue(fsWatchDog instanceof FsWatchDogNative);
	}
}
