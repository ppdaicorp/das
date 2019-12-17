package com.ppdai.das.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Preconditions;
import com.ppdai.das.client.sqlbuilder.ColumnOrder;
import com.ppdai.das.core.DasDiagnose;
import com.ppdai.das.core.DasVersionInfo;
import com.ppdai.das.core.HintEnum;
import com.ppdai.das.core.KeyHolder;

/**
 * Additional parameters for operation.
 *
 * IMPORTANT NOTE:
 * Hints is not thread safe, you should never share it among threads.
 * Hints is not designed for reuse, you should never create a Hints instance and use it in multiple operations
 *
 * @author hejiehui
 *
 */
public class Hints {

    private boolean diagnoseMode;
    private DasDiagnose dasDiagnose;

    private Map<HintEnum, Object> hints = new ConcurrentHashMap<>();
    // It is not so nice to put keyholder here, but to make Task stateless, I have no other choice
    private KeyHolder keyHolder;
    private DasVersionInfo versionInfo;

    private static final Object NULL = new Object();

    private List<ColumnOrder> sorter = new ArrayList<>();

    private boolean crossShardsPageRoughly = false;

    /**
     * Creates a {@code Hints} instance.
     *
     * @return {@code Hints} instance
     */
    public static Hints hints() {
        return new Hints();
    }

    /**
     * Enable diagnose mode.
     *
     * @see DasDiagnose
     * @return this {@code Hints}
     */
    public Hints diagnose() {
        diagnoseMode = true;
        try {
            dasDiagnose = new DasDiagnose("diagnose", 1);
            set(HintEnum.userDefined1, dasDiagnose);
        } catch (Exception ignored) {
        }
        return this;
    }

    /**
     * Returns {@code DasDiagnose} instance.
     *
     * @return {@code DasDiagnose} instance
     */
    public DasDiagnose getDiagnose() {
        return dasDiagnose;
    }

    /**
     * Append diagnose info
     *
     * @param key
     * @param info
     * @return Hints
     */
    public Hints appendDiagnose(String key, Object info) {
        if(isDiagnose()) {
            dasDiagnose.append(key, info);
        }
        return this;
    }

    /**
     * Set the generated ID back into the entity.
     *
     * @return this {@code Hints}
     */
    public Hints setIdBack() {
        set(HintEnum.setIdentityBack);
        prepareInsert();
        return this;
    }

    /**
     * Insert recorder with the given ID in entity.
     *
     * @return this {@code Hints}
     */
    public Hints insertWithId() {
        return set(HintEnum.enableIdentityInsert);
    }

//    public Hints inShards(String... shardIds) {
//        //TODO finish
//        return this;
//    }

    /**
     * Set in which shard the operation will be performed.
     *
     * @param shardId
     * @return this {@code Hints}
     * @see HintEnum#shard
     */
    public Hints inShard(String shardId) {
        hints.put(HintEnum.shard, shardId);
        return this;
    }

    /**
     * Set in which shard the operation will be performed.
     *
     * @param shardId
     * @return this {@code Hints}
     * @see HintEnum#shard
     */
    public Hints inShard(int shardId) {
        hints.put(HintEnum.shard, shardId);
        return this;
    }

    /**
     * Set which table shard the operation will be performed.
     *
     * @param tableShardId
     * @return this {@code Hints}
     * @see HintEnum#tableShard
     */
    public Hints inTableShard(String tableShardId) {
        hints.put(HintEnum.tableShard, tableShardId);
        return this;
    }

    /**
     * Set which table shard the operation will be performed.
     *
     * @param tableShardId
     * @return this {@code Hints}
     * @see HintEnum#tableShard
     */
    public Hints inTableShard(int tableShardId) {
        hints.put(HintEnum.tableShard, tableShardId);
        return this;
    }
    
    /**
     * Set value used to help sharding strategy locate DB shard.
     *
     * @param value
     * @return this {@code Hints}
     * @see HintEnum#shardValue
     */
    public Hints shardValue(Object shardValue) {
        return set(HintEnum.shardValue, shardValue);
    }
    
    public boolean hasShardValue() {
        return is(HintEnum.shardValue);
    }

    /**
     * Set value used to help sharding strategy locate table shard
     *
     * @param tableShardValue
     * @return this {@code Hints}
     * @see HintEnum#tableShardValue
     */
    public Hints tableShardValue(Object tableShardValue) {
        this.setTableShardValue(tableShardValue);
        return this;
    }

    public boolean hasTableShardValue() {
        return is(HintEnum.tableShardValue);
    }

