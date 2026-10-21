package com.carmind.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small, read-only ELM-compatible protocol. No arbitrary vehicle commands. */
final class ObdProtocol {
    interface Exchange { String send(String command) throws IOException; }

    static Double[] read(Exchange connection) throws IOException {
        return sample(connection, initialize(connection));
    }

    static byte[] initialize(Exchange connection) throws IOException {
        connection.send("ATZ");
        for (String command : new String[]{"ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"}) {
            if (!connection.send(command).toUpperCase(Locale.ROOT).contains("OK")) {
                throw new IOException("Adapter setup failed");
            }
        }
        byte[] supported = payload(connection.send("0100"), 0, 4);
        if (supported == null) throw new IOException("No supported PID response");
        return supported;
    }

    static Double[] sample(Exchange connection, byte[] supported) throws IOException {
        Double rpm = supports(supported, 12) ? decode(connection.send("010C"), 12) : null;
        Double speed = supports(supported, 13) ? decode(connection.send("010D"), 13) : null;
        Double coolant = supports(supported, 5) ? decode(connection.send("0105"), 5) : null;
        if (rpm == null && speed == null && coolant == null) throw new IOException("No readings");
        return new Double[]{rpm, speed, coolant};
    }

    static boolean supports(byte[] mask, int pid) {
        if (mask == null || mask.length != 4 || pid < 1 || pid > 32) return false;
        int offset = pid - 1;
        return ((mask[offset / 8] & 0xff) & (1 << (7 - offset % 8))) != 0;
    }

    static Double decode(String reply, int pid) {
        if (pid != 12 && pid != 13 && pid != 5) return null;
        byte[] bytes = payload(reply, pid, pid == 12 ? 2 : 1);
        if (bytes == null) return null;
        int a = bytes[0] & 0xff;
        if (pid == 12) return (a * 256 + (bytes[1] & 0xff)) / 4.0;
        return pid == 5 ? a - 40.0 : (double) a;
    }

    static byte[] payload(String reply, int pid, int count) {
        String prefix = String.format(Locale.ROOT, "41%02X", pid);
        for (String line : reply.toUpperCase(Locale.ROOT).split("[\\r\\n>]")) {
            String hex = line.replaceAll("\\s", "");
            // Headers are disabled during setup. Reject truncated, wrong-PID or non-hex frames.
            if (!hex.startsWith(prefix) || hex.length() != 4 + count * 2
                    || !hex.matches("[0-9A-F]+")) continue;
            byte[] data = new byte[count];
            for (int i = 0; i < count; i++) {
                data[i] = (byte) Integer.parseInt(hex.substring(4 + i * 2, 6 + i * 2), 16);
            }
            return data; // First valid responder; no multi-ECU aggregation.
        }
        return null;
    }

    static String exchange(InputStream input, OutputStream output, String command) throws IOException {
        output.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        StringBuilder response = new StringBuilder();
        for (int i = 0; i < 8192; i++) {
            int value = input.read();
            if (value < 0) throw new IOException("Adapter disconnected");
            if (value == '>') return response.toString();
            response.append((char) value);
        }
        throw new IOException("Adapter response too long");
    }
}
