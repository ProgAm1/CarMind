package com.carmind.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripRecordTest {
    private VehicleReading reading(Double speed, Double rpm, Double coolant) { return new VehicleReading(1000, "OBD", rpm, speed, coolant); }
    @Test public void realElapsedTimeAndObservedMetricsAreKept() {
        TripRecord trip = new TripRecord(1, 100000);
        trip.add(reading(30.0, 1000.0, 80.0), 0);
        trip.add(reading(60.0, 2000.0, 90.0), 10000);
        trip.finish("finished", 12000);
        assertEquals(112000, trip.endedAt()); assertEquals(2, trip.samples);
        assertEquals(45, trip.speedSum / trip.speedSamples, 0);
        assertEquals(60, trip.maxSpeed, 0); assertEquals(2000, trip.maxRpm, 0); assertEquals(90, trip.maxCoolant, 0);
        assertEquals(0.125, trip.kilometers, 0.000001); assertEquals(10000, trip.speedCoverageMs);
    }
    @Test public void missingValuesAndLongGapsDoNotInventDistanceOrZeroSpeed() {
        TripRecord trip = new TripRecord(2, 1000);
        trip.add(reading(60.0, null, null), 0);
        trip.add(reading(null, 900.0, null), 1000);
        trip.add(reading(60.0, null, null), 2000);
        trip.add(reading(60.0, null, null), 18000);
        assertEquals(0, trip.kilometers, 0); assertEquals(0, trip.speedCoverageMs);
        assertEquals(3, trip.speedSamples); assertEquals(1, trip.rpmSamples); assertEquals(0, trip.coolantSamples);
    }
    @Test public void stationaryAndNegativeCoolantAreRealValues() {
        TripRecord trip = new TripRecord(1, 1000);
        trip.add(reading(0.0, null, -20.0), 0); trip.add(reading(0.0, null, -10.0), 5000);
        assertEquals(0, trip.maxSpeed, 0); assertEquals(5000, trip.speedCoverageMs);
        assertEquals(-10, trip.maxCoolant, 0); assertEquals(-15, trip.coolantSum / trip.coolantSamples, 0);
    }
    @Test public void stopIsIdempotent() {
        TripRecord trip = new TripRecord(1, 1000); trip.add(reading(20.0, null, null), 0);
        trip.finish("disconnected", 0); trip.finish("finished", 5000);
        assertEquals("disconnected", trip.reason); assertEquals(1000, trip.endedAt());
    }
    @Test(expected = IllegalArgumentException.class) public void stoppedTripRejectsLateSamples() {
        TripRecord trip = new TripRecord(1, 1000); trip.finish("finished", 1000); trip.add(reading(1.0, null, null), 2000);
    }
    @Test(expected = IllegalArgumentException.class) public void outOfOrderSampleIsRejected() {
        TripRecord trip = new TripRecord(1, 1000); trip.add(reading(1.0, null, null), 2000); trip.add(reading(1.0, null, null), 1000);
    }
}
