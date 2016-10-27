package com.appspace.evyalerts.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.appspace.appspacelibrary.manager.Contextor;
import com.appspace.appspacelibrary.util.LoggerUtils;
import com.appspace.evyalerts.BuildConfig;
import com.appspace.evyalerts.R;
import com.appspace.evyalerts.fragment.EventListFragment;
import com.appspace.evyalerts.fragment.MapFragment;
import com.appspace.evyalerts.manager.ApiManager;
import com.appspace.evyalerts.model.Event;
import com.appspace.evyalerts.model.Province;
import com.appspace.evyalerts.util.ChromeCustomTabUtil;
import com.appspace.evyalerts.util.DataStoreUtils;
import com.appspace.evyalerts.util.EventUtil;
import com.appspace.evyalerts.util.Helper;
import com.appspace.evyalerts.view.holder.EventHolder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import jp.wasabeef.glide.transformations.BlurTransformation;
import jp.wasabeef.glide.transformations.CropCircleTransformation;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        EventHolder.OnEventItemClickCallback,
        CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "MainActivity";

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;

    private FirebaseAnalytics mFirebaseAnalytics;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    private GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Location mCurrentLocation;

    DrawerLayout drawerLayout;
    ActionBarDrawerToggle actionBarDrawerToggle;
    Toolbar toolbar;
    Button btnProfile;
    ImageView ivProfile;
    ImageView ivProfileBackground;
    TextView tvUsername;
    Button btnAbout;
    Switch swAccident;
    Switch swNaturalDisaster;
    Switch swOther;
    Switch swTrafficJam;

    FloatingActionButton fabAddEvent;

    MaterialDialog mProgressDialog;
    MaterialDialog mGpsDialog;

    boolean wasFirstLocationFig = false;
    boolean wasFirstTimeGetLocation = false;
    boolean isFirstTimeGetAcceptableAccuracy = false;
    boolean isAcceptableAccuracy = false;
    boolean isFirstTimeLoadEvent = false;
    float mAcceptableAccuracy;
    int mCurrentFilterOption = 0;
    boolean didChangeEventFilter1 = false;
    boolean didChangeEventFilter2 = false;
    boolean didChangeEventFilter3 = false;
    boolean didChangeEventFilter4 = false;

    Event recentPostedEvent;
    boolean didPostEvent;

    Location mFirstTimeLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDrawer();
        initFirebase();
        initInstances();
        initGoogleApiClient();
        initTab();

        loadProfileData();

        createLocationRequest();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();

        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        stopLocationUpdates();

        super.onPause();
    }

    private void initDrawer() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        actionBarDrawerToggle = new ActionBarDrawerToggle(
                MainActivity.this,
                drawerLayout,
                toolbar,
                R.string.open_drawer_menu,
                R.string.close_drawer_menu
        );

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                LoggerUtils.log2D(TAG, "onDrawerOpened");
                didChangeEventFilter1 = false;
                didChangeEventFilter2 = false;
                didChangeEventFilter3 = false;
                didChangeEventFilter4 = false;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                LoggerUtils.log2D(TAG, "onDrawerClosed");
                if (didChangeEventFilter1
                        || didChangeEventFilter2
                        || didChangeEventFilter3
                        || didChangeEventFilter4)
                    loadEvent(mCurrentFilterOption);
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
    }

    private void initFirebase() {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        if (BuildConfig.DEBUG) {
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setDeveloperModeEnabled(BuildConfig.DEBUG)
                    .build();
            mFirebaseRemoteConfig.setConfigSettings(configSettings);
        }
        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);
        fetchConfig();
    }

    private void fetchConfig() {
        long cacheExpiration = 3600; // 1 hour in seconds.
        // If in developer mode cacheExpiration is set to 0 so each fetch will retrieve values from
        // the server.
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }

        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
