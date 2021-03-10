package utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StratoUtils {

    public static byte[] intToByte(int num) {
        return ByteBuffer.allocate(4).putInt(num).array();
    }

    public static byte[] makeAuthMessage(byte phase, byte type, String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        byte[] length = StratoUtils.intToByte(payloadBytes.length);

        byte[] request = new byte[6 + payloadBytes.length];

        request[0] = phase;
        request[1] = type;

        System.arraycopy(length, 0, request, 2, 4);
        System.arraycopy(payloadBytes, 0, request, 6, payloadBytes.length);

        return request;
    }
}
