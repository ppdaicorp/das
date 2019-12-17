package com.ppdai.das.client;

import static com.ppdai.das.client.ParameterDefinition.integerVar;
import static com.ppdai.das.client.ParameterDefinition.varcharVar;
import static com.ppdai.das.client.SqlBuilder.deleteFrom;
import static com.ppdai.das.client.SqlBuilder.insertInto;
import static com.ppdai.das.client.SqlBuilder.select;
import static com.ppdai.das.client.SqlBuilder.selectAllFrom;
import static com.ppdai.das.client.SqlBuilder.selectTop;
import static com.ppdai.das.core.enums.DatabaseCategory.MySql;
import static com.ppdai.das.core.enums.DatabaseCategory.SqlServer;
import static junit.framework.Assert.assertNotSame;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.ppdai.das.client.Person.PersonDefinition;
import com.ppdai.das.core.enums.DatabaseCategory;

@RunWith(Parameterized.class)
public class DasClientDBTest extends DataPreparer {
    private final static String DATABASE_LOGIC_NAME_MYSQL = "MySqlConditionDbShard";
    private final static String DATABASE_LOGIC_NAME_SQLSVR = "SqlSvrConditionDbShard";

    private ShardInfoProvider provider;
    private static PersonDefinition p = Person.PERSON;

