package com.acms.iexplore.activities;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.acms.iexplore.customviews.LoadingDialog;
import com.acms.iexplore.geofence.GeofenceHelper;
import com.acms.iexplore.googlesigninoptions.GoogleSignInOptionsInstance;
import com.acms.iexplore.R;
import com.acms.iexplore.retrofit.RetrofitClientInstance;
import com.acms.iexplore.retrofit.RetrofitInterface;
import com.acms.iexplore.models.BuildingModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.SignInButton;
import com.google.android.material.snackbar.Snackbar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";

    private static final int RC_GET_TOKEN = 9002;
    private GoogleSignInClient googleSignInClient;

    private GeofencingClient geofencingClient;
    private GeofenceHelper geofenceHelper;

    private LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        // Build GoogleAPIClient with the Google Sign-In API and google sign in options.
        googleSignInClient = GoogleSignIn.getClient (SignInActivity.this, GoogleSignInOptionsInstance.getGoogleSignInOptionsInstance(this));

        SignInButton googleSignInButton = findViewById(R.id.googleSignInButton);
        TextView signInButtonText = (TextView) googleSignInButton.getChildAt(0);
        signInButtonText.setText("Sign in with Google");
        googleSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getIdToken();
            }
        });

        loadingDialog = new LoadingDialog(SignInActivity.this);

        geofencingClient = LocationServices.getGeofencingClient(this);
        geofenceHelper = new GeofenceHelper(this);

        if (getIntent().getBooleanExtra("signed_out", false)) {
            Snackbar.make(findViewById(R.id.signInLinearLayout), "Signed out successfully! Geofences are removed.", Snackbar.LENGTH_LONG).show();
        }

        //refreshIdToken();
    }

    private void getIdToken() {
        // Show an account picker to let the user choose a Google account from the device.
        // If the GoogleSignInOptions only asks for IDToken and/or profile and/or email then no
        // consent screen will be shown here.
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GET_TOKEN);
    }

    private void refreshIdToken() {
        // Attempt to silently refresh the GoogleSignInAccount. If the GoogleSignInAccount
        // already has a valid token this method may complete immediately.
        //
        // If the user has not previously signed in on this device or the sign-in has expired,
        // this asynchronous branch will attempt to sign in the user silently and get a valid
        // ID token. Cross-device single sign on will occur in this branch.
        googleSignInClient.silentSignIn()
                .addOnCompleteListener(this, new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        goToHomeActivity();
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GET_TOKEN) {
            // This task is always completed immediately, there is no need to attach an asynchronous listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(@NonNull Task<GoogleSignInAccount> completedTask) {
            // Check permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                checkPermissions();
            else
                addGeofencesFromDB();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            checkBackgroundAndFineLocationPermssions();
        else
            checkFineLocationPermission();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkBackgroundAndFineLocationPermssions() {
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        // check if all permissions are granted
                        if (report.areAllPermissionsGranted()) {
                            // Add geofences
                            addGeofencesFromDB();
                        }

                        // check for permanent denial of any permission
                        if (report.isAnyPermissionPermanentlyDenied()) {
                            // permission is denied permenantly, navigate user to app settings
                            showSettingsDialog();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                })
                .onSameThread()
                .check();
    }

    private void checkFineLocationPermission() {
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        // permission is granted
                        // Add geofence
                        addGeofencesFromDB();
                    }
                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {

                        // sign out
                        googleSignInClient.signOut();

                        // check for permanent denial of permission
                        if (response.isPermanentlyDenied()) {
                            // navigate user to app settings
                            showSettingsDialog();
                        }
                    }
                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SignInActivity.this);
        builder.setTitle("Need permissions");
        builder.setMessage("This app requires location permissions. You can grant it from app settings");
        builder.setPositiveButton("Go to settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                openSettings();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void addGeofencesFromDB() {

        Retrofit retrofit = RetrofitClientInstance.getRetrofitInstance();
        RetrofitInterface retrofitInterface = retrofit.create(RetrofitInterface.class);

        loadingDialog.startLoadingDialog();

        Call<List<BuildingModel>> call = retrofitInterface.executeGetAllBuildingsInfo();

        call.enqueue(new Callback<List<BuildingModel>>() {
            @Override
            public void onResponse(Call<List<BuildingModel>> call, Response<List<BuildingModel>> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(SignInActivity.this, "Failed to fetch geofences, failure code: " + response.code(), Toast.LENGTH_SHORT).show();
                    loadingDialog.dismissLoadingDialog();
                    googleSignInClient.signOut();
                    return;
                }

                List <BuildingModel> buildings = response.body();
                List <Geofence> geofencesList = getGeofencesList(buildings);

                GeofencingRequest geofencingRequest = geofenceHelper.getGeofencingRequest(geofencesList);
                PendingIntent pendingIntent = geofenceHelper.getPendingIntent();
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    loadingDialog.dismissLoadingDialog();
                                    goToHomeActivity();
                                }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    String errorMessage = geofenceHelper.getErrorString(e);
                                    Toast.makeText(SignInActivity.this, "Failed to add geofences: " + errorMessage, Toast.LENGTH_SHORT).show();
                                    loadingDialog.dismissLoadingDialog();
                                    googleSignInClient.signOut();
                                }
                        });
            }

            @Override
            public void onFailure(Call<List<BuildingModel>> call, Throwable t) {
                Toast.makeText(SignInActivity.this, "Failed to fetch geofences from db: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                loadingDialog.dismissLoadingDialog();
                googleSignInClient.signOut();
            }
        });
    }

    private List <Geofence> getGeofencesList(List <BuildingModel> buildings) {
        List <Geofence> geofencesList = new ArrayList<Geofence>();
        for (BuildingModel building : buildings) {
            Geofence geofence = geofenceHelper.getGeofence(
                    building.getId(),
                    building.getLatitude(),
                    building.getLongitude(),
                    building.getRadius(),
                    Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT
            );
            geofencesList.add(geofence);
        }
        return geofencesList;
    }

    private void goToHomeActivity() {
        Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
        intent.putExtra("signed_in", true);
        startActivity(intent);
    }
}
