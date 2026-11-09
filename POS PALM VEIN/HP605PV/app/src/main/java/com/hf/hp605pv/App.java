package com.hf.hp605pv;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Camera;

import com.github.yuweiguocn.library.greendao.MigrationHelper;
import com.hf.hp605pv.palm.DaoMaster;
import com.hf.hp605pv.palm.DaoSession;
import com.hf.hp605pv.palm.UserDao;

import org.greenrobot.greendao.database.Database;

/**
 * @author tx
 * @date 2024/3/19 21:21
 * @target this class will do...
 */
public class App extends Application {
    DaoSession daoSession;

    @Override
    public void onCreate() {
        super.onCreate();
        daoSession = getDaoSession();
    }


    public static boolean hasCamera(Context context) {
        // 首先检查设备是否支持相机
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // 然后尝试实例化相机
            Camera camera = null;
            try {
                camera = Camera.open();
                if (camera == null) {
                    return false;
                }
            } catch (RuntimeException e) {
                // 无法实例化相机
                return false;
            } finally {
                // 如果相机不为null，释放资源
                if (camera != null) {
                    camera.release();
                }
            }
            return true;
        }
        return false;
    }

    public UserDao getUserDao() {
        if (daoSession != null) {
            return daoSession.getUserDao();
        }
        return null;
    }

    public DaoSession getDaoSession() {
        OnepassOpenHelper helper = new OnepassOpenHelper(this, "HP-605PV_db", null);
        Database db = helper.getWritableDb();
        DaoSession daoSession = new DaoMaster(db).newSession();
        return daoSession;
    }

    public static class OnepassOpenHelper extends DaoMaster.OpenHelper {

        public OnepassOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory);
        }

        @Override
        public void onUpgrade(Database db, int oldVersion, int newVersion) {

            //把需要管理的数据库表DAO作为最后一个参数传入到方法中
            MigrationHelper.migrate(db, new MigrationHelper.ReCreateAllTableListener() {

                @Override
                public void onCreateAllTables(Database db, boolean ifNotExists) {
                    DaoMaster.createAllTables(db, ifNotExists);
                }

                @Override
                public void onDropAllTables(Database db, boolean ifExists) {
                    DaoMaster.dropAllTables(db, ifExists);
                }
            }, UserDao.class);// 修改beanDao对象
        }
    }
}
