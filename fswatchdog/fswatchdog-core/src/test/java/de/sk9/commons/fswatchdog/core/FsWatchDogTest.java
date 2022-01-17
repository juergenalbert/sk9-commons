package de.sk9.commons.fswatchdog.core;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FsWatchDogTest {

	private static final String DIR_PREFIX = "dir";
	private static final String FILE_PREFIX = "file";
	private Path testDir;
	private AtomicInteger created;
	private AtomicInteger modified;
	private AtomicInteger deleted;
	private AtomicInteger error;
	private FsWatchDog watchDir;
	
	@BeforeEach
	void beforeEach() throws IOException {
		testDir = Files.createTempDirectory(FsWatchDogTest.class.getSimpleName());

		created = new AtomicInteger(0);
		modified = new AtomicInteger(0);
		deleted = new AtomicInteger(0);
		error = new AtomicInteger(0);
		
		watchDir = new FsWatchDog(testDir, Executors.defaultThreadFactory(), new Subscriber() {
			@Override
			public void onCreate(Path path) {
				created.addAndGet(1);
			}
			@Override
			public void onModify(Path path) {
				modified.addAndGet(1);
			}
			@Override
			public void onDelete(Path path) {
				deleted.addAndGet(1);
			}
			@Override
			public void onOverflow() {
				throw new IllegalStateException("overflow");
			}
			@Override
			public void onError(IOException ioe) {
				throw new IllegalStateException(ioe);
			}
		});	
	}
	
	@AfterEach
	void afterEach() throws InterruptedException {
		watchDir.finish();
	}

	@Test
	void testFileLifecycle() throws IOException, InterruptedException {
		Path tempFile = Files.createTempFile(testDir, FILE_PREFIX, null);
		Files.write(tempFile, "foo".getBytes(), StandardOpenOption.APPEND);
		Files.delete(tempFile);
		
		await().untilAtomic(created, is(1));
		await().untilAtomic(modified, is(1));
		await().untilAtomic(deleted, is(1));
	}
	
	@Test
	void testFileLifecycleDeferred() throws IOException, InterruptedException {
		Path tempFile = Files.createTempFile(testDir, FILE_PREFIX, null);
		await().untilAtomic(created, is(1));

		Files.write(tempFile, "foo".getBytes(), StandardOpenOption.APPEND);
		await().untilAtomic(modified, is(1));
		
		Files.delete(tempFile);
		await().untilAtomic(deleted, is(1));
	}
	
	@Test
	void testRecursiveDirs() throws IOException {
		Path subDir = Files.createTempDirectory(testDir, DIR_PREFIX);
		Path subSubDir = Files.createTempDirectory(subDir, DIR_PREFIX);
		Files.createTempFile(subSubDir, FILE_PREFIX, null);
		
		await().untilAtomic(created, is(3));
		deleteRecursivly(subDir);

		await().untilAtomic(created, is(3));
		await().untilAtomic(modified, is(0));
		await().untilAtomic(deleted, is(3));
	}
	
	@Test
	void testRecursiveDirsDeferred() throws IOException {
		Path subDir = Files.createTempDirectory(testDir, DIR_PREFIX);
		await().untilAtomic(created, is(1));
		Path subSubDir = Files.createTempDirectory(subDir, DIR_PREFIX);
		await().untilAtomic(created, is(2));
		Files.createTempFile(subSubDir, FILE_PREFIX, null);
		
		await().untilAtomic(created, is(3));
		deleteRecursivly(subDir);
		await().untilAtomic(deleted, is(3));

		await().untilAtomic(modified, is(0));
	}
	
	private void deleteRecursivly(Path path) throws IOException {
		try (Stream<Path> walk = Files.walk(path)) {
		    walk.sorted(Comparator.reverseOrder())
		        .map(Path::toFile)
		        .peek(System.out::println)
		        .forEach(File::delete);
		}		
	}
	
	@Test
	void testFinish() throws IOException, InterruptedException {
		watchDir.finish();
		Files.createTempFile(testDir, FILE_PREFIX, null);
		TimeUnit.SECONDS.sleep(1);
		assertThat(created.intValue(), is(0));
	}
	
	@Test
	void testnotExistingPath() {
		watchDir = new FsWatchDog(Path.of("not-existing-directory") , Executors.defaultThreadFactory(), new Subscriber() {
			@Override
			public void onCreate(Path path) {
				throw new IllegalStateException("create");
			}
			@Override
			public void onModify(Path path) {
				throw new IllegalStateException("modify");
			}
			@Override
			public void onDelete(Path path) {
				throw new IllegalStateException("delete");
			}
			@Override
			public void onOverflow() {
				throw new IllegalStateException("overflow");
			}
			@Override
			public void onError(IOException ioe) {
				error.addAndGet(1);
			}
		});	
		await().untilAtomic(error, is(1));
	}
}
