package io.github.newrelickk.logback;

import ch.qos.logback.classic.LoggerContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Flow;

public class BufferedProcessorTest {
    @Test
    public void log() throws InterruptedException {
        Logger log = LoggerFactory.getLogger(BufferedProcessorTest.class);
        for (int i = 0; i < 100; i++) {
            log.warn("hello warn " + i);
            Thread.sleep(100);
        }
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

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
