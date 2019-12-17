package com.ppdai.das.core.task;

import static com.ppdai.das.core.ShardingManager.detectDistributedTransaction;
import static com.ppdai.das.core.ShardingManager.isShardingEnabled;
import static com.ppdai.das.core.ShardingManager.isTableShardingEnabled;
import static com.ppdai.das.core.ShardingManager.locateShards;
import static com.ppdai.das.core.ShardingManager.locateTableShards;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.ppdai.das.client.Hints;
import com.ppdai.das.client.Parameter;
import com.ppdai.das.client.SqlBuilder;
import com.ppdai.das.core.DasConfigureFactory;
import com.ppdai.das.core.DasException;
import com.ppdai.das.core.DasLogger;
import com.ppdai.das.core.DasVersionInfo;
import com.ppdai.das.core.ResultMerger;
import com.ppdai.das.core.client.DalClient;
import com.ppdai.das.strategy.ConditionList;

public class SqlBuilderRequest<T> implements SqlRequest<T>{
    private String appId;
    private DasLogger logger;
    private String logicDbName;
    private StatementConditionProvider provider;
    private Hints hints;
    private SqlBuilderTask<T> task;
    private Set<String> shards;
    private List<Parameter> parameters;

    public SqlBuilderRequest(String appId, String logicDbName, StatementConditionProvider provider)
             throws SQLException {
        this.appId = appId;
        logger = DasConfigureFactory.getLogger();
        this.logicDbName = logicDbName;
        this.provider = provider;
        this.hints = provider.getHints();
        this.task = provider.buildTask(appId, logicDbName);
        shards = getShards();
        reWritePage();
    }

    //Rewrite page size and offset, if shards exist
    private void reWritePage() throws SQLException {
        if(shards != null && shards.size() > 1 && hints.isCrossShardsPageRoughly()) {
            SqlBuilder sqlBuilder = provider.getRawRequest();
            sqlBuilder.reWritePage(shards.size());
        }
        this.parameters = provider.buildParameters();
    }

    private Set<String> getShards() throws SQLException {
        if (isShardingEnabled(appId, logicDbName) == false)
            return null;
        
        if(provider.allowCrossShards() == false)
            return null;
        
        shards = locateShards(appId, logicDbName, hints.clone().setParameters(parameters), provider.buildConditions());
        
        if(shards != null && shards.size() > 1)
            logger.warn(String.format("Execute on multiple shards %s detected: %s", shards, provider.getRawRequest()));
        
        return shards;
    }

    @Override
    public void validate() throws SQLException {
        detectDistributedTransaction(shards);
    }

    @Override
    public boolean isCrossShard() {
        return shards != null && shards.size() > 1;
    }

    @Override
    public Callable<T> createTask() throws SQLException {
        Hints tmpHints = hints.clone();
        if(shards != null && shards.size() == 1) {
            tmpHints.inShard(shards.iterator().next());
        }

        return create(tmpHints);
    }

    @Override
    public Map<String, Callable<T>> createTasks() throws SQLException {
        Map<String, Callable<T>> tasks = new HashMap<>();
        
        for(String shard: shards)
            tasks.put(shard, create(hints.clone().inShard(shard)));
        
        return tasks;
    }

    private Callable<T> create(Hints hints) throws SQLException {
        return new SqlBuilderTaskCallable<>(appId, logicDbName, provider, hints, Parameter.duplicate(parameters), task);
    }

    @Override
    public ResultMerger<T> getMerger() {
        return provider.buildMerger();
    }

    @Override
    public void endExecution() {

    }

    private static class SqlBuilderTaskCallable<T> implements Callable<T> {
        private String appId;
        private String logicDbName;
        private DalClient client;
        private StatementConditionProvider provider;
        private ConditionList conditions;
        private Hints hints;
        private SqlBuilderTask<T> task;
        private ResultMerger<T> merger;
        private List<Parameter> parameters;
        private Set<String> tableShards;

        public SqlBuilderTaskCallable(String appId, String logicDbName, StatementConditionProvider provider, Hints hints, List<Parameter> parameters, SqlBuilderTask<T> task)
                throws SQLException {
            this.appId = appId;
            this.logicDbName = logicDbName;
            this.client = DasConfigureFactory.getClient(appId, logicDbName);
            this.provider = provider;
            this.hints = hints;
            this.task = task;
            conditions = provider.buildConditions();
            this.parameters = parameters;
            this.merger = provider.buildMerger();
        }
        
        @Override
        public T call() throws Exception {
            if(isCrossShardAction())
                return executeByTableShards();
            else
                return task.execute(client, provider, parameters, hints);
        }
        
        private boolean isCrossShardAction() throws SQLException {
            if(provider.allowCrossTableShards() == false)
                return false;

            if(isTableShardingEnabled(appId, logicDbName) == false)
                return false;

            if(provider.isTableShardDecided(appId, logicDbName))
                return false;

            tableShards = locateTableShards(appId, logicDbName, hints.setParameters(parameters), conditions);
            return tableShards.size() > 0 ;
        }

        private T executeByTableShards() throws SQLException {
            Hints localHints;
            for(String curTableShardId: tableShards) {
                localHints = hints.clone().inTableShard(curTableShardId);

                Throwable error = null;
                try {
                    T partial = task.execute(client, provider, Parameter.duplicate(parameters), localHints);
                    merger.addPartial(curTableShardId, partial);
                } catch (Throwable e) {
                    error = e;
                }

                DasException.handleError("Error when execute table shard operation", error);
            }
            return merger.merge();
        }
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public DasVersionInfo getVersionInfo() {
        return hints.getVersionInfo();
    }

    @Override
    public Hints getHints() {
        return hints;
    }
}