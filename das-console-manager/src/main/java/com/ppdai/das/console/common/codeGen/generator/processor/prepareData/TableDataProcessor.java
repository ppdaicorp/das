package com.ppdai.das.console.common.codeGen.generator.processor.prepareData;

import com.google.common.collect.Lists;
import com.ppdai.das.console.common.codeGen.entity.ExecuteResult;
import com.ppdai.das.console.common.codeGen.enums.DatabaseCategory;
import com.ppdai.das.console.common.codeGen.generator.java.context.JavaCodeGenContext;
import com.ppdai.das.console.common.codeGen.generator.processor.AbstractDataPreparer;
import com.ppdai.das.console.common.codeGen.host.java.JavaTableHost;
import com.ppdai.das.console.common.codeGen.utils.DbUtils;
import com.ppdai.das.console.dao.DaoBySqlBuilder;
import com.ppdai.das.console.dao.DataBaseDao;
import com.ppdai.das.console.dao.TableEntityDao;
import com.ppdai.das.console.dto.entry.codeGen.Progress;
import com.ppdai.das.console.dto.entry.das.DataBaseInfo;
import com.ppdai.das.console.dto.entry.das.TaskAuto;
import com.ppdai.das.console.dto.entry.das.TaskTable;
import com.ppdai.das.console.dto.model.Paging;
import com.ppdai.das.console.dto.view.TaskTableView;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Queue;

public class TableDataProcessor extends AbstractDataPreparer {

    private static DaoBySqlBuilder daoBySqlBuilder;
    private static TableEntityDao daoByTableViewSp;
    private static DataBaseDao dataBaseDao;

    static {
        daoBySqlBuilder = new DaoBySqlBuilder();
        daoByTableViewSp = new TableEntityDao();
        dataBaseDao = new DataBaseDao();
    }

    public void process(JavaCodeGenContext context) throws Exception {
        Long projectId = context.getProjectId();
        final Progress progress = context.getProgress();
        List<TaskTableView> taskTableViews = daoByTableViewSp.getTasksByProjectId(projectId);
        List<TaskAuto> taskAutos = daoBySqlBuilder.updateAndGetAllTasks(projectId);
        prepareDbFromTableViewSp(context, taskTableViews);

        Queue<TaskAuto> sqlBuilders = context.getSqlBuilders();
        for (TaskAuto sqlBuilder : taskAutos) {
            sqlBuilders.add(sqlBuilder);
        }
        this.processTaskTableView(context, progress, taskTableViews);
    }

    public void process(JavaCodeGenContext context, Long task_table_id) throws Exception {
        Long projectId = context.getProjectId();
        final Progress progress = context.getProgress();
        Paging paging = new Paging();
        paging.setData(TaskTable.builder().id(task_table_id).build());
        List<TaskTableView> taskTableViews = daoByTableViewSp.findTableEntityPageList(paging);
        if (CollectionUtils.isNotEmpty(taskTableViews)) {
            TaskTableView taskTableView = taskTableViews.get(0);
            DataBaseInfo dataBaseInfo = dataBaseDao.getMasterCologByDatabaseSetId(taskTableView.getDbset_id());
            taskTableView.setAlldbs_id(dataBaseInfo.getId());
            taskTableViews = Lists.newArrayList(taskTableViews.get(0));
        }
        List<TaskAuto> taskAutos = daoBySqlBuilder.updateAndGetAllTasks(projectId);
        prepareDbFromTableViewSp(context, taskTableViews);
        Queue<TaskAuto> sqlBuilders = context.getSqlBuilders();
        for (TaskAuto sqlBuilder : taskAutos) {
            sqlBuilders.add(sqlBuilder);
        }
        this.processTaskTableView(context, progress, taskTableViews);
    }

    private void processTaskTableView(JavaCodeGenContext context, Progress progress, List<TaskTableView> taskTableViews) throws Exception {
        final Queue<JavaTableHost> tableHosts = context.getTableHosts();
        for (final TaskTableView tableViewSp : taskTableViews) {
            final String[] tableNames = StringUtils.split(tableViewSp.getTable_names(), ",");

            final DatabaseCategory dbCategory;
            String dbType = DbUtils.getDbType(tableViewSp.getAlldbs_id());
            if (null != dbType && !dbType.equalsIgnoreCase("Microsoft SQL Server")) {
                dbCategory = DatabaseCategory.MySql;
            } else {
                dbCategory = DatabaseCategory.SqlServer;
            }

            try {
                prepareTable(context, progress, tableHosts, tableViewSp, tableNames, dbCategory);
            } catch (Throwable e) {
                throw e;
            }
        }
    }

    private void prepareTable(final JavaCodeGenContext ctx, final Progress progress,
                              final Queue<JavaTableHost> _tableHosts, final TaskTableView tableViewSp, final String[] tableNames,
                              final DatabaseCategory dbCategory) throws Exception {
        for (final String tableName : tableNames) {
            ExecuteResult result = new ExecuteResult(
                    "Build Table[" + tableViewSp.getAlldbs_id() + "." + tableName + "] Host");
            progress.setOtherMessage(result.getTaskName());
            JavaTableHost tableHost = buildTableHost(ctx, tableViewSp, tableName, dbCategory);
            result.setSuccessal(true);
            if (null != tableHost) {
                _tableHosts.add(tableHost);
            }
        }
    }

    private void prepareDbFromTableViewSp(JavaCodeGenContext codeGenCtx, List<TaskTableView> tableViewSps) throws SQLException {
        for (TaskTableView task : tableViewSps) {
            addDatabaseSet(codeGenCtx, task.getDbset_id());
        }
    }
}
