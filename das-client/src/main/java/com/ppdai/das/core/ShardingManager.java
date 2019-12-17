package com.ppdai.das.core;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.ppdai.das.client.Hints;
import com.ppdai.das.core.client.DalTransactionManager;
import com.ppdai.das.strategy.ConditionList;
import com.ppdai.das.strategy.ShardingContext;
import com.ppdai.das.strategy.ShardingStrategy;
import com.ppdai.das.strategy.TableShardingContext;

/**
 * Sharding related logic center
 * 
 * @author hejiehui
 *
 */
public class ShardingManager {
    public static boolean isShardingEnabled(String appId, String logicDbName) {
        return getDatabaseSet(appId, logicDbName).isShardingSupported();
    }
    
    public static boolean isTableShardingEnabled(String appId, String logicDbName) {
        return getDatabaseSet(appId, logicDbName).isShardByTable();
    }
    
    public static boolean isTableShardingEnabled(String appId, String logicDbName, String tableName) {
        return getDatabaseSet(appId, logicDbName).isTableShardingSupported(tableName);
    }
    
    public static String buildTableName(String appId, String logicDbName, String logicTableName, String shardId) throws SQLException {
        return getDatabaseSet(appId, logicDbName).getStrategy().getTableName(logicTableName, shardId);
    }
    
    public static DatabaseSet getDatabaseSet(String appId, String logicDbName) {
        return DasConfigureFactory.getConfigure(appId).getDatabaseSet(logicDbName);
    }
    
    /**
     * Try to locate DB shard id by hints
     * @param logicDbName
     * @param hints
     * @return shard id
     * @throws SQLException
     */
    public static String locateShardId(String appId, String logicDbName, Hints hints) throws SQLException {
        DatabaseSet dbSet = getDatabaseSet(appId, logicDbName);
        
        String shardId = hints.getShard();
        if(shardId == null) {
            if(hints.is(HintEnum.shardValue)) {
                Set<String> shards = dbSet.getStrategy().locateDbShards(new ShardingContext(appId, logicDbName, dbSet.getAllShards(), hints, ConditionList.andList()));
                return validateId(shards, dbSet.getAllShards());
            }else
                return null;
        }else {
            return validateId(shardId, dbSet.getAllShards());
        }
    }

    /**
     * Try to locate DB shard id by entity fields
     * @param appId
     * @param logicDbName
     * @param tableName
     * @param pojo
     * @return
     * @throws SQLException
     */
    public static String locateShardId(String appId, String logicDbName, String tableName, Map<String, ?> pojo) throws SQLException {
        DatabaseSet dbSet = getDatabaseSet(appId, logicDbName);

        Hints tmpHints = new Hints();
        //TODO ? this can be removed because Hints does not has this field;
        tmpHints.setFields(pojo);
        Set<String> shards = dbSet.getStrategy().locateDbShards(new ShardingContext(appId, logicDbName, dbSet.getAllShards(), tmpHints, ConditionList.ofColumns(tableName, pojo)));
                
        return validateId(shards, dbSet.getAllShards());
    }

    /**
     * Locate table shard id by hints.
     * @param logicDbName
     * @param hints
     * @return
     * @throws SQLException
     */
    public static String locateTableShardId(String appId, String logicDbName, Hints hints) throws SQLException {
        ShardingStrategy strategy = getDatabaseSet(appId, logicDbName).getStrategy();

        String tableShardId = hints.getTableShard();
        if(tableShardId == null) {
            if(hints.is(HintEnum.tableShardValue)) {
                Set<String> tableShards = strategy.locateTableShards(new TableShardingContext(appId, logicDbName, null, strategy.getAllTableShards(), hints, ConditionList.andList()));
                return validateId(tableShards, strategy.getAllTableShards());
            }else
                return null;
        }else {
            return validateId(tableShardId, strategy.getAllTableShards());
        }
    }
    
    /**
     * Locate table shard id by entity fields.
     * 
     * @param logicDbName
     * @param hints
     * @return
     * @throws SQLException
     */
    public static String locateTableShardId(String appId, String logicDbName, String tableName, Map<String, ?> pojo) throws SQLException {
        ShardingStrategy strategy = getDatabaseSet(appId, logicDbName).getStrategy();
        
        if(!strategy.isShardingEnable(tableName))
            return null;

        Hints tmpHints = new Hints();
        //TODO ? this can be removed because Hints does not has this field;
        tmpHints.setFields(pojo);

        Set<String> tableShards = strategy.locateTableShards(new TableShardingContext(appId, logicDbName, tableName, strategy.getAllTableShards(), tmpHints, ConditionList.ofColumns(tableName, pojo)));

        return validateId(tableShards, strategy.getAllTableShards());
    }
    
    public static String locateTableShardId(String appId, String logicDbName, String tableName, Hints hints, Map<String, ?> pojo) throws SQLException {
        String tableShardId = locateTableShardId(appId, logicDbName, hints);
        return tableShardId == null ? locateTableShardId(appId, logicDbName, tableName, pojo) : tableShardId; 
    }
    
    public static String locateTableShardId(String appId, String logicDbName, Hints hints, ConditionList conditions) throws SQLException {
        hints.appendDiagnose("ConditionList", Objects.toString(conditions, "conditions is null"));

        ShardingStrategy strategy = getDatabaseSet(appId, logicDbName).getStrategy();
        Set<String> tableShards = locateTableShards(appId, logicDbName, hints, conditions);
        String shardId = validateId(tableShards, strategy.getAllTableShards());

        hints.appendDiagnose("TableShardID", shardId);
        return shardId;
    }

