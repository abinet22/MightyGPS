package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_devices")
data class CachedDevice(
    @PrimaryKey val id: Long,
    val name: String,
    val uniqueId: String,
    val status: String,
    val lastUpdate: String?,
    val latitude: Double,
    val longitude: Double,
    val speed: Double,
    val address: String?,
    val category: String?
)

@Entity(tableName = "cached_alerts")
data class CachedAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,
    val deviceName: String,
    val type: String, // "alarm", "geofence", "overspeed"
    val alarmType: String?, // "sos", "shock", "powerCut", "overspeed", etc.
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val message: String
)

@Dao
interface CachedDeviceDao {
    @Query("SELECT * FROM cached_devices ORDER BY name ASC")
    fun getAllDevicesFlow(): Flow<List<CachedDevice>>

    @Query("SELECT * FROM cached_devices ORDER BY name ASC")
    suspend fun getAllDevicesDirect(): List<CachedDevice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<CachedDevice>)

    @Query("DELETE FROM cached_devices")
    suspend fun clearDevices()
}

@Dao
interface CachedAlertDao {
    @Query("SELECT * FROM cached_alerts ORDER BY timestamp DESC")
    fun getAllAlertsFlow(): Flow<List<CachedAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: CachedAlert)

    @Query("DELETE FROM cached_alerts WHERE id = :id")
    suspend fun deleteAlert(id: Long)

    @Query("DELETE FROM cached_alerts")
    suspend fun clearAlerts()
}

@Database(entities = [CachedDevice::class, CachedAlert::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): CachedDeviceDao
    abstract fun alertDao(): CachedAlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "saas_gps_tracker_db"
                )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
