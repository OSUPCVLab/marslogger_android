package org.ros.android;

import android.os.Environment;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class IPTool {


    public static String getHostEthernetIp() {
        // copied from https://www.reddit.com/r/AndroidStudio/comments/ulsvrw/android_studio_and_ethernet_get_ip_adress/
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); ((Enumeration) en).hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static String composeLidarIp(String hostip, String lidarid) {
        String[] hostparts = hostip.split("[.]");
        return hostparts[0] + '.' + hostparts[1] + '.' + hostparts[2] + '.' + lidarid;
    }

    public static String getPreviousLidarId(File configFile) {
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

                String[] lidarparts = lidarip.split("[.]");
                String lidarid = lidarparts[3];
                return lidarid;
            } catch (Exception e) {
                e.printStackTrace();
                return new String();
            }
        } else {
            return new String();
        }
    }
}
