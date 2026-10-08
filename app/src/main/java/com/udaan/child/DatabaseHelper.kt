package com.udaan.child

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

data class PendingUpload(
    val id: Int,
    val type: String,
    val filePath: String,
    val jsonData: String,
    val timestamp: Long,
    val retryCount: Int
)

class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        private const val DB_NAME = "udaan_child.db"
        private const val DB_VERSION = 1
        private const val TABLE = "pending_uploads"
        private const val MAX_ITEMS = 100

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                file_path TEXT NOT NULL,
                json_data TEXT,
                timestamp INTEGER NOT NULL,
                retry_count INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun addToQueue(type: String, filePath: String, jsonData: String, timestamp: Long): Long {
        val db = writableDatabase
        var insertedId = -1L

        db.beginTransaction()
        try {
            val count = getQueueCount()
            if (count >= MAX_ITEMS) {
                deleteOldestItems(db, (count - MAX_ITEMS + 1).toInt())
            }

            val values = ContentValues().apply {
                put("type", type)
                put("file_path", filePath)
                put("json_data", jsonData)
                put("timestamp", timestamp)
                put("retry_count", 0)
            }
            insertedId = db.insert(TABLE, null, values)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add to queue", e)
        } finally {
            db.endTransaction()
        }

        return insertedId
    }

    fun getPendingItems(limit: Int = MAX_ITEMS): List<PendingUpload> {
        val result = mutableListOf<PendingUpload>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE,
            arrayOf("id", "type", "file_path", "json_data", "timestamp", "retry_count"),
            null, null, null, null,
            "timestamp ASC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                result.add(
                    PendingUpload(
                        id = it.getInt(0),
                        type = it.getString(1),
                        filePath = it.getString(2),
                        jsonData = it.getString(3) ?: "",
                        timestamp = it.getLong(4),
                        retryCount = it.getInt(5)
                    )
                )
            }
        }
        return result
    }

    fun markAsUploaded(id: Int) {
        deleteItem(id)
    }

    fun deleteItem(id: Int) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    // Atomic increment: multiple threads hone par bhi race condition nahi aayegi
    fun incrementRetry(id: Int) {
        writableDatabase.execSQL(
            "UPDATE $TABLE SET retry_count = retry_count + 1 WHERE id = ?",
            arrayOf(id.toString())
        )
    }

    fun getQueueCount(): Long {
        return DatabaseUtils.queryNumEntries(readableDatabase, TABLE)
    }

    private fun deleteOldestItems(db: SQLiteDatabase, count: Int) {
        if (count <= 0) return
        db.execSQL(
            "DELETE FROM $TABLE WHERE id IN (SELECT id FROM $TABLE ORDER BY timestamp ASC LIMIT $count)"
        )
        Log.d(TAG, "Deleted $count oldest items from queue")
    }
    }
    
