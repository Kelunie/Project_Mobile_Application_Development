package com.chesskel.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.chesskel.R
import com.chesskel.data.DBHelper
import com.chesskel.data.User
import com.chesskel.data.toUser
import com.chesskel.ui.theme.CenteredActivity
import com.chesskel.ui.theme.ThemeUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import com.chesskel.net.ApiClient

class ProfileActivity : CenteredActivity() {

    private lateinit var dbHelper: DBHelper
    private var currentUser: User? = null
    private var currentProfileImageUri: Uri? = null
    private var tempCameraImageUri: Uri? = null

    private lateinit var ivProfileImage: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserNameValue: TextView
    private lateinit var tvUserEmailValue: TextView
    private lateinit var tvLocationValue: TextView
    private lateinit var btnSaveProfile: LinearLayout
    private lateinit var btnGetLocation: LinearLayout
    private lateinit var btnLogout: LinearLayout

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // Attempt to persist permissions if provided by picker
                    val flags = result.data?.flags ?: 0
                    val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (takeFlags != 0) {
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    }
                } catch (e: Exception) {
                    // ignore - permission may not be persistable for some pickers
                }
                currentProfileImageUri = uri
                ivProfileImage.setImageURI(uri)
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraImageUri != null) {
            currentProfileImageUri = tempCameraImageUri
            ivProfileImage.setImageURI(tempCameraImageUri)
        } else {
            tempCameraImageUri = null
        }
    }

    private enum class PendingPermissionAction { NONE, CAMERA, LOCATION }

    private var pendingPermissionAction: PendingPermissionAction = PendingPermissionAction.NONE

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        when {
            isGranted && pendingPermissionAction == PendingPermissionAction.CAMERA -> {
                pendingPermissionAction = PendingPermissionAction.NONE
                launchCamera()
            }
            isGranted && pendingPermissionAction == PendingPermissionAction.LOCATION -> {
                pendingPermissionAction = PendingPermissionAction.NONE
                startLocationAcquisition()
            }
            else -> {
                Toast.makeText(this, getString(R.string.profile_permission_denied), Toast.LENGTH_SHORT).show()
                pendingPermissionAction = PendingPermissionAction.NONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeUtils.applySavedTheme(this)
        setCenteredContentView(R.layout.activity_profile)

        dbHelper = DBHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        ivProfileImage = findViewById(R.id.ivProfileImage)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvUserNameValue = findViewById(R.id.tvUserNameValue)
        tvUserEmailValue = findViewById(R.id.tvUserEmailValue)
        tvLocationValue = findViewById(R.id.tvLocationValue)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnGetLocation = findViewById(R.id.btnGetLocation)
        btnLogout = findViewById(R.id.btnLogout)

        loadUser()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case current_user_id changed (register/login elsewhere)
        loadUser()
    }

    private fun loadUser() {
        // Prefer the explicitly logged-in user id saved in SharedPreferences
        val prefs = getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
        val savedUserId = prefs.getLong("current_user_id", -1L)
        var entity = if (savedUserId > 0L) dbHelper.getUserById(savedUserId) else null
        if (entity == null) {
            // Try fallback: if we have an email saved in prefs, load by email
            val savedEmail = prefs.getString("current_user_email", null)
            if (!savedEmail.isNullOrBlank()) {
                entity = dbHelper.getUserByEmail(savedEmail)
            }
        }
        if (entity == null) {
            // Last resort: get the first user in the DB
            entity = dbHelper.getFirstUser()
        }
        if (entity == null) {
            Toast.makeText(this, getString(R.string.profile_no_user), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentUser = entity.toUser()

        tvUserName.text = currentUser?.name
        tvUserEmail.text = currentUser?.email
        tvUserNameValue.text = currentUser?.name
        tvUserEmailValue.text = currentUser?.email
        tvLocationValue.text = currentUser?.location ?: getString(R.string.profile_location_hint)

        currentUser?.profileImageUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                // Check if we can still read the URI.
                contentResolver.openInputStream(uri)?.close()
                currentProfileImageUri = uri
                ivProfileImage.setImageURI(uri)
            } catch (e: SecurityException) {
                // This can happen if the URI permission is lost.
                // Clear the invalid URI from the database.
                currentUser?.let { user ->
                    dbHelper.updateUserProfile(user.id, null, user.location)
                }
                // Optionally, show a toast to the user.
                Toast.makeText(this, getString(R.string.profile_image_access_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupListeners() {
        ivProfileImage.setOnClickListener {
            showChangePhotoDialog()
        }

        btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        btnLogout.setOnClickListener {
            // clear current user and go to login
            val prefs = getSharedPreferences("chesskel_prefs", MODE_PRIVATE).edit()
            prefs.remove("current_user_id")
            prefs.remove("current_user_email")
            prefs.remove("current_user_name")
            prefs.apply()
            startActivity(Intent(this, com.chesskel.ui.auth.LoginActivity::class.java))
            finish()
        }

        btnGetLocation.setOnClickListener {
            ensureLocationPermissionAndFetch()
        }
    }

    private fun showChangePhotoDialog() {
        val options = arrayOf(
            getString(R.string.profile_pick_gallery),
            getString(R.string.profile_take_photo)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_change_photo))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> openCamera()
                }
            }
            .show()
    }

    private fun openGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // Use ACTION_OPEN_DOCUMENT to get a persistent, grantable URI from the system picker
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                // Request persistable permission when possible
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            }
            pickImageLauncher.launch(intent)
        } else {
            pendingPermissionAction = PendingPermissionAction.NONE
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun openCamera() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = PendingPermissionAction.CAMERA
            requestPermissionLauncher.launch(permission)
            return
        }

        launchCamera()
    }

    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val imageFile = File.createTempFile("profile_", ".jpg", imagesDir)

        val authority = "$packageName.fileprovider"
        val uri = FileProvider.getUriForFile(this, authority, imageFile)
        tempCameraImageUri = uri

        takePictureLauncher.launch(uri)
    }

    private fun ensureLocationPermissionAndFetch() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            startLocationAcquisition()
        } else {
            pendingPermissionAction = PendingPermissionAction.LOCATION
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationAcquisition() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            Toast.makeText(this, getString(R.string.profile_location_permission_required), Toast.LENGTH_SHORT).show()
            return
        }

        tvLocationValue.text = getString(R.string.profile_location_fetching)

        lifecycleScope.launchWhenStarted {
            val location = getBestLocationOrNull()
            if (location == null) {
                Toast.makeText(this@ProfileActivity, getString(R.string.profile_location_not_available), Toast.LENGTH_SHORT).show()
                tvLocationValue.text = currentUser?.location ?: getString(R.string.profile_location_hint)
                return@launchWhenStarted
            }

            val formatted = withContext(Dispatchers.IO) {
                formatLocationFromCoordinates(location)
            }

            if (formatted != null) {
                tvLocationValue.text = formatted
            } else {
                Toast.makeText(this@ProfileActivity, getString(R.string.profile_location_geocode_failed), Toast.LENGTH_SHORT).show()
                tvLocationValue.text = currentUser?.location ?: getString(R.string.profile_location_hint)
            }
        }
    }

    private suspend fun getBestLocationOrNull(): Location? {
        return try {
            val last: Location? = fusedLocationClient.lastLocation.await()
            if (last != null) {
                last
            } else {
                val current: Location? = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).await()
                current
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatLocationFromCoordinates(location: Location): String? {
        if (!Geocoder.isPresent()) return null

        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val address = addresses?.firstOrNull() ?: return null

            val country = address.countryName

            val isCostaRica = address.countryCode == "CR"

            val level2: String?
            val level3: String?

            if (isCostaRica) {
                // Costa Rica: país / provincia / cantón
                level2 = address.adminArea // Provincia
                level3 = address.subAdminArea ?: address.locality // Cantón aproximado
            } else {
                // General: país / estado / ciudad
                level2 = address.adminArea ?: address.subAdminArea // Estado/Provincia
                level3 = address.locality ?: address.subLocality ?: address.subAdminArea // Ciudad
            }

            val parts = listOf(country, level2, level3)
                .filterNotNull()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (parts.isEmpty()) null else parts.joinToString(" / ")
        } catch (e: IOException) {
            null
        }
    }

    private fun saveProfile() {
        val user = currentUser ?: return
        val locationText = tvLocationValue.text.toString().trim()
        val location = locationText.takeIf { it.isNotEmpty() && it != getString(R.string.profile_location_hint) }
        val uriString = currentProfileImageUri?.toString()

        dbHelper.updateUserProfile(user.id, uriString, location)
        Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()

        // Upsert profile by email on remote server (will update if exists or create if not)
        lifecycleScope.launchWhenStarted {
            try {
                val resp = ApiClient.upsertProfileByEmail(user.email, user.name, null, uriString, location)
                if (resp != null) {
                    Toast.makeText(this@ProfileActivity, getString(R.string.profile_synced_remote), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, getString(R.string.profile_sync_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, getString(R.string.profile_sync_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
