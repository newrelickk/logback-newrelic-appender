package com.github.newrelickk.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.Status;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public abstract class NewRelicLogsAppenderBase extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private SubmissionPublisher<ILoggingEvent> publisher;
    private NewRelicLogSubscriber newRelicLogSubscriber;

    private String url;
    private String licenseKey;
    private int bufferSize;
    private int milliSeconds;
    private int maxCapacity;
    private Encoder<ILoggingEvent> encoder;

    public void start() {
        if (licenseKey == null) {
            try {
                var key = System.getenv("NEW_RELIC_LICENSE_KEY");
                if (key == null) {
                    addError("You should have to specify licenseKey.");
                    return;
                }
                addInfo("Use environment variable for License Key.");
                licenseKey = key;
            } catch (SecurityException e) {
                addError("Exception occurred during getting Environment variable for licenseKey. You should have to specify licenseKey in the logback configuration.", e);
                return;
            }
        }
        publisher = new SubmissionPublisher<>();
        newRelicLogSubscriber = new NewRelicLogSubscriber(bufferSize, milliSeconds, maxCapacity);
        publisher.subscribe(newRelicLogSubscriber);
        encoder.start();
        addInfo("configured NewRelicLogsAppender");
        super.start();
    }

    @Override
    protected void append(ILoggingEvent e) {
        publisher.submit(e);
    }

    @Override
    public void stop() {
        addInfo("stopping to send logs to New Relic...");
        publisher.close();
        while (!newRelicLogSubscriber.isDone) {
            addInfo("waiting for the completion...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
               //ignore
            }
        }
        addInfo("stopped to send logs to New Relic...");
        super.stop();
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public void setMilliSeconds(int milliSeconds) {
        this.milliSeconds = milliSeconds;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    class NewRelicLogSubscriber extends BufferedSubscriber<ILoggingEvent>  {

        ExecutorService pool = Executors.newFixedThreadPool(10);

        private volatile boolean isDone = false;

        public NewRelicLogSubscriber(int capacity, int milliSeconds, int maxCapacity) {
            super(capacity, milliSeconds, maxCapacity);
        }

        @Override
        protected void onOverflow(ILoggingEvent item) {
            addError("Discard a new log event due to max capacity.");
        }

        @Override
        protected void onBufferedNext(List<ILoggingEvent> items) {
            var task = new SendTask(items);
            pool.submit(task);
        }

        @Override
        public void onError(Throwable throwable) {
            addError("onError", throwable);
        }

        @Override
        public void onComplete() {
            super.onComplete();
            addInfo("New Relic Logs subscriber is being shutdown...");
            pool.shutdown();
            try {
                pool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                addError("timeout during waiting for the completion.", e);
            }
            isDone = true;
        }
    }

    class SendTask implements Runnable {

        private List<ILoggingEvent> items;

        public SendTask(List<ILoggingEvent> items) {
            this.items = items;
        }

        @Override
        public void run() {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest
                    .newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-License-Key", licenseKey)
                    .POST(HttpRequest.BodyPublishers.ofString(generateBody(items)))
                    .build();
            try {
                var resp = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 300) {
                    addError("error: " + resp.statusCode() + ": " + resp.body());
                } else {
                    addInfo(resp.statusCode() + ": " + resp.body());
                }
            } catch (IOException e) {
                //TODO retry
                addError("send error", e);
            } catch (InterruptedException e) {
                addError("send error", e);
            }
        }

        protected String generateBody(List<ILoggingEvent> items) {
            var sb = new StringBuilder();
            sb.append("[{\"logs\":[");
            int c = items.size();
            for (int i = 0; i < c; i++) {
                var item = items.get(i);
                sb.append(new String(encoder.encode(item), StandardCharsets.UTF_8));
                if (i < c - 1) {sb.append(",");}
            }
            sb.append("]}]");
            return sb.toString();
        }
    }
}
