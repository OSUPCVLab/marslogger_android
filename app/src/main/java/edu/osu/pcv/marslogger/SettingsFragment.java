package edu.osu.pcv.marslogger;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import android.os.Environment;
import android.util.Range;
import android.util.Size;

import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ros.android.IPTool;
//import org.ros.android.MasterChooser;
import org.ros.android.RosURIPattern;
import org.ros.exception.RosRuntimeException;
import org.ros.node.NodeConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Activities that contain this fragment must implement the
 * {@link SettingsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link SettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private OnFragmentInteractionListener mListener;
    private Range<Integer> isoRange = null;
    private Range<Float> exposureTimeRangeMs = null;

    public SettingsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment SettingsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static SettingsFragment newInstance(String param1, String param2) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        PreferenceManager.getDefaultSharedPreferences(
                getActivity()).registerOnSharedPreferenceChangeListener(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());

        ListPreference cameraList = (ListPreference)
                getPreferenceManager().findPreference("prefCamera");
        ListPreference cameraRez = (ListPreference)
                getPreferenceManager().findPreference("prefSizeRaw");
        EditTextPreference prefCameraFocus = (EditTextPreference)
                getPreferenceManager().findPreference("prefFocusDistance");

        EditTextPreference prefISO = (EditTextPreference)
                getPreferenceScreen().findPreference("prefISO");
        EditTextPreference prefExposureTime = (EditTextPreference)
                getPreferenceScreen().findPreference("prefExposureTime");

        prefISO.setOnPreferenceChangeListener(checkISOListener);
        try {
            Activity activity = getActivity();
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            int cameraSize = manager.getCameraIdList().length;
            CharSequence[] entries = new CharSequence[cameraSize];
            CharSequence[] entriesValues = new CharSequence[cameraSize];
            String physicalBackCamId = "";
            for (int i = 0; i < cameraSize; i++) {
                String cameraId = manager.getCameraIdList()[i];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                try {
                    Set<String> physicalCameraIds = CameraUtils.getPhysicalCameraIds(characteristics);
                    String prefix = " - Physical";
                    String suffix = "";
                    if (!physicalCameraIds.isEmpty()) {
                        prefix = " - Logical";
                        suffix = " " + physicalCameraIds.toString();
                    }
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraMetadata.LENS_FACING_BACK) {
                        entries[i] = cameraId + prefix + " Lens Facing Back" + suffix;
                        physicalBackCamId = cameraId;
                    } else if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraMetadata.LENS_FACING_FRONT) {
                        entries[i] = cameraId + prefix + " Lens Facing Front" + suffix;
                    } else {
                        entries[i] = cameraId + prefix + " Lens External" + suffix;
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    entries[i] = cameraId + " - Lens Facing Unknown";
                }
                entriesValues[i] = cameraId;
            }

            // Update our settings entry
            cameraList.setEntries(entries);
            cameraList.setEntryValues(entriesValues);
            if (physicalBackCamId.length() == 0)
                physicalBackCamId = entriesValues[0].toString();
            cameraList.setDefaultValue(physicalBackCamId);
            // Do not call "cameraList.setValueIndex(0)" which will invoke onSharedPreferenceChanged
            // if the previous camera is not 0, and cause null pointer exception.

            // Right now we have selected the first camera, so lets populate the resolution list
            // We should just use the default if there is not a shared setting yet
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(
                    sharedPreferences.getString("prefCamera", entriesValues[0].toString()));
            StreamConfigurationMap streamConfigurationMap = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

            int rezSize = sizes.length;
            CharSequence[] rez = new CharSequence[rezSize];
            CharSequence[] rezValues = new CharSequence[rezSize];
            int defaultIndex = 0;
            for (int i = 0; i < sizes.length; i++) {
                rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                if (sizes[i].getWidth() + sizes[i].getHeight() ==
                        DesiredCameraSetting.mDesiredFrameWidth +
                                DesiredCameraSetting.mDesiredFrameHeight) {
                    defaultIndex = i;
                }
            }

            cameraRez.setEntries(rez);
            cameraRez.setEntryValues(rezValues);
            cameraRez.setValueIndex(defaultIndex);

            isoRange = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (isoRange != null) {
                String rangeStr = "[" + isoRange.getLower() + "," + isoRange.getUpper() + "] (1)";
                prefISO.setDialogTitle("Adjust ISO in range " + rangeStr);
            }

            Range<Long> exposureTimeRangeNs = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (exposureTimeRangeNs != null) {
                exposureTimeRangeMs = new Range<Float>(
                        new Float(exposureTimeRangeNs.getLower().floatValue() / 1e6),
                        new Float(exposureTimeRangeNs.getUpper().floatValue() / 1e6));
                String rangeStr = "[" + exposureTimeRangeMs.getLower() + "," +
                        exposureTimeRangeMs.getUpper() + "] (ms)";
                prefExposureTime.setDialogTitle("Adjust exposure time in range " + rangeStr);
            }

            // Get the possible focus lengths, on non-optical devices this only has one value
            // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            float[] focus_lengths = characteristics.get(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focus_lengths != null && focus_lengths.length > 0) {
                StringBuilder fociiStrBuilder = new StringBuilder();
                for (int i = 0; i < focus_lengths.length; i++) {
                    if (i > 0) {
                        fociiStrBuilder.append(", ");
                    }
                    fociiStrBuilder.append(focus_lengths[i]);
                }
                String fociiStr = fociiStrBuilder.toString();
                prefCameraFocus.setDialogTitle("Adjust focus distances (mm): " + fociiStr);
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
        ListPreference networkInterfaces = (ListPreference)getPreferenceManager().findPreference("prefNetworkInterface");
        try {
            final List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            int numInterfaces = interfaces.size();
            CharSequence[] entries = new CharSequence[numInterfaces];
            CharSequence[] entriesValues = new CharSequence[numInterfaces];
            int i = 0;
            int d = 0;
            for (NetworkInterface networkInterface : interfaces) {
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    String ifname = networkInterface.getName();
                    entries[i] = ifname;
                    entriesValues[i] = ifname;
                    if (ifname.startsWith("eth"))
                        d = i;
                    ++i;
                }
            }
            networkInterfaces.setEntries(Arrays.copyOfRange(entries, 0, i));
            networkInterfaces.setEntryValues(Arrays.copyOfRange(entriesValues, 0, i));
            networkInterfaces.setValueIndex(d);
        } catch (SocketException e) {
            throw new RosRuntimeException(e);
        }
        String prevMasterURI = sharedPreferences.getString("prefMasterURI", "");
        if (prevMasterURI.length() == 0)
            sharedPreferences.edit().putString("prefMasterURI", NodeConfiguration.DEFAULT_MASTER_URI.toString()).apply();

        String currentHostIp = IPTool.getHostEthernetIp();
        String ipnote = "";
        String extdir = getActivity().getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        File configFile = new File(extdir, "MID360_config.json");
        String lidarid = "";
        if (configFile.exists()) {
            try {
                InputStream is = new FileInputStream(configFile);
                String jsonTxt = IOUtils.toString(is);
                JSONObject json = new JSONObject(jsonTxt);
                JSONObject mid360 = json.getJSONObject("MID360");
                JSONObject hostnet = mid360.getJSONObject("host_net_info");
                String hostip = hostnet.getString("point_data_ip");
                JSONArray lidarconfigs = json.getJSONArray("lidar_configs");
                JSONObject lidarconfig = lidarconfigs.getJSONObject(0);
                String lidarip = lidarconfig.getString("ip");
//        ipnote += ". Previous host IP: " + hostip + ", lidar 0 IP: " + lidarip;
                if (!hostip.equals(currentHostIp)) {
                    ipnote += "Warning: The host address and lidar address are on different network segments, " +
                            "the host_ip: " + currentHostIp + ", previous lidar_ip: " + lidarip;
                    ipnote += "\nTo fix this, open livox viewer2 in a laptop, connect to mid360 by " +
                            "setting the laptop ethernet static IP to: " + hostip;

                    String[] hostparts = currentHostIp.split("[.]");
                    String subnet = hostparts[2];
                    String[] lidarparts = lidarip.split("[.]");
                    lidarid = lidarparts[3];
                    ipnote += "\nThen in livox viewer2 settings, set the lidar IP to 192.168." + subnet + "." + lidarid;
                    ipnote += "\nAlso, set the points IP, IMU IP, and lidar info IP to " + currentHostIp;
                    ipnote += "\nDo not start recording in case of inconsistent IPs as recording will override the previous config.";
                } else {
                    ipnote = "The host IP and lidar IP look consistent, host_ip: " + currentHostIp + " lidar_ip: " + lidarip;
                    ipnote += ". But this may be wrong if recording had been attempted in case of inconsistent IPs.";
                }
            } catch (Exception e) {
                ipnote += "Exception in loading previous IP from " + configFile.getAbsolutePath();
                e.printStackTrace();
            }
        } else {
            ipnote += "No previous IP record found.";
        }
        sharedPreferences.edit().putString("prefIPNote", ipnote).apply();
        if (lidarid.length() > 0)
            sharedPreferences.edit().putString("prefLidarId", lidarid).apply();

    }

    /**
     * Checks that a preference is a valid numerical value
     */
    Preference.OnPreferenceChangeListener checkISOListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            //Check that the string is an integer.
            return checkIso(newValue);
        }
    };

    private boolean checkIso(Object newValue) {
        if (!newValue.toString().equals("") && newValue.toString().matches("\\d*")) {
            return true;
        } else {
            Toast.makeText(getActivity(),
                    newValue + " is not a valid number!", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("prefCamera")) {
            try {
                String cameraId = sharedPreferences.getString("prefCamera", "0");

                Activity activity = getActivity();
                CameraManager manager = (CameraManager)
                        activity.getSystemService(Context.CAMERA_SERVICE);

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

                int rezSize = sizes.length;
                CharSequence[] rez = new CharSequence[rezSize];
                CharSequence[] rezValues = new CharSequence[rezSize];
                int defaultIndex = 0;
                for (int i = 0; i < sizes.length; i++) {
                    rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    if (sizes[i].getWidth() + sizes[i].getHeight() ==
                            DesiredCameraSetting.mDesiredFrameWidth +
                                    DesiredCameraSetting.mDesiredFrameHeight) {
                        defaultIndex = i;
                    }
                }

                ListPreference cameraRez = (ListPreference)
                        getPreferenceManager().findPreference("prefSizeRaw");
                cameraRez.setEntries(rez);
                cameraRez.setEntryValues(rezValues);
                cameraRez.setValueIndex(defaultIndex);

            } catch (CameraAccessException | NullPointerException e) {
                e.printStackTrace();
            }
        } else if (key.equals("prefMasterURI")) {
            final String uri = sharedPreferences.getString("prefMasterURI", "");
            final Pattern uriPattern = RosURIPattern.URI;
            if(!uriPattern.matcher(uri).matches()) {
                sharedPreferences.edit().putString("prefMasterURI", "Please enter valid URI").apply();
            }
        } else if (key.equals("prefNetworkInterface")) {
            final String face = sharedPreferences.getString("prefNetworkInterface", "");
            Timber.d("Using " + face + " interface.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }
}
