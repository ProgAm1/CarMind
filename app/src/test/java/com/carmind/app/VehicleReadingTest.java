package com.carmind.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleReadingTest {
    @Test public void missingValuesAreDistinctFromZero() {
        VehicleReading reading = new VehicleReading(1, "Reader", null, 0.0, null);
        assertNull(reading.rpm);
        assertEquals(0.0, reading.speed, 0.0);
    }
    @Test(expected = IllegalArgumentException.class) public void emptySnapshotIsRejected() {
        new VehicleReading(1, "Reader", null, null, null);
    }
    @Test(expected = IllegalArgumentException.class) public void nonFiniteValuesAreRejected() {
        new VehicleReading(1, "Reader", Double.NaN, 10.0, null);
    }
    @Test(expected = IllegalArgumentException.class) public void invalidRangeIsRejected() {
        new VehicleReading(1, "Reader", 1000.0, -1.0, null);
    }
    @Test(expected = IllegalArgumentException.class) public void timestampIsRequired() {
        new VehicleReading(0, "Reader", 1000.0, null, null);
    }
    @Test(expected = IllegalArgumentException.class) public void sourceIsRequired() {
        new VehicleReading(1, "", 1000.0, null, null);
    }
}