    /**
     * Set fetch size of statement.
     *
     * @param fetchSize
     * @return this {@code Hints}
     * @see HintEnum#fetchSize
     */
    public Hints fetchSize(int fetchSize) {
        Preconditions.checkArgument(fetchSize > 0, "Please input valid fetchSize > 0, fetchSize: " + fetchSize);
        set(HintEnum.fetchSize, fetchSize);
        return this;
    }
    /**
     * Set max row of statement.
     *
     * @param maxRows
     * @return this {@code Hints}
     * @see HintEnum#maxRows
     */
    public Hints maxRows(int maxRows) {
        Preconditions.checkArgument(maxRows > 0, "Please input valid maxRows > 0, maxRows: " + maxRows);
        set(HintEnum.maxRows, maxRows);
        return this;
    }

    /**
     * Set number of seconds the driver will wait for a Statement object to execute.
     *
     * @param seconds
     * @return this {@code Hints}
     * @see HintEnum#timeout
     */
    public Hints timeout(int seconds) {
        set(HintEnum.timeout, seconds);
        return this;
    }

    /**
     * Set how many seconds the slave is behind master.
     *
     * @param seconds
     * @return this {@code Hints}
     * @see HintEnum#freshness
     */
    public Hints freshness(int seconds) {
        set(HintEnum.freshness, seconds);
        return this;
    }

    /**
     * Indicate using master database even the operation can be routed to slave database
     *
     * @param masterOnly
     * @return this {@code Hints}
     * @see HintEnum#masterOnly
     */
    public Hints masterOnly() {
        set(HintEnum.masterOnly, Boolean.TRUE);
        hints.remove(HintEnum.slaveOnly);
        return this;
    }

    /**
     * Indicate using slave database even the operation is not a query.
     *
     * @param slaveOnly
     * @return this {@code Hints}
     * @see HintEnum#slaveOnly
     */
    public Hints slaveOnly() {
        set(HintEnum.slaveOnly, Boolean.TRUE);
        hints.remove(HintEnum.masterOnly);
        return this;
    }

    /**
     * Specify which db to execute the operation
     *
     * @param databaseName the connectionString part of the das.xml/config
     * @return this {@code Hints}
     */
    public Hints inDatabase(String databaseName) {
        hints.put(HintEnum.designatedDatabase, databaseName);
        return this;
    }

    /**
     * Returns {@code KeyHolder} instance.
     *
     * @return {@code KeyHolder} instance
     * @see KeyHolder
     */
    public KeyHolder getKeyHolder() {
        return keyHolder;
    }

    /**
     * Set {@code KeyHolder} instance.
     *
     * @param keyHolder
     * @return this {@code Hints}
     * @see KeyHolder
     */
    public Hints prepareInsert() {
        this.keyHolder = new KeyHolder();
        return this;
    }

    /**
     * Returns {@code DasVersionInfo} instance
     *
     * @return {@code DasVersionInfo}
     * @see DasVersionInfo
     */
    public DasVersionInfo getVersionInfo() {
        return versionInfo;
    }

    /**
     * Set {@code DasVersionInfo} instance.
     *
     * @param versionInfo
     * @return this {@code Hints}
     * @see DasVersionInfo
     */
    public Hints setVersionInfo(DasVersionInfo versionInfo) {
        this.versionInfo = versionInfo;
        return this;
    }

    /**
     * Deep copy from this {@code Hints}
     *
     * @return a clone {@code Hints} instance
     */
    public Hints clone() {
        Hints newHints = new Hints();
        newHints.hints.putAll(hints);

        // Make sure we do deep copy for Map
        Map fields = (Map)newHints.get(HintEnum.fields);
        if(fields != null)
            newHints.setFields(new LinkedHashMap<String, Object>(fields));

        newHints.keyHolder = keyHolder;
        newHints.dasDiagnose = getDiagnose();
        newHints.diagnoseMode = isDiagnose();
        newHints.versionInfo = getVersionInfo();
        return newHints;
    }

    /**
     * Constructor with version information
     */
    public Hints() {
        DasVersionInfo versionInfo = new DasVersionInfo();
        versionInfo.setDasClientVersion(DasClientVersion.getVersion());

        setVersionInfo(versionInfo);
        allowPartial();
    }

    /**
     * Make sure only shardId, tableShardId, shardValue, shardColValue will be used to locate shard Id.
     */
    public Hints cleanUp() {
        hints.remove(HintEnum.fields);
        hints.remove(HintEnum.parameters);
        return this;
    }

    /**
     * Returns <tt>true</tt> if given {@code HintEnum} is used.
     *
     * @param hint
     * @return <tt>true</tt> if given {@code HintEnum} is used
     */
    public boolean is(HintEnum hint) {
        return hints.containsKey(hint);
    }

