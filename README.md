# logback-newrelic-appender

[![Java CI with Maven](https://github.com/newrelickk/logback-newrelic-appender/actions/workflows/maven.yml/badge.svg)](https://github.com/newrelickk/logback-newrelic-appender/actions/workflows/maven.yml)

Note: This is an unofficial package. Since this is an experimental library, please consider using more robust log shipping method (e.g. fluentd) in the production.

## Requirements

- New Relic Logs ([License Key](https://docs.newrelic.com/docs/accounts/install-new-relic/account-setup/license-key) or [Insert API key](https://docs.newrelic.com/docs/apis/get-started/intro-apis/types-new-relic-api-keys#event-insert-key) is required)
- New Relic APM Agent if you'd like to enable Logs in Context
- logback 1.2.0 or above and com.newrelic.logging.logback 2.0 or above.

## Usage

1. Configure dependency.

```
<dependency>
    <groupId>io.github.newrelickk</groupId>
    <artifactId>logback-newrelic-appender</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>
```

2. Configure the appender. We strongly recommend using `com.newrelic.logging.logback.NewRelicEncoder` as an encoder.

   Here is an configuration example. You can configure other options as long as NLog supports.
   
    1. With logback.xml.

    ```xml
    <configuration debug="true">

        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>
                    %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
                </pattern>
            </encoder>
        </appender>

        <appender name="NewRelicLogs" class="io.github.newrelickk.logback.NewRelicLogsAppender">
            <url>https://log-api.newrelic.com/logs/v1</url>
            <licenseKey>REPLACE_YOUR_LICENSE_KEY</licenseKey>
            <!-- if you'd like to use api key, please use the following line instead of the above line.-->
            <!-- <apiKey>REPLACE_YOUR_API_KEY</apiKey> -->
            <bufferSize>10</bufferSize>
            <maxCapacity>100</maxCapacity>
            <queueSize>10</queueSize>
            <encoder class="com.newrelic.logging.logback.NewRelicEncoder"/>
        </appender>
        
        <!-- if you'd like to Log in Context, use the NewRelicAsyncAppender. (see New Relic doc for details) -->
        <appender name="ASYNC" class="com.newrelic.logging.logback.NewRelicAsyncAppender">
            <appender-ref ref="NewRelicLogs" />
        </appender>

        <root level="DEBUG">
            <appender-ref ref="ASYNC" />
            <appender-ref ref="STDOUT" />
        </root>

    </configuration>
    ```
  
3. (Option) Instead of specify LicenseKey or APIKey in the code or configuration file, you can specify `NEW_RELIC_LICENSE_KEY` or `NEW_RELIC_API_KEY` environment variable for a New Relic License Key.
  
4. Output your log with NLog.

5. You will see your log in New Relic Logs.

## Configuration Options.

- url (required): New Relic Logs endpoint. https://log-api.newrelic.com/logs/v1 (US) or https://log-api.eu.newrelic.com/log/v1 (EU).
- licenseKey (required but you can specify through environment variable): New Relic license key.
- bufferSize (option, default 10): The buffer size of log events. This appender buffers until either buffersize of logs are stored or bufferSeconds has passed.
- buferSeonds (option, default 10): The buffer time. 
- queueSize (option, default 256): The max number of buffering log events. The appender refuses to receive log event if the remaining capacity of buffering queue is less than 1/5 of queueSize.
- encoder (reqruied): Logback encoder. The specified encoder must generate JSON element formatted text `{"key": value}`.

## Release Notes

## File an issue

Please enable [NLog Internal Logging](https://github.com/NLog/NLog/wiki/Internal-Logging) and submit issue with your environment, configuration and logs.
