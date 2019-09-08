package mchehab.com.behavioranalysis;


import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class FragmentBehavior extends Fragment implements SocketActivityListener {

    private final int FEATURE_COUNT = 9;

    private TFModel tfModel;

    private ListView listView;
    private ArrayAdapter<String> arrayAdapter;

    private HashMap<Integer, String> behaviorHashMap = new HashMap<>();
    private HashMap<Integer, Integer> placesHashMap = new HashMap<>();
    private HashMap<String, Integer> streetNameHashMap = new HashMap<>();

    private SocketConnection socketConnection;
    private StringBuilder stringBuilderData = new StringBuilder();
    private int activity = 3;//default value

    private List<String> listData = new ArrayList<>();

    private Handler handler = new Handler();

    private final int DELAY = 2000; //delay in ms
    private StringBuilder stringBuilder = new StringBuilder();

    private BehaviorListener behaviorListener;

    private Spinner spinnerHour;
    private Spinner spinnerMinutes;
    private Spinner spinnerStreet;
    private Spinner spinnerActivity;
    private CheckBox checkBoxIsWeekend;
    private CheckBox checkBoxTestData;

    public FragmentBehavior() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        tfModel = new TFModel(getActivity());
        View view = inflater.inflate(R.layout.fragment_bheavior, container, false);
        initUI(view);
        initHashMap();
        initPlacesHashMap();
        initStreetHashMap();
//        convertLocationToStreetName();
//        socketConnection = new SocketConnection(this);
//        socketConnection.readFromSocket();
        getActivitySnapshot();
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (getActivity() instanceof BehaviorListener){
            behaviorListener = (BehaviorListener)getActivity();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        behaviorListener = null;
    }

    private void initUI(View view) {
        spinnerHour = view.findViewById(R.id.spinnerTime);
        spinnerMinutes = view.findViewById(R.id.spinnerMinutes);
        spinnerStreet = view.findViewById(R.id.spinnerStreet);
        spinnerActivity = view.findViewById(R.id.spinnerActivity);
        checkBoxIsWeekend = view.findViewById(R.id.checkBoxIsWeekend);
        checkBoxTestData = view.findViewById(R.id.checkBoxTestData);

        listView = view.findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1);
        listView.setAdapter(arrayAdapter);
    }

    private void initPlacesHashMap() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader
                    (getActivity().getAssets().open
                            ("place_map.txt")));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] map = line.split(":");
                placesHashMap.put(Integer.parseInt(map[1]), Integer.parseInt(map[0]));
            }
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initHashMap() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader
                    (getActivity().getAssets()
                            .open("labels.txt")));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] items = line.split(" ");
                behaviorHashMap.put(Integer.parseInt(items[0]), items[1]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initStreetHashMap() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader
                    (getActivity().getAssets().open("street_names.txt")));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] items = line.split(" ");
                streetNameHashMap.put(items[1], Integer.parseInt(items[0]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void feedSyntheticData() {
        int activity = spinnerActivity.getSelectedItemPosition();
        int hours = spinnerHour.getSelectedItemPosition();
        int minutes = spinnerMinutes.getSelectedItemPosition();
        int street = spinnerStreet.getSelectedItemPosition();
        float time = hours + minutes / 60.0f;

        stringBuilderData.append(activity).append(" ")
                .append(street).append(" ");
        getAudioSnapshot();
        getKeyguardSnapshot();
        stringBuilderData.append(time).append(" ");
        if (checkBoxIsWeekend.isChecked())
            stringBuilderData.append(1);
        else
            stringBuilderData.append(0);
        inferBehavior(stringBuilderData.toString());
        stringBuilderData.setLength(0);
    }

    private void getActivitySnapshot() {
        if (checkBoxTestData.isChecked()) {
            feedSyntheticData();
            return;
        }
        if (getActivity() == null)
            return;
        Awareness.getSnapshotClient(getActivity()).getDetectedActivity()
                .addOnSuccessListener(e -> {
                    ActivityRecognitionResult activityRecognitionResult = e.getActivityRecognitionResult();
                    DetectedActivity probableActivity = activityRecognitionResult.getMostProbableActivity();
                    int activityType = probableActivity.getType();
//                    invalid results!
                    if (activityType != 4 && activityType != 6) {
                        activity = activityType;
                    }
                    stringBuilderData.append(activity)
                            .append(" ");
                })
                .addOnFailureListener(e -> stringBuilderData
                        .append(-1)
                        .append(" "))
                .addOnCompleteListener(task -> getLocationSnapshot());
    }

    private void getLocationSnapshot() {
        if (getActivity() == null)
            return;
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            Awareness.getSnapshotClient(getActivity()).getLocation()
                    .addOnSuccessListener(e -> {
                        Location location = e.getLocation();
                        String streetName = getStreetName(location.getLatitude(), location
                                .getLongitude());
                        int value = -1;
                        if (streetNameHashMap.containsKey(streetName)) {
                            value = streetNameHashMap.get(streetName);
                        }
                        stringBuilderData
                                .append(value)
                                .append(" ");
                    })
                    .addOnFailureListener(e -> stringBuilderData
                            .append(-1)
                            .append(" "))
                    .addOnCompleteListener(e -> {
                        getAudioSnapshot();
                        getKeyguardSnapshot();
//                        getPlacesSnapshot();
                        getTimeSnapshot();
                    });
        } else {
            stringBuilderData
                    .append(-1)
                    .append(" ")
                    .append(-1)
                    .append(" ");
            getAudioSnapshot();
            getKeyguardSnapshot();
//            getPlacesSnapshot();
            getTimeSnapshot();
        }
    }

    private String getStreetName(double lat, double lng) {
        String streetName;
        Geocoder geocoder = new Geocoder(getActivity(), Locale.getDefault());
        try {
            List<Address> listAddress = geocoder.getFromLocation(lat, lng, 1);
            streetName = listAddress.get(0).getFeatureName();
            if (streetName == null)
                return "NULL";
            return streetName;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "NULL";
    }

    private void getKeyguardSnapshot() {
        KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context
                .KEYGUARD_SERVICE);
        if (keyguardManager != null) {
            boolean isPhoneLocked = keyguardManager.isKeyguardLocked();
            boolean isLockEnabled = keyguardManager.isKeyguardSecure();

            if (isPhoneLocked) stringBuilderData.append(1);
            else stringBuilderData.append(0);

            stringBuilderData.append(" ");

            if (isLockEnabled) stringBuilderData.append(1);
            else stringBuilderData.append(0);

            stringBuilderData.append(" ");
        } else {
            stringBuilderData
                    .append(-1)
                    .append(" ")
                    .append(-1)
                    .append(" ");
        }
    }

    private void getAudioSnapshot() {
        AudioManager audioManager = (AudioManager) getActivity().getSystemService(Context
                .AUDIO_SERVICE);
        if (audioManager == null) {
            stringBuilderData
                    .append(-1)// music
                    .append(" ")
                    .append(-1)// mode
                    .append(" ")
                    .append(-1)// ringer mode
                    .append(" ");
            return;
        }
//        is music playing
        if (audioManager.isMusicActive()) {
            stringBuilderData.append(1);
        } else {
            stringBuilderData.append(0);
        }
        stringBuilderData.append(" ");
//        audio mode
        stringBuilderData.append(audioManager.getMode()).append(" ");
//        ringer mode
        stringBuilderData.append(audioManager.getRingerMode()).append(" ");
    }

    private void inferBehavior(String data) {
        String[] features = data.split(" ");
        float[] feature_values = new float[features.length];
        if (feature_values.length != FEATURE_COUNT)
            return;
        for (int i = 0; i < feature_values.length; i++) {
            feature_values[i] = Float.parseFloat(features[i]);
        }
        // probability for each category
        float[] results = tfModel.predict(feature_values);
        float max = Float.MIN_VALUE;
        int index = 0;
        for (int i = 0; i < results.length; i++) {
            if (results[i] > max) {
                index = i;
                max = results[i];
            }
        }
//        socketConnection.writeToSocket(behaviorHashMap.get(index));
        listData.add(data + " label =" + behaviorHashMap.get(index));
        arrayAdapter.add(behaviorHashMap.get(index) + ", " + String.format("%.2f",
                results[index]) + " confidence" + ", activity = " + activity);
        arrayAdapter.notifyDataSetChanged();
//        socketConnection.readFromSocket();
        handler.postDelayed(this::getActivitySnapshot, DELAY);

    }

    @Override
    public void getMessageFromSocket(String message) {
        if (message.equalsIgnoreCase("behavior")) {
            getActivitySnapshot();
            socketConnection.readFromSocket();
        }
    }

    private void getTimeSnapshot() {
        Date date = new Date();
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(date);
//        time features
        float hours;
        int is_weekend = 0;

        int day = calendar.get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
            is_weekend = 1;
        }
        hours = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h format
        hours += calendar.get(Calendar.MINUTE) / 60.0f;

        stringBuilderData.append(hours).append(" ").append(is_weekend);
        inferBehavior(stringBuilderData.toString());
//        inferBehaviorPerformance(stringBuilderData.toString(), 1000);
//        reset string after inferring behavior
        stringBuilderData.setLength(0);
    }
}