    @Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][]{
            {SqlServer, new DefaultProvider()},
            {SqlServer, new ShardIdProvider()},
            {SqlServer, new ShardValueProvider()},
            {MySql, new DefaultProvider()},
            {MySql, new ShardIdProvider()},
            {MySql, new ShardValueProvider()},
            });
        }

    public DasClientDBTest(DatabaseCategory dbCategory, ShardInfoProvider provider) throws SQLException {
        super(dbCategory);
        this.provider = provider;
    }
    
    public static DasClientDBTest of(DatabaseCategory dbCategory) throws SQLException {
        return new DasClientDBTest(dbCategory, new DefaultProvider());
    }

    @Override
    public String getDbName(DatabaseCategory dbCategory) {
        return dbCategory.equals(MySql) ? DATABASE_LOGIC_NAME_MYSQL : DATABASE_LOGIC_NAME_SQLSVR;
    }
    
    public static interface ShardInfoProvider {
        void process(Person p, Hints hints, int dbShard);
        void where(SqlBuilder sb, int dbShard);
        SqlBuilder insert(int dbShard);
        SqlBuilder update(int dbShard);
        void inShard(Hints hints, int dbShard);
    }
    
    private static class DefaultProvider implements ShardInfoProvider {
        public void process(Person p, Hints hints, int dbShard) {
            p.setCountryID(dbShard);
        }

        public void where(SqlBuilder sb, int dbShard) {
            sb.and(p.CountryID.eq(dbShard));
        }
        
        public SqlBuilder insert(int dbShard) {
            SqlBuilder builder = insertInto(p, p.Name, p.CountryID).values(p.Name.of("Jerry"), p.CountryID.of(dbShard));
            return builder;
        }
        
        public SqlBuilder update(int dbShard) {
            SqlBuilder builder = SqlBuilder.update(Person.PERSON).set(p.Name.eq("Tom"), p.CountryID.eq(dbShard));
            return builder;
        }
        
        public void inShard(Hints hints, int dbShard) {}
    }
    
    private static class ShardIdProvider implements ShardInfoProvider {
        public void process(Person p, Hints hints, int dbShard) {
            hints.inShard(dbShard);
        }

        public void where(SqlBuilder sb, int dbShard) {
            sb.hints().inShard(dbShard);
        }
        
        public SqlBuilder insert(int dbShard) {
            SqlBuilder builder = insertInto(p, p.Name).values(p.Name.of("Jerry"));
            builder.hints().inShard(dbShard);
            return builder;
        }
        
        public SqlBuilder update(int dbShard) {
            SqlBuilder builder = SqlBuilder.update(Person.PERSON).set(p.Name.eq("Tom"));
            builder.hints().inShard(dbShard);
            return builder;
        }
        
        public void inShard(Hints hints, int dbShard) {
            hints.inShard(dbShard);
        }
    }
    
    private static class ShardValueProvider implements ShardInfoProvider {
        public void process(Person p, Hints hints, int dbShard) {
            hints.shardValue(dbShard);
        }

        public void where(SqlBuilder sb, int dbShard) {
            sb.hints().shardValue(dbShard);
        }
        
        public SqlBuilder insert(int dbShard) {
            SqlBuilder builder = insertInto(p, p.Name).values(p.Name.of("Jerry"));
            builder.hints().shardValue(dbShard);
            return builder;
        }
        
        public SqlBuilder update(int dbShard) {
            SqlBuilder builder = SqlBuilder.update(Person.PERSON).set(p.Name.eq("Tom"));
            builder.hints().shardValue(dbShard);
            return builder;
        }

        public void inShard(Hints hints, int dbShard) {
            hints.shardValue(dbShard);
        }
    }
    
    public void process(Person p, Hints hints, int i) {
        provider.process(p, hints, i);
    }
    
    public void process(Person p, Hints hints, int i, int j) {
        process(p, hints, i);
        p.setCityID(j);
    }
    
    public SqlBuilder where(SqlBuilder sb, int i) {
        provider.where(sb, i);
        return sb;
    }
    
    public Hints hints(int i) {
        return new Hints().inShard(i);
    }
    
    public Hints hints() {
        return new Hints();
    }
    
    private long count(int i) throws SQLException {
        PersonDefinition p = Person.PERSON;
        SqlBuilder sb = new SqlBuilder().select("count(1)").from(p).intoObject();
        sb.hints().inShard(i);
        return ((Number)dao.queryObject(sb)).longValue();
    }
    
    @Before
    public void setup() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            String[] statements = new String[TABLE_MODE];
            for (int j = 0; j < TABLE_MODE; j++) {
                statements[j] = String.format("INSERT INTO person(PeopleID, Name, CountryID, CityID, ProvinceID) VALUES(%d, 'test', %d, %d, 1)", j + 1, i, j);
            }

            if(!allowInsertWithId())
                statements = DbSetupUtil.handle("Person", statements);

            BatchUpdateBuilder builder = new BatchUpdateBuilder(statements);
            builder.hints().inShard(i);
            dao.batchUpdate(builder);
        }
    }
    
    private boolean allowInsertWithId() {
        return setuper.turnOnIdentityInsert("person") == null;
    }

    @After
    public void tearDown() throws SQLException {
        for (int i = 0; i < DB_MODE; i++) {
            String[] statements = new String[] {"DELETE FROM " + TABLE_NAME};
            BatchUpdateBuilder builder = new BatchUpdateBuilder(statements);
            builder.hints().inShard(i);
            dao.batchUpdate(builder);
        }
    }

    @Test
    public void testSort() throws Exception{
        for(int i = 0; i < DB_MODE;i++) {
            Person pk = new Person();
            pk.setName("test");
            Hints hints = new Hints().sortBy(p.PeopleID.asc(), p.CountryID.desc());
            List<Person> plist = dao.queryBySample(pk, hints);
            assertNotNull(plist);
            assertEquals(8, plist.size());
            Person pre = null;
            for(Person p : plist) {
                if(pre != null) {
                    //Assert order
                   assertTrue(p.getPeopleID() >= pre.getPeopleID());
                }
                pre = p;
            }
            assertEquals("test", pk.getName());
        }
    }

    @Test
    public void testSortMap() throws Exception{
        for(int i = 0; i < DB_MODE;i++) {
            SqlBuilder sqlBuilder = SqlBuilder.selectAllFrom(p).where(p.Name.eq("test")).intoMap();
            sqlBuilder.hints()
                    .sortBy(p.PeopleID.asc(), p.CountryID.desc())
                    .diagnose();
            List<Map> plist = dao.query(sqlBuilder);
            assertNotNull(plist);
            assertEquals(8, plist.size());
            Map pre = null;
            for(Map p : plist) {
                if(pre != null) {
                    //Assert order
                    assertTrue(Integer.parseInt(p.get("PeopleID").toString())  >= Integer.parseInt(pre.get("PeopleID").toString()));
                }
                pre = p;
            }
        }
    }

    @Test
    public void testCrossShardsPageRoughly() throws Exception{
        for(int i = 0; i < DB_MODE;i++) {
            Person pk = new Person();
            pk.setName("test");
            Hints hints = new Hints();
            List<Person> plist = dao.queryBySample(pk, PageRange.atPage(2, 2, p.PeopleID.asc()), hints.crossShardsPageRoughly());
            assertNotNull(plist);
            assertEquals(2, plist.size());

            assertEquals(plist.get(0).getPeopleID(), plist.get(1).getPeopleID());
            assertNotSame(plist.get(0).getCountryID(), plist.get(1).getCountryID());
        }
    }

    @Test
    public void testQueryById() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                Hints hints = new Hints();
                process(pk, hints, i);
                pk = dao.queryByPk(pk, hints);
                assertNotNull(pk);
                assertEquals("test", pk.getName());
            }
        }
    }

    @Test
    public void testQueryBySample() throws Exception {
        for(int i = 0; i < DB_MODE;i++) {
            Person pk = new Person();
            pk.setName("test");
            Hints hints = new Hints();
            process(pk, hints, i);
            List<Person> plist = dao.queryBySample(pk, hints);
            assertNotNull(plist);
            assertEquals(4, plist.size());
            assertEquals("test", pk.getName());
        }
    }
    
    @Test
    public void testQueryBySamplePage() throws Exception {
        for(int i = 0; i < DB_MODE;i++) {
            List<Person> plist;
            Hints hints = new Hints();
            Person pk = new Person();
            pk.setName("test");

            
            process(pk, hints, i);
            plist = dao.queryBySample(pk, PageRange.atPage(1, 10, p.PeopleID), hints);
            assertList(4, plist);
            
            plist = dao.queryBySample(pk, PageRange.atPage(2, 2, p.CityID, p.CountryID), hints);
            assertList(2, plist);
            
            if(dbCategory == DatabaseCategory.MySql) {
                plist = dao.queryBySample(pk, PageRange.atPage(3, 2), hints);
                assertList(0, plist);
            }
        
            plist = dao.queryBySample(pk, PageRange.atPage(1, 10, p.PeopleID.asc()), hints);
            assertList(4, plist);
            assertOrder(plist, true);
            
            plist = dao.queryBySample(pk, PageRange.atPage(1, 10, p.PeopleID.desc()), hints);
            assertList(4, plist);
            assertOrder(plist, false);
            
            plist = dao.queryBySample(pk, PageRange.atPage(1, 10, p.PeopleID.asc(), p.CityID.desc()), hints);
            assertList(4, plist);
            assertOrder(plist, true);
        }
    }
    
    private void assertList(int size, List<Person> plist) {
        assertNotNull(plist);
        assertEquals(size, plist.size());
        
        int id = -1;
        for(Person p: plist) {
            assertEquals("test", p.getName());
        }
    }
    
    private void assertOrder(List<Person> plist, boolean asc) {
        int id = asc ? -1 : 10000;
        for(Person p: plist) {
            if(asc)
                assertTrue(p.getPeopleID() > id);
            else
                assertTrue(p.getPeopleID() < id);
        }
    }

    @Test
    public void testInsertOne() throws Exception {
        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setName("jerry");
                Hints hints = new Hints();
                process(p, hints, i, j);
                assertEquals(1, dao.insert(p, hints));
                p.setName(null);
                List<Person> plist = dao.queryBySample(p, hints(i));
                assertNotNull(plist);
                assertEquals(2, plist.size());
            }
        }
    }

    @Test
    public void testInsertWithId() throws Exception {
        if(!allowInsertWithId())
            return;

        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setPeopleID(TABLE_MODE + j + 1);
                p.setName("jerry");
                Hints hints = new Hints();
                process(p, hints, i, j);
                assertEquals(1, dao.insert(p, hints.insertWithId()));
                p = dao.queryByPk(p, hints(i));
                assertNotNull(p);
            }
        }
    }

    @Test
    public void testInsertSetIdBack() throws Exception {
        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setName("jerry" + j);
                Hints hints = new Hints();
                process(p, hints, i, j);
                assertEquals(1, dao.insert(p, hints.setIdBack()));
                assertNotNull(p.getPeopleID());
                p = dao.queryByPk(p, hints(i));
                assertNotNull(p);
                assertEquals("jerry" + j, p.getName());
            }
        }
    }

    @Test
    public void testInsertList() throws Exception {
        for(int i = 0; i < DB_MODE;i++) {
            List<Person> pl = new ArrayList<>();
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setName("jerry");
                Hints hints = new Hints();
                process(p, hints, i, j);
                pl.add(p);
            }
            Person p = new Person();
            Hints hints = new Hints();
            process(p, hints, i);
            assertEquals(TABLE_MODE, dao.insert(pl, hints));
                
            assertEquals(TABLE_MODE * 2, count(i));
        }
    }

    private List<Person> getAll(int i) throws SQLException {
        PersonDefinition p = Person.PERSON;
        SqlBuilder sb = new SqlBuilder().select("*").from(p).into(Person.class);
        sb.hints().inShard(i);
        return dao.query(sb);
    }

    @Test
    public void testInsertListWithId() throws Exception {
        if(!allowInsertWithId())
            return;
        
        for(int i = 0; i < DB_MODE;i++) {
            List<Person> pl = new ArrayList<>();
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setPeopleID(TABLE_MODE + j + 1);
                p.setName("jerry");
                Hints hints = new Hints();
                process(p, hints, i, j);
                pl.add(p);
            }
            Hints hints = new Hints();
            process(new Person(), hints, i);
            
            assertEquals(TABLE_MODE, dao.insert(pl, hints.insertWithId()));

            for(int k = 0; k < TABLE_MODE;k++) {
                Person p = new Person();
                p.setPeopleID(TABLE_MODE + k + 1);
                int id=p.getPeopleID();
                p = new Person();
                p.setPeopleID(id);
                process(p, hints, i, k);
                p = dao.queryByPk(p, hints);
//
//                p.setCountryID(i);
//                p = dao.queryByPk(p);
                assertNotNull(p);
            }
        }
    }

    @Test
    public void testInsertListSetIdBack() throws Exception {
        if(!allowInsertWithId())
            return;

        for(int i = 0; i < DB_MODE;i++) {
            List<Person> pl = new ArrayList<>();
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setName("jerry" + j);
                Hints hints = new Hints();
                process(p, hints, i, j);
                pl.add(p);
            }
            Hints hints = new Hints();
            process(new Person(), hints, i);

            assertEquals(TABLE_MODE, dao.insert(pl, hints.setIdBack()));

            for(int k = 0; k < TABLE_MODE;k++) {
                Person p = pl.get(k);
                p.setName(null);
                assertNotNull(p.getPeopleID());

                int id=p.getPeopleID();
                p = new Person();
                p.setPeopleID(id);
                process(p, hints, i, k);
                p = dao.queryByPk(p, hints);
//                p.setCountryID(i);
//                p = dao.queryByPk(p);
                assertNotNull(p);
                assertEquals("jerry" + k, p.getName());
            }
        }
    }

    @Test
    public void testBatchInsert() throws Exception {
        for(int i = 0; i < DB_MODE;i++) {
            List<Person> pl = new ArrayList<>();
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setName("jerry");
                Hints hints = new Hints();
                process(p, hints, i, j);
                pl.add(p);
            }
            Hints hints = new Hints();
            process(new Person(), hints, i);

            int[] ret = dao.batchInsert(pl, hints);
            for(int x: ret)
                assertEquals(batchRet, x);
            
            assertEquals(TABLE_MODE, ret.length);
            
            Person p = new Person();
            p.setCountryID(i);

            List<Person> plist = getAll(i);
            assertNotNull(plist);
            assertEquals(TABLE_MODE * 2, plist.size());
            for(Person p1: plist)
                assertNotNull(p1.getPeopleID());    
        }
    }

    @Test
    public void testBatchInsert2() throws Exception {
        List<Person> pl = new ArrayList<>();
        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                Person p = new Person();
                p.setName("jerry");
                p.setCountryID(i);
                p.setCityID(j);
                pl.add(p);
            }
        }
        
        int[] ret = dao.batchInsert(pl);
        for(int x: ret)
            assertEquals(batchRet, x);
        
        assertEquals(TABLE_MODE * 2, ret.length);

        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {

                Person p = new Person();
                p.setCountryID(i);
                p.setCityID(j);

                List<Person> plist = dao.queryBySample(p);
                assertNotNull(plist);
                assertEquals(2, plist.size());
                for(Person p1: plist)
                    assertNotNull(p1.getPeopleID());   
            }
        }
    }

    @Test
    public void testDeleteOne() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                Hints hints = new Hints();
                process(pk, hints, i, j);
                assertEquals(1, dao.deleteByPk(pk, hints));
                assertNull(dao.queryByPk(pk, hints));
            }
        }
    }

    @Test
    public void testDeleteBySample() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            Person sample = new Person();
            Hints hints = new Hints();
            process(sample, hints, i);
            sample.setName("test");
            assertEquals(4, dao.deleteBySample(sample, hints));
            assertEquals(0, count(i));
        }
    }

    @Test
    public void testBatchDelete() throws Exception {
        List<Person> pl = new ArrayList<>();
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                pk.setCountryID(i);
                pk.setCityID(j);
                pl.add(pk);
            }
        }
        int[] ret = dao.batchDelete(pl);
        for(int i: ret)
            assertEquals(1, i);
        
        assertEquals(TABLE_MODE * DB_MODE, ret.length);
        
        for(Person pk: pl)
            assertNull(dao.queryByPk(pk));
    }

    @Test
    public void testBatchDeleteByShard() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            List<Person> pl = new ArrayList<>();
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                Hints hints = new Hints();
                process(pk, hints, i, j);
                pl.add(pk);
            }
            
            Hints hints = new Hints();
            process(new Person(), hints, i);
            int[] ret = dao.batchDelete(pl, hints);
            for(int k: ret)
                assertEquals(1, k);
            
            assertEquals(TABLE_MODE, ret.length);
            for(Person pk: pl)
                assertNull(dao.queryByPk(pk, hints(i)));
        }
    }

    @Test
    public void testUpdate() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                pk.setName("Tom");
                Hints hints = new Hints();
                process(pk, hints, i, j);
                assertEquals(1, dao.update(pk, hints));
                assertEquals("Tom", dao.queryByPk(pk, hints).getName());
            }
        }
    }

    @Test
    public void testBatchUpdate() throws Exception {
        List<Person> pl = new ArrayList<>();
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                pk.setName("Tom");
                pk.setCountryID(i);
                pk.setCityID(j);
                pl.add(pk);
            }
        }
        int[] ret = dao.batchUpdate(pl);
        for(int i: ret)
            assertEquals(1, i);
        
        assertEquals(TABLE_MODE * DB_MODE, ret.length);
        
        for(Person pk: pl)
            assertEquals("Tom", dao.queryByPk(pk).getName());
    }

    @Test
    public void testBatchUpdateByShard() throws Exception {
        for (int i = 0; i < DB_MODE; i++) {
            List<Person> pl = new ArrayList<>();
            for (int j = 0; j < TABLE_MODE; j++) {
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                pk.setName("Tom");
                Hints hints = new Hints();
                process(pk, hints, i, j);
                pl.add(pk);
            }
            
            Hints hints = new Hints();
            process(new Person(), hints, i);
            int[] ret = dao.batchUpdate(pl, hints);
            for(int k: ret)
                assertEquals(1, k);
            
            assertEquals(4, ret.length);
            for(Person pk: pl)
                assertEquals("Tom", dao.queryByPk(pk, hints).getName());
        }
    }

    @Test
    public void testBatchUpdateBuillder() throws Exception {
        String[] statements = new String[]{
                "INSERT INTO " + TABLE_NAME + "( Name, CityID, ProvinceID, CountryID)"
                        + " VALUES( 'test', 10, 1, 1)",
                "INSERT INTO " + TABLE_NAME + "( Name, CityID, ProvinceID, CountryID)"
                        + " VALUES( 'test', 10, 1, 1)",
                "INSERT INTO " + TABLE_NAME + "( Name, CityID, ProvinceID, CountryID)"
                        + " VALUES( 'test', 10, 1, 1)",};

        BatchUpdateBuilder builder = new BatchUpdateBuilder(statements);
        builder.hints().inShard(0);
        int[] ret = dao.batchUpdate(builder);
    }

    @Test
    public void testBatchUpdateBuillderValue() throws Exception {
        BatchUpdateBuilder builder = new BatchUpdateBuilder(
                "INSERT INTO " + TABLE_NAME + "( Name, CityID, ProvinceID, CountryID) VALUES( ?, ?, ?, ?)",
                p.Name, p.CityID, p.ProvinceID, p.CountryID);
        
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        
        builder.hints().inShard(0);
        assertArrayEquals(new int[] {batchRet,  batchRet, batchRet}, dao.batchUpdate(builder));
        
    }

    @Test
    public void testBatchUpdateBuillderDef() throws Exception {
        BatchUpdateBuilder builder = new BatchUpdateBuilder(
                "INSERT INTO " + TABLE_NAME + "( Name, CityID, ProvinceID, CountryID) VALUES( ?, ?, ?, ?)",
                varcharVar("Name"), integerVar("CityID"), integerVar("ProvinceID"), integerVar("CountryID"));
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        
        builder.hints().inShard(0);
        assertArrayEquals(new int[] {batchRet,  batchRet, batchRet}, dao.batchUpdate(builder));
    }

    @Test
    public void testBatchUpdateBuillderBuilder() throws Exception {
        BatchUpdateBuilder builder = new BatchUpdateBuilder(
                insertInto(p, p.Name, p.CityID, p.ProvinceID, p.CountryID).values(
                        varcharVar("Name"), integerVar("CityID"), integerVar("ProvinceID"), integerVar("CountryID")));
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        
        builder.hints().inShard(0);
        assertArrayEquals(new int[] {batchRet,  batchRet, batchRet}, dao.batchUpdate(builder));
    }

    @Test
    public void testBatchUpdateBuillderBuilderVar() throws Exception {
        BatchUpdateBuilder builder = new BatchUpdateBuilder(
                insertInto(p, p.Name, p.CityID, p.ProvinceID, p.CountryID).values(
                        p.Name.var(), p.CityID.var(), p.ProvinceID.var(), p.CountryID.var()));
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        builder.addBatch("test", 10, 1, 1);
        
        builder.hints().inShard(0);
        assertArrayEquals(new int[] {batchRet,  batchRet, batchRet}, dao.batchUpdate(builder));
    }

    // For builder tests
    @Test
    public void testInsertBuilder() throws Exception {
        PersonDefinition p = Person.PERSON;
        
        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                SqlBuilder builder = provider.insert(i);
                assertEquals(1, dao.update(builder));

                assertEquals(TABLE_MODE + j + 1, count(i));
                Person pk = new Person();
                pk.setName("Jerry");
                List<Person> plist = dao.queryBySample(pk, hints(i));
                assertNotNull(plist);
                assertEquals(j + 1, plist.size());
            }
        }
    }
    
    @Test
    public void testUpdateBuilder() throws Exception {
        PersonDefinition p = Person.PERSON;
        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                SqlBuilder builder = provider.update(i).where(p.PeopleID.eq(j+1));
                
                assertEquals(1, dao.update(builder));
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                pk = dao.queryByPk(pk, hints(i));
                
                assertEquals("Tom", pk.getName());
                assertEquals(i, pk.getCountryID().intValue());
            }
        }
    }

    @Test
    public void testDeleteBuilder() throws Exception {
        PersonDefinition p = Person.PERSON;
        for(int i = 0; i < DB_MODE;i++) {
            for(int j = 0; j < TABLE_MODE;j++) {
                SqlBuilder builder = deleteFrom(p).where(p.PeopleID.eq(j+1));
                where(builder, i);
                assertEquals(1, dao.update(builder));
                Person pk = new Person();
                pk.setPeopleID(j + 1);
                assertNull(dao.queryByPk(pk, hints(i)));
            }
        }
    }

    @Test
    public void testQueryObject() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                SqlBuilder builder = selectAllFrom(p).where().allOf(p.PeopleID.eq(j+1)).into(Person.class);
                where(builder, i);
                Person pk = dao.queryObject(builder);
                assertNotNull(pk);
                assertEquals(j+1,  pk.getPeopleID().intValue());
                assertEquals(i,  pk.getCountryID().intValue());
            }
        }
    }

    @Test
    public void testQueryObjectPPartial() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                SqlBuilder builder = select(p.PeopleID, p.CountryID, p.CityID).from(p).where(p.PeopleID.eq(j+1)).into(Person.class);
                where(builder, i);
                Person pk = dao.queryObject(builder);
                assertNotNull(pk);
                assertNull(pk.getName());
                assertNull(pk.getDataChange_LastTime());
                assertNull(pk.getProvinceID());
                assertEquals(j+1,  pk.getPeopleID().intValue());
                assertEquals(i,  pk.getCountryID().intValue());
            }
        }
    }

    @Test
    public void testQuerySimpleObject() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            for (int j = 0; j < TABLE_MODE; j++) {
                SqlBuilder builder = select(p.Name).from(p).where().allOf(p.PeopleID.eq(j+1), p.Name.eq("test")).into(String.class);
                where(builder, i);
                String name = dao.queryObject(builder);
                assertEquals("test", name);
            }
        }
    }
    
    @Test
    public void testQuery() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            List<Integer> pks = new ArrayList<>();
            for (int j = 0; j < TABLE_MODE; j++) 
                pks.add(j+1);

            SqlBuilder builder = selectAllFrom(p).where().allOf(p.PeopleID.in(pks)).into(Person.class);
            where(builder, i);

            builder.orderBy(p.PeopleID.asc());
            List<Person> plist = dao.query(builder);

            assertEquals(4, plist.size());
            for (int j = 0; j < TABLE_MODE; j++) { 
                Person pk = plist.get(j);
                assertNotNull(p);
                assertEquals(j+1,  pk.getPeopleID().intValue());
                assertEquals(i,  pk.getCountryID().intValue());
            }
        }
    }

    @Test
    public void testQueryByPage() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            List<Integer> pks = new ArrayList<>();
            for (int j = 0; j < TABLE_MODE; j++) 
                pks.add(j+1);

            SqlBuilder builder = selectAllFrom(p).where().allOf(p.PeopleID.in(pks), p.Name.like("te%")).into(Person.class);
            where(builder, i);
            builder.orderBy(p.PeopleID.asc());
            setuper.range(builder, 0, 3);
            List<Person> plist = dao.query(builder);
            
            assertEquals(3, plist.size());
            for (int k = 0; k < 3; k++) {
                Person pk = plist.get(k);
                assertNotNull(p);
                assertEquals(k+1,  pk.getPeopleID().intValue());
                assertEquals(i,  pk.getCountryID().intValue());
            }
            
            builder = selectAllFrom(p).where().allOf(p.PeopleID.in(pks), p.Name.like("te%")).into(Person.class);
            where(builder, i);
            builder.orderBy(p.PeopleID.asc());
            setuper.range(builder, 2, 4);
            plist = dao.query(builder);

            assertEquals(2, plist.size());
        }
    }

    @Test
    public void testQueryTopObj() throws Exception {
        if(dbCategory == MySql)
            return;
        
        for (int k = 0; k < DB_MODE; k++) {
            PersonDefinition p = Person.PERSON;
            SqlBuilder builder = selectTop(3, p.PeopleID).from(p).where(p.PeopleID.gt(-1));
            where(builder, k).orderBy(p.PeopleID.asc()).intoObject();
            List plist = dao.query(builder);
    
            assertEquals(3, plist.size());
            for(int i = 0; i < plist.size(); i++){
                assertEquals(i + 1, ((Number)plist.get(i)).intValue());
            }
        }
    }

    @Test
    public void testQueryTopEntity() throws Exception {
        if(dbCategory == MySql)
            return;

        for (int k = 0; k < DB_MODE; k++) {
            PersonDefinition p = Person.PERSON;
            SqlBuilder builder = selectTop(3, p.PeopleID, p.Name).from(p).where(p.PeopleID.gt(-1));
            where(builder, k).orderBy(p.PeopleID.asc()).into(Person.class);
            
            List<Person> plist = null;
            plist = dao.query(builder);
    
            assertEquals(3, plist.size());
            for(int i = 0; i < plist.size(); i++){
                assertEquals("test", plist.get(i).getName());
            }
        }
    }
    
    @Test
    public void testBatchQueryBuilder() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            BatchQueryBuilder batchBuilder = new BatchQueryBuilder();
            for (int j = 0; j < TABLE_MODE; j++) {
                SqlBuilder builder = selectAllFrom(p).where().allOf(p.PeopleID.eq(j+1), p.CountryID.eq(i)).orderBy(p.PeopleID.asc()).into(Person.class);
                batchBuilder.addBatch(builder);
            }
            
            provider.inShard(batchBuilder.hints(), i);
            List<List<Person>> plist = (List<List<Person>>)dao.batchQuery(batchBuilder);

            assertEquals(TABLE_MODE, plist.size());
            for (int j = 0; j < TABLE_MODE; j++) {
                List<Person> list = plist.get(j);
                assertEquals(1, list.size());
                Person pk = list.get(0);
                assertNotNull(p);
                assertEquals(j+1,  pk.getPeopleID().intValue());
                assertEquals(i,  pk.getCountryID().intValue());
                assertEquals("test",  pk.getName());
            }
        }
    }

    @Test
    public void testTransaction() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            int ifinal = i;
            Hints hints = new Hints();
            provider.inShard(hints, i);
            
            if(hints.getShard() == null && hints.getShardValue() == null)
                continue;

            dao.execute(() -> {
                SqlBuilder builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                List<Person> plist = dao.query(builder);

                assertEquals(4, plist.size());
                
                assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                
                builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                assertEquals(0, dao.query(builder).size());
                
                assertEquals(4, dao.insert(plist));
                builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                plist = dao.query(builder);
                assertEquals(4, plist.size());
                
                assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
            }, hints);
            
            assertEquals(0, count(i));
        }        
    }
    
    @Test
    public void testTransactionRollback() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            int ifinal = i;
            Hints hints = new Hints();
            provider.inShard(hints, i);
            
            if(hints.getShard() == null && hints.getShardValue() == null)
                continue;

            try {
                dao.execute(() -> {
                    SqlBuilder builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                    List<Person> plist = dao.query(builder);

                    assertEquals(4, plist.size());
                    
                    assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                    
                    builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                    assertEquals(0, dao.query(builder).size());
                    
                    assertEquals(4, dao.insert(plist));
                    builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                    plist = dao.query(builder);
                    assertEquals(4, plist.size());
                    
                    assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                    throw new RuntimeException("1");
                }, hints);
                fail();
            } catch (Exception e) {
            }
            
            assertEquals(4, count(i));
        }        
    }
    
    @Test
    public void testCallableTransaction() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            int ifinal = i;

            Hints hints = new Hints();
            provider.inShard(hints, i);
            
            if(hints.getShard() == null && hints.getShardValue() == null)
                continue;

            assertEquals(10,  (int)dao.execute(() -> {
                SqlBuilder builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                List<Person> plist = dao.query(builder);

                assertEquals(4, plist.size());
                
                assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                
                builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                assertEquals(0, dao.query(builder).size());
                
                assertEquals(4, dao.insert(plist));
                builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                plist = dao.query(builder);
                assertEquals(4, plist.size());
                
                assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                return 10;
            }, hints));
            
            assertEquals(0, count(i));
        }        
    }

    @Test
    public void testCallableTransactionRollback() throws Exception {
        PersonDefinition p = Person.PERSON;
        for (int i = 0; i < DB_MODE; i++) {
            int ifinal = i;

            Hints hints = new Hints();
            provider.inShard(hints, i);
            
            if(hints.getShard() == null && hints.getShardValue() == null)
                continue;

            try {
                assertEquals(10,  (int)dao.execute(() -> {
                    SqlBuilder builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                    List<Person> plist = dao.query(builder);

                    assertEquals(4, plist.size());
                    
                    assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                    
                    builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                    assertEquals(0, dao.query(builder).size());
                    
                    assertEquals(4, dao.insert(plist));
                    builder = selectAllFrom(p).where().allOf(p.CountryID.eq(ifinal)).orderBy(p.PeopleID.asc()).into(Person.class);
                    plist = dao.query(builder);
                    assertEquals(4, plist.size());
                    
                    assertArrayEquals(new int[] {1, 1, 1, 1}, dao.batchDelete(plist));
                    throw new RuntimeException("1");
                }, hints));
                fail();
            } catch (Exception e) {
            }
            
            assertEquals(4, count(i));
        }        
    }
}
