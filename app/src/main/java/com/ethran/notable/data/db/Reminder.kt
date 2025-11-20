package com.ethran.notable.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.util.Date
import java.util.UUID

@Entity
data class Reminder(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isDone: Boolean = false,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

@Dao
interface ReminderDao {
    @Insert
    fun create(reminder: Reminder)

    @Update
    fun update(reminder: Reminder)

    @Query("SELECT * FROM reminder ORDER BY createdAt DESC")
    fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminder WHERE isDone = 0 ORDER BY createdAt DESC")
    fun getAllActive(): List<Reminder>
    
    @Query("DELETE FROM reminder WHERE id = :id")
    fun deleteById(id: String)
}

class ReminderRepository(context: Context) {
    var db = AppDatabase.getDatabase(context).reminderDao()

    fun create(reminder: Reminder) {
        db.create(reminder)
    }

    fun update(reminder: Reminder) {
        db.update(reminder)
    }

    fun getAll(): List<Reminder> {
        return db.getAll()
    }
    
    fun getAllActive(): List<Reminder> {
        return db.getAllActive()
    }

    fun deleteById(id: String) {
        db.deleteById(id)
    }
}
