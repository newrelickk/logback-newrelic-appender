package com.github.newrelickk.logback;

import ch.qos.logback.classic.LoggerContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public class BufferedProcessorTest {
    @Test
    public void log() {
        Logger log = LoggerFactory.getLogger(BufferedProcessorTest.class);
        for (int i = 0; i < 100; i++) {
            log.warn("hello warn " + i);
        }
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }
//    @Test
//    public void test() {
////        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
////        BufferedProcessor<String> bufferedProcessor = new BufferedProcessor<>(3, 1000);
////        MySubscriber2 subscriber = new MySubscriber2();
////        publisher.subscribe(bufferedProcessor);
////        bufferedProcessor.subscribe(subscriber);
////
////        System.out.println("Publishing Items...");
////        String[] items = {"1", "x", "2", "x", "3", "x"};
////        Arrays.asList(items).stream().forEach(i -> publisher.submit(i));
////        publisher.close();
//    }

//    public class MySubscriber2 extends BufferedSubscriber<String> {
//
//    }
    public class MySubscriber implements Flow.Subscriber<List<String>> {
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1); //a value of  Long.MAX_VALUE may be considered as effectively unbounded
        }

        @Override
        public void onNext(List<String> list) {
            System.out.println("#: " + list.size());
            for (String i: list) {
                System.out.println("Got : " + i);
            }

            subscription.request(1); //a value of  Long.MAX_VALUE may be considered as effectively unbounded
        }

        @Override
        public void onError(Throwable t) {
            t.printStackTrace();
        }

        @Override
        public void onComplete() {
            System.out.println("Done");
        }
    }
}