    /**
     * Returns object associated with given {@code HintEnum}.
     *
     * @param hint
     * @return object associated with given {@code HintEnum}.
     */
    public Object get(HintEnum hint) {
        return hints.get(hint);
    }

    /**
     * Get integer associated with given {@code HintEnum}.
     *
     * @param hint
     * @param defaultValue if null is associated
     * @return integer associated with given {@code HintEnum}, defaultValue if null is associated
     */
    public Integer getInt(HintEnum hint, int defaultValue) {
        Object value = hints.get(hint);
        if(value == null)
            return defaultValue;
        return (Integer)value;
    }

    /**
     * Get integer associated with given {@code HintEnum}.
     *
     * @param hint
     * @return integer associated with given {@code HintEnum}.
     */
    public Integer getInt(HintEnum hint) {
        return (Integer)hints.get(hint);
    }

    /**
     * Get string associated with given {@code HintEnum}.
     *
     * @param hint
     * @return string associated with given {@code HintEnum}
     */
    public String getString(HintEnum hint) {
        Object value = hints.get(hint);
        if(value == null)
            return null;

        if(value instanceof String)
            return (String)value;

        return value.toString();
    }

    /**
     * Get {@code Set<String>} associated with given {@code HintEnum}.
     *
     * @param hint
     * @return {@code Set<String>} associated with given {@code HintEnum} as {@code Set<String>}
     */
    public Set<String> getStringSet(HintEnum hint) {
        return (Set<String>)hints.get(hint);
    }

    /**
     * Set dummy object with given {@code HintEnum}
     *
     * @param hint
     * @return {@code Hints}
     */
    private Hints set(HintEnum hint) {
        set(hint, NULL);
        return this;
    }

    /**
     * Set dummy given object with given {@code HintEnum}
     *
     * @param hint
     * @param value
     * @return this {@code Hints}
     */
    private Hints set(HintEnum hint, Object value) {
        hints.put(hint, value);
        return this;
    }

    /**
     * Set {@code Map<String, Object>} of column name value pair.
     *
     * @param tableShardValue
     * @return this {@code Hints}
     * @see HintEnum#shardColValues
     */
    public Hints setTableShardValue(Object tableShardValue) {
        return set(HintEnum.tableShardValue, tableShardValue);
    }

    /**
     * Set entity columns to help sharding strategy locate shard.
     *
     * @param fields
     * @return this {@code Hints}
     * @see HintEnum#fields
     */
    public Hints setFields(Map<String, ?> fields) {
        if(fields == null)
            return this;

        return set(HintEnum.fields, fields);
    }

    /**
     * Set {@code Parameter}s to help sharding strategy locate shard.
     *
     * @param parameters
     * @return this {@code Hints}
     * @see HintEnum#parameters
     */
    public Hints setParameters(List<Parameter> parameters) {
        if(parameters == null)
            return this;

        return set(HintEnum.parameters, parameters);
    }

    /**
     * Returns if insert incremental id is disabled or not.
     *
     * @return insert incremental id is disabled
     * @see HintEnum#enableIdentityInsert
     */
    public boolean isIdentityInsertDisabled() {
        return !is(HintEnum.enableIdentityInsert);
    }

    /**
     * Returns if the update field can be null value or not
     *
     * @return if the update field can be null value or not
     * @see HintEnum#updateNullField
     */
    public boolean isUpdateNullField() {
        return is(HintEnum.updateNullField);
    }

    /**
     * Returns if set the update field can be unchanged value after select from DB or not
     *
     * @return if set the update field can be unchanged value after select from DB or not
     * @see HintEnum#updateUnchangedField
     */
    public boolean isUpdateUnchangedField() {
        return is(HintEnum.updateUnchangedField);
    }

    /**
     * Returns if insert field can be null value or not.
     *
     * @return if insert field can be null value or not
     * @see HintEnum#insertNullField
     */
    public boolean isInsertNullField() {
        return is(HintEnum.insertNullField);
    }

    /**
     * Returns columns that will be included for update
     *
     * @return columns that will be included for update
     * @see HintEnum#includedColumns
     */
    public Set<String> getIncluded() {
        return getStringSet(HintEnum.includedColumns);
    }

    /**
     * Returns columns that will be excluded for update.
     *
     * @return columns that will be excluded for update
     * @see HintEnum#excludedColumns
     */
    public Set<String> getExcluded() {
        return getStringSet(HintEnum.excludedColumns);
    }

