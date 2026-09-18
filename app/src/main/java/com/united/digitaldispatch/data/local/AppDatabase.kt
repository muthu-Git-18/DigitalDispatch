package com.united.digitaldispatch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.united.digitaldispatch.data.local.dao.OrganizationDao
import com.united.digitaldispatch.data.local.dao.UserDao
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.UserMasterEntity

@Database(
    entities = [OrganizationEntity::class, UserMasterEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun organizationDao(): OrganizationDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "digital_dispatch.db"
                ).build().also { INSTANCE = it }
            }
    }
}
