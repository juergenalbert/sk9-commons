package de.sk9.commons.fswatchdog.reactive;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import de.sk9.commons.fswatchdog.core.FsWatchDog;
import de.sk9.commons.fswatchdog.core.FsWatchDogFactory;
import de.sk9.commons.fswatchdog.reactive.FsEvent.Type;

public class FsWatchDogFlowPublisher implements Publisher<FsEvent>, de.sk9.commons.fswatchdog.core.Subscriber {

	private Map<FsSubscription, Subscriber<? super FsEvent>> subscriptions = new HashMap<>();
	private FsWatchDog watchDog;
	boolean initialized = false;

	public FsWatchDogFlowPublisher(Path dir) throws IOException {
		watchDog = FsWatchDogFactory.getInstance().create(dir, this);
		initialized = true;
	}

	public void close() throws InterruptedException, IOException {
		watchDog.close();
	}

	@Override
	public void onCreate(Path path) {
		emit(new FsEvent(Type.CREATED, path));
	}

	@Override
	public void onModify(Path path) {
		emit(new FsEvent(Type.MODIFIED, path));
	}

	@Override
	public void onDelete(Path path) {
		emit(new FsEvent(Type.DELETED, path));
	}

	private void emit(FsEvent ev) {
		subscriptions.keySet().stream()
				.filter(subscription -> !subscription.isSaturated())
				.forEach(subscription -> { 
					subscription.decrement();
					subscriptions.get(subscription).onNext(ev);
				});
	}

	@Override
	public void onOverflow() {
		subscriptions.keySet().stream().forEach(s -> subscriptions.get(s).onError(new BufferOverflowException()));
	}

	@Override
	public void onError(IOException ex) {
		if (initialized) {
			subscriptions.keySet().stream().forEach(s -> subscriptions.get(s).onError(ex));
		} else {
			throw new IllegalStateException(ex);
		}
	}

	@Override
	public void subscribe(Subscriber<? super FsEvent> subscriber) {
		var subscription = new FsSubscription();
		subscriptions.put(subscription, subscriber);
		subscriber.onSubscribe(subscription);
	}

	class FsSubscription implements Subscription {
		long count = 0;

		@Override
		public void request(long n) {
			if (n <= 0) {
				throw new IllegalArgumentException();
			}
			count = n;
		}

		public void decrement() {
			if (count != Long.MAX_VALUE && count > 0) {
				count--;
			}
		}

		public boolean isSaturated() {
			return count == 0;
		}

		@Override
		public void cancel() {
			subscriptions.remove(this);
		}

	}
}
