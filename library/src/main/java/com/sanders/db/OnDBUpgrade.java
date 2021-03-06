package com.sanders.db;

import android.database.sqlite.SQLiteDatabase;

/**
 * 数据库升级操作类
 * Created by sanders on 15/5/9.
 */
public abstract class OnDBUpgrade {

    /**
     * 开始升级数据库之前调用此方法
     * @param db
     * @param oldVersion
     * @param newVersion
     * @return
     */
    public boolean beginUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
        return false;
    }

    /**
     * 数据库升级调用此方法
     * @param db
     * @param oldVersion
     * @param newVersion
     * @return true是手动升级，false是自动升级
     */
    public boolean onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
        return false;
    }

    /**
     * 数据库升级结束调用此方法
     * @param db
     * @param oldVersion
     * @param newVersion
     * @return
     */
    public boolean endUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
        return false;
    }
}
