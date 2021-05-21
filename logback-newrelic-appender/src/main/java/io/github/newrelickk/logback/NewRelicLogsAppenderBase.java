package io.github.newrelickk.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * This appender buffers the elements based on count and time,
 * and then send them to New Relic Logs.
 *
 * A structure is based on ch.qos.logback.core.AsyncAppenderBase class.
 *
 * @param <E>
 */
public abstract class NewRelicLogsAppenderBase<E> extends UnsynchronizedAppenderBase<E> {

    BlockingQueue<E> blockingQueue;

    public static final int DEFAULT_QUEUE_SIZE = 256;
    int queueSize = DEFAULT_QUEUE_SIZE;

    public static final int DEFAULT_MAX_FLUSH_TIME = 1000;
    int maxFlushTime = DEFAULT_MAX_FLUSH_TIME;

    static final int UNDEFINED = -1;
    int discardingThreshold = UNDEFINED;
    boolean neverBlock = false;

    ExecutorService bufferExecutor;
    ExecutorService taskExecutor;

    /**
     * Appender is named.
     */
    protected String name;

    private String url;
    private static final String ENV_VAR_LICENSE_KEY = "NEW_RELIC_LICENSE_KEY";
    private static final String HEADER_LICENSE_KEY = "X-License-Key";
    private String licenseKey;
    private static final String ENV_VAR_API_KEY = "NEW_RELIC_API_KEY";
    private static final String HEADER_API_KEY = "Api-Key";
    private String apiKey;
    private String authHeaderKey;
    private String authHeaderValue;
    public static final int DEFAULT_BUFFER_SIZE=10;
    private int bufferSize=DEFAULT_BUFFER_SIZE;
    public static final int DEFAULT_BUFFER_SECONDS=10;
    private int bufferSeconds=DEFAULT_BUFFER_SECONDS;

    protected Encoder<ILoggingEvent> encoder;

    public void start() {
        if (isStarted())
            return;
        if (licenseKey != null) { // use configured licenseKey
            authHeaderKey = HEADER_LICENSE_KEY;
            authHeaderValue = licenseKey;
        } else if (apiKey != null) { // use configured apiKey
            authHeaderKey = HEADER_API_KEY;
            authHeaderValue = apiKey;
        } else { // if none is configured, let's get from ENV VAR
            try {
                // try to get licenseKey first.
                var key = System.getenv(ENV_VAR_LICENSE_KEY);
                if (key != null) {
                    addInfo("Use environment variable for License Key.");
                    licenseKey = key;
                    authHeaderKey = HEADER_LICENSE_KEY;
                    authHeaderValue = licenseKey;
                } else {
                    // then try to get api key
                    key = System.getenv(ENV_VAR_API_KEY);
                    if (key != null) {
                        addInfo("Use environment variable for Api Key.");
                        apiKey = key;
                        authHeaderKey = HEADER_API_KEY;
                        authHeaderValue = apiKey;
                    } else {
                        addError("You should have to specify licenseKey or apiKey.");
                    }
                }
            } catch (SecurityException e) {
                addError("Exception occurred during getting Environment variable for licenseKey. You should have to specify licenseKey in the logback configuration.", e);
                return;
            }
        }
        if (queueSize < 1) {
            addError("Invalid queue size [" + queueSize + "]");
            return;
        }
        blockingQueue = new ArrayBlockingQueue<E>(queueSize);

        if (discardingThreshold == UNDEFINED)
            discardingThreshold = queueSize/5;
        addInfo("Setting discardingThreshold to " + discardingThreshold);

        bufferExecutor = Executors.newSingleThreadExecutor();
        taskExecutor = Executors.newFixedThreadPool(10);

        encoder.start();

        bufferExecutor.submit(new BufferTask());
        addInfo("configured NewRelicLogsAppender");
        super.start();
    }

