package com.example.navigationchangeroutetest

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.navigationchangeroutetest.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.navigation.DisplayOptions
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.SimulationOptions
import com.google.android.libraries.navigation.SupportNavigationFragment
import com.google.android.libraries.navigation.Waypoint

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding

    private var googleMap: GoogleMap? = null

    private val cancellationTokenSource = CancellationTokenSource()
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(
            this@MainActivity
        )
    }

    @SuppressLint("MissingPermission")
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var counterGranted = 0
            for (item in permissions.entries) {
                if (item.value) {
                    counterGranted++
                }
            }
            val isGranted = counterGranted == permissions.size
            log("LOCATION PERMISSION GRANTED = $isGranted")

            if (isGranted) {
                googleMap?.isMyLocationEnabled = true
                getLastLocation { lastLocation ->
                    animateCameraToTargetPosition(lastLocation)
                }
            }
        }

    private var mNavFragment: SupportNavigationFragment? = null
    private var mNavigator: Navigator? = null
    private var mRoutingOptions: RoutingOptions = RoutingOptions().apply {
        travelMode(RoutingOptions.TravelMode.DRIVING)
        avoidFerries(false)
        avoidTolls(false)
        avoidHighways(false)
    }

    private val mArrivalListener: Navigator.ArrivalListener by lazy {
        Navigator.ArrivalListener { event ->
            if (event?.isFinalDestination == true) {
                //onEndNavigation()
            } else {
                mNavigator?.continueToNextDestination()
                mNavigator?.startGuidance()
            }
        }
    }

    private val mRemainingTimeOrDistanceListener: Navigator.RemainingTimeOrDistanceChangedListener by lazy {
        Navigator.RemainingTimeOrDistanceChangedListener {
            val timeAndDistance = mNavigator?.currentTimeAndDistance
            //navigationViewModel.updateNavigationTimeAndDistance(timeAndDistance)
        }
    }

    private var mCurrentAudioMode = Navigator.AudioGuidance.SILENT
    private var mCurrentCameraMode = GoogleMap.CameraPerspective.TILTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.map.getFragment<SupportNavigationFragment>().getMapAsync(this)

        binding.buttonStartNavigation.setOnClickListener {
            googleMap?.cameraPosition?.target?.let { targetLocation ->
                startNavigationToLocation(targetLocation)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        this.googleMap = googleMap

        googleMap?.uiSettings?.isCompassEnabled = false
        googleMap?.uiSettings?.isMapToolbarEnabled = false
        googleMap?.uiSettings?.isMyLocationButtonEnabled = true
        if (checkLocationPermission()) {
            googleMap?.isMyLocationEnabled = true
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION
                )
            )
        }

        getLastLocation { lastLocation ->
            animateCameraToTargetPosition(lastLocation)
        }
    }

    private fun animateCameraToTargetPosition(location: Location) {
        googleMap?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().zoom(15f).target(location.toLatLng()).build()
            ),
            350,
            null
        )
    }

    private fun animateCameraToPlaceBounds(placeList: List<LatLng>) {
        getLastLocation { lastLocation ->
            val boundsBuilder = LatLngBounds.builder()
            boundsBuilder.include(lastLocation.toLatLng())
            placeList.forEach {
                boundsBuilder.include(it)
            }
            googleMap?.animateCamera(
                if (placeList.isEmpty()) {
                    CameraUpdateFactory.newLatLngZoom(
                        lastLocation.toLatLng(),
                        15f,
                    )
                } else {
                    CameraUpdateFactory.newLatLngBounds(
                        boundsBuilder.build(),
                        160
                    )
                },
                350,
                null
            )
        }
    }

    private fun getLastLocation(onSuccess: (location: Location) -> Unit) {
        if (checkLocationPermission()) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            )
                .addOnSuccessListener { lastLocation ->
                    onSuccess(lastLocation)
                }
                .addOnCanceledListener {
                    loge("get current location request canceled")
                }
                .addOnFailureListener {
                    loge("get current location request error", it)
                }
        }
    }

    private fun startNavigationToLocation(location: LatLng) {
        checkNavigatorTermsAndConditionsAccepted(startTravel = true) {
            navigateToPlace(listOf(location), mRoutingOptions, startTravel = false)
        }
    }

    private fun checkNavigatorTermsAndConditionsAccepted(
        startTravel: Boolean,
        onSuccess: () -> Unit
    ) {
        if (NavigationApi.areTermsAccepted(application).not()) {
            NavigationApi.showTermsAndConditionsDialog(
                this, "google_maps_terms_dialog_title"
            ) { termsAccepted ->
                if (termsAccepted) {
                    initNavigatorApi(startTravel, onSuccess)
                } else {
                    loge("Google Maps Navigation terms not accepted!")
                }
            }
        } else {
            initNavigatorApi(startTravel, onSuccess)
        }
    }

    private fun initNavigatorApi(startTravel: Boolean, onSuccess: () -> Unit) {
        NavigationApi.getNavigator(application, object :
            NavigationApi.NavigatorListener {
            @SuppressLint("MissingPermission")
            override fun onNavigatorReady(navigator: Navigator) {
                log("Navigator ready!")
                mNavigator = navigator
                mNavFragment = binding.map.getFragment() as? SupportNavigationFragment

                if (startTravel) {
                    googleMap?.followMyLocation(mCurrentCameraMode)
                }

                onSuccess()
            }

            override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                when (errorCode) {
                    NavigationApi.ErrorCode.NOT_AUTHORIZED -> loge(
                        "Error loading Navigation SDK: Your API key is "
                                + "invalid or not authorized to use the Navigation SDK."
                    )

                    NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> loge(
                        ("Error loading Navigation SDK: User did not accept "
                                + "the Navigation Terms of Use.")
                    )

                    NavigationApi.ErrorCode.NETWORK_ERROR -> loge("Error loading Navigation SDK: Network error.")
                    NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> loge(
                        ("Error loading Navigation SDK: Location permission "
                                + "is missing.")
                    )

                    else -> loge("Error loading Navigation SDK: $errorCode")
                }
            }
        })
    }

    private fun navigateToPlace(
        placeList: List<LatLng>,
        travelMode: RoutingOptions,
        startTravel: Boolean
    ) {
        val destinations: List<Waypoint> = placeList.mapNotNull { place ->
            try {
                Waypoint.builder().setLatLng(place.latitude, place.longitude)
                    .build()
            } catch (e: Waypoint.UnsupportedPlaceIdException) {
                loge("Error starting navigation: Place ID is not supported.")
                null
            }
        }

        if (destinations.isEmpty()) {
            loge("Error setting navigation destination")
            return
        }

        mNavigator?.addRouteChangedListener {
            //TODO check if the Navigation Sdk is broken
            log("Called route changed listener!")
        }

        mNavigator?.addRemainingTimeOrDistanceChangedListener(
            5,
            10,
            mRemainingTimeOrDistanceListener
        )

        mNavigator?.setHeadsUpNotificationEnabled(false)
        mNavigator?.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)

        mNavigator?.fetchRouteInfo(destinations.last(), travelMode)
            ?.setOnResultListener { routeInfo ->
                val timeAndDistance =
                    routeInfo.getTimeAndDistance(RoutingOptions.RoutingStrategy.DEFAULT_BEST)
                log("routeInfo SECONDS = ${timeAndDistance?.seconds} - METERS = ${timeAndDistance?.meters}")
            }

        val displayOptions = DisplayOptions().apply {
            hideDestinationMarkers(true)
            showTrafficLights(true)
            showStopSigns(true)
        }

        val pendingRoute = mNavigator?.setDestinations(destinations, travelMode, displayOptions)

        pendingRoute?.setOnResultListener { code ->
            when (code) {
                Navigator.RouteStatus.OK -> {
                    loge("Pending route result OK!")

                    if (startTravel) {

                        mNavigator?.setAudioGuidance(mCurrentAudioMode)

                        if (BuildConfig.DEBUG) {
                            mNavigator?.simulator?.simulateLocationsAlongExistingRoute(
                                SimulationOptions().speedMultiplier(6.5f)
                            )
                        }

                        mNavigator?.addArrivalListener(mArrivalListener)

                        mNavigator?.startGuidance()
                    } else {
                        animateCameraToPlaceBounds(placeList)
                    }
                }

                Navigator.RouteStatus.NO_ROUTE_FOUND -> loge("Error starting navigation: No route found.")
                Navigator.RouteStatus.NETWORK_ERROR -> loge("Error starting navigation: Network error.")
                Navigator.RouteStatus.ROUTE_CANCELED -> loge("Error starting navigation: Route canceled.")
                else -> loge(
                    "Error starting navigation: "
                            + code.toString()
                )
            }
        }
    }
}