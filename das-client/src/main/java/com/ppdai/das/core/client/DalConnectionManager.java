package com.ppdai.das.core.client;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import com.ppdai.das.core.ConnectionLocator;
import com.ppdai.das.core.HintEnum;
import com.ppdai.das.core.EventEnum;
import com.ppdai.das.core.DasConfigure;
import com.ppdai.das.core.DasException;
import com.ppdai.das.core.DasLogger;
import com.ppdai.das.core.DatabaseSet;
import com.ppdai.das.core.ErrorCode;
import com.ppdai.das.core.HaContext;
import com.ppdai.das.core.SelectionContext;
import com.ppdai.das.client.Hints;
import com.ppdai.das.core.markdown.MarkdownManager;
import com.ppdai.das.core.status.StatusManager;
import com.ppdai.das.strategy.ConditionList;
import com.ppdai.das.strategy.ShardingContext;

public class DalConnectionManager {
	private DasConfigure config;
	private String logicDbName;
	private DasLogger logger;
	private ConnectionLocator locator;

	public DalConnectionManager(String logicDbName, DasConfigure config) {
		this.logicDbName = logicDbName;
		this.config = config;
		this.logger = config.getDasLogger();
		this.locator = config.getConnectionLocator();
	}

	public String getLogicDbName() {
		return logicDbName;
	}

	public DasConfigure getConfig() {
		return config;
	}

	public DasLogger getLogger() {
		return logger;
	}

	public DalConnection getNewConnection(Hints hints, boolean useMaster, EventEnum operation, HaContext ha)
			throws SQLException {
		DalConnection connHolder = null;
		String realDbName = logicDbName;
		try
		{
			if(StatusManager.getDatabaseSetStatus(config.getAppId(), logicDbName).isMarkdown())
				throw new DasException(ErrorCode.MarkdownLogicDb, logicDbName);

			boolean isMaster = hints.is(HintEnum.masterOnly) || useMaster;
			boolean isSelect = operation == EventEnum.QUERY;

			connHolder = getConnectionFromDSLocator(hints, isMaster, isSelect, ha);

			connHolder.setAutoCommit(true);
			connHolder.applyHints(hints);

			realDbName = connHolder.getDatabaseName();
		}
		catch(SQLException ex)
		{
			logger.getConnectionFailed(realDbName, ex);
			throw ex;
		}
		return connHolder;
	}

	public String evaluateShard(Hints hints) throws SQLException {
		DatabaseSet dbSet = config.getDatabaseSet(logicDbName);
		String shardId;

		if(!dbSet.isShardingSupported())
			return null;

		shardId = hints.getShard();
		if(shardId == null)
			shardId = getShardId(hints);

		// We allow this happen
//		if(shardId == null)
//			return null;

		dbSet.validate(shardId);

		return shardId;
	}
	
	private String getShardId(Hints hints) throws SQLException {
	    Set<String> shards = config.getDatabaseSet(logicDbName).getStrategy().locateDbShards(new ShardingContext(config, logicDbName, hints, ConditionList.andList()));
	    if(shards.isEmpty())
	        return null;

	    if(shards.size() == 1) 
	        return shards.iterator().next();
	    
	    throw new IllegalStateException("More than one shards detected" + shards);
	}

	private DalConnection getConnectionFromDSLocator(Hints hints,
													 boolean isMaster, boolean isSelect, HaContext ha) throws SQLException {
		Connection conn;
		String allInOneKey;
		DatabaseSet dbSet = config.getDatabaseSet(logicDbName);
		String shardId = null;

		if(dbSet.isShardingSupported()){
			shardId = hints.getShard();
			if(shardId == null)
				shardId = getShardId(hints);
			if(shardId == null)
				throw new DasException(ErrorCode.ShardLocated, logicDbName);
			dbSet.validate(shardId);
		}

		allInOneKey = select(logicDbName, dbSet, hints, shardId, isMaster, isSelect, ha);
		
		try {	
			conn = locator.getConnection(allInOneKey);
			DbMeta meta = DbMeta.createIfAbsent(allInOneKey, dbSet.getDatabaseCategory(), conn);
			return new DalConnection(conn, isMaster, shardId, meta);
		} catch (Throwable e) {
			throw new DasException(ErrorCode.CantGetConnection, e, allInOneKey);
		}
	}
	
	private String select(String logicDbName, DatabaseSet dbSet, Hints hints, String shard, boolean isMaster, boolean isSelect, HaContext ha) throws DasException {
	    SelectionContext context = new SelectionContext(config.getAppId(), logicDbName, hints, shard, isMaster, isSelect, ha);
	    
	    if(shard == null) {
	        context.setMasters(dbSet.getMasterDbs());
	        context.setSlaves(dbSet.getSlaveDbs());
	    }else{
            context.setMasters(dbSet.getMasterDbs(shard));
            context.setSlaves(dbSet.getSlaveDbs(shard));	        
	    }
	    
	    return config.getDatabaseSelector().select(context);
	}
	
	public <T> T doInConnection(ConnectionAction<T> action, Hints hints)
			throws SQLException {
	    action.config = config;
	    // If HA disabled or not query, we just directly call _doInConnnection

		if(!StatusManager.getHaStatus().isEnabled()
				|| action.operation != EventEnum.QUERY)
			return _doInConnection(action, hints);

		HaContext highAvalible = new HaContext();
		highAvalible.setDatabaseCategory(config.getDatabaseSet(logicDbName).getDatabaseCategory());
		action.highAvalible = highAvalible;
		do{
			try {
				return _doInConnection(action, hints);
			} catch (SQLException e) {
				highAvalible.update(e);
			}
		}while(highAvalible.needTryAgain());

		throw highAvalible.getException();
	}

	private <T> T _doInConnection(ConnectionAction<T> action, Hints hints)
			throws SQLException {
		action.initLogEntry(logicDbName, hints);
		action.start();

		Throwable ex = null;
		T result = null;
		try {
			result = action.execute();
		} catch (Throwable e) {
			MarkdownManager.detect(action.connHolder, action.start, e);
			action.error(e);
		} finally {
		    action.endExectue();
			action.populateDbMeta();
			action.cleanup();
		}

		action.end(result);
		return result;
	}
}
