package com.mysafeatl.atlcomp.mysafeatl;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.mysafeatl.atlcomp.mysafeatl.api.DirectionsAPI;
import com.mysafeatl.atlcomp.mysafeatl.api.DirectionsAPIListener;
import com.mysafeatl.atlcomp.mysafeatl.models.CSVFile;
import com.mysafeatl.atlcomp.mysafeatl.models.Route;
import com.mysafeatl.atlcomp.mysafeatl.repository.ThreadedStack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, DirectionsAPIListener {

    //  Maps
    private GoogleMap googleMap;

    //  Maps Available
    private GoogleApiAvailability api;

    //  Geocoder
    private Geocoder geocoder;
    private List<Address> listAddress;
    private Address address;

    //  Google API Client
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;

    //  GPS Service
    private BroadcastReceiver broadcastReceiver;
    private static final int REQUEST_CODE = 1;
    private static final String EXTRAS_KEY = "coordinates";
    private static final String EXTRAS_KEY_LATITUDE = "latitude";
    private static final String EXTRAS_KEY_LONGITUDE = "longitude";

    //  Google API Directions
    private List<Marker> originMarkers = new ArrayList<>();
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();
    private ProgressDialog progressDialog;
    private static final String LOG_TAG = "DirectionsTest";

    //    private SearchView userLocationSV;
    private Map locationInfo = new HashMap();
    private LatLng currentLocation;

    private Boolean isAllpointsOn = false;
    LatLng oldLatLng = new LatLng(0f, 0f);
    LocationManager lm;
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        oldLatLng = new LatLng(0f, 0f);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


        if (googlePlayServicesAvailable()) {
            setContentView(R.layout.activity_main);
            initMap();

            InputStream inputStream = getResources().openRawResource(R.raw.finaldatatwo);
            CSVFile csvFile = new CSVFile(inputStream);
            List scoreList = csvFile.read();

            for (Object scoreData : scoreList) {
                String[] curr = (String[]) scoreData;
                if (!curr[0].equals("lat")) {
                    if (!curr[0].equals("0")) {
                        float lat = Float.valueOf(curr[1]);
                        float lon = Float.valueOf(curr[0]);

                        LatLng newLatLng = new LatLng(lat, lon);

                        int incidents = Integer.parseInt(curr[2]);
                        locationInfo.put(newLatLng, incidents); //Pair.create(lat, lon)

                    }

                }
            }

        } else {
            //  No Google Maps Layout
        }

        if (!runtimePermission()) {
            enableButtons();
        }


    }

    private void addAllPoints() {
        for (Object allpoints : locationInfo.keySet()) {
            LatLng currenPoint = (LatLng) allpoints;
            googleMap.addCircle(new CircleOptions()
                    .center(currenPoint)
                    .radius(20)
                    .strokeColor(R.color.redtransparent)
                    .fillColor(R.color.redtransparent));
        }
    }

    private void addSinglePoints(LatLng currentPoint) {
        googleMap.addCircle(new CircleOptions()
                .center(currentPoint)
                .radius(20)
                .strokeColor(R.color.redtransparent)
                .fillColor(R.color.redtransparent));
    }

    @Override
    public void onDirectionStart() {
//        progressDialog = ProgressDialog.show(this, "Please wait.", "Finding direction..!", true);
        if (originMarkers != null) {
            for (Marker marker : originMarkers) {
                marker.remove();
            }
        }

        if (destinationMarkers != null) {
            for (Marker marker : destinationMarkers) {
                marker.remove();
            }
        }

        if (polylinePaths != null) {
            for (Polyline polyline : polylinePaths) {
                polyline.remove();
            }
        }
    }

    @Override
    public void onDirectionSuccess(List<Route> routes) {
        //progressDialog.dismiss();
        polylinePaths = new ArrayList<>();
        originMarkers = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        if (routes.size() == 0) {
            Toast.makeText(MainActivity.this, "Sorry, no route was found", Toast.LENGTH_LONG).show();
        } else {
            findBestRoute(routes);
        }
    }

    private void findBestRoute(List<Route> routes) {
        int bestRouteScore = 9999999;

        Route bestRoute = routes.get(0); //this sets bestRoutes to something

        for (Route route : routes) {
            int currentRouteScore = 0;
            for (int i = 0; i < route.points.size(); i++) {

                LatLng currentPoint = route.points.get(i);
                for (Object allpoints : locationInfo.keySet()) {

                    LatLng cur = (LatLng) allpoints;
                    Location locationA = new Location("point A");
                    locationA.setLatitude(currentPoint.latitude);
                    locationA.setLongitude(currentPoint.longitude);
                    Location locationB = new Location("point B");
                    locationB.setLatitude(cur.latitude);
                    locationB.setLongitude(cur.longitude);

                    double distanceInMiles = locationA.distanceTo(locationB) * (0.000621371);

                    if (distanceInMiles <= 0.10) {
                        Object curScore = locationInfo.get(cur);
                        currentRouteScore += (int) curScore;
//                        this.addSinglePoints(cur);
                    }
                }

            }

            if (currentRouteScore < bestRouteScore) {
                bestRouteScore = currentRouteScore;
                bestRoute = route;
            }

        }


        PolylineOptions polylineOptions = new PolylineOptions().
                geodesic(true).
                color(Color.BLUE).
                width(10);

        for (int i = 0; i < bestRoute.points.size(); i++)
            polylineOptions.add(bestRoute.points.get(i));

        polylinePaths.add(googleMap.addPolyline(polylineOptions));

        if (bestRouteScore > 2000) {
            Toast.makeText(MainActivity.this, "We recommend you to take an Uber or Lyft", Toast.LENGTH_SHORT).show();
        }

    }

    private void sendRequest(double originLat, double originLng, double destinationLat, double destinationLng) {
        String origin = originLat + "," + originLng;
        String destination = destinationLat + "," + destinationLng;
        try {
            new DirectionsAPI(this, origin, destination).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        oldLatLng = new LatLng(0f, 0f);

        if (broadcastReceiver == null) {
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    LatLng latLng = new LatLng(intent.getExtras().getDouble(EXTRAS_KEY_LATITUDE), intent.getExtras().getDouble(EXTRAS_KEY_LONGITUDE));
                    MarkerOptions options = new MarkerOptions();
                    options.position(latLng);
                    options.icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_vehicle2));
                    googleMap.addMarker(options);

                    ThreadedStack stack = new ThreadedStack();
                    stack.stackAdd(intent.getExtras().getString(EXTRAS_KEY));
                    Log.d("Stack", "Top: " + stack.getTop());
                }
            };
        }
        registerReceiver(broadcastReceiver, new IntentFilter("location_update"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent(getApplicationContext(), GpsService.class);
        stopService(intent);
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Intent intent = new Intent(getApplicationContext(), GpsService.class);
        stopService(intent);
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    /**
     * RUNTIME PERMISSION #1
     */
    private boolean runtimePermission() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_CODE);
            return true;
        }
        return false;
    }

    /**
     * RUNTIME PERMISSION #2
     */
    private void enableButtons() {
        Intent intent = new Intent(getApplicationContext(), GpsService.class);
        startService(intent);
    }

    /**
     * RUNTIME PERMISSION #3
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                enableButtons();
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED) {
                    googleMap.setMyLocationEnabled(true);
                    googleApiClientConnect();

                    lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 0, 0, new android.location.LocationListener() {
                                @Override
                                public void onLocationChanged(Location location) {
                                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                    if (distance(location.getLatitude(), location.getLongitude(),
                                            oldLatLng.latitude, oldLatLng.longitude) > 30) {
                                        oldLatLng = currentLocation;
                                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));
                                    }
                                }

                                @Override
                                public void onStatusChanged(String s, int i, Bundle bundle) {

                                }

                                @Override
                                public void onProviderEnabled(String s) {

                                }

                                @Override
                                public void onProviderDisabled(String s) {

                                }
                            });
                } else {
                    Toast.makeText(this, "Sorry, you need to allow location permissions to use this app.", Toast.LENGTH_LONG).show();
                }
            } else {
                runtimePermission();
            }
        }
    }

    public boolean googlePlayServicesAvailable() {
        api = GoogleApiAvailability.getInstance();
        int isAvailable = api.isGooglePlayServicesAvailable(this);
        if (isAvailable == ConnectionResult.SUCCESS) {
            return true;
        } else if (api.isUserResolvableError(isAvailable)) {
            Dialog dialog = api.getErrorDialog(this, isAvailable, REQUEST_CODE);
            dialog.show();
        } else {
            Toast.makeText(this, "Can't connect to Google Play Services", Toast.LENGTH_LONG).show();
        }
        return false;
    }

    private void initMap() {
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        this.googleMap = googleMap;
//        this.addAllPoints();

        //current lat/long on device
        try {
            Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            double latcur = 0.00;
            double longcur = 0.00;
            if (location == null) {

                latcur = 33.7779798;
                longcur = -84.38929519999999;
                currentLocation = new LatLng(latcur, longcur);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));

                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {


                    lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 0, 0, new android.location.LocationListener() {
                                @Override
                                public void onLocationChanged(Location location) {
                                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                    if (distance(location.getLatitude(), location.getLongitude(),
                                            oldLatLng.latitude, oldLatLng.longitude) > 30) {
                                        oldLatLng = currentLocation;
                                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));
                                    }
                                }

                                @Override
                                public void onStatusChanged(String s, int i, Bundle bundle) {

                                }

                                @Override
                                public void onProviderEnabled(String s) {

                                }

                                @Override
                                public void onProviderDisabled(String s) {

                                }
                            });
                }
            } else {
                longcur = location.getLongitude();
                latcur = location.getLatitude();
                currentLocation = new LatLng(latcur, longcur);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));

            }
        } catch (SecurityException e) {
//            Toast.makeText(MainActivity.this, "TODO: ADD EXCEPTION TEXT", Toast.LENGTH_LONG).show();
        }


        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleApiClientConnect();
        }

    }

    private void goTolocation(double latitude, double longitude, float zoom) {
        LatLng latLng = new LatLng(latitude, longitude);
    }

    private void goTolocationNow(double latitude, double longitude, float zoom) {
        LatLng latLng = new LatLng(latitude, longitude);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
        googleMap.animateCamera(cameraUpdate);
    }

    public void googleApiClientConnect() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        } else {
            Toast.makeText(this, "Sorry, you need to allow location permissions to use this app.", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            Toast.makeText(this, "Can't get current location", Toast.LENGTH_LONG).show();
        } else {
            final double latitude = location.getLatitude();
            final double longitude = location.getLongitude();
            LatLng latLng = new LatLng(latitude, longitude);

            goTolocation(latitude, longitude, 14);

            try {
                geocoder = new Geocoder(this, Locale.getDefault());
                listAddress = geocoder.getFromLocation(latitude, longitude, 1);
                address = listAddress.get(0);
                String street = address.getAddressLine(0);
                String locality = address.getLocality();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private LatLng get(String url) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        String resStr = response.body().string().toString();
        JSONObject json = new JSONObject(resStr);
        JSONArray routeResponse = (JSONArray)json.get("routes");
        JSONObject routeBounds = (JSONObject)routeResponse.get(0);
        JSONObject boundVals = (JSONObject)routeBounds.get("bounds");
        JSONObject southWestCorner = (JSONObject)boundVals.get("southwest");
        Double latVal = (Double)southWestCorner.get("lat");
        Double longVal = (Double)southWestCorner.get("lng");
        LatLng finalLatLng = new LatLng(latVal, longVal);
        return finalLatLng;
    }

    public LatLng midPoint(double lat1, double lon1, double lat2, double lon2) {

        double dLon = Math.toRadians(lon2 - lon1);
        //convert to radians
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        lon1 = Math.toRadians(lon1);

        double Bx = Math.cos(lat2) * Math.cos(dLon);
        double By = Math.cos(lat2) * Math.sin(dLon);
        double lat3 = Math.atan2(Math.sin(lat1) + Math.sin(lat2), Math.sqrt((Math.cos(lat1) + Bx) * (Math.cos(lat1) + Bx) + By * By));
        double lon3 = lon1 + Math.atan2(By, Math.cos(lat1) + Bx);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {
            String url = "http://maps.googleapis.com/maps/api/directions/" +
                    "json?origin="+Math.toDegrees(lat3)+","+Math.toDegrees(lon3)+"&" +
                    "destination="+Math.toDegrees(lat3)+","+Math.toDegrees(lon3)+"&sensor=false";
            return get(url);
        } catch (IOException e) {
            return new LatLng(Math.toDegrees(lat3), Math.toDegrees(lon3));
        } catch (JSONException e) {
            e.printStackTrace();
            return new LatLng(Math.toDegrees(lat3), Math.toDegrees(lon3));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        final MenuItem searchItem = menu.findItem(R.id.action_search);

        searchItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                try {
                    Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                            .build(MainActivity.this);
                    startActivityForResult(intent, 1);
                } catch (GooglePlayServicesRepairableException e) {
                    // TODO: Handle the error.
                } catch (GooglePlayServicesNotAvailableException e) {
                    // TODO: Handle the error.
                }
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                googleMap.clear();
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                try {

                    double latitude = place.getLatLng().latitude;
                    double longitude = place.getLatLng().longitude;

                    LatLng lat = new LatLng(latitude, longitude);
                    goTolocationNow(latitude, longitude, 16);

                    MarkerOptions marker = new MarkerOptions().position(lat).title("Google Maps");
                    marker.icon(BitmapDescriptorFactory
                            .defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                    try {
                        //my current location. had bug in using find my location from previous saved coordinate
                        Location findme = googleMap.getMyLocation();
                        double latitude2 = findme.getLatitude();
                        double longitude2 = findme.getLongitude();


                        LatLng midOne = midPoint(latitude2, longitude2, latitude, longitude);
                        LatLng midFromStart = midPoint(latitude2, longitude2, midOne.latitude, midOne.longitude);
                        LatLng midFromEnd = midPoint(midOne.latitude, midOne.longitude, latitude, longitude);

                        sendRequest(latitude2, longitude2, midFromStart.latitude, midFromStart.longitude);
                        sendRequest(midFromStart.latitude, midFromStart.longitude, midOne.latitude, midOne.longitude);
                        sendRequest(midOne.latitude, midOne.longitude, midFromEnd.latitude, midFromEnd.longitude);
                        sendRequest(midFromEnd.latitude, midFromEnd.longitude, latitude, longitude);
                    } catch (SecurityException e) {
                        Toast.makeText(MainActivity.this, "Sorry, something went wrong. Try again.", Toast.LENGTH_LONG).show();
                    }

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "There was an issue processing the location",
                            Toast.LENGTH_SHORT).show();
                }

            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.e("Tag", status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

}
