package com.example.publisherapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isPublishing = false
    private var client: Mqtt5BlockingClient? = null

    private lateinit var studentIdEditText: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        studentIdEditText = findViewById(R.id.studentIdEditText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        // Configure the MQTT client as you showed us in the lab
        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816034662.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Location callback to handle location updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    sendLocationToBroker(location)
                }
            }
        }


        startButton.setOnClickListener {
            if (!isPublishing) {
                checkLocationPermissionAndStart()
            }
        }

        stopButton.setOnClickListener {
            if (isPublishing) {
                stopPublishing()
            }
        }
    }

    private fun checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startPublishing()
        }
    }

    private fun startPublishing() {
        isPublishing = true
        connectToBroker()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // 5 seconds
            .setMinUpdateIntervalMillis(2000) // for every 2 seconds
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            Toast.makeText(this, "Started publishing location updates", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
        }
    }



    private fun stopPublishing() {
        isPublishing = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        disconnectFromBroker()
        Toast.makeText(this, "Stopped publishing location updates", Toast.LENGTH_SHORT).show()
    }

    private fun connectToBroker() {
        try {
            Log.d("PublisherApp-ConnectBroker", "Connecting to broker...")
            client?.connect()
            Log.d("PublisherApp-ConnectBroker", "Connected to broker")
            Toast.makeText(this, "Connected to broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "An error occurred when connecting to broker", Toast.LENGTH_SHORT).show()
            Log.e("PublisherApp-ConnectBroker", "Error connecting to broker", e)
        }
    }


    private fun disconnectFromBroker() {
        try {
            client?.disconnect()
            Toast.makeText(this, "Disconnected from broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "An error occurred when disconnecting from broker", Toast.LENGTH_SHORT).show()
            Log.e("PublisherApp", "Error disconnecting from broker", e)
        }
    }

    private fun sendLocationToBroker(location: Location) {
        val studentId = studentIdEditText.text.toString()
        val speed = location.speed // Speed in meters/second
        val msTime = location.time // Timestamp in milliseconds since epoch

        // Convert speed to km/h for easier understanding
        val speedInKmH = speed * 3.6
        val message = "Student ID: $studentId | Speed: $speedInKmH km/h | Timestamp: $msTime | Location: ${location.latitude}, ${location.longitude}"

        try {
            Log.d("PublisherApp-LocationPayload", "Attempting to publish message: $message")
            client?.publishWith()
                ?.topic("assignment/location")
                ?.payload(message.toByteArray())
                ?.send()
            Log.d("PublisherApp-LocationPayload", "Message successfully published: $message")
        } catch (e: Exception) {
            Toast.makeText(this, "An error occurred when sending a message to the broker", Toast.LENGTH_SHORT).show()
            Log.e("PublisherApp-LocationPayload", "Error publishing message", e)
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPublishing()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
