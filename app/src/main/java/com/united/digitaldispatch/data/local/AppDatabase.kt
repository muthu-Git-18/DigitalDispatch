package com.united.digitaldispatch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.united.digitaldispatch.data.local.dao.DispatchHeaderDao
import com.united.digitaldispatch.data.local.dao.ItemMasterDao
import com.united.digitaldispatch.data.local.dao.OrganizationDao
import com.united.digitaldispatch.data.local.dao.StockDao
import com.united.digitaldispatch.data.local.dao.TransporterDao
import com.united.digitaldispatch.data.local.dao.UserDao
import com.united.digitaldispatch.data.local.entity.DispatchHeaderEntity
import com.united.digitaldispatch.data.local.entity.ItemMasterEntity
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.StockEntity
import com.united.digitaldispatch.data.local.entity.TransporterEntity
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity
import com.united.digitaldispatch.data.local.entity.UserMasterEntity
import com.united.digitaldispatch.data.local.dao.PswDispatchHeaderDao
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity

@Database(
    entities = [OrganizationEntity::class,
                UserMasterEntity::class,
                TransporterEntity::class,
                ItemMasterEntity::class,
                StockEntity::class,
                TruckMasterEntity::class,
                DispatchHeaderEntity::class,
                PswDispatchHeaderEntity::class
               ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun organizationDao(): OrganizationDao
    abstract fun userDao(): UserDao
    abstract fun transporterDao(): TransporterDao

    abstract fun itemMasterDao(): ItemMasterDao

    abstract fun stockDao(): StockDao

    abstract fun dispatchHeaderDao(): DispatchHeaderDao

    abstract fun pswDispatchHeaderDao(): PswDispatchHeaderDao

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
