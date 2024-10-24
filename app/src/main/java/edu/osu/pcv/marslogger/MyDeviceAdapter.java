package edu.osu.pcv.marslogger;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.woncan.device.Device;

import java.util.List;

public class MyDeviceAdapter extends ArrayAdapter<Device> {

    public MyDeviceAdapter(Context context, List<Device> devices) {
        super(context, android.R.layout.simple_spinner_item, devices);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Use the default layout for displaying the selected item
        View view = super.getView(position, convertView, parent);
        TextView textView = (TextView) view.findViewById(android.R.id.text1);

        // Get the device for the current position
        Device device = getItem(position);

        // Display the device name if available, otherwise a default string
        if (device != null) {
            textView.setText(getDeviceName(device)); // Use a helper method to get the name
        } else {
            textView.setText("Unknown Device");
        }

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        // Use the same logic for the dropdown view
        return getView(position, convertView, parent);
    }

    // Helper method to safely get the device name
    private String getDeviceName(Device device) {
        // Assuming there's a method like `getName()` in the Device class
        return device.getName() != null ? device.getName() : "Unknown Device";
    }
}
