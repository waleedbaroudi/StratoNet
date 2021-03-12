package utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StratoUtils {

    public static final int TOKEN_LENGTH = 17;
    public static final String APOD_URL =
            "https://api.nasa.gov/planetary/apod?api_key=VgdpOJ1gLggIX6FTts3OAZhu9J0d7iaSNx921Itr&date=";
    public static final String INSIGHT_URL =
            "https://api.nasa.gov/insight_weather/?api_key=VgdpOJ1gLggIX6FTts3OAZhu9J0d7iaSNx921Itr&feedtype=json&ver=1.0";

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

    public static byte[] downloadImage(String url) throws IOException {
        URL imgUrl = new URL(url);
        InputStream imgInput = new BufferedInputStream(imgUrl.openStream());
        ByteArrayOutputStream imgOutput = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = imgInput.read(buffer)) != -1) {
            imgOutput.write(buffer, 0, length);
        }
        imgInput.close();
        imgOutput.close();
        return imgOutput.toByteArray();
    }

    public static String[] extractPREObjects(String response) {
        String[] preObjects = new String[7];
        String patString = "\"PRE\"[^}]+}";
        Pattern pattern = Pattern.compile(patString);
        Matcher matcher = pattern.matcher(response);
        int solCount = 0;
        while (matcher.find() && (solCount < preObjects.length)) {
            String pre = matcher.group().substring(7);
            preObjects[solCount] = pre;
            solCount++;
        }
        return preObjects;
    }

    public static String[] getPressureValues(String pressure) {
        String[] info = pressure.substring(1, pressure.length() - 1).split(",");
        List<String> values = Arrays.asList(info).stream().map(field -> field.split(":")[1].trim()).collect(Collectors.toList());
        info[0] = String.format("%-40s %s pascals", "Average atmospheric pressure:", values.get(0));
        info[1] = String.format("%-40s %s samples", "Total number of samples:", values.get(1));
        info[2] = String.format("%-40s %s pascals", "Minimum atmospheric pressure:", values.get(2));
        info[3] = String.format("%-40s %s pascals", "Maximum atmospheric pressure:", values.get(3));

        return info;
    }
}
