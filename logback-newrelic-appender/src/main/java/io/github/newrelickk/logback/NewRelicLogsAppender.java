package io.github.newrelickk.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * This appender buffers the ILoggingEvents based on count and time,
 *  and then send them to New Relic Logs.
 */
public class NewRelicLogsAppender extends NewRelicLogsAppenderBase<ILoggingEvent> {

    @Override
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

