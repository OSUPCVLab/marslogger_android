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
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Size;

/**
 * Activities that contain this fragment must implement the
 * {@link SettingsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link SettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    // The request code must be 0 or greater.
    private static final int PLUS_ONE_REQUEST_CODE = 0;
    // The URL to +1.  Must be a valid URL.
    private final String PLUS_ONE_URL = "http://developer.android.com";
    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

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
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Indicate here the XML resource you created above that holds the preferences
        setPreferencesFromResource(R.xml.settings, rootKey);

        // Make it so that we listen to change events
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

        // Current prefs, if this is called mid usage, we would want to use this to get our active settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // Get our camera id list preference
        ListPreference cameraList = (ListPreference) getPreferenceManager().findPreference("prefCamera");
        ListPreference cameraRez = (ListPreference) getPreferenceManager().findPreference("prefSizeRaw");

        try {
            // Load our camera settings
            Activity activity = getActivity();
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            // Our two values we need to update
            int cameraSize = manager.getCameraIdList().length;
            CharSequence[] entries = new CharSequence[cameraSize];
            CharSequence[] entriesValues = new CharSequence[cameraSize];
            // Loop through our camera list
            for (int i = 0; i < manager.getCameraIdList().length; i++) {
                // Get the camera
                String cameraId = manager.getCameraIdList()[i];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                // Try to find what direction it is pointing
                try {
                    // Check to see if the camera is facing the back, front, or external
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                        entries[i] = cameraId + " - Lens Facing Back";
                    } else if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT) {
                        entries[i] = cameraId + " - Lens Facing Front";
                    } else {
                        entries[i] = cameraId + " - Lens External";
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    entries[i] = cameraId + " - Lens Facing Unknown";
                }
                // Set the value to just the camera id
                entriesValues[i] = cameraId;
            }

            // Update our settings entry
            cameraList.setEntries(entries);
            cameraList.setEntryValues(entriesValues);
            cameraList.setDefaultValue(entriesValues[0]);

            // Right now we have selected the first camera, so lets populate the resolution list
            // We should just use the default if there is not a shared setting yet
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(sharedPreferences.getString("prefCamera", entriesValues[0].toString()));
            StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

            // Our new rez entries
            int rezSize = sizes.length;
            CharSequence[] rez = new CharSequence[rezSize];
            CharSequence[] rezValues = new CharSequence[rezSize];

            // Loop through and create our entries
            for (int i = 0; i < sizes.length; i++) {
                rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
            }

            // Update our settings entry
            cameraRez.setEntries(rez);
            cameraRez.setEntryValues(rezValues);
            cameraRez.setDefaultValue(rezValues[0]);

            // Get the possible focus lengths, on non-optical devices this only has one value
            // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            float[] focus_lengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            CharSequence[] focuses = new CharSequence[focus_lengths.length];
            for (int i = 0; i < focus_lengths.length; i++) {
                focuses[i] = focus_lengths[i] + "";
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error)).show(getFragmentManager(), "dialog");
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
//         Add the listener to the camera pref, so we can update the camera resolution field
        if (key.equals("prefCamera")) {
            try {
                // Get what camera we have selected
                String cameraId = sharedPreferences.getString("prefCamera", "0");

                // Load our camera settings
                Activity activity = getActivity();
                CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

                // Right now we have selected the first camera, so lets populate the resolution list
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

                // Our new rez entries
                int rezSize = sizes.length;
                CharSequence[] rez = new CharSequence[rezSize];
                CharSequence[] rezValues = new CharSequence[rezSize];

                // Loop through and create our entries
                for (int i = 0; i < sizes.length; i++) {
                    rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                }

                // Update our settings entry
                ListPreference cameraRez = (ListPreference) getPreferenceManager().findPreference("prefSizeRaw");
                cameraRez.setEntries(rez);
                cameraRez.setEntryValues(rezValues);
                cameraRez.setDefaultValue(rezValues[0]);
                cameraRez.setValueIndex(0);

                // Get the possible focus lengths, on non-optical devices this only has one value
                // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                float[] focus_lengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                CharSequence[] focuses = new CharSequence[focus_lengths.length];
                for (int i = 0; i < focus_lengths.length; i++) {
                    focuses[i] = focus_lengths[i] + "";
                }

            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
                // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
                ErrorDialog.newInstance(getString(R.string.camera_error)).show(getFragmentManager(), "dialog");
            }
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
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
