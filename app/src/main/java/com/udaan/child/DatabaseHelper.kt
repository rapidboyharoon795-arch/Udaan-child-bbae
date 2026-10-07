package com.udaan.child

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "UdaanChild.db", null, 1) {
    companion object {
        private const val TABLE_PENDING = "pending_uploads"
        private const val COL_ID = "id"
        private const val COL_TYPE = "type"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_JSON_DATA = "json_data"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_RETRY_COUNT = "retry_count"
        private const val MAX_ITEMS = 100
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_PENDING ($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COL_TYPE TEXT, $COL_FILE_PATH TEXT, $COL_JSON_DATA TEXT, $COL_TIMESTAMP LONG, $COL_RETRY_COUNT INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PENDING")
        onCreate(db)
    }

    @Synchronized
    fun addToQueue(type: String, filePath: String?, jsonData: String, timestamp: Long) {
        val db = this.writableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM $TABLE_PENDING", null).use { it.moveToFirst(); it.getInt(0) }
        if (count >= MAX_ITEMS) db.execSQL("DELETE FROM $TABLE_PENDING ORDER BY $COL_TIMESTAMP ASC LIMIT 1")
        val values = ContentValues().apply {
            put(COL_TYPE, type); put(COL_FILE_PATH, filePath); put(COL_JSON_DATA, jsonData); put(COL_TIMESTAMP, timestamp); put(COL_RETRY_COUNT, 0)
        }
        db.insert(TABLE_PENDING, null, values)
    }

    fun getPendingItems(): List<PendingItem> {
        val list = mutableListOf<PendingItem>()
        val cursor = this.readableDatabase.rawQuery("SELECT * FROM $TABLE_PENDING", null)
        if (cursor.moveToFirst()) {
            do { list.add(PendingItem(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getInt(5))) } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun markAsUploaded(id: Int) = this.writableDatabase.delete(TABLE_PENDING, "$COL_ID=?", arrayOf(id.toString()))
    fun incrementRetry(id: Int) = this.writableDatabase.execSQL("UPDATE $TABLE_PENDING SET $COL_RETRY_COUNT = $COL_RETRY_COUNT + 1 WHERE $COL_ID = $id")
    fun deleteItem(id: Int) = this.writableDatabase.delete(TABLE_PENDING, "$COL_ID=?", arrayOf(id.toString()))

    data class PendingItem(val id: Int, val type: String, val filePath: String?, val jsonData: String, val timestamp: Long, val retryCount: Int)
}
