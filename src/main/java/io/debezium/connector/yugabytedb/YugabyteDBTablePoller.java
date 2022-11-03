package io.debezium.connector.yugabytedb;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.kafka.connect.connector.ConnectorContext;
import org.apache.kafka.connect.errors.ConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yb.client.GetDBStreamInfoResponse;
import org.yb.client.YBClient;
import org.yb.master.MasterReplicationOuterClass.GetCDCDBStreamInfoResponsePB.TableInfo;

/**
 * Poller thread which extends {@link Thread} to keep polling for new tables on YugabyteDB
 * server side to the provided stream ID.
 * 
 * @author Vaibhav Kushwaha (vkushwaha@yugabyte.com)
 */
public class YugabyteDBTablePoller extends Thread {
  private static final Logger LOGGER = LoggerFactory.getLogger(YugabyteDBTablePoller.class);
  private final short MAX_RETRY_COUNT = 5;

  private final YugabyteDBConnectorConfig connectorConfig;
  private final ConnectorContext connectorContext;
  private final YBClient ybClient;
  private final CountDownLatch shutdownLatch;
  private final long pollMs;

  private short retryCount = 0;
  
  private Set<TableInfo> cachedTableInfoSet = null;

  public YugabyteDBTablePoller(YugabyteDBConnectorConfig connectorConfig,
                               ConnectorContext connectorContext) {
    super();
    this.connectorConfig = connectorConfig;
    this.connectorContext = connectorContext;
    this.ybClient = YBClientUtils.getYbClient(connectorConfig);
    this.shutdownLatch = new CountDownLatch(1);
    this.pollMs = connectorConfig.newTablePollIntervalMs();
  }

  @Override
  public void run() {
    LOGGER.info("Starting thread to monitor the tables");
    while (shutdownLatch.getCount() > 0) {
      if (areThereNewTablesInStream()) {
        this.connectorContext.requestTaskReconfiguration();
      }

      try {
        LOGGER.debug("Waiting for {} ms to poll again for new tables", pollMs);
        boolean shuttingDown = shutdownLatch.await(pollMs, TimeUnit.MILLISECONDS);
        if (shuttingDown) {
          return;
        }
      } catch (InterruptedException ie) {
        LOGGER.error("Unexpected interrupted exception, ignoring", ie);
      }
    }
  }

  /**
   * Verify if there are any new tables added to the change data stream. This function continuously
   * fetches the DB stream info for a given stream and as soon as there is a change in the stream
   * info, it returns a signal to the connector.
   * @return true if there is a new table in the stream info
   */
  private boolean areThereNewTablesInStream() {
    try {
      GetDBStreamInfoResponse resp = this.ybClient.getDBStreamInfo(this.connectorConfig.streamId());

      // Reset the retry counter.
      retryCount = 0;

      if (cachedTableInfoSet == null) {
        LOGGER.debug("Cached table list in the poller thread is null, initializing it now");
        cachedTableInfoSet = resp.getTableInfoList().stream().collect(Collectors.toSet());
      } else {
        if (cachedTableInfoSet.size() != resp.getTableInfoList().size()) {
          // TODO: We can also check for a condition whether the new added tables are a part of the
          // include list (if they satisfy the regex criteria), if they don't satisfy then we
          // should not restart.

          String message = "Found {} new table(s), signalling context reconfiguration";
          LOGGER.info(message, resp.getTableInfoList().size() - cachedTableInfoSet.size());

          Set<TableInfo> tableInfoSetFromResponse =
            resp.getTableInfoList().stream().collect(Collectors.toSet());

          if (LOGGER.isDebugEnabled()) {
            Set<TableInfo> cachedSet = new HashSet<>(cachedTableInfoSet);
            Set<TableInfo> responseSet = new HashSet<>(tableInfoSetFromResponse);

            LOGGER.debug("Common tables between the cached table info set and the set received "
                         + "from GetDBStreamInfoResponse:");
            Set<TableInfo> intersection = new HashSet<>(cachedSet);
            intersection.retainAll(responseSet);
            intersection.forEach(tableInfo -> {
              LOGGER.debug(tableInfo.getTableId().toStringUtf8());
            });

            LOGGER.debug("New tables as received in the GetDBStreamInfoResponse: ");
            Set<TableInfo> difference = new HashSet<>(responseSet);
            difference.removeAll(cachedSet);
            difference.forEach(tableInfo -> {
              LOGGER.debug(tableInfo.getTableId().toStringUtf8());
            });
          }
          
          // Update the cached table list.
          cachedTableInfoSet = tableInfoSetFromResponse;
          
          return true;
        }
      }

      return false;
    } catch (Exception e) {
      ++retryCount;

      if (retryCount > MAX_RETRY_COUNT) {
        LOGGER.error("Retries exceeded the maximum retry count in table poller thread,"
                   + " all {} retries failed", MAX_RETRY_COUNT);
        throw fail(e);
      }

      LOGGER.warn("Exception while trying to get DB stream Info in poller thread,"
                + "will retry again", e);
      return false;
    }
  }

  /**
   * Shutdown the table poller thread
   */
  public void shutdown() {
    LOGGER.info("Shutting down the poller thread to monitor tables");
    shutdownLatch.countDown();
  }

  /**
   * Return a failure with {@link RuntimeException}
   * @param t the throwable object
   * @return a RuntimeException
   */
  private RuntimeException fail(Throwable t) {
    String errorMessage = "Error while trying to get the DB stream Info in poller thread";
    LOGGER.error(errorMessage, t);

    RuntimeException runtimeException = new ConnectException(errorMessage, t);
    connectorContext.raiseError(runtimeException);

    // Shutdown the monitoring thread.
    shutdownLatch.countDown();

    return runtimeException;
  }
}
