package com.carmind.app;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class ObdProtocolTest {
    @Test public void rpmUsesBothUnsignedBytes() {
        assertEquals(1726.0, ObdProtocol.decode("41 0C 1A F8\r>", 12), 0.0);
        assertEquals(16383.75, ObdProtocol.decode("410CFFFF", 12), 0.0);
    }
    @Test public void speedKeepsZeroAsARealReading() {
        assertEquals(0.0, ObdProtocol.decode("410D00", 13), 0.0);
        assertEquals(100.0, ObdProtocol.decode("410D64", 13), 0.0);
    }
    @Test public void coolantHandlesNegativeAndPositiveTemperatures() {
        assertEquals(-40.0, ObdProtocol.decode("410500", 5), 0.0);
        assertEquals(90.0, ObdProtocol.decode("410582", 5), 0.0);
    }
    @Test public void echoSearchingSpacesAndLowerCaseAreIgnored() {
        assertEquals(1726.0, ObdProtocol.decode("010C\rSEARCHING...\r41 0c 1a f8\r>", 12), 0.0);
    }
    @Test public void badAndUnrelatedResponsesNeverBecomeZero() {
        for (String reply : new String[]{"NO DATA", "?", "STOPPED", "UNABLE TO CONNECT",
                "410C1A", "410CZZFF", "410D64", "410C1AF800", "7E804410C1AF8", ""}) {
            assertNull(reply, ObdProtocol.decode(reply, 12));
        }
    }
    @Test public void firstValidResponseIsUsedWithoutCombiningEcus() {
        assertEquals(1726.0, ObdProtocol.decode("410C1AF8\r410C2000", 12), 0.0);
    }
    @Test public void supportMaskHonorsBitPositions() {
        byte[] mask = ObdProtocol.payload("410008180000", 0, 4);
        assertTrue(ObdProtocol.supports(mask, 5));
        assertTrue(ObdProtocol.supports(mask, 12));
        assertTrue(ObdProtocol.supports(mask, 13));
        assertFalse(ObdProtocol.supports(mask, 1));
        assertFalse(ObdProtocol.supports(mask, 32));
    }
    @Test public void invalidSupportMaskOrPidIsRejected() {
        assertFalse(ObdProtocol.supports(null, 5));
        assertFalse(ObdProtocol.supports(new byte[3], 5));
        assertFalse(ObdProtocol.supports(new byte[4], 0));
        assertFalse(ObdProtocol.supports(new byte[4], 33));
    }
    @Test public void onlySupportedReadsAreSent() throws Exception {
        List<String> commands = new ArrayList<>();
        Double[] values = ObdProtocol.read(command -> {
            commands.add(command);
            if (command.equals("0100")) return "410000080000"; // Speed only.
            if (command.equals("010D")) return "410D00";
            return "OK";
        });
        assertEquals(Arrays.asList("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100", "010D"), commands);
        assertNull(values[0]);
        assertEquals(0.0, values[1], 0.0);
        assertNull(values[2]);
    }
    @Test public void partialResponseKeepsOtherReadings() throws Exception {
        Double[] values = ObdProtocol.read(command -> {
            if (command.equals("0100")) return "410008180000";
            if (command.equals("010C")) return "NO DATA";
            if (command.equals("010D")) return "410D32";
            if (command.equals("0105")) return "410582";
            return "OK";
        });
        assertNull(values[0]);
        assertEquals(50.0, values[1], 0.0);
        assertEquals(90.0, values[2], 0.0);
    }
    @Test(expected = IOException.class) public void noSupportedReadingsFails() throws Exception {
        ObdProtocol.read(command -> command.equals("0100") ? "410000000000" : "OK");
    }
    @Test(expected = IOException.class) public void badSetupStopsBeforeVehicleRequests() throws Exception {
        ObdProtocol.read(command -> "?");
    }
    @Test(expected = IOException.class) public void noVehicleResponseFails() throws Exception {
        ObdProtocol.read(command -> command.equals("0100") ? "UNABLE TO CONNECT" : "OK");
    }
    @Test public void continuousSamplesReuseSetupAndKeepChangingRealReplies() throws Exception {
        List<String> commands = new ArrayList<>();
        int[] speed = {0};
        ObdProtocol.Exchange exchange = command -> {
            commands.add(command);
            if (command.equals("0100")) return "410000080000";
            if (command.equals("010D")) return speed[0]++ == 0 ? "410D00" : "410D32";
            return "OK";
        };
        byte[] supported = ObdProtocol.initialize(exchange);
        assertEquals(0.0, ObdProtocol.sample(exchange, supported)[1], 0);
        assertEquals(50.0, ObdProtocol.sample(exchange, supported)[1], 0);
        assertEquals(1, java.util.Collections.frequency(commands, "ATZ"));
        assertEquals(1, java.util.Collections.frequency(commands, "0100"));
        assertEquals(2, java.util.Collections.frequency(commands, "010D"));
        assertFalse(commands.contains("010C"));
    }
    @Test public void exchangeWritesCarriageReturnAndStopsAtPrompt() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream("410D32\r>unused".getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals("410D32\r", ObdProtocol.exchange(input, output, "010D"));
        assertEquals("010D\r", output.toString("US-ASCII"));
        assertEquals('u', input.read());
    }
    @Test(expected = IOException.class) public void disconnectionBeforePromptFails() throws Exception {
        ObdProtocol.exchange(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), "010D");
    }
    @Test(expected = IOException.class) public void oversizedReplyIsBounded() throws Exception {
        ObdProtocol.exchange(new ByteArrayInputStream(new byte[8193]), new ByteArrayOutputStream(), "010D");
    }
}
