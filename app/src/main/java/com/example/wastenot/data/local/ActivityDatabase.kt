package com.example.wastenot.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activities", primaryKeys = ["owner", "localId"],
    indices = [Index(value = ["owner", "serverId"], unique = true), Index(value = ["owner", "date"])])
data class LocalActivity(
    val owner: String,
    val localId: String,
    val serverId: String?,
    val category: String,
    val method: String,
    val date: String,
    val payload: String,
    val revision: Int,
    val serverVersion: Int,
    val createdAt: String,
    val updatedAt: String,
    val deleted: Boolean = false,
    val syncState: String = "SYNCED",
    val remotePayload: String? = null,
    val remoteVersion: Int? = null,
    val error: String? = null,
    val seen: String? = null,
)

@Entity(tableName = "activity_outbox", indices = [Index(value = ["owner", "localId"]),
    Index(value = ["owner", "requestId"], unique = true)])
data class ActivityMutation(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val owner: String,
    val localId: String,
    val kind: String,
    val payload: String,
    val requestId: String,
    val revision: Int,
    val attemptVersion: Int? = null,
)

@Entity(tableName = "activity_receipts", primaryKeys = ["owner", "requestId"])
data class ActivityReceipt(val owner: String, val requestId: String, val payload: String, val localId: String)

@Entity(tableName = "account_identity")
data class AccountIdentity(@PrimaryKey val slot: Int = 1, val owner: String, val payload: String)

@Entity(tableName = "server_snapshots", primaryKeys = ["owner", "key"])
data class ServerSnapshot(val owner: String, val key: String, val payload: String, val savedAt: Long)

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE owner = :owner AND (localId = :id OR serverId = :id) LIMIT 1")
    suspend fun find(owner: String, id: String): LocalActivity?

    @Query("""SELECT * FROM activities WHERE owner = :owner AND deleted = 0
        AND (:category IS NULL OR category = :category) AND (:method IS NULL OR method = :method)
        AND (:start IS NULL OR date >= :start) AND (:end IS NULL OR date <= :end)
        ORDER BY date DESC, localId DESC LIMIT :limit OFFSET :offset""")
    suspend fun page(owner: String, category: String?, method: String?, start: String?, end: String?,
        limit: Int, offset: Int): List<LocalActivity>

    @Query("SELECT * FROM activities WHERE owner = :owner AND syncState != 'SYNCED' ORDER BY createdAt")
    fun observePending(owner: String): Flow<List<LocalActivity>>

    @Query("SELECT * FROM activities WHERE owner = :owner ORDER BY date DESC, localId DESC")
    fun observeActivities(owner: String): Flow<List<LocalActivity>>

    @Upsert suspend fun put(activity: LocalActivity)
    @Query("DELETE FROM activities WHERE owner = :owner AND localId = :id")
    suspend fun remove(owner: String, id: String)

    @Insert suspend fun enqueue(mutation: ActivityMutation): Long
    @Query("SELECT * FROM activity_outbox WHERE owner = :owner ORDER BY sequence")
    suspend fun mutations(owner: String): List<ActivityMutation>
    @Query("SELECT * FROM activity_outbox WHERE owner = :owner AND requestId = :requestId LIMIT 1")
    suspend fun request(owner: String, requestId: String): ActivityMutation?
    @Query("SELECT * FROM activity_receipts WHERE owner = :owner AND requestId = :requestId")
    suspend fun receipt(owner: String, requestId: String): ActivityReceipt?
    @Insert suspend fun receipt(receipt: ActivityReceipt)
    @Update suspend fun put(mutation: ActivityMutation)
    @Query("DELETE FROM activity_outbox WHERE sequence = :sequence AND owner = :owner")
    suspend fun acknowledge(owner: String, sequence: Long)
    @Query("DELETE FROM activity_outbox WHERE owner = :owner AND localId = :id")
    suspend fun discard(owner: String, id: String)
    @Query("SELECT COUNT(*) FROM activity_outbox WHERE owner = :owner AND localId = :id")
    suspend fun outstanding(owner: String, id: String): Int

    @Query("SELECT * FROM account_identity WHERE slot = 1") suspend fun identity(): AccountIdentity?
    @Upsert suspend fun identify(identity: AccountIdentity)
    @Query("DELETE FROM account_identity") suspend fun clearIdentity()

    @Query("SELECT * FROM server_snapshots WHERE owner = :owner AND `key` = :key")
    suspend fun snapshot(owner: String, key: String): ServerSnapshot?
    @Query("SELECT * FROM server_snapshots WHERE owner = :owner")
    fun observeSnapshots(owner: String): Flow<List<ServerSnapshot>>
    @Upsert suspend fun snapshot(snapshot: ServerSnapshot)
}

@Database(entities = [LocalActivity::class, ActivityMutation::class, ActivityReceipt::class, AccountIdentity::class, ServerSnapshot::class],
    version = 1, exportSchema = true)
abstract class ActivityDatabase : RoomDatabase() {
    abstract fun activities(): ActivityDao

    companion object {
        fun create(context: Context): ActivityDatabase = Room.databaseBuilder(
            context.applicationContext, ActivityDatabase::class.java, "activities.db",
        ).build()
    }
}