    @Override
    public void stop() {
        if (!isStarted())
            return;

        // mark this appender as stopped
        super.stop();

        addInfo("stopping to send logs to New Relic...");

        var tasks = bufferExecutor.shutdownNow();
        addInfo("remaining buffer task: " + tasks.size());
        for (var task :
                tasks) {
            taskExecutor.submit(new SendTask(((BufferTask)task).getRemainingItems()));
        }
        addInfo("remaining log events: " + blockingQueue.size());
        while (!blockingQueue.isEmpty()) {
            ArrayList<E> res = new ArrayList<>(bufferSize);
            for (int i = 0; i < bufferSize; i++) {
                var e = blockingQueue.poll();
                if (e == null)
                    break;
                res.add(e);
            }
            taskExecutor.submit(new SendTask(res));
        }

        taskExecutor.shutdown();
        try {
            taskExecutor.awaitTermination(maxFlushTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            addError("Failed to await termination of sending. Some events may be discarded.", e);
        }

        addInfo("stopped to send logs to New Relic...");
    }

    protected void append(E eventObject) {
        if (isQueueBelowDiscardingThreshold() && isDiscardable(eventObject)) {
            return;
        }
        put(eventObject);
    }

    protected boolean isDiscardable(E eventObject) {
        return false;
    }

    /**
     * Generate the JSON formatted string for specified itesm.
     * The item is usually ILoggingEvent and com.newrelic.logging.logback.NewRelicEncoder is available.
     * @param items
     * @return
     */
    protected abstract String generateBody(List<E> items);

    private boolean isQueueBelowDiscardingThreshold() {
        return (blockingQueue.remainingCapacity() < discardingThreshold);
    }

    private void put(E eventObject) {
        if (neverBlock) {
            if (!blockingQueue.offer(eventObject)) {
                addError("Appender [" + name + "] failed to append.");
            }
        } else {
            try {
                blockingQueue.put(eventObject);
            } catch (InterruptedException e) {
                addError("Appender [" + name + "] failed to append.", e);
            }
        }
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

    public void setBufferSeconds(int bufferSeconds) {
        this.bufferSeconds = bufferSeconds;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getDiscardingThreshold() {
        return discardingThreshold;
    }

    public void setDiscardingThreshold(int discardingThreshold) {
        this.discardingThreshold = discardingThreshold;
    }

    public int getMaxFlushTime() {
        return maxFlushTime;
    }

    public void setMaxFlushTime(int maxFlushTime) {
        this.maxFlushTime = maxFlushTime;
    }

    /**
     * Returns the number of elements currently in the blocking queue.
     *
     * @return number of elements currently in the queue.
     */
    public int getNumberOfElementsInQueue() {
        return blockingQueue.size();
    }

    public void setNeverBlock(boolean neverBlock) {
        this.neverBlock = neverBlock;
    }

    public boolean isNeverBlock() {
        return neverBlock;
    }

    /**
     * The remaining capacity available in the blocking queue.
     *
     * @return the remaining capacity
     * @see {@link java.util.concurrent.BlockingQueue#remainingCapacity()}
     */
    public int getRemainingCapacity() {
        return blockingQueue.remainingCapacity();
    }

    class BufferTask implements Runnable {

        private List<E> res = new ArrayList<>(bufferSize);

        synchronized List<E> getRemainingItems() {
            return res;
        }

        @Override
        public void run() {
            for (int i = 0; i < bufferSize; i++) {
                try {
                    synchronized (this) {
                        var e = blockingQueue.poll(bufferSeconds, TimeUnit.SECONDS);
                        if (e != null)
                            res.add(e);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            if (!res.isEmpty()) {
                taskExecutor.submit(new SendTask(res));
            }
            if (!bufferExecutor.isShutdown())
                bufferExecutor.submit(new BufferTask());
        }
    }

    class SendTask implements Runnable {

        private List<E> items;

        public SendTask(List<E> items) {
            this.items = items;
        }

        @Override
        public void run() {
            var client = HttpClient.newHttpClient();
            //TODO GZIP
            var request = HttpRequest
                    .newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header(authHeaderKey, authHeaderValue)
                    .POST(HttpRequest.BodyPublishers.ofString(generateBody(items)))
                    .build();
            try {
                var resp = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 300) {
                    //TODO retry
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


    }
}
