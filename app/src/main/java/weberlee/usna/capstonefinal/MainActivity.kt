package weberlee.usna.capstonefinal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.res.ResourcesCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import kotlin.text.Typography.degree

@ExperimentalStdlibApi
class MainActivity() : AppCompatActivity(),SensorEventListener {
    private var setup = false
    private var mSensorManager : SensorManager ?= null
    private var magneto : Sensor ?= null
    private var accelerometer : Sensor ?= null
    private var stepCounter: Sensor ?= null
    private var network : Boolean = false
    private lateinit var intLocObj: Location
    private var initialLocation = FloatArray(2)
    private var prevAccelerometer = FloatArray(3)
    private var prevAccelSet = false
    private var prevMagnetometer = FloatArray(3)
    private var prevMagnetoSet = false
    private var currentBearing = 0.0f
    private var captureID = "Placeholder"
    private var captureIndex = 0
    private var strideLen = 0.0f
    private var captureNameSet = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //    private val volleyQueue = Volley.newRequestQueue()
    val REQUEST_LOCATION = 2 // sets location request permissions


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            lowPassFilter(event.values,prevAccelerometer)
            prevAccelSet = true

        }
        else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD){
            lowPassFilter(event.values,prevMagnetometer)
            prevMagnetoSet = true

        }
        if (prevAccelSet && prevMagnetoSet){
            val r = FloatArray(9)
            if (SensorManager.getRotationMatrix(r,null,prevAccelerometer,prevMagnetometer)){

                val orientation = FloatArray(3)
                SensorManager.getOrientation(r,orientation)
                val degree = (Math.toDegrees(orientation[0].toDouble()) + 360).toFloat() % 360
                Log.i("Bearing",degree.toString())
                currentBearing = degree

            }
        }
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR){
            captureID = captureIDfield.text.toString()
            strideLen = stride_len.text.toString().toFloat()
            if(captureNameSet){
                sendToBackend(createJSONEntry(),"datapoint")

            }else{
                createCaptureInstance()
                getInitialLocation()
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSensorSuite()

        checkNetworkStatus()
        val startButton = findViewById<Button>(R.id.start_button)
        val captureNameField = findViewById<EditText>(R.id.captureIDfield)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startButton.setOnClickListener{
            captureID = captureIDfield.text.toString()
            strideLen = stride_len.text.toString().toFloat()
            if(captureNameSet){
                sendToBackend(createJSONEntry(),"datapoint")

            }else{
                createCaptureInstance()
                getInitialLocation()
            }
        }
    }


    private fun setupSensorSuite(){
        val results = checkSensorSuite()
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (results[1]){
            magneto = mSensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            mSensorManager!!.registerListener(this, magneto, SensorManager.SENSOR_DELAY_UI)
        }


        if (results[0]){
            accelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            mSensorManager!!.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        if (results[2]){
            stepCounter = mSensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            Log.i("SETUP","STEP SENSOR PRESENT")
            mSensorManager!!.registerListener(this,stepCounter,SensorManager.SENSOR_DELAY_UI)
        }
        else{
            Toast.makeText(applicationContext,"StepCounter Not Available",Toast.LENGTH_SHORT).show()
        }
    }
    private fun getInitialLocation(){
        //get permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){ ActivityCompat.requestPermissions(this,arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),1);return }
        //get location
        fusedLocationClient.lastLocation.addOnSuccessListener(this) {location ->
            if (location != null){
                intLocObj = location;
                initialLocation[0] = intLocObj.latitude.toFloat()
                initialLocation[1] = intLocObj.longitude.toFloat()
                Log.i("IntLocation",initialLocation.contentToString())
                var entryJSON = JSONObject()
                entryJSON.accumulate("dbtype","intLocation")
                entryJSON.accumulate("tag",captureID)
                entryJSON.accumulate("lat",initialLocation[0])
                entryJSON.accumulate("lon",initialLocation[1])
                sendToBackend(entryJSON,"intLocation")
            }
            else{
                Log.i("locationWasNull","true")
            }
        }

    }

    private fun checkNetworkStatus(){
        sendToBackend(JSONObject().accumulate("dbtype","status"),"networkSetup")
    }
    private fun checkSensorSuite():BooleanArray{
        var sensorStatusArray = booleanArrayOf(false,false,false)
        sensorStatusArray[0] = true
        sensorStatusArray[1] = checkMagnetometer()
        sensorStatusArray[2] = checkStepCounter()
        return sensorStatusArray
    }
    private fun checkMagnetometer(): Boolean{
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null){
            Log.i("SENSOR","Magnetometer Present")
//            magCheck.setImageDrawable(ResourcesCompat.getDrawable(applicationContext.resources,R.drawable.ic_check_black_24dp,null))
            return true
        } else {
            Log.i("SENSOR","Magnetometer Not Present")
//            magCheck.setImageDrawable(ResourcesCompat.getDrawable(applicationContext.resources,R.drawable.ic_clear_black_24dp,null))

        }
        return false
    }
    private fun checkStepCounter(): Boolean{
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null){
            Log.i("SENSOR","STEP COUNTER Present")
//            stepCheck.setImageDrawable(ResourcesCompat.getDrawable(applicationContext.resources,R.drawable.ic_check_black_24dp,null))
            return true
        } else {
            Log.i("SENSOR","STEP COUNTER Not Present")
//            stepCheck.setImageDrawable(ResourcesCompat.getDrawable(applicationContext.resources,R.drawable.ic_clear_black_24dp,null))
        }
        return false
    }
    private fun lowPassFilter(input: FloatArray, output: FloatArray){
        val alpha = 0.1f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }
    private fun createJSONEntry():JSONObject{
        var entryJSON = JSONObject()
        entryJSON.accumulate("dbtype","datapoints")
        entryJSON.accumulate("tag",captureID)
        entryJSON.accumulate("captureIndex",captureIndex)
        entryJSON.accumulate("bearing",currentBearing)
        jsonLabel.text = entryJSON.toString()
        return entryJSON
    }

    override fun onResume() {
        super.onResume()

        mSensorManager?.registerListener(this, accelerometer, SENSOR_DELAY_GAME)
        mSensorManager?.registerListener(this, magneto, SENSOR_DELAY_GAME)
    }
    override fun onPause(){
        super.onPause()
        mSensorManager?.unregisterListener(this, accelerometer)
        mSensorManager?.unregisterListener(this, magneto)
    }
    private fun createCaptureInstance(){
        var entryJSON = JSONObject()
        entryJSON.accumulate("dbtype","captures")
        entryJSON.accumulate("tag",captureID)
        entryJSON.accumulate("stridelen",strideLen)
        sendToBackend(entryJSON,"captureSetup")

        sendToBackend(createJSONEntry(),"datapoint")
    }

    private fun sendToBackend(JSONobj : JSONObject,msgType:String){
        val volleyQueue = Volley.newRequestQueue(this)
        val url = "http://54.175.22.220/cgi-bin/cgipython.py"
        val jsonObjReq = JsonObjectRequest(
            Request.Method.POST,
            url,
            JSONobj,
            Response.Listener {
                response ->
                if (response["response"] == 0){
                    Toast.makeText(applicationContext,"Yay",Toast.LENGTH_SHORT).show()
                }
                if (msgType == "NetworkSetup"){
                    network = true
                } else if(msgType == "datapoint") {
                    captureIndex+=1
                } else if (msgType == "captureSetup") {
                    captureNameSet = true
                }
            },
            Response.ErrorListener{ error ->
                Log.e("volley","Error: %s".format(error.toString()))
                Toast.makeText(applicationContext,"Network Connection Unavailable", Toast.LENGTH_SHORT).show()
            })
        volleyQueue.add(jsonObjReq)

    }
}



