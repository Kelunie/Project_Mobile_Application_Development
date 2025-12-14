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
    val profileImagePath: String?,
    val location: String?,
    val profileImageUrl: String?
)

// DBHelper class
class DBHelper(context: Context) :
    SQLiteOpenHelper(context, "chesskel.db", null, 3) { // version bumped to 3

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
                profile_image_path TEXT,
                location TEXT,
                profile_image_url TEXT
            )
            """.trimIndent()
        )
    }

    // Upgrade database
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE usuarios ADD COLUMN profile_image_path TEXT")
            db.execSQL("ALTER TABLE usuarios ADD COLUMN location TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE usuarios ADD COLUMN profile_image_url TEXT")
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
            putNull("profile_image_path")
            putNull("location")
            putNull("profile_image_url")
        }
        return writableDatabase.insert("usuarios", null, values)
    }

    // Update user profile fields (image URI and location) for an existing user
    fun updateUserProfile(id: Long, profileImageUri: String?, location: String?) {
        val values = ContentValues().apply {
            if (profileImageUri != null) put("profile_image_path", profileImageUri) else putNull("profile_image_path")
            if (location != null) put("location", location) else putNull("location")
        }
        writableDatabase.update("usuarios", values, "id=?", arrayOf(id.toString()))
    }

    // Update user profile image URL
    fun updateUserProfileImageUrl(id: Long, profileImageUrl: String?) {
        val values = ContentValues().apply {
            if (profileImageUrl != null) put("profile_image_url", profileImageUrl) else putNull("profile_image_url")
        }
        writableDatabase.update("usuarios", values, "id=?", arrayOf(id.toString()))
    }

    // Get first user from the table
    fun getFirstUser(): UserEntity? {
        readableDatabase.rawQuery(
            "SELECT id,nombre,email,password_hash,created_at,profile_image_path,location,profile_image_url FROM usuarios ORDER BY id LIMIT 1",
            null
        ).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }

    // Get a user by id
    fun getUserById(id: Long): UserEntity? {
        readableDatabase.rawQuery(
            "SELECT id,nombre,email,password_hash,created_at,profile_image_path,location,profile_image_url FROM usuarios WHERE id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }

    // Get a user by email
    fun getUserByEmail(email: String): UserEntity? {
        readableDatabase.rawQuery(
            "SELECT id,nombre,email,password_hash,created_at,profile_image_path,location,profile_image_url FROM usuarios WHERE email=? LIMIT 1",
            arrayOf(email)
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
            profileImagePath = getString(getColumnIndexOrThrow("profile_image_path")).takeUnless { it.isNullOrEmpty() },
            location = getString(getColumnIndexOrThrow("location")).takeUnless { it.isNullOrEmpty() },
            profileImageUrl = getString(getColumnIndexOrThrow("profile_image_url")).takeUnless { it.isNullOrEmpty() }
        )
}
