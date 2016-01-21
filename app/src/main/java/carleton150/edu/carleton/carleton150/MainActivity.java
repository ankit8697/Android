package carleton150.edu.carleton.carleton150;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import android.os.Bundle;
import android.service.carrier.CarrierMessagingService;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;


import java.util.ArrayList;

import carleton150.edu.carleton.carleton150.MainFragments.FragmentChangeListener;
import carleton150.edu.carleton.carleton150.MainFragments.MainFragment;
import carleton150.edu.carleton.carleton150.MainFragments.MyFragmentPagerAdapter;
import carleton150.edu.carleton.carleton150.MainFragments.QuestFragment;
import carleton150.edu.carleton.carleton150.MainFragments.QuestInProgressFragment;
import carleton150.edu.carleton.carleton150.Models.GeofenceErrorMessages;
import carleton150.edu.carleton.carleton150.Models.GeofenceMonitor;
import carleton150.edu.carleton.carleton150.Models.VolleyRequester;
import carleton150.edu.carleton.carleton150.POJO.EventObject.Events;
import carleton150.edu.carleton.carleton150.POJO.GeofenceInfoObject.GeofenceInfoContent;
import carleton150.edu.carleton.carleton150.POJO.GeofenceObject.GeofenceObjectContent;

/**
 * Monitors location and geofence information and calls methods in the main view fragments
 * to handle geofence and location changes. Also controls which fragment is in view.
 */
