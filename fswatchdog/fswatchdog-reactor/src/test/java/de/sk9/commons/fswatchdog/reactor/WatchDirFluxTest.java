package de.sk9.commons.fswatchdog.reactor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import de.sk9.commons.fswatchdog.reactive.FsEvent;
import de.sk9.commons.fswatchdog.reactive.FsEvent.Type;
import de.sk9.commons.fswatchdog.reactive.FsWatchDogFlowPublisher;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class WatchDirFluxTest {
	
	private static final String FILE_PREFIX = null;
	private static Path testDir1;
	private static Path testDir2;


	@BeforeEach
	void beforeEach() throws IOException {
		testDir1 = Files.createTempDirectory(WatchDirFluxTest.class.getSimpleName());
		testDir2 = Files.createTempDirectory(WatchDirFluxTest.class.getSimpleName());
	}

	@Test
	void testTwoDirs() throws IOException, InterruptedException {
		Flux<Path> dirFlux = Flux.just(testDir1, testDir2); 
		
		Flux<FsEvent> eventsFlux = dirFlux
			.flatMap(path -> JdkFlowAdapter.flowPublisherToFlux(new FsWatchDogFlowPublisher(path)))
			.log();

		Subscriber<FsEvent> subscriber = new FSEventSubscriber();		
		Subscriber<FsEvent> mock = spy(subscriber);
		
		eventsFlux.subscribe(mock);
		
		Files.createTempFile(testDir1, FILE_PREFIX, null);
		Files.createTempFile(testDir2, FILE_PREFIX, null);
		
		TimeUnit.SECONDS.sleep(1);
		
		verify(mock, times(1)).onSubscribe(any(Subscription.class));
		verify(mock, times(2)).onNext(argThat(event -> {
			return event.type() == Type.CREATED;
		}));
		verify(mock, times(0)).onError(any(Throwable.class));
		verify(mock, times(0)).onComplete();
	}

	@Test
	void testTwoHotDirs() throws IOException, InterruptedException {
		Sinks.Many<Path> dirSink = Sinks.unsafe().many().multicast().directBestEffort();
		Flux<Path> dirFlux = dirSink.asFlux(); 
		
		Flux<FsEvent> eventsFlux = dirFlux
			.flatMap(path -> JdkFlowAdapter.flowPublisherToFlux(new FsWatchDogFlowPublisher(path)))
			.log();

		Subscriber<FsEvent> subscriber = new FSEventSubscriber();		
		Subscriber<FsEvent> mock = spy(subscriber);
		
		eventsFlux.subscribe(mock);
		
		dirSink.tryEmitNext(testDir1);
		Files.createTempFile(testDir1, FILE_PREFIX, null);

		Files.createTempFile(testDir2, FILE_PREFIX, null);
		dirSink.tryEmitNext(testDir2);
		Files.createTempFile(testDir2, FILE_PREFIX, null);
		
		TimeUnit.SECONDS.sleep(1);
		
		verify(mock, times(1)).onSubscribe(any(Subscription.class));
		verify(mock, times(2)).onNext(argThat(event -> {
			return event.type() == Type.CREATED;
		}));
		verify(mock, times(0)).onError(any(Throwable.class));
		verify(mock, times(0)).onComplete();
	}

	class FSEventSubscriber implements Subscriber<FsEvent> {
		@Override
		public void onSubscribe(Subscription s) {
			s.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(FsEvent t) {
		}

		@Override
		public void onError(Throwable t) {
		}

		@Override
		public void onComplete() {
		}
	}
}
