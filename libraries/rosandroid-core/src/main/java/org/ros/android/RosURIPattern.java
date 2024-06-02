package org.ros.android;


import java.util.regex.Pattern;

/**
 * Regular expressions used with ROS URIs.
 *
 * The majority of the expressions and variables were copied from
 * {@link android.util.Patterns}. The {@link android.util.Patterns} class could not be
 * utilized because the PROTOCOL regex included other web protocols besides http. The
 * http protocol is required by ROS.
 */
public class RosURIPattern
{
    /* A word boundary or end of input.  This is to stop foo.sure from matching as foo.su */
    private static final String WORD_BOUNDARY = "(?:\\b|$|^)";

    /**
     * Valid UCS characters defined in RFC 3987. Excludes space characters.
     */
    private static final String UCS_CHAR = "[" +
            "\u00A0-\uD7FF" +
            "\uF900-\uFDCF" +
            "\uFDF0-\uFFEF" +
            "\uD800\uDC00-\uD83F\uDFFD" +
            "\uD840\uDC00-\uD87F\uDFFD" +
            "\uD880\uDC00-\uD8BF\uDFFD" +
            "\uD8C0\uDC00-\uD8FF\uDFFD" +
            "\uD900\uDC00-\uD93F\uDFFD" +
            "\uD940\uDC00-\uD97F\uDFFD" +
            "\uD980\uDC00-\uD9BF\uDFFD" +
            "\uD9C0\uDC00-\uD9FF\uDFFD" +
            "\uDA00\uDC00-\uDA3F\uDFFD" +
            "\uDA40\uDC00-\uDA7F\uDFFD" +
            "\uDA80\uDC00-\uDABF\uDFFD" +
            "\uDAC0\uDC00-\uDAFF\uDFFD" +
            "\uDB00\uDC00-\uDB3F\uDFFD" +
            "\uDB44\uDC00-\uDB7F\uDFFD" +
            "&&[^\u00A0[\u2000-\u200A]\u2028\u2029\u202F\u3000]]";

    /**
     * Valid characters for IRI label defined in RFC 3987.
     */
    private static final String LABEL_CHAR = "a-zA-Z0-9" + UCS_CHAR;

    /**
     * RFC 1035 Section 2.3.4 limits the labels to a maximum 63 octets.
     */
    private static final String IRI_LABEL =
            "[" + LABEL_CHAR + "](?:[" + LABEL_CHAR + "\\-]{0,61}[" + LABEL_CHAR + "]){0,1}";

    private static final Pattern IP_ADDRESS
            = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))");

    /**
     * Regular expression that matches domain names without a TLD
     */
    private static final String RELAXED_DOMAIN_NAME =
            "(?:" + "(?:" + IRI_LABEL + "(?:\\.(?=\\S))" +"?)+" +
                    "|" + IP_ADDRESS + ")";

    private static final String HTTP_PROTOCOL = "(?i:http):\\/\\/";

    public static final int HTTP_PROTOCOL_LENGTH = ("http://").length();

    private static final String PORT_NUMBER = "\\:\\d{1,5}\\/?";

    /**
     *  Regular expression pattern to match valid rosmaster URIs.
     *  This assumes the port number and trailing "/" will be auto
     *  populated (default port: 11311) if left out.
     */
    public static final Pattern URI = Pattern.compile("("
            + WORD_BOUNDARY
            + "(?:"
            + "(?:" + HTTP_PROTOCOL + ")"
            + "(?:" + RELAXED_DOMAIN_NAME + ")"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + WORD_BOUNDARY
            + ")");

    public static final Pattern PORT = Pattern.compile(PORT_NUMBER);
}