public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, ResultCallback<Status>, FragmentChangeListener{

    //things for managing fragments
    public static FragmentManager fragmentManager;


    //things for location
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private Location mLastLocation;
    //last location where we requested new geofences
    private Location lastGeofenceUpdateLocation;
    // Google client to interact with Google API
    public GoogleApiClient mGoogleApiClient;
    // boolean flag to toggle periodic location updates
    private boolean mRequestingLocationUpdates = true;
    private LocationRequest mLocationRequest;
    // Location updates intervals in milliseconds
    private static int UPDATE_INTERVAL = 30000; // 30 sec
    private static int FASTEST_INTERVAL = 10000; // 10 sec
    private static int DISPLACEMENT = 100; // 100 meters

    private LogMessages logMessages = new LogMessages();

    MainFragment curFragment = null;

    //things for detecting geofence entry



    private MyFragmentPagerAdapter adapter;

    public VolleyRequester mVolleyRequester = new VolleyRequester();
    AlertDialog networkAlertDialog;
    AlertDialog playServicesConnectivityAlertDialog;

    public GeofenceMonitor geofenceMonitor = new GeofenceMonitor(this);



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        networkAlertDialog = new AlertDialog.Builder(MainActivity.this).create();
        playServicesConnectivityAlertDialog = new AlertDialog.Builder(MainActivity.this).create();
            // check availability of play services for location data and geofencing
            if (checkPlayServices()) {
                buildGoogleApiClient();
                createLocationRequest();
                if(isConnectedToNetwork()) {
                    mGoogleApiClient.connect();
                }
            } else {
                showGooglePlayServicesUnavailableDialog();
            }




        //populateGeofenceList();

        //managing fragments and UI
        fragmentManager = getSupportFragmentManager();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);

        }
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_action_history));
        tabLayout.addTab(tabLayout.newTab().setText("Events"));
        tabLayout.addTab(tabLayout.newTab().setText("Quests"));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        adapter = new MyFragmentPagerAdapter
                (getSupportFragmentManager(), tabLayout.getTabCount());
        viewPager.setAdapter(adapter);
        curFragment = adapter.getCurFragment();
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                if (curFragment != null) {
                    curFragment.fragmentOutOfView();
                }
                curFragment = adapter.getCurFragment();
                curFragment.fragmentInView();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

    }






    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Resuming the periodic location updates
        if (mGoogleApiClient.isConnected()) {
            isConnectedToNetwork();
            if(mRequestingLocationUpdates) {
                startLocationUpdates();
            }
        }
        else{
            if(isConnectedToNetwork()){
                // check availability of play services for location data and geofencing
                if (checkPlayServices()) {
                    mGoogleApiClient.connect();
                } else {
                    showGooglePlayServicesUnavailableDialog();
                }
            }
        }

    }


    /**
     * Method that is called when google API Client is connected
     * @param bundle
     */
    @Override
    public void onConnected(Bundle bundle) {

        // Once connected with google api, get the location
        mLastLocation = LocationServices.FusedLocationApi
                .getLastLocation(mGoogleApiClient);
        tellFragmentLocationChanged();
        //starts periodic location updates
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
        geofenceMonitor.googlePlayServicesConnected();
        tellFragmentGoogleServicesConnected();

        //gets new geofences from the server
        //Sets the last geofence update location since we just retrieved geofences
        lastGeofenceUpdateLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    private void tellFragmentGoogleServicesConnected(){
        Log.i(logMessages.GEOFENCE_MONITORING, "MainActivity: google services connected");
        MainFragment curFragment = adapter.getCurFragment();
        if(curFragment != null) {
            curFragment.googlePlayServicesConnected();
        }
    }

    /**
     * If google api client connection was suspended, keeps trying to connect
     * @param i
     */
    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    /**
     * Displays an alert dialog if unable to connect to the GoogleApiClient
     * @param connectionResult
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        showAlertDialog("Connection to play services failed with message: " +
                        connectionResult.getErrorMessage() + "\nCode: " + connectionResult.getErrorCode(),
                playServicesConnectivityAlertDialog);
    }

    /**
     * Builds a GoogleApiClient
     */
    protected synchronized void buildGoogleApiClient() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    /**
     * Method to verify google play services on the device
     */
    public boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {

                finish();
            }
            return false;
        }
        return true;
    }
    

    @Override
    public void onLocationChanged(Location location) {
        // Assign the new location
        mLastLocation = location;
        tellFragmentLocationChanged();
    }

    /**
     * Calls a method in the current fragment to handle a location change.
     * The contents of handleLocationChange() varies depending on the fragment
     */
    private void tellFragmentLocationChanged(){
        MainFragment curFragment = adapter.getCurFragment();
        if(curFragment != null) {
            curFragment.handleLocationChange(mLastLocation);
        }
    }


    public void handleGeofenceChange(ArrayList<GeofenceObjectContent> content){
        MainFragment curFragment = adapter.getCurFragment();
        if(curFragment != null) {
            curFragment.handleGeofenceChange(content);
        }
    }

    public void notifyQuestFragmentClueCompleted(){
        if(curFragment == null) {
            curFragment = adapter.getCurFragment();
        }
        if(curFragment != null){
            curFragment.clueCompleted();
        }
    }




    /**
     * Creating location request object
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    /**
     * Starting the location updates
     */
    protected void startLocationUpdates() {

        if (mGoogleApiClient.isConnected()) {
            if(mRequestingLocationUpdates) {
                LocationServices.FusedLocationApi.requestLocationUpdates(
                        mGoogleApiClient, mLocationRequest, this);
            }
        }
    }

    /**
     * Stopping location updates
     */
    protected void stopLocationUpdates() {
        if(mGoogleApiClient.isConnected()) {
            Log.i(logMessages.LOCATION, "stopLocationUpdates : location updates stopped");
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }


    /**
     * checks whether phone has network connection. If not, displays a dialog
     * requesting that the user connects to a network.
     * @return
     */
    public boolean isConnectedToNetwork(){
        ConnectivityManager connectivityManager
                = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if(activeNetworkInfo != null){
            if(activeNetworkInfo.isConnected()) {
                return true;
            } else {
                showNetworkNotConnectedDialog();
                return false;
            }
        }else {
            showNetworkNotConnectedDialog();
            return false;
        }
    }

   /**
     * displays a dialog requesting that the user connect to a network
     */
    public void showNetworkNotConnectedDialog() {
        showAlertDialog(getResources().getString(R.string.no_network_connection),
                networkAlertDialog);
    }

    private void showGooglePlayServicesUnavailableDialog(){
        showAlertDialog(getResources().getString(R.string.no_google_services), playServicesConnectivityAlertDialog);
    }

    /**
     * shows an alert dialog with the specified message
     * @param message
     */
    public void showAlertDialog(String message, AlertDialog dialog) {
        if(!dialog.isShowing()) {
            dialog.setTitle("Alert");
            dialog.setMessage(message);
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener()

                    {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            dialog.show();
        }
    }

    public void handleNewGeofences(GeofenceObjectContent[] content){
        if(curFragment == null){
            curFragment = adapter.getCurFragment();
        }
        curFragment.handleNewGeofences(content);
    }

    public GeofenceMonitor getGeofenceMonitor(){
        return this.geofenceMonitor;
    }

    /**
     * Runs when the result of calling addGeofences() and removeGeofences() becomes available.
     * Either method can complete successfully or with an error.
     *
     * The activity implements ResultCallback, so this is a required method
     *
     * @param status The Status returned through a PendingIntent when addGeofences() or
     *               removeGeofences() get called.
     */
    public void onResult(Status status) {
        if (status.isSuccess()) {
            geofenceMonitor.mGeofencesAdded = !geofenceMonitor.mGeofencesAdded;
        } else {
            // Get the status code and log it.
            String errorMessage = GeofenceErrorMessages.getErrorString(this,
                    status.getStatusCode());
            Log.e(logMessages.GEOFENCE_MONITORING, "onResult error: " + errorMessage);
        }
    }

    @Override
    public void replaceFragment(MainFragment fragment) {
        adapter.replaceFragment(fragment);
    }

    @Override
    public void onBackPressed() {
        if(adapter.getCurFragment() instanceof QuestInProgressFragment){
            adapter.replaceFragment();
        }
    }

    /**
     * TODO: Fix bug where if you start app when not connected to internet then come back you have to restart app
     */
}
