package edu.osu.pcv.marslogger;

public class WoncanUtils {
    // Method to convert woncan status code to a string
    public static String convertStatusToString(int status) {
        switch (status) {
            case 0:
                return "无法定位";
            case 1:
                return "单点定位";
            case 5:
                return "浮点定位";
            case 4:
                return "固定定位";
            default:
                return "未知状态"; // Default if the status doesn't match any case
        }
    }
}
