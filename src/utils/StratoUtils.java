package utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StratoUtils {

    public static final int TOKEN_LENGTH = 17;
    public static final String APOD_URL =
            "https://api.nasa.gov/planetary/apod?api_key=VgdpOJ1gLggIX6FTts3OAZhu9J0d7iaSNx921Itr";
    public static final String INSIGHT_URL = ""; // TODO: set later

    public static byte[] intToByte(int num) {
        return ByteBuffer.allocate(4).putInt(num).array();
    }

    public static byte[] makeAuthMessage(byte type, String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] length = StratoUtils.intToByte(payloadBytes.length);
        byte[] request = new byte[6 + payloadBytes.length];

        request[0] = 0;
        request[1] = type;

        System.arraycopy(length, 0, request, 2, 4);
        System.arraycopy(payloadBytes, 0, request, 6, payloadBytes.length);

        return request;
    }

    public static byte[] makeQueryMessage(String token, byte type, String payload) throws InvalidTokenException {
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        if (tokenBytes.length != TOKEN_LENGTH)
            throw new InvalidTokenException();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] length = StratoUtils.intToByte(payloadBytes.length);
        byte[] request = new byte[6 + TOKEN_LENGTH + payloadBytes.length];

        request[0] = 1;
        request[18] = type;

        System.arraycopy(tokenBytes, 0, request, 1, 17);
        System.arraycopy(length, 0, request, 19, 4);
        System.arraycopy(payloadBytes, 0, request, 23, payloadBytes.length);

        return request;
    }

    public static String extractURL(String json) {
        String pattern = "\\burl\":\"[^\"]+";
        Pattern urlPattern = Pattern.compile(pattern);
        Matcher matcher = urlPattern.matcher(json);

        String url = "";

        while (matcher.find()) {
            url = matcher.group().substring(6);
        }

        return url;
    }
}
