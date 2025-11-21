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
    val createdAt: Long,
    val profileImageUri: String?,
    val location: String?
)

// DBHelper class
class DBHelper(context: Context) :
    SQLiteOpenHelper(context, "chesskel.db", null, 2) { // version bumped to 2

    // Create  table if not exists
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usuarios(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                profile_image_uri TEXT,
                location TEXT
            )
            """.trimIndent()
        )
    }

    // Upgrade database
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE usuarios ADD COLUMN profile_image_uri TEXT")
            db.execSQL("ALTER TABLE usuarios ADD COLUMN location TEXT")
        }
    }

    // Insert user into the table
    fun insertUser(nombre: String, email: String, passwordHash: String): Long {
        val values = ContentValues().apply {
            put("nombre", nombre)
            put("email", email)
            put("password_hash", passwordHash)
            put("created_at", System.currentTimeMillis())
            // Nuevos campos: inicialmente null
            putNull("profile_image_uri")
            putNull("location")
        }
        return writableDatabase.insert("usuarios", null, values)
    }

    // Get first user from the table
    fun getFirstUser(): UserEntity? {
        readableDatabase.rawQuery(
            "SELECT id,nombre,email,password_hash,created_at,profile_image_uri,location FROM usuarios ORDER BY id LIMIT 1",
            null
        ).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }

    // Get a user by id
    fun getUserById(id: Long): UserEntity? {
        readableDatabase.rawQuery(
            "SELECT id,nombre,email,password_hash,created_at,profile_image_uri,location FROM usuarios WHERE id=? LIMIT 1",
            arrayOf(id.toString())
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
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            profileImageUri = getString(getColumnIndexOrThrow("profile_image_uri")).takeUnless { it.isNullOrEmpty() },
            location = getString(getColumnIndexOrThrow("location")).takeUnless { it.isNullOrEmpty() }
        )
}
