package io.debezium.connector.yugabytedb;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.DebeziumException;
import io.debezium.pipeline.spi.Partition;
import io.debezium.util.Collect;

/**
 * Partition class to represent the Debezium partitions for YugabyteDB.
 *
 * @author Vaibhav Kushwaha (vkushwaha@yugabyte.com)
 */
public class YBPartition implements Partition {
    private static final String PARTITION_KEY = "yb_partition";

    private final String tabletId;
    private final String tableId;

    private boolean isTableColocated;

    public YBPartition(String tableId, String tabletId) {
        this.tableId = tableId;
        this.tabletId = tabletId;

        // By default, assume that the table is not colocated.
        this.isTableColocated = false;
    }

    public YBPartition(String tableId, String tabletId, boolean isTableColocated) {
        this.tableId = tableId;
        this.tabletId = tabletId;
        this.isTableColocated = isTableColocated;
    }

    @Override
    public Map<String, String> getSourcePartition() {
        return Collect.hashMapOf(PARTITION_KEY, getId());
    }

    public String getTableId() {
        return this.tableId;
    }

    public String getTabletId() {
        return this.tabletId;
    }

    /**
     * @return the ID of this partition in the format {@code tableId.tabletId}, this is essentially
     * the same thing as using {@code p.getTableId() + "." + p.getTabletId()}
     */
    public String getId() {
//        if (!isTableColocated()) {
//            // If table is not colocated, we need to process the table just by its tablet ID.
//            return getTabletId();
//        }

        return getTableId() + "." + getTabletId();
    }

    public boolean isTableColocated() {
        return this.isTableColocated;
    }

    public void markTableAsColocated() {
        this.isTableColocated = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final YBPartition other = (YBPartition) obj;

        return this.tabletId.equals(other.getTabletId()) && this.tableId.equals(other.getTableId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return String.format("YBPartition {tableId=%s, tabletId=%s}", this.tableId, this.tabletId);
    }

    static class Provider implements Partition.Provider<YBPartition> {
        private final YugabyteDBConnectorConfig connectorConfig;
        private static final Logger LOGGER = LoggerFactory.getLogger(YBPartition.class);

        Provider(YugabyteDBConnectorConfig connectorConfig) {
            this.connectorConfig = connectorConfig;
        }

        @Override
        public Set<YBPartition> getPartitions() {
            String tabletList = this.connectorConfig.getConfig().getString(YugabyteDBConnectorConfig.TABLET_LIST);
            List<Pair<String, String>> tabletPairList;
            try {
                tabletPairList = (List<Pair<String, String>>) ObjectUtil.deserializeObjectFromString(tabletList);
                LOGGER.debug("The tablet list is " + tabletPairList);
            } catch (IOException | ClassNotFoundException e) {
                // The task should fail if tablet list cannot be deserialized
                throw new DebeziumException("Error while deserializing tablet list", e);
            }

            Set<YBPartition> partitions = new HashSet<>();
            for (Pair<String, String> tabletPair : tabletPairList) {
                partitions.add(new YBPartition(tabletPair.getLeft(), tabletPair.getRight()));
            }
            LOGGER.debug("The partition being returned is " + partitions);
            return partitions;
        }
    }
}