    private static String validateId(Set<String> shards, Set<String> validateShards) throws SQLException {
        if(shards.size() == 0)
            return null;
        
        if(shards.size() > 1)
            throw new IllegalArgumentException(String.format("Expect only one shard id but found %d shard ids: %s", shards.size(), shards));

        String shardId = shards.iterator().next();
        
        return validateId(shardId, validateShards);
    }
    
    private static String validateId(String shardId, Set<String> validateShards) throws SQLException {
        if(validateShards != null && !validateShards.isEmpty() && !validateShards.contains(shardId))
            throw new SQLException("No shard defined for id: " + shardId);

        return shardId;
    }
    
    /**
     * Group pojos by shard id. Should be only used for DB that support sharding.
     * 
     * 
     * @param logicDbName
     * @param pojos
     * @return Grouped pojos
     * @throws SQLException In case locate shard id faild
     */
    public static Map<String, Map<Integer, Map<String, ?>>> shuffleEntities(String appId, String logicDbName, String tableName, List<Map<String, ?>> daoPojos) throws SQLException {
        Map<String, Map<Integer, Map<String, ?>>> shuffled = new HashMap<>();
        
        for (int i = 0; i < daoPojos.size(); i++) {
            Map<String, ?> pojo = daoPojos.get(i);
            
            String shardId = locateShardId(appId, logicDbName, tableName, pojo);

            Map<Integer, Map<String, ?>> pojosInShard = shuffled.get(shardId);
            if(pojosInShard == null) {
                pojosInShard = new LinkedHashMap<>();
                shuffled.put(shardId, pojosInShard);
            }
            
            pojosInShard.put(i, pojo);
        }

        return shuffled;
    }
    
    public static Set<String> locateShards(String appId, String logicDbName, Hints hints, ConditionList conditions) throws SQLException {
        hints.appendDiagnose("ConditionList", Objects.toString(conditions, "conditions is null"));
        if (!isShardingEnabled(appId, logicDbName))
            throw new IllegalArgumentException(String.format("Logic DB %s of App %s does not support DB shard", logicDbName, appId));
        
        DatabaseSet dbSet = DasConfigureFactory.getConfigure(appId).getDatabaseSet(logicDbName);

        Set<String> shards;
        if(hints.is(HintEnum.shard)){
            shards = new HashSet<>();
            shards.add(hints.getShard());
        } else {
            shards = dbSet.getStrategy().locateDbShards(new ShardingContext(appId, dbSet.getName(), dbSet.getAllShards(), hints, conditions));
        }
        
        for(String shardId: shards)
            dbSet.validate(shardId);

        hints.appendDiagnose("ShardID", shards);
        detectDistributedTransaction(shards);
        return shards;
    }

    /**
     * Shuffle by table shard id.
     * @param logicDbName
     * @param pojos
     * @return
     * @throws SQLException
     */
    public static Map<String, Map<Integer, Map<String, ?>>> shuffleByTable(String appId, String logicDbName, String tableName, Map<Integer, Map<String, ?>> pojos) throws SQLException {
        Map<String, Map<Integer, Map<String, ?>>> shuffled = new HashMap<>();
        
        for (Integer index: pojos.keySet()) {
            Map<String, ?> pojo = pojos.get(index);

            String shardId = locateTableShardId(appId, logicDbName, tableName, pojo);
            
            Map<Integer, Map<String, ?>> pojosInShard = shuffled.get(shardId);
            if(pojosInShard == null) {
                pojosInShard = new LinkedHashMap<>();
                shuffled.put(shardId, pojosInShard);
            }
            
            pojosInShard.put(index, pojo);
        }
        
        return shuffled;
    }
    
    public static Set<String> locateTableShards(String appId, String logicDbName, Hints hints, ConditionList conditions) throws SQLException {
        hints.appendDiagnose("ConditionList", Objects.toString(conditions, "conditions is null"));
        ShardingStrategy strategy = getDatabaseSet(appId, logicDbName).getStrategy();

        String tableShardId = locateTableShardId(appId, logicDbName, hints);//hints.getTableShardId();

        Set<String> shards = new HashSet<>();
        if(tableShardId != null) {
            shards.add(tableShardId);
        } else {
            shards = strategy.locateTableShards(new TableShardingContext(appId, logicDbName, null, strategy.getAllTableShards(), hints, conditions));
        }

        hints.appendDiagnose("TableShardID", shards);
        return shards;
    }

    public static void detectDistributedTransaction(Set<String> shardIds) throws SQLException {
        if(!DalTransactionManager.isInTransaction())
            return;
        
        if(shardIds == null)
            return;
        
        // Not allowed for distributed transaction
        if(shardIds.size() > 1)
            throw new SQLException("Potential distributed operation detected in shards: " + shardIds);
        
        String shardId = shardIds.iterator().next();
        
        isSameShard(shardId);
    }

    public static void detectDistributedTransaction(String shardId) throws SQLException {
        if(!DalTransactionManager.isInTransaction())
            return;

        isSameShard(shardId);
    }

    private static void isSameShard(String shardId) throws SQLException {
        if(!shardId.equals(DalTransactionManager.getCurrentShardId()))
            throw new SQLException("Operation is not allowed in different database shard within current transaction. Current shardId: " + DalTransactionManager.getCurrentShardId() + ". Requested shardId: " + shardId);
    }
}