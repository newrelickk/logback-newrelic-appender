package io.github.newrelickk.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * This appender buffers the ILoggingEvents based on count and time,
 *  and then send them to New Relic Logs.
 */
public class NewRelicLogsAppender extends NewRelicLogsAppenderBase<ILoggingEvent> {

    @Override
    protected String generateBody(List<ILoggingEvent> items) {
        var sb = new StringBuilder();
        sb.append("[{\"common\":{\"attributes\":{");
        boolean isFirst = true;
        for(var entry : attributeMap.entrySet()){
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(",");
            }
            sb.append("\"");
            sb.append(entry.getKey());
            sb.append("\":\"");
            sb.append(entry.getValue());
            sb.append("\"");
        }
        sb.append("}},\"logs\":[");
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

