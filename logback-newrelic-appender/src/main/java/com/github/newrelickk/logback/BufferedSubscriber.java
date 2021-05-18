package com.github.newrelickk.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class BufferedSubscriber<E> implements Flow.Subscriber<E> {

    private int capacity;
    private int milliSeconds;
    private Flow.Subscription subscription;
    private LinkedBlockingQueue<E> queue;

    //TODO Timeout処理をどう実装するか
    public BufferedSubscriber(int capacity, int milliSeconds, int maxCapacity) {
        this.capacity = capacity;
        this.milliSeconds = milliSeconds;
        queue = new LinkedBlockingQueue<>(maxCapacity);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(capacity);
    }

    @Override
    public void onNext(E item) {
        if (!queue.offer(item)) {
            onOverflow(item);
            return;
        }
        if (queue.size() >= capacity) {
            ArrayList<E> res = new ArrayList<>(capacity);
            for (int i = 0; i < capacity; i++) {
                res.add(queue.poll());
            }
            onBufferedNext(res);
            subscription.request(capacity);
        }
    }

    protected abstract void onOverflow(E item);

    protected abstract void onBufferedNext(List<E> items);

    @Override
    public void onComplete() {
        cleanupQueue();
    }

    protected void cleanupQueue() {
        //TODO 同期処理
        while (!queue.isEmpty()) {
            ArrayList<E> res = new ArrayList<>(capacity);
            for (int i = 0; i < queue.size() || i < capacity; i++) {
                res.add(queue.poll());
            }
            onBufferedNext(res);
            subscription.request(capacity);
        }
    }

}
