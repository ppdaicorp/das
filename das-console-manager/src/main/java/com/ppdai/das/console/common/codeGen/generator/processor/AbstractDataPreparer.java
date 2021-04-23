package com.ppdai.das.console.common.codeGen.generator.processor;

import com.ppdai.das.console.common.codeGen.domain.StoredProcedure;
import com.ppdai.das.console.common.codeGen.entity.Resource;
import com.ppdai.das.console.common.codeGen.enums.ConditionType;
import com.ppdai.das.console.common.codeGen.enums.DatabaseCategory;
import com.ppdai.das.console.common.codeGen.generator.java.context.JavaCodeGenContext;
import com.ppdai.das.console.common.codeGen.host.AbstractParameterHost;
import com.ppdai.das.console.common.codeGen.host.DalConfigHost;
import com.ppdai.das.console.common.codeGen.host.java.*;
import com.ppdai.das.console.common.codeGen.utils.CommonUtils;
import com.ppdai.das.console.common.codeGen.utils.DbUtils;
import com.ppdai.das.console.dao.DataBaseDao;
import com.ppdai.das.console.dao.DataBaseSetEntryDao;
import com.ppdai.das.console.dao.DatabaseSetDao;
import com.ppdai.das.console.dto.entry.das.DatabaseSet;
import com.ppdai.das.console.dto.entry.das.DatabaseSetEntry;
import com.ppdai.das.console.dto.entry.das.TaskAuto;
import com.ppdai.das.console.dto.view.TaskTableView;
import com.ppdai.das.console.enums.DataFieldTypeEnum;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class AbstractDataPreparer {

    protected void addDatabaseSet(JavaCodeGenContext context, Long databaseSetid) throws SQLException {
        DatabaseSetDao daoOfDatabaseSet = new DatabaseSetDao();
        List<DatabaseSet> sets = daoOfDatabaseSet.getAllDatabaseSetById(databaseSetid);
        if (null == sets || sets.isEmpty()) {
            return;
        }
        ContextHost contextHost = context.getContextHost();
        DalConfigHost dalConfigHost = context.getDalConfigHost();
        dalConfigHost.addDatabaseSet(sets);
        for (DatabaseSet databaseSet : sets) {
            List<DatabaseSetEntry> entries = new DataBaseSetEntryDao().getAllDbSetEntryByDbSetId(databaseSet.getId());
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            dalConfigHost.addDatabaseSetEntry(entries);
            Map<Long, DatabaseSetEntry> map = dalConfigHost.getDatabaseSetEntryMap();

            for (DatabaseSetEntry entry : entries) {
                Long key = entry.getDb_Id();
                if (map.containsKey(key)) {
                    DatabaseSetEntry value = map.get(key);
                    String name = new DataBaseDao().getDataBaseInfoByDbId(key).getDbname();    //value.getConnectionString();//FIXME
                    String dbCatalog = value.getDb_catalog(); //FIXME
                    Resource resource = new Resource(name, value.getUserName(),
                            value.getPassword(), value.getDbAddress(), value.getDbPort(), dbCatalog,
                            value.getProviderName());
                    contextHost.addResource(resource);
                }
            }
        }
    }

    protected JavaTableHost buildTableHost(JavaCodeGenContext context, TaskTableView taskTableView, String tableName, DatabaseCategory dbCategory) throws Exception {
        JavaTableHost tableHost = new JavaTableHost();
        if (!DbUtils.tableExists(taskTableView.getAlldbs_id(), tableName)) {
            throw new RuntimeException(String.format("Table[%s] doesn't exist.", tableName));
        }
        Integer field_type = taskTableView.getField_type();
        tableHost.setPackageName(context.getNamespace());
        tableHost.setDatabaseCategory(getDatabaseCategory(taskTableView.getAlldbs_id()));
        tableHost.setDbSetName(taskTableView.getDbsetName());
        tableHost.setTableName(tableName);
        tableHost.setPojoClassName(getPojoClassName(taskTableView.getPrefix(), taskTableView.getSuffix(), tableName));
        tableHost.setSp(taskTableView.getCud_by_sp());
        tableHost.setApi_list(taskTableView.getApi_list());
        tableHost.setPojoViewName(taskTableView.getView_names());
        tableHost.setCustomTableame(taskTableView.getCustom_table_name());
        tableHost.setField_type(field_type);
        // tableHost.setLength(tableViewSp.getLength());

        // 主键及所有列
        List<String> primaryKeyNames = DbUtils.getPrimaryKeyNames(taskTableView.getAlldbs_id(), tableName);
        List<AbstractParameterHost> allColumnsAbstract = DbUtils.getAllColumnNames(taskTableView.getAlldbs_id(),
                tableName, new JavaColumnNameResultSetExtractor(taskTableView.getAlldbs_id(), tableName, dbCategory));
        if (null == allColumnsAbstract) {
            throw new RuntimeException(String.format("The column names of table[%s] is null", tableName));
        }
        List<JavaParameterHost> allColumns = new ArrayList<>();
        for (AbstractParameterHost h : allColumnsAbstract) {
            JavaParameterHost host = (JavaParameterHost) h;
            host.setField_type(field_type);
                /*if (field_type == DataFieldTypeEnum.UTIL_DATE.getType() && DataFieldTypeEnum.SQL_TIMESTAMP.getDetail().equals(host.getJavaClass().getName())) {
                    host.setJavaClass(DataFieldTypeEnum.UTIL_DATE.getJavaClass());
                }*/
            allColumns.add(host);
        }

        if (CollectionUtils.isNotEmpty(allColumns)) {
            List<JavaParameterHost> list = allColumns.stream().filter(field -> DataFieldTypeEnum.SQL_DATE.getDetail().equals(field.getJavaClass().getName())).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(list)) {
                allColumns.stream().forEach(p -> p.setSqlDateExist(true));
            }
        }

        List<JavaParameterHost> primaryKeys = new ArrayList<>();
        boolean hasIdentity = false;
        String identityColumnName = null;
        for (JavaParameterHost h : allColumns) {
            if (!hasIdentity && h.isIdentity()) {
                hasIdentity = true;
                identityColumnName = h.getName();
            }
            if (primaryKeyNames.contains(h.getName())) {
                h.setPrimary(true);
                primaryKeys.add(h);
            }
        }

        List<TaskAuto> currentTableBuilders =
                filterExtraMethods(context, taskTableView.getAlldbs_id(), tableName);
        List<JavaMethodHost> methods = buildSqlBuilderMethodHost(allColumns, currentTableBuilders);

        tableHost.setFields(allColumns);
        tableHost.setPrimaryKeys(primaryKeys);
        tableHost.setHasIdentity(hasIdentity);
        tableHost.setIdentityColumnName(identityColumnName);
        tableHost.setMethods(methods);

        if (tableHost.isSp()) {
            tableHost.setSpInsert(getSpaOperation(taskTableView.getAlldbs_id(), tableName, "i"));
            tableHost.setSpUpdate(getSpaOperation(taskTableView.getAlldbs_id(), tableName, "u"));
            tableHost.setSpDelete(getSpaOperation(taskTableView.getAlldbs_id(), tableName, "d"));
        }
        return tableHost;
    }

    protected DatabaseCategory getDatabaseCategory(Long allldbs_id) throws Exception {
        DatabaseCategory dbCategory = DatabaseCategory.SqlServer;
        String dbType = DbUtils.getDbType(allldbs_id);
        if (null != dbType && !dbType.equalsIgnoreCase("Microsoft SQL Server")) {
            dbCategory = DatabaseCategory.MySql;
        }
        return dbCategory;
    }

    protected String getPojoClassName(String prefix, String suffix, String tableName) {
        String className = tableName;
        if (null != prefix && !prefix.isEmpty() && className.indexOf(prefix) == 0) {
            className = className.replaceFirst(prefix, "");
        }
        if (null != suffix && !suffix.isEmpty()) {
            className = className + WordUtils.capitalize(suffix);
        }

        StringBuilder result = new StringBuilder();
        for (String str : StringUtils.split(className, "_")) {
            result.append(WordUtils.capitalize(str));
        }

        return WordUtils.capitalize(result.toString());
    }

    private List<TaskAuto> filterExtraMethods(JavaCodeGenContext ctx, Long alldbs_id, String tableName) {
        List<TaskAuto> currentTableBuilders = new ArrayList<>();
        Queue<TaskAuto> sqlBuilders = ctx.getSqlBuilders();
        Iterator<TaskAuto> iter = sqlBuilders.iterator();
        while (iter.hasNext()) {
            TaskAuto currentSqlBuilder = iter.next();
            if (currentSqlBuilder.getAlldbs_id().equals(alldbs_id)
                    && currentSqlBuilder.getTable_name().equals(tableName)) {
                currentTableBuilders.add(currentSqlBuilder);
                iter.remove();
            }
        }

        return currentTableBuilders;
    }

    private SpOperationHost getSpaOperation(Long alldbs_id, String tableName, String operation) throws Exception {
        List<StoredProcedure> allSpNames = DbUtils.getAllSpNames(alldbs_id);
        return SpOperationHost.getSpaOperation(alldbs_id, tableName, allSpNames, operation);
    }

    private List<JavaMethodHost> buildSqlBuilderMethodHost(List<JavaParameterHost> allColumns,
                                                           List<TaskAuto> currentTableSqlBuilders) throws Exception {
        List<JavaMethodHost> methods = new ArrayList<>();
        methods.addAll(buildSelectMethodHosts(allColumns, currentTableSqlBuilders));
        methods.addAll(buildDeleteMethodHosts(allColumns, currentTableSqlBuilders));
        methods.addAll(buildInsertMethodHosts(allColumns, currentTableSqlBuilders));
        methods.addAll(buildUpdateMethodHosts(allColumns, currentTableSqlBuilders));
        return methods;
    }

    private String buildSelectFieldExp(TaskAuto sqlBuilder) throws Exception {
        String fieldStr = sqlBuilder.getFields();

        if ("*".equalsIgnoreCase(fieldStr)) {
            return fieldStr;
        }

        String[] fields = fieldStr.split(",");

        String[] result = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            String field = "\"" + fields[i] + "\"";
            result[i] = field;
        }
        return StringUtils.join(result, ",");
    }

    private List<JavaMethodHost> buildSelectMethodHosts(List<JavaParameterHost> allColumns,
                                                        List<TaskAuto> currentTableBuilders) throws Exception {
        List<JavaMethodHost> methods = new ArrayList<>();

        for (TaskAuto builder : currentTableBuilders) {
            if (!builder.getCrud_type().equals("select")) {
                continue;
            }
            JavaMethodHost method = new JavaMethodHost();
            method.setCrud_type(builder.getCrud_type());
            method.setName(builder.getMethod_name());
            method.setSql(builder.getSql_content());
            method.setScalarType(builder.getScalarType());
            method.setPaging(builder.getPagination());
            method.setComments(builder.getComment());
            method.setField(buildSelectFieldExp(builder));
            method.setTableName(builder.getTable_name());

            String orderBy = builder.getOrderby();
            if (orderBy != null && !orderBy.trim().isEmpty() && orderBy.indexOf("-1,") != 0) {
                String[] str = orderBy.split(",");
                String odyExp = "\"" + str[0] + "\", ";
                odyExp = "asc".equalsIgnoreCase(str[1]) ? odyExp + "true" : odyExp + "false";
                method.setOrderByExp(odyExp);
            }
            // select sql have select field and where condition clause
            List<AbstractParameterHost> paramAbstractHosts = DbUtils.getSelectFieldHosts(builder.getAlldbs_id(),
                    builder.getSql_content(), new JavaSelectFieldResultSetExtractor());
            List<JavaParameterHost> paramHosts = new ArrayList<>();
            for (AbstractParameterHost phost : paramAbstractHosts) {
                paramHosts.add((JavaParameterHost) phost);
            }
            method.setFields(paramHosts);
            method.setParameters(buildMethodParameterHost4SqlConditin(builder, allColumns));
            method.setHints(builder.getHints());
            methods.add(method);
        }
        return methods;
    }

    private List<JavaMethodHost> buildDeleteMethodHosts(List<JavaParameterHost> allColumns,
                                                        List<TaskAuto> currentTableBuilders) throws Exception {
        List<JavaMethodHost> methods = new ArrayList<>();

        for (TaskAuto builder : currentTableBuilders) {
            if (!builder.getCrud_type().equals("delete")) {
                continue;
            }
            JavaMethodHost method = new JavaMethodHost();
            method.setCrud_type(builder.getCrud_type());
            method.setName(builder.getMethod_name());
            method.setSql(builder.getSql_content());
            method.setScalarType(builder.getScalarType());
            method.setPaging(builder.getPagination());
            method.setComments(builder.getComment());
            method.setTableName(builder.getTable_name());
            // Only have condition clause
            method.setParameters(buildMethodParameterHost4SqlConditin(builder, allColumns));
            method.setHints(builder.getHints());
            methods.add(method);
        }
        return methods;
    }

    private List<JavaMethodHost> buildInsertMethodHosts(List<JavaParameterHost> allColumns,
                                                        List<TaskAuto> currentTableBuilders) throws Exception {
        List<JavaMethodHost> methods = new ArrayList<>();

        for (TaskAuto builder : currentTableBuilders) {
            if (!builder.getCrud_type().equals("insert")) {
                continue;
            }
            JavaMethodHost method = new JavaMethodHost();
            method.setCrud_type(builder.getCrud_type());
            method.setName(builder.getMethod_name());
            method.setSql(builder.getSql_content());
            method.setScalarType(builder.getScalarType());
            method.setPaging(builder.getPagination());
            method.setComments(builder.getComment());
            method.setTableName(builder.getTable_name());
            List<JavaParameterHost> parameters = new ArrayList<>();

            // Have no where condition
            String[] fields = StringUtils.split(builder.getFields(), ",");
            Map<String, Boolean> sensitive = new HashMap<>();
            String conditions = builder.getCondition();
            if (conditions != null) {
                String[] temp = conditions.split(";");
                for (String field : temp) {
                    sensitive.put(field.split(",")[0], Boolean.parseBoolean(field.split(",")[4]));
                }
            }
            for (String field : fields) {
                for (JavaParameterHost pHost : allColumns) {
                    if (pHost.getName().equals(field)) {
                        pHost.setSensitive(sensitive.get(field) == null ? false : sensitive.get(field));
                        parameters.add(pHost);
                        break;
                    }
                }
            }

            method.setParameters(parameters);
            method.setHints(builder.getHints());
            methods.add(method);
        }
        return methods;
    }

    private List<JavaMethodHost> buildUpdateMethodHosts(List<JavaParameterHost> allColumns,
                                                        List<TaskAuto> currentTableBuilders) throws Exception {
        List<JavaMethodHost> methods = new ArrayList<>();

        for (TaskAuto builder : currentTableBuilders) {
            if (!builder.getCrud_type().equals("update")) {
                continue;
            }
            JavaMethodHost method = new JavaMethodHost();
            method.setCrud_type(builder.getCrud_type());
            method.setName(builder.getMethod_name());
            method.setSql(builder.getSql_content());
            method.setScalarType(builder.getScalarType());
            method.setPaging(builder.getPagination());
            method.setComments(builder.getComment());
            method.setField(buildSelectFieldExp(builder));
            method.setTableName(builder.getTable_name());
            List<JavaParameterHost> updateSetParameters = new ArrayList<>();
            // Have both set and condition clause
            String[] fields = StringUtils.split(builder.getFields(), ",");
            for (String field : fields) {
                for (JavaParameterHost pHost : allColumns) {
                    if (pHost.getName().equals(field)) {
                        JavaParameterHost host_ls = new JavaParameterHost(pHost);
                        updateSetParameters.add(host_ls);
                        break;
                    }
                }
            }
            method.setUpdateSetParameters(updateSetParameters);
            method.setParameters(buildMethodParameterHost4SqlConditin(builder, allColumns));
            method.setHints(builder.getHints());
            methods.add(method);
        }
        return methods;
    }

    private List<JavaParameterHost> buildMethodParameterHost4SqlConditin(TaskAuto builder,
                                                                         List<JavaParameterHost> allColumns) {
        List<JavaParameterHost> parameters = new ArrayList<>();
        String[] conditions = StringUtils.split(builder.getCondition(), ";");
        for (String condition : conditions) {
            String[] tokens = StringUtils.split(condition, ",");
            if (tokens.length == 1) { //
                JavaParameterHost host = new JavaParameterHost();
                host.setConditionType(ConditionType.valueOf(Integer.parseInt(tokens[0])));
                host.setOperator(true);
                parameters.add(host);
                continue;
            }
            String name = tokens[0];
            int type = tokens.length >= 2 ? CommonUtils.tryParse(tokens[1], -1) : -1;
            String alias = tokens.length >= 3 ? tokens[2] : "";
            for (JavaParameterHost pHost : allColumns) {
                if (pHost.getName().equals(name)) {
                    JavaParameterHost host_ls = new JavaParameterHost(pHost);
                    host_ls.setAlias(alias);
                    host_ls.setConditional(true);
                    if (type != -1){
                        host_ls.setConditionType(ConditionType.valueOf(type));
                    }

                    parameters.add(host_ls);
                    // Between need an extra parameter
                    if (host_ls.getConditionType() == ConditionType.Between) {
                        JavaParameterHost host_bw = new JavaParameterHost(host_ls);
                        String alias_bw = tokens.length >= 4 ? tokens[3] : "";
                        host_bw.setAlias(alias_bw);
                        host_bw.setConditionType(ConditionType.Between);
                        parameters.add(host_bw);
                        boolean nullable = tokens.length >= 5 ? Boolean.valueOf(tokens[4]) : false;
                        host_ls.setNullable(nullable);
                        host_bw.setNullable(nullable);
                        boolean sensitive = tokens.length >= 6 ? Boolean.valueOf(tokens[5]) : false;
                        host_ls.setSensitive(sensitive);
                        host_bw.setSensitive(sensitive);
                    } else {
                        boolean nullable = tokens.length >= 4 ? Boolean.valueOf(tokens[3]) : false;
                        host_ls.setNullable(nullable);
                        boolean sensitive = tokens.length >= 5 ? Boolean.valueOf(tokens[4]) : false;
                        host_ls.setSensitive(sensitive);
                    }
                    break;
                }
            }
        }
        return parameters;
    }
}
