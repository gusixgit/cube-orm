package com.sanders.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sanders on 15/4/4.
 * 数据库操作类
 */
public class DBProxy {

    /**
     * 用于缓存实体类Class和实体类详情
     */
    public Map<Class, ClassInfo> classInfoMap = new HashMap<Class, ClassInfo>();

    /**
     * SQLiteOpenHelper实现类
     */
    private SQLiteOpenHelper helper;

    /**
     * SQLiteDatabase数据库
     */
    private SQLiteDatabase database;

    /**
     * 数据库操作计数，防止异常关闭问题
     */
    private int closeIndex = 0;

    /**
     * 构建数据库操作类
     */
    public static class DBBuilder {
        /**
         * 数据库名称
         */
        private String dbName;
        /**
         * 外建数据库文件
         */
        private File dbFile;
        /**
         * 数据库版本
         */
        private int dbVersion;
        /**
         * 数据库升级接口
         */
        private OnDBUpgrade upgrade;
        /**
         * 自动建表Class集合
         */
        private Set<Class> classes = new HashSet<Class>();

        /**
         * 设置数据库名称
         *
         * @param dbName
         * @return
         */
        public DBBuilder setDbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        /**
         * 设置外部数据库文件
         *
         * @param dbFile
         * @return
         */
        public DBBuilder setDbFile(File dbFile) {
            this.dbFile = dbFile;
            return this;
        }

        /**
         * 设置外部数据库文件路径
         *
         * @param dbFilePath
         * @return
         */
        public DBBuilder setDbFilePath(String dbFilePath) {
            return this.setDbFile(new File(dbFilePath));
        }

        /**
         * 设置数据库版本号
         *
         * @param dbVersion
         * @return
         */
        public DBBuilder setDbVersion(int dbVersion) {
            this.dbVersion = dbVersion;
            return this;
        }

        /**
         * 设置对应的实体类Class并会自动建表
         *
         * @param clazz
         * @return
         */
        public DBBuilder createTable(Class clazz) {
            this.classes.add(clazz);
            return this;
        }

        /**
         * 设置数据库升级操作接口实现类
         *
         * @param upgrade
         * @return
         */
        public DBBuilder setOnDBUpgrade(OnDBUpgrade upgrade) {
            this.upgrade = upgrade;
            return this;
        }

        /**
         * build一个数据库操作类
         * 如果是外部数据库文件则不能自动升级及创建表
         *
         * @param context
         * @return
         */
        public DBProxy build(Context context) {
            DBProxy proxy = new DBProxy();
            if (dbName != null && dbName.trim().length() > 0 && dbVersion > 0) {
                SQLiteOpenHelperProxy helper = new SQLiteOpenHelperProxy(context, dbName, dbVersion, classes, upgrade);
                helper.setDBProxy(proxy);
                proxy.setSQLiteOpenHelper(helper);
            } else if (dbFile != null) {
                SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
                proxy.setSQLiteDatabase(database);
            }
            return proxy;
        }
    }

    private DBProxy() {

    }

    /**
     * 设置实现SQLiteOpenHelper类的实现
     *
     * @param helper
     */
    private void setSQLiteOpenHelper(SQLiteOpenHelper helper) {
        this.helper = helper;
    }

    private void setSQLiteDatabase(SQLiteDatabase database) {
        this.database = database;
    }

    /**
     * 构建数据库操作类
     *
     * @param helper
     */
    public DBProxy(SQLiteOpenHelper helper) {
        this.helper = helper;
    }

    /**
     * 获取一个实体类Class的详细信息并缓存
     *
     * @param t
     * @param <T>
     * @return
     */
    private synchronized <T extends IDColumn> ClassInfo getClassInfo(T t) {
        ClassInfo classInfo = classInfoMap.get(t.getClass());
        if (classInfo == null) {
            classInfo = new ClassInfo(t.getClass());
        }
        return classInfo;
    }

    /**
     * 获取一个实体类Class的详细信息并缓存
     *
     * @param clazz
     * @param <T>
     * @return
     */
    public synchronized <T extends IDColumn> ClassInfo getClassInfo(Class<T> clazz) {
        ClassInfo classInfo = classInfoMap.get(clazz);
        if (classInfo == null) {
            classInfo = new ClassInfo(clazz);
        }
        return classInfo;
    }