//                            Toast.makeText(MainActivity.this, "Fetch Succeeded",
//                                    Toast.LENGTH_SHORT).show();
                            LoggerUtils.log2D("RemoteConfig", "Fetch Succeeded");

                            // Once the config is successfully fetched it must be activated before newly fetched
                            // values are returned.
                            mFirebaseRemoteConfig.activateFetched();
                        } else {
//                            Toast.makeText(MainActivity.this, "Fetch Failed",
//                                    Toast.LENGTH_SHORT).show();
                            LoggerUtils.log2D("RemoteConfig", "Fetch Failed");
                            FirebaseCrash.logcat(Log.ERROR, TAG, "Fetch Remote Config Failed");
                            FirebaseCrash.report(new Exception("Fetch Remote Config Failed"));
                        }
                    }
                });
    }

    private void initInstances() {
        mAcceptableAccuracy = (float) mFirebaseRemoteConfig.getDouble(Helper.ACCEPTABLE_ACCURACY_CONFIG_KEY);

        btnProfile = (Button) findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(this);

        btnAbout = (Button) findViewById(R.id.btnAbout);
        btnAbout.setOnClickListener(this);

        swAccident = (Switch) findViewById(R.id.swAccident);
        swNaturalDisaster = (Switch) findViewById(R.id.swNaturalDisaster);
        swOther = (Switch) findViewById(R.id.swOther);
        swTrafficJam = (Switch) findViewById(R.id.swTrafficJam);
        swAccident.setChecked(DataStoreUtils.getInstance().isAccidentSwitchOn());
        swNaturalDisaster.setChecked(DataStoreUtils.getInstance().isNaturalDisasterSwitchOn());
        swOther.setChecked(DataStoreUtils.getInstance().isOtherSwitchOn());
        swTrafficJam.setChecked(DataStoreUtils.getInstance().isTrafficJamSwitchOn());
        swAccident.setOnCheckedChangeListener(this);
        swNaturalDisaster.setOnCheckedChangeListener(this);
        swOther.setOnCheckedChangeListener(this);
        swTrafficJam.setOnCheckedChangeListener(this);

        ivProfile = (ImageView) findViewById(R.id.ivProfile);
        ivProfileBackground = (ImageView) findViewById(R.id.ivProfileBackground);
        tvUsername = (TextView) findViewById(R.id.tvUsername);

        mProgressDialog = new MaterialDialog.Builder(this)
                .title(R.string.progressing)
                .autoDismiss(false)
                .progress(true, 0)
                .build();
        mGpsDialog = new MaterialDialog.Builder(this)
                .title(R.string.wait_gps)
                .autoDismiss(false)
                .progress(true, 0)
                .build();

        fabAddEvent = (FloatingActionButton) findViewById(R.id.fabAddEvent);
        fabAddEvent.setOnClickListener(this);
    }

    private void initGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    private void initTab() {
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
//        tabLayout.getTabAt(0).setIcon(mSectionsPagerAdapter.imageResId[0]);
//        tabLayout.getTabAt(1).setIcon(mSectionsPagerAdapter.imageResId[1]);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.actionFilters) {
            showFilterDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        actionBarDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        actionBarDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Helper.LOGIN_RESUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Snackbar.make(fabAddEvent, R.string.login_ok, Snackbar.LENGTH_SHORT)
                        .show();
                loadProfileData();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    Snackbar.make(fabAddEvent, R.string.login_cancel, Snackbar.LENGTH_SHORT)
                            .show();
                    MaterialDialog dialog = new MaterialDialog.Builder(this)
                            .title(R.string.need_login)
                            .content(R.string.need_login_description)
                            .positiveText(R.string.ok)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    gotoLoginActivity();
                                }
                            })
                            .show();
                } else {
                    loadProfileData();
                }
            }
        } else if (requestCode == Helper.POST_EVENT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                LoggerUtils.log2D(TAG, "POST_MESSAGE_REQUEST - OK");
                Event event = data.getParcelableExtra(Helper.MODEL_EVENT_KEY);
                Snackbar.make(fabAddEvent, "Event posted", Snackbar.LENGTH_SHORT)
                        .show();
//                loadEvent(mCurrentFilterOption);
                // TODO: focus on recent post
                didPostEvent = true;
                recentPostedEvent = event;
                loadEvent(2);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                LoggerUtils.log2D(TAG, "POST_MESSAGE_REQUEST - CANCELED");
                Snackbar.make(fabAddEvent, "CANCELED", Snackbar.LENGTH_SHORT)
                        .show();
                didPostEvent = false;
            }
        } else if (requestCode == Helper.EVENT_COMMENT_REQUEST_CODE) {
            if (resultCode == Helper.RESULT_DID_COMMENT) {
                LoggerUtils.log2D(TAG, "EVENT_COMMENT_REQUEST_CODE - RESULT_DID_COMMENT");
                loadEventWithRecentOption();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View view) {
        if (view == btnProfile) {
            gotoLoginActivity();
        } else if (view == btnAbout) {
            String url = mFirebaseRemoteConfig.getString(Helper.ABOUT_URL_CONFIG_KEY);
            ChromeCustomTabUtil.open(this, url);
        } else if (view == fabAddEvent) {
            openPostEventActivity();
        }
    }

    protected void gotoLoginActivity() {
        Intent i = new Intent(this, LoginActivity.class);
        startActivityForResult(i, Helper.LOGIN_RESUEST_CODE);
    }

    private void openPostEventActivity() {
        if (!isAcceptableAccuracy || mCurrentLocation == null) {
            Snackbar.make(fabAddEvent, R.string.wait_gps, Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }
        Intent i = new Intent(this, PostEventActivity.class);
//        LoggerUtils.log2D("ProfileLogedinFragment", "openPostMessageActivity");
        i.putExtra(Helper.LATITUDE_KEY, mCurrentLocation.getLatitude());
        i.putExtra(Helper.LONGITUDE_KEY, mCurrentLocation.getLongitude());
        startActivityForResult(i, Helper.POST_EVENT_REQUEST_CODE);
    }

    private void loadProfileData() {
        setProfile();
    }

    private void setProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            LoggerUtils.log2D("profile", "cannot get firebaseUser");
            gotoLoginActivity();
            return;
        }
        Glide.with(this)
                .load(firebaseUser.getPhotoUrl())
                .bitmapTransform(new CropCircleTransformation(Contextor.getInstance().getContext()))
                .into(ivProfile);

        Glide.with(this)
                .load(firebaseUser.getPhotoUrl())
                .bitmapTransform(new BlurTransformation(Contextor.getInstance().getContext(), 10),
                        new CenterCrop(Contextor.getInstance().getContext()))
                .into(ivProfileBackground);

        tvUsername.setText(firebaseUser.getDisplayName());
    }

    public void showProgressDialog() {
        mProgressDialog.show();
    }

    public void hideProgressDialog() {
        mProgressDialog.dismiss();
        EventListFragment eventListFragment = (EventListFragment) mSectionsPagerAdapter.getItem(1);
        eventListFragment.stopLayoutRefresh();
    }

    public void showGpsDialog() {
        mGpsDialog.show();
    }

    public void hideGpsDialog() {
        mGpsDialog.dismiss();
    }

    private void showFilterDialog() {
        String currentFilter = getResources()
                .getStringArray(R.array.scope_new)[mCurrentFilterOption];
        new MaterialDialog.Builder(this)
                .title(getString(R.string.filter_events, currentFilter))
                .items(R.array.scope_new)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        LoggerUtils.log2D(TAG, "select: " + which + ", " + text);
                        if (mCurrentFilterOption != which) {
                            mCurrentFilterOption = which;
                            loadEvent(mCurrentFilterOption);
                            isFirstTimeLoadEvent = true;
                        }

                        Bundle bundle = new Bundle();
                        bundle.putString(Helper.FILTER_OPTION, String.valueOf(text));
                        mFirebaseAnalytics.logEvent(Helper.SELECT_FILTER_OPTION_EVENT, bundle);
                    }
                })
                .show();
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                ) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this
        );
        showGpsDialog();
        LoggerUtils.log2D("startLocationUpdates", "started");
    }

    void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        LoggerUtils.log2D("GoogleApiClient", "onConnectionFailed");
        FirebaseCrash.logcat(Log.ERROR, TAG, "onGoogleServiceConnectionFailed");
        FirebaseCrash.report(new Exception(connectionResult.getErrorMessage()));
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LoggerUtils.log2D("GoogleApiClient", "onConnected");

        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        LoggerUtils.log2D("GoogleApiClient", "onConnectionSuspended");
    }

    @Override
    public void onLocationChanged(Location location) {
//        LoggerUtils.log2I("onLocationChanged", location.toString());
        mCurrentLocation = location;

        if (!wasFirstTimeGetLocation) {
            mFirstTimeLocation = location;
            wasFirstTimeGetLocation = true;
        }

        if (location.hasAccuracy()
                && location.getAccuracy() < mAcceptableAccuracy && location.getAccuracy() != 0.0) {
            isAcceptableAccuracy = true;
            hideGpsDialog();
        }

        if (isAcceptableAccuracy && !wasFirstLocationFig) {
            wasFirstLocationFig = true;
            isFirstTimeGetAcceptableAccuracy = true;

            if (!isFirstTimeLoadEvent) {
                loadEvent(0);
                isFirstTimeLoadEvent = true;
            }

            long timeBetweenGetFirstLocation = mCurrentLocation.getTime() - mFirstTimeLocation.getTime();
            Bundle bundle = new Bundle();
            bundle.putString(Helper.MODEL, Build.MODEL);
            bundle.putString(Helper.BRAND, Build.BRAND);
            bundle.putString(Helper.OS_VERSION, Build.VERSION.CODENAME);
            bundle.putString(Helper.MANUFACTURER, Build.MANUFACTURER);
            bundle.putDouble(
                    Helper.ACCEPTABLE_ACCURACY,
                    mFirebaseRemoteConfig.getDouble(Helper.ACCEPTABLE_ACCURACY_CONFIG_KEY));
            bundle.putLong(Helper.DURATION, timeBetweenGetFirstLocation);
            mFirebaseAnalytics.logEvent(Helper.DURATION_ACCEPTABLE_ACCURACY_EVENT, bundle);
        }

        MapFragment fragment = (MapFragment) mSectionsPagerAdapter.getItem(0);
        if (fragment.isMapReady) {
            fragment.onMyLocationChange(location);
        }
    }

    public void loadEventWithRecentOption() {
        loadEvent(mCurrentFilterOption);
    }

    public void loadEvent(int option) {
        showProgressDialog();
        switch (option) {
            case 0:
                loadEventsNearBy(option);
                break;
            case 1:
                loadEventsNearBy(option);
                break;
            case 2:
                loadEventsLast2Days(option);
                break;
            default:
                loadEventsByProvince(option - 2);
        }
    }

    private void loadEventsLast2Days(int option) {
        Call<Event[]> call = ApiManager.getInstance().getAPIService()
                .loadEventsLast2Days(
                        String.valueOf(option),
                        EventUtil.makeEventFilterString());
        call.enqueue(new Callback<Event[]>() {
            @Override
            public void onResponse(Call<Event[]> call, Response<Event[]> response) {
                hideProgressDialog();
                Event[] events = response.body();
                loadDataToView(events);
            }

            @Override
            public void onFailure(Call<Event[]> call, Throwable t) {
                hideProgressDialog();
                FirebaseCrash.logcat(Log.ERROR, TAG, "loadEventsLast2Days");
                FirebaseCrash.report(t);
            }
        });
    }

    private void loadEventsNearBy(final int option) {
        final double lat = mCurrentLocation.getLatitude();
        final double lng = mCurrentLocation.getLongitude();
        Call<Event[]> call = ApiManager.getInstance().getAPIService()
                .loadEvents(
                        String.valueOf(option),
                        String.valueOf(lat),
                        String.valueOf(lng),
                        EventUtil.makeEventFilterString()
                );
        call.enqueue(new Callback<Event[]>() {
            @Override
            public void onResponse(Call<Event[]> call, Response<Event[]> response) {
                hideProgressDialog();
                Event[] events = response.body();
                loadDataToView(events);
                MapFragment mapFragment = (MapFragment) mSectionsPagerAdapter.getItem(0);
                LatLng latLng = new LatLng(lat, lng);
                switch (option) {
                    case 0:
                        mapFragment.moveCameraToMyLocation(latLng, 10);
                        mapFragment.drawCircle(latLng, 20000);
                        break;
                    case 1:
                        mapFragment.moveCameraToMyLocation(latLng, 8);
                        mapFragment.drawCircle(latLng, 50000);
                        break;
                }
            }

            @Override
            public void onFailure(Call<Event[]> call, Throwable t) {
                hideProgressDialog();
                FirebaseCrash.logcat(Log.ERROR, TAG, "loadEventsNearBy");
                FirebaseCrash.report(t);
            }
        });
    }


    private void loadEventsByProvince(int provinceId) {
        LoggerUtils.log2D(TAG, "provinceId: " + provinceId);
        Call<Event[]> call = ApiManager.getInstance().getAPIService()
                .loadEventsByProvinces(
                        "3",
                        String.valueOf(provinceId),
                        EventUtil.makeEventFilterString());
        call.enqueue(new Callback<Event[]>() {
            @Override
            public void onResponse(Call<Event[]> call, Response<Event[]> response) {
                hideProgressDialog();
                Event[] events = response.body();
                loadDataToView(events);
            }

            @Override
            public void onFailure(Call<Event[]> call, Throwable t) {
                hideProgressDialog();
                FirebaseCrash.logcat(Log.ERROR, TAG, "loadEventsByProvince");
                FirebaseCrash.report(t);
            }
        });

        Call<Province> call1 = ApiManager.getInstance().getAPIService()
                .loadProvince(provinceId);
        call1.enqueue(new Callback<Province>() {
            @Override
            public void onResponse(Call<Province> call, Response<Province> response) {
                Province province = response.body();
                MapFragment mapFragment = (MapFragment) mSectionsPagerAdapter.getItem(0);
                mapFragment.moveCameraToProvince(province);
            }

            @Override
            public void onFailure(Call<Province> call, Throwable t) {
                FirebaseCrash.report(t);
            }
        });
    }

    private void loadDataToView(Event[] events) {
        EventListFragment eventListFragment = (EventListFragment) mSectionsPagerAdapter.getItem(1);
        eventListFragment.loadDataToRecyclerView(events);

        MapFragment mapFragment = (MapFragment) mSectionsPagerAdapter.getItem(0);
        mapFragment.createMarker(events);

        if (didPostEvent) {
            mViewPager.setCurrentItem(0, true);
            mapFragment.focusOnMarker(recentPostedEvent);
        }
    }

    private void deleteEvent(Event event) {
        showProgressDialog();
        Call<Response<Void>> call = ApiManager.getInstance().getAPIService()
                .deleteEvent(event.eventUid);
        call.enqueue(new Callback<Response<Void>>() {
            @Override
            public void onResponse(Call<Response<Void>> call, Response<Response<Void>> response) {
                hideProgressDialog();
                if (response.code() == 204) {
                    Snackbar.make(fabAddEvent, "Event deleted", Snackbar.LENGTH_SHORT)
                            .show();
                    loadEvent(mCurrentFilterOption);
                }
            }

            @Override
            public void onFailure(Call<Response<Void>> call, Throwable t) {
                hideProgressDialog();
                FirebaseCrash.logcat(Log.ERROR, TAG, "deleteEvent");
                FirebaseCrash.report(t);
            }
        });
    }

    public void showEventCommentActivity(Event event) {
        Intent i = new Intent(this, EventCommentActivity.class);
        i.putExtra(Helper.KEY_EVENT_ITEM, event);
        startActivityForResult(i, Helper.EVENT_COMMENT_REQUEST_CODE);
    }

    @Override
    public void onEventItemClickCallback(Event event, int position) {
        LoggerUtils.log2D(TAG, "onEventItemClickCallback: " + event.eventUid);
        mViewPager.setCurrentItem(0, true);
        MapFragment mapFragment = (MapFragment) mSectionsPagerAdapter.getItem(0);
        mapFragment.focusOnMarker(position);
    }

    @Override
    public void onEventItemLongClickCallback(final Event event, int position) {
        LoggerUtils.log2D(TAG, "onEventItemLongClickCallback: " + event.eventUid);
        new MaterialDialog.Builder(this)
                .title(R.string.delete)
                .content(R.string.delete_confirm)
                .positiveText(R.string.delete)
                .negativeText(R.string.cancel)
                .neutralText(R.string.later)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        deleteEvent(event);
                    }
                })
                .show();
    }

    @Override
    public void onEventItemCommentClickCallback(Event event, int position) {
        LoggerUtils.log2D(TAG, "onEventItemCommentClickCallback: " + event.eventUid);
        showEventCommentActivity(event);
    }

    @Override
    public void onEventItemPhotoClickCallback(final Event event, int position) {
        LoggerUtils.log2D(TAG, "onEventItemPhotoClickCallback: " + event.eventUid);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (compoundButton == swAccident) {
            DataStoreUtils.getInstance().setAccidentSwitch(b);
            didChangeEventFilter1 = !didChangeEventFilter1;
        } else if (compoundButton == swNaturalDisaster) {
            DataStoreUtils.getInstance().setNaturalDisasterSwitch(b);
            didChangeEventFilter2 = !didChangeEventFilter2;
        } else if (compoundButton == swOther) {
            DataStoreUtils.getInstance().setOtherSwitch(b);
            didChangeEventFilter3 = !didChangeEventFilter3;
        } else if (compoundButton == swTrafficJam) {
            DataStoreUtils.getInstance().setTrafficJamSwitch(b);
            didChangeEventFilter4 = !didChangeEventFilter4;
        }
    }

    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
            return rootView;
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        int[] imageResId = {
                R.drawable.ic_map_white_24dp,
                R.drawable.ic_view_list_white_24dp
        };

        String[] tabTitles = {"MAP", "LIST"};

        MapFragment mapFragment;
        EventListFragment eventListFragment;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            switch (position) {
                case 0:
                    if (mapFragment == null) {
                        mapFragment = MapFragment.newInstance();
                    }
                    return mapFragment;
                case 1:
                    if (eventListFragment == null) {
                        eventListFragment = EventListFragment.newInstance();
                    }
                    return eventListFragment;
                default:
                    return PlaceholderFragment.newInstance(position + 1);
            }
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
//            Drawable image = getResources().getDrawable(imageResId[position], null);
//            image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
//            SpannableString sb = new SpannableString("");
//            ImageSpan imageSpan = new ImageSpan(image, ImageSpan.ALIGN_BOTTOM);
//            sb.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            return sb;
        }
    }
}