    /**
     * Returns columns that will be included for query.
     *
     * @return columns that will be included for query
     * @see HintEnum#partialQuery
     */
    public String[] getPartialQueryColumns() {
        return getStringSet(HintEnum.partialQuery).toArray(new String[getStringSet(HintEnum.partialQuery).size()]);
    }

    /**
     * Returns columns that will be included for query.
     *
     * @return this {@code Hints}
     * @see HintEnum#partialQuery
     */
    public Hints allowPartial() {
        return set(HintEnum.allowPartial);
    }
    // For internal use
    
    /**
     * Set {@code DasDiagnose} instance
     *
     * @param dasDiagnose
     * @return this {@code Hints}
     * @see DasDiagnose
     */
    public Hints setDasDiagnose(DasDiagnose dasDiagnose) {
        this.dasDiagnose = dasDiagnose;
        return this;
    }

    public <T> Hints setSize(List<T> pojos) {
        keyHolder = vaidateKeyHolder();

        if (keyHolder != null && pojos != null)
            keyHolder.initialize(pojos.size());

        return this;
    }

    public <T> Hints setSize(T pojo) {
        keyHolder = vaidateKeyHolder();

        if (keyHolder != null && pojo != null)
            keyHolder.initialize(1);

        return this;
    }

    private KeyHolder vaidateKeyHolder() {
        if (is(HintEnum.setIdentityBack))
            keyHolder = keyHolder == null ? new KeyHolder() : keyHolder;
        return keyHolder;
    }

    /**
     * Returns <tt>true</tt> if {@code setIdBack} is called.
     *
     * @return <tt>true</tt> if {@code setIdBack} is called
     */
    public boolean isSetIdBack() {
        return is(HintEnum.setIdentityBack);
    }

    /**
     * Returns <tt>true</tt> if diagnose mode is enabled
     *
     * @return <tt>true</tt> if diagnose mode is enabled
     */
    public boolean isDiagnose() {
        return diagnoseMode;
    }

    /**
     * Returns <tt>true</tt> if {@code insertWithId} is called.
     *
     * @return <tt>true</tt> if {@code insertWithId} is called
     */
    public boolean isInsertWithId() {
        return is(HintEnum.enableIdentityInsert);
    }
    
    /**
     * Returns which shard the operation will be performed.
     *
     * @return which shard the operation will be performed
     * @see HintEnum#shard
     */
    public String getShard() {
        return getString(HintEnum.shard);
    }

    /**
     * Returns value used to help sharding strategy locate DB shard.
     *
     * @return value used to help sharding strategy locate DB shard.
     * @see HintEnum#shardValue
     */
    public Object getShardValue() {
        return this.getString(HintEnum.shardValue);
    }

    /**
     * Returns value used to help sharding strategy locate table shard.
     *
     * @return value used to help sharding strategy locate table shard
     * @see HintEnum#tableShardValue
     */
    public Object getTableShardValue() {
        return getString(HintEnum.tableShardValue);
    }

    /**
     * Returns which table shard the operation will be performed.
     *
     * @return which table shard the operation will be performed
     * @see HintEnum#tableShard
     */
    public String getTableShard() {
        return getString(HintEnum.tableShard);
    }

    /**
     * Sort results in-memory by given ColumnOrders.
     *
     * @param orders
     * @return
     */
    public Hints setSorter(ColumnOrder... orders) {
        for(Object order : orders) {
            this.sorter.add((ColumnOrder) order);
        }
        return this;
    }

    public List<ColumnOrder> getSorter() {
        return sorter;
    }

    /**
     * Enable page function roughly
     */
    public Hints crossShardsPageRoughly() {
        crossShardsPageRoughly = true;
        return this;
    }

    public boolean isCrossShardsPageRoughly() {
        return crossShardsPageRoughly;
    }
//The following are methods to be tested and opened

//    /**
//     * Set the insert field can be null value.
//     *
//     * @param insertNullField
//     * @return this {@code Hints}
//     * @see HintEnum#insertNullField
//     */
//    public Hints insertNullField() {
//        set(HintEnum.insertNullField, Boolean.TRUE);
//        return this;
//    }
//
//    /**
//     * Set the update field can be null value.
//     *
//     * @param updateNullField
//     * @return this {@code Hints}
//     * @see HintEnum#updateNullField
//     */
//    public Hints updateNullField() {
//        set(HintEnum.updateNullField, Boolean.TRUE);
//        return this;
//    }
//
//    /**
//     * Set isolation level should be used to set on connection.
//     *
//     * @param isolationLevel
//     * @return this {@code Hints}
//     * @see HintEnum#isolationLevel
//     */
//    public Hints isolationLevel(int isolationLevel) {
//        set(HintEnum.isolationLevel, isolationLevel);
//        return this;
//    }


}