    /**
     * 插入对应实体到数据库
     *
     * @param t
     * @param <T>
     * @return
     */
    public synchronized <T extends IDColumn> long insert(T t) {
        if (t == null) {
            return -1;
        }
        ClassInfo<T> classInfo = getClassInfo(t);
        String tableName = classInfo.getTableName();
        ContentValues values = classInfo.getContentValues(t);
        if (values != null) {
            SQLiteDatabase database = getDatabase();
            database.beginTransaction();
            long id = database.insert(tableName, null, values);
            t.setPrimaryKey(id);
            database.setTransactionSuccessful();
            database.endTransaction();
            close(database);
            return id;
        }
        return -1;
    }

    /**
     * 批量插入对应实体类到数据库。建议集合不要太大，这是一次性事务
     *
     * @param list
     * @param <T>
     */
    public synchronized <T extends IDColumn> void insert(List<T> list) {
        if (isEmpty(list)) {
            return;
        }
        ClassInfo<T> classInfo = getClassInfo(list.get(0));
        SQLiteDatabase database = getDatabase();
        database.beginTransaction();
        for (T t : list) {
            ContentValues values = classInfo.getContentValues(t);
            if (values != null) {
                long id = database.insert(classInfo.getTableName(), null, values);
                t.setPrimaryKey(id);
            }
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        close(database);
    }

    /**
     * 更具条件更新实体到数据库
     *
     * @param t
     * @param where
     * @param args
     * @param <T>
     * @return
     */
    public synchronized <T extends IDColumn> int update(T t, String where, String... args) {
        if (t == null) {
            throw new NullPointerException("T对象不能为NULL！");
        }
        if (where == null) {
            throw new NullPointerException("缺少WHERE条件语句！");
        }
        ClassInfo<T> classInfo = getClassInfo(t);
        String tableName = classInfo.getTableName();
        ContentValues values = classInfo.getContentValues(t);
        if (values == null) {
            return -1;
        }
        values.remove(IDColumn.PRIMARY_KEY);
        SQLiteDatabase database = getDatabase();
        database.beginTransaction();
        int row = database.update(tableName, values, where, args);
        database.setTransactionSuccessful();
        database.endTransaction();
        close(database);
        return row;
    }

    /**
     * 更具实体中的主键(_key_id)更新实体到数据库
     *
     * @param t
     * @param <T>
     * @return
     */
    public synchronized <T extends IDColumn> int update(T t) {
        long keyId;
        if (t == null) {
            throw new NullPointerException("T对象不能为NULL！");
        } else if ((keyId = t.getPrimaryKey()) < 1) {
            return -1;
        }
        return update(t, IDColumn.PRIMARY_KEY + "=" + keyId);
    }

    /**
     * 更具实体中的主键更新实体到数据库
     *
     * @param t
     * @param keyId
     * @param <T>
     * @return
     */
    public synchronized <T extends IDColumn> int update(T t, long keyId) {
        return update(t, IDColumn.PRIMARY_KEY + "=" + keyId);
    }

    /**
     * 更具集合实体中的主键(_key_id)更新实体到数据库
     *
     * @param list
     * @param <T>
     */
    public synchronized <T extends IDColumn> void update(List<T> list) {
        if (isEmpty(list)) {
            return;
        }
        ClassInfo<T> classInfo = getClassInfo(list.get(0));
        String tableName = classInfo.getTableName();
        SQLiteDatabase database = getDatabase();
        database.beginTransaction();
        for (T t : list) {
            long keyId = t.getPrimaryKey();
            ContentValues values = classInfo.getContentValues(t);
            if (values != null && keyId > 0) {
                values.remove(IDColumn.PRIMARY_KEY);
                database.update(tableName, values, IDColumn.PRIMARY_KEY + "=" + keyId, null);
            }
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        close(database);
    }

    public synchronized <T extends IDColumn> long insertOrUpdate(T t) {
        if (t == null) {
            return -1;
        }
        ClassInfo<T> classInfo = getClassInfo(t);
        ContentValues values = classInfo.getContentValues(t);
        long rowId = -1;
        if (values != null) {
            SQLiteDatabase database = getDatabase();
            if (t.getPrimaryKey() > 0) {
                rowId = update(t);
            } else {
                rowId = insert(t);
            }
        }
        return rowId;
    }

    /**
     * 插入或者更新集合
     *
     * @param list
     * @param <T>
     */
    public synchronized <T extends IDColumn> void insertOrUpdate(List<T> list) {
        if (isEmpty(list)) {
            return;
        }
        ClassInfo<T> classInfo = getClassInfo(list.get(0));
        String tableName = classInfo.getTableName();
        SQLiteDatabase database = getDatabase();
        database.beginTransaction();
        for (T t : list) {
            ContentValues values = classInfo.getContentValues(t);
            if (values != null) {
                long keyId = t.getPrimaryKey();
                if (keyId > 0) {
                    database.update(tableName, values, IDColumn.PRIMARY_KEY + "=" + keyId, null);
                } else {
                    database.insert(tableName, null, values);
                }
            }
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        close(database);
    }

    /**
     * 执行原生sql
     *
     * @param sql
     */
    public synchronized void execSQL(String... sql) {
        SQLiteDatabase database = getDatabase();
        database.beginTransaction();
        for (String s : sql) {
            database.execSQL(s);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        close(database);
    }

    /**
     * 更具条件删除数据库内容
     *
     * @param clazz
     * @param where
     * @param args
     * @return
     */
    public synchronized int delete(Class<?> clazz, String where, String... args) {
        String table = ClassInfo.conversionClassNameToTableName(clazz.getName());
        SQLiteDatabase database = getDatabase();
        database.beginTransaction();
        int row = database.delete(table, where, args);
        database.setTransactionSuccessful();
        database.endTransaction();
        close(database);
        return row;
    }

    /**
     * 根据主键删除数据库内容
     *
     * @param clazz
     * @param keyId
     * @return
     */
    public synchronized int delete(Class<?> clazz, long keyId) {
        return delete(clazz, IDColumn.PRIMARY_KEY + "=" + keyId);
    }

    /**
     * 查询数量
     *
     * @param clazz
     * @param where
     * @param args
     * @param <T>
     * @return
     */
    public <T extends IDColumn> long queryCount(Class<T> clazz, String where, String... args) {
        SQLiteDatabase database = getDatabase();
        ClassInfo<T> classInfo = getClassInfo(clazz);
        StringBuilder sql = new StringBuilder("SELECT COUNT(").append(IDColumn.PRIMARY_KEY).append(") AS count FROM ");
        sql.append(classInfo.getTableName());
        if (where != null && where.trim().length() > 0) {
            sql.append(" WHERE ").append(where);
        }
        sql.append(";");
        Cursor cursor = database.rawQuery(sql.toString(), args);
        long count = 1;
        if (cursor.moveToNext()) {
            count = cursor.getLong(0);
        }
        close(cursor);
        close(database);
        return count;
    }

    /**
     * 查询主键
     *
     * @param clazz
     * @param where
     * @param args
     * @param <T>
     * @return
     */
    public <T extends IDColumn> long queryPrimaryKey(Class<T> clazz, String where, String... args) {
        if (where == null) {
            throw new NullPointerException("缺少WHERE条件语句！");
        }
        ClassInfo<T> classInfo = getClassInfo(clazz);
        SQLiteDatabase database = getDatabase();
        StringBuilder sql = new StringBuilder("SELECT ").append(IDColumn.PRIMARY_KEY).append(" FROM ").append(classInfo.getTableName()).append(" WHERE ").append(where);
        Cursor cursor = database.rawQuery(sql.toString(), args);
        long id = -1;
        if (cursor.moveToNext()) {
            id = cursor.getLong(0);
        }
        close(cursor);
        close(database);
        return id;
    }

    /**
     * 根据条件查询一条记录到实体
     *
     * @param clazz
     * @param where
     * @param args
     * @param <T>
     * @return
     */
    public <T extends IDColumn> T query(Class<T> clazz, String where, String... args) {
        SQLiteDatabase database = getDatabase();
        ClassInfo<T> classInfo = getClassInfo(clazz);
        Cursor cursor = database.query(classInfo.getTableName(), null, where, args, null, null, null);
        T t = classInfo.getInstanceObject(cursor);
        close(cursor);
        close(database);
        return t;
    }

    /**
     * 根据主键查询实体
     *
     * @param clazz
     * @param keyId
     * @param <T>
     * @return
     */
    public <T extends IDColumn> T query(Class<T> clazz, long keyId) {
        return query(clazz, IDColumn.PRIMARY_KEY + "=" + keyId);
    }

    /**
     * 根据sql语句查询实体
     *
     * @param clazz
     * @param sql
     * @param args
     * @param <T>
     * @return
     */
    public <T extends IDColumn> T querySql(Class<T> clazz, String sql, String... args) {
        SQLiteDatabase database = getDatabase();
        Cursor cursor = database.rawQuery(sql, args);
        ClassInfo<T> classInfo = getClassInfo(clazz);
        T t = classInfo.getInstanceObject(cursor);
        close(cursor);
        close(database);
        return t;
    }

    /**
     * 根据sql语句查询实体集合
     *
     * @param clazz
     * @param sql
     * @param args
     * @param <T>
     * @return
     */
    public <T extends IDColumn> List<T> querySqlList(Class<T> clazz, String sql, String... args) {
        SQLiteDatabase database = getDatabase();
        Cursor cursor = database.rawQuery(sql, args);
        ClassInfo<T> classInfo = getClassInfo(clazz);
        List<T> list = classInfo.getInstanceList(cursor);
        close(cursor);
        close(database);
        return list;
    }

    /**
     * 更具条件插叙实体集合
     *
     * @param clazz
     * @param selection
     * @param selectionArgs
     * @param groupBy
     * @param having
     * @param orderBy
     * @param limit
     * @param <T>
     * @return
     */
    public <T extends IDColumn> List<T> queryList(Class<T> clazz, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        ClassInfo<T> classInfo = getClassInfo(clazz);
        SQLiteDatabase database = getDatabase();
        Cursor cursor = database.query(classInfo.getTableName(), null, selection, selectionArgs, groupBy, having, orderBy, limit);
        List<T> list = classInfo.getInstanceList(cursor);
        close(cursor);
        close(database);
        return list;
    }

    /**
     * 分页查询实体集合
     *
     * @param clazz
     * @param selection
     * @param pageNumber
     * @param pageSize
     * @param selectionArgs
     * @param <T>
     * @return
     */
    public <T extends IDColumn> List<T> queryList(Class<T> clazz, String selection, int pageNumber, int pageSize, String... selectionArgs) {
        int startPosition = ((pageNumber - 1) * pageSize);
        return queryList(clazz, selection, selectionArgs, null, null, null, startPosition + "," + pageSize);
    }

    /**
     * 根据条件查询实体集合
     *
     * @param clazz
     * @param selection
     * @param selectionArgs
     * @param <T>
     * @return
     */
    public <T extends IDColumn> List<T> queryList(Class<T> clazz, String selection, String... selectionArgs) {
        return queryList(clazz, selection, selectionArgs, null, null, null, null);
    }

    /**
     * <b>此方法适用于Build.VERSION_CODES.HONEYCOMB以上版本</b><br>
     * 查询一条记录到Map
     *
     * @param sql
     * @param args
     * @return
     */
    public Map<String, Object> query(String sql, String... args) {
        SQLiteDatabase database = getDatabase();
        Cursor cursor = database.rawQuery(sql, args);
        String[] names = cursor.getColumnNames();
        Map<String, Object> map = null;
        if (cursor.moveToNext()) {
            map = new HashMap<String, Object>();
            for (String columnName : names) {
                putMapKeyValue(cursor, columnName, map);
            }
        }
        close(cursor);
        close(database);
        return map;
    }

    /**
     * <b>此方法适用于Build.VERSION_CODES.HONEYCOMB以上版本</b><br>
     * 根据sql语句查询map到list
     *
     * @param sql
     * @param args
     * @return
     */
    public List<Map<String, Object>> queryList(String sql, String... args) {
        SQLiteDatabase database = getDatabase();
        Cursor cursor = database.rawQuery(sql, args);
        String[] names = cursor.getColumnNames();
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        while (cursor.moveToNext()) {
            Map<String, Object> map = new HashMap<String, Object>();
            for (String columnName : names) {
                putMapKeyValue(cursor, columnName, map);
            }
            list.add(map);
        }
        close(cursor);
        close(database);
        return list;
    }

    private void putMapKeyValue(Cursor cursor, String columnName, Map<String, Object> map) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            map.put(columnName, cursor.getString(columnIndex));
        } else {
            int type = cursor.getType(columnIndex);
            switch (type) {
                case Cursor.FIELD_TYPE_INTEGER:
                    map.put(columnName, cursor.getLong(columnIndex));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    map.put(columnName, cursor.getString(columnIndex));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    map.put(columnName, cursor.getFloat(columnIndex));
                    break;
                case Cursor.FIELD_TYPE_BLOB:
                    map.put(columnName, cursor.getBlob(columnIndex));
                    break;
                case Cursor.FIELD_TYPE_NULL:
                    map.put(columnName, cursor.getString(columnIndex));
                    break;
                default:
                    break;
            }
        }
    }

    private synchronized SQLiteDatabase getDatabase() {
        closeIndex++;
        if (helper != null) {
            return helper.getReadableDatabase();
        } else if (database != null) {
            return database;
        } else {
            throw new NullPointerException("SQLiteOpenHelper is null or SQLiteDatabase is null, please set the value");
        }
    }

    private synchronized void close(SQLiteDatabase database) {
        closeIndex--;
        if (closeIndex == 0) {
            if (database != null && database != this.database && database.isOpen()) {
                database.close();
            }
        }
    }

    private void close(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    private boolean isEmpty(Collection collection) {
        if (collection == null || collection.isEmpty()) {
            return true;
        }
        return false;
    }
}
