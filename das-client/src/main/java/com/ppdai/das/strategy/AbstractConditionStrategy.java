package com.ppdai.das.strategy;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

import com.ppdai.das.client.sqlbuilder.AbstractColumn;

/**
 * This strategy provides common implementation for locating shards by conditions.
 * 
 * @author hejiehui
 *
 */
public abstract class AbstractConditionStrategy extends AbstractShardingStrategy {
    /**
     * Key used to declared columns for locating DB shards.
     */
    public static final String COLUMNS = "columns";

    /**
     * Key used to declared columns for locating table shards.
     */
    public static final String TABLE_COLUMNS = "tableColumns";

    public abstract Set<String> locateDbShardsByValue(ShardingContext ctx, Object shardValue);

    public abstract Set<String> locateDbShards(ConditionContext ctx);
    
    public abstract Set<String> locateTableShardsByValue(TableShardingContext ctx, Object tableShardValue);
    
    public abstract Set<String> locateTableShards(TableConditionContext ctx);

    private Set<String> dbShardColumns;
    private Set<String> tableShardColumns;

    @Override
    public void initialize(Map<String, String> settings) {
        super.initialize(settings);
        
        if(settings.containsKey(COLUMNS)) {
            dbShardColumns = parseNames(settings.get(COLUMNS));
            setShardByDb(true);
        }
        
        if(settings.containsKey(TABLE_COLUMNS)) {
            tableShardColumns = parseNames(settings.get(TABLE_COLUMNS));
            setShardByTable(true);
        }

        if(isShardByDb() == false && isShardByTable() == false)
            throw new IllegalArgumentException("Property " + COLUMNS + " or " + TABLE_COLUMNS + " is required for the strategy");
    }

    protected boolean isDbShardingRelated(ConditionContext ctx) {
        return dbShardColumns.contains(ctx.getColumnName().toLowerCase());
    }

    protected boolean isTableShardingRelated(TableConditionContext ctx) {
        return tableShardColumns.contains(ctx.getColumnName().toLowerCase());
    }

    @Override
    public Set<String> locateDbShards(ShardingContext ctx) {
        Set<String> dbShards = ctx.hasShardValue() ?
                locateDbShardsByValue(ctx, ctx.getShardValue()):
                    locateShards(this::isDbShardingRelated, this::locateDbShards, ctx, ctx.getConditions());
        return dbShards;
    }

    @Override
    public Set<String> locateTableShards(TableShardingContext ctx) {
        Set<String> tableShards = ctx.hasTableShardValue() ?
                locateTableShardsByValue(ctx, ctx.getTableShardValue()) :
                    locateShards((tableCtx)->isTableShardingRelated((TableConditionContext)tableCtx), (tableCtx)->locateTableShards((TableConditionContext)tableCtx), ctx, ctx.getConditions());
        return tableShards;
    }

    private Set<String> locateShards(Predicate<ConditionContext> isRelated, Function<ConditionContext, Set<String>> locator, ShardingContext shardingContext, ConditionList conditions) {
        Set<String> allShards = shardingContext.getAllShards();
        Set<String> shards = conditions.isIntersected() ? new TreeSet<>(allShards) : new TreeSet<>();
        
        for(Condition condition: conditions.getConditions()) {
            Set<String> range;
            if(condition instanceof ConditionList)
                range = locateShards(isRelated, locator, shardingContext, (ConditionList)condition);
            else
                range = locateShards(isRelated, locator, shardingContext, (ColumnCondition)condition);

            if(!allShards.containsAll(range)) {
                range.removeAll(allShards);
                throw new IllegalStateException("Illegal shard detected:" + range);
            }

            if(conditions.isIntersected()) {
                shards.retainAll(range);

                if(shards.isEmpty())
                    break;
            }else {
                shards.addAll(range);

                if(allShards.size() == shards.size())
                    break;
            }
        }
        
        return shards;
    }
    
    private Set<String> locateShards(Predicate<ConditionContext> isRelated, Function<ConditionContext, Set<String>> locator, ShardingContext shardingContext, ColumnCondition condition) {
        ConditionContext context = shardingContext.create(condition);

        if(context.getValue() instanceof AbstractColumn)
            return shardingContext.getAllShards();

        if(context.getOperator() == OperatorEnum.UN_DEFINED)
            return shardingContext.getAllShards();

        if(!isRelated.test(context))
            return shardingContext.getAllShards();
        
        return locator.apply(context);
    }
}