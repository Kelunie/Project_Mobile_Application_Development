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
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
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
import kotlinx.coroutines.launch

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
                val localPath = saveImageToLocal(uri)
                if (localPath != null) {
                    pendingImagePath = localPath
                    currentProfileImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(filesDir, localPath))
                    loadUser()
                } else {
                    Toast.makeText(this, R.string.profile_image_save_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraImageUri != null) {
            // La cámara YA guardó la imagen en filesDir/profile_images/{id}.jpg
            val localPath = "profile_images/${currentUser?.id}.jpg"

            pendingImagePath = localPath
            currentProfileImageUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                File(filesDir, localPath)
            )

            loadUser()
        } else {
            Toast.makeText(this, R.string.profile_image_save_error, Toast.LENGTH_SHORT).show()
        }

        tempCameraImageUri = null
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

    private var pendingImagePath: String? = null

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

        // Load image: prioritize selected image, then from DB
        if (currentProfileImageUri != null) {
            Glide.with(this).load(currentProfileImageUri).skipMemoryCache(true).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).into(ivProfileImage)
        } else {
            currentUser?.profileImagePath?.let { relativePath ->
                val file = File(filesDir, relativePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    Glide.with(this).load(uri).skipMemoryCache(true).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).into(ivProfileImage)
                }
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
            // Use ACTION_PICK to open the gallery
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
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
        val imagesDir = File(filesDir, "profile_images").apply { mkdirs() }
        val imageFile = File(imagesDir, "${currentUser?.id ?: "temp"}.jpg")

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

        var localPath: String? = user.profileImagePath
        val isNewImage = pendingImagePath != null

        if (isNewImage) {
            localPath = pendingImagePath
        }

        lifecycleScope.launch {
            val profileImageUrl: String? = if (isNewImage && currentProfileImageUri != null) {
                val remoteUser = ApiClient.getUserByEmail(user.email)
                val remoteId = remoteUser?.optLong("id", -1L) ?: -1L
                if (remoteId == -1L) {
                    Toast.makeText(this@ProfileActivity, "Unable to get remote user ID", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val (url, error) = ApiClient.uploadImage(this@ProfileActivity, currentProfileImageUri!!, remoteId)
                if (url == null) {
                    Toast.makeText(this@ProfileActivity, "Upload failed: ${error ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                url
            } else {
                null
            }

            dbHelper.updateUserProfile(user.id, localPath, location)
            Toast.makeText(this@ProfileActivity, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()

            pendingImagePath = null
            currentProfileImageUri = null
            loadUser()

            if (isNewImage && profileImageUrl != null) {
                try {
                    ApiClient.upsertProfileByEmail(user.email, user.name, null, profileImageUrl, location)
                } catch (e: Exception) {
                    Toast.makeText(this@ProfileActivity, getString(R.string.profile_sync_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToLocal(uri: Uri): String? {
        val user = currentUser ?: return null
        val imagesDir = File(filesDir, "profile_images").apply { mkdirs() }
        val imageFile = File(imagesDir, "${user.id}.jpg")
        // Delete old image if exists
        if (imageFile.exists()) imageFile.delete()
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return "profile_images/${user.id}.jpg"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
