package com.acms.iexplore;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class HomeFragment extends Fragment {

    private TextView latitudeView;
    private TextView longitudeView;
    private TextView altitudeView;
    private TextView buildingIdView;
    private WebView webView;

    private final String WEBVIEW_URL = "http://192.168.0.104:4200";

    public HomeFragment() {
        // Required empty public constructor
    }

    private BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            updateUI(intent.getDoubleExtra("latitude", 0),
                    intent.getDoubleExtra("longitude", 0),
                    intent.getDoubleExtra("altitude", 0),
                    intent.getStringExtra("buildingId"));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register a local broadcast manager to receive location updates
        // Receive intents with actions named "newLocationUpdate"
        LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                .registerReceiver(locationUpdateReceiver, new IntentFilter("newLocationUpdate"));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        latitudeView = root.findViewById(R.id.latitude);
        longitudeView = root.findViewById(R.id.longitude);
        altitudeView = root.findViewById(R.id.altitude);
        buildingIdView = root.findViewById(R.id.buildingId);

        webView = root.findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.loadUrl(WEBVIEW_URL);

        return root;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                .unregisterReceiver(locationUpdateReceiver);
        super.onDestroy();
    }

    private void updateUI(double latitude, double longitude, double altitude, String buildingId) {
        latitudeView.setText("Latitude : " + latitude);
        longitudeView.setText("Longitude : " + longitude);
        altitudeView.setText("Altitude : " + altitude);
        buildingIdView.setText("Building ID : " + buildingId);
    }
}