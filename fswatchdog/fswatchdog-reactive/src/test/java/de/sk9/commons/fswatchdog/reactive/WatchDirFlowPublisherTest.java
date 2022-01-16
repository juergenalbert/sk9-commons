package de.sk9.commons.fswatchdog.reactive;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.sk9.commons.fswatchdog.reactive.FsEvent.Type;


class WatchDirFlowPublisherTest {

	private static final String FILE_PREFIX = "file";
	private Path testDir;

	@BeforeEach
	void beforeEach() throws IOException {
		testDir = Files.createTempDirectory(WatchDirFlowPublisherTest.class.getSimpleName());
	}
	
	@Test
	void testHappyPath() throws IOException, InterruptedException {
		WatchDirFlowPublisher publisher = new WatchDirFlowPublisher(testDir);
		
		Subscriber<FsEvent> subscriber = new FSEventSubscriber();		
		Subscriber<FsEvent> mock = spy(subscriber);
		
		publisher.subscribe(mock);
		
		Path tempFile = Files.createTempFile(testDir, FILE_PREFIX, null);
		
		TimeUnit.SECONDS.sleep(1);
		
		verify(mock, times(1)).onSubscribe(any(Subscription.class));
		verify(mock, times(1)).onNext(new FsEvent(Type.CREATED, tempFile));
		verify(mock, times(0)).onError(any(Throwable.class));
		verify(mock, times(0)).onComplete();
	}

	@Test 
	void testNotSoHappyPath() throws InterruptedException {
		Path notExisting = Path.of("this-path-does-not-exist");
		assertThrows(IllegalStateException.class, () -> {
			new WatchDirFlowPublisher(notExisting);
		});
	}
	
	class FSEventSubscriber implements Subscriber<FsEvent> {

		private Subscription subscription;

		@Override
		public void onSubscribe(Subscription subscription) {
			this.subscription = subscription;
			this.subscription.request(1);
		}

		@Override
		public void onNext(FsEvent item) {
			subscription.request(1);
		}

		@Override
		public void onError(Throwable throwable) {
		}

		@Override
		public void onComplete() {
		}
	}
}
