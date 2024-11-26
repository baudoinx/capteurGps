package com.example.locationapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isTracking = false
    private val handler = Handler()
    private var runnable: Runnable? = null
    private val locationList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialisation du FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContentView(R.layout.activity_main)

        val textLocation: TextView = findViewById(R.id.text_location)
        val textPointsCounts: TextView =findViewById(R.id.text_points_count)
        val buttonStart: Button = findViewById(R.id.button_start)
        val buttonStop: Button = findViewById(R.id.button_stop)

        // Demander la permission d'accès à la localisation
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission accordée, commencer à tracker la localisation
            } else {
                Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
            }
        }

        // Clic sur le bouton Start
        buttonStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startTracking(textLocation, textPointsCounts)
            } else {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Clic sur le bouton Stop
        buttonStop.setOnClickListener {
            stopTracking()
            saveToDownloads()
        }
    }

    // Méthode pour démarrer l'enregistrement des coordonnées GPS
    private fun startTracking(textView: TextView,textPointsCount: TextView) {
        if (!isTracking) {
            isTracking = true

            // Configuration de LocationRequest
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateDelayMillis(500)
                .build()

            // Définition du LocationCallback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (location in locationResult.locations) {
                        val lat = location.latitude
                        val lon = location.longitude
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                        textView.text = "Lat: $lat, Lon: $lon"
                        textPointsCount.text = "Points enregistrés : ${locationList.size}"

                        locationList.add("<trkpt lat=\"$lat\" lon=\"$lon\"><time>$timestamp</time></trkpt>")
                    }
                }
            }

            // Demander des mises à jour de localisation
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    // Méthode pour arrêter l'enregistrement des coordonnées GPS
    private fun stopTracking() {
        if (isTracking) {
            isTracking = false

            // Vérifiez si runnable n'est pas null
            runnable?.let {
                handler.removeCallbacks(it)
                runnable = null
            }

            // Supprimez les mises à jour de localisation
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }


    // Méthode pour récupérer la localisation actuelle
    private fun getLocation(textView: TextView) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    textView.text = "Lat: $lat, Lon: $lon"

                    // Ajouter le point GPS à la liste pour sauvegarde dans le fichier GPX
                    locationList.add("<trkpt lat=\"$lat\" lon=\"$lon\"><time>$timestamp</time></trkpt>")
                } else {
                    textView.text = "Impossible d'obtenir la localisation"
                }
            }
        } else {
            textView.text = "Permission de localisation non accordée"
        }
    }

    // Méthode pour sauvegarder les données GPS dans un fichier GPX dans le répertoire Downloads
    private fun saveToDownloads() {
        try {
            val gpxContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<gpx version=\"1.1\" creator=\"LocationApp\">\n" +
                    "<trk>\n" +
                    "<name>Tracking</name>\n" +
                    "<trkseg>\n" +
                    locationList.joinToString("\n") +  // Ajouter tous les points de localisation capturés
                    "</trkseg>\n" +
                    "</trk>\n" +
                    "</gpx>"

            // Créer un fichier GPX dans le répertoire Downloads
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "tracking.gpx")  // Nom du fichier
                put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)  // Le répertoire Downloads
                put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)  // Timestamp
            }

            val contentResolver = contentResolver
            val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            // Utiliser l'OutputStream pour écrire dans le fichier GPX
            uri?.let { contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(gpxContent.toByteArray())
                Toast.makeText(this, "Fichier GPX sauvegardé dans Downloads", Toast.LENGTH_LONG).show()
            } }

        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lors de l'enregistrement du fichier GPX: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()  // Afficher la trace d'erreur pour le debugging
        }
    }
}
