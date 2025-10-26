package com.chesskel.data

// imports we need
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

//data class
data class UserEntity(
    val id: Long,
    val nombre: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Long
)

// DBHelper class
class DBHelper(context: Context) :
    SQLiteOpenHelper(context, "chesskel.db", null, 1) {

    // Create  table if not exists
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usuarios(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    // Upgrade database
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        onCreate(db)
    }

    // Insert user into the table
    fun insertUser(nombre: String, email: String, passwordHash: String): Long {
        val values = ContentValues().apply {
            put("nombre", nombre)
            put("email", email)
            put("password_hash", passwordHash)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("usuarios", null, values)
    }

    // Get first user from the table
    fun getFirstUser(): UserEntity? {
        readableDatabase.rawQuery(
            "SELECT id,nombre,email,password_hash,created_at FROM usuarios ORDER BY id LIMIT 1",
            null
        ).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }

    // Extension function to convert Cursor to UserEntity
    private fun Cursor.toUser(): UserEntity =
        UserEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            nombre = getString(getColumnIndexOrThrow("nombre")),
            email = getString(getColumnIndexOrThrow("email")),
            passwordHash = getString(getColumnIndexOrThrow("password_hash")),
            createdAt = getLong(getColumnIndexOrThrow("created_at"))
        )
}
