package com.carmind.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DemoTripTest {
    @Test public void generatedTripsAreStableForAccountAndDifferentAcrossAccounts() {
        long now = 1792560000000L;
        List<DemoTrip> first = DemoTrip.examples("account-a", now);
        List<DemoTrip> again = DemoTrip.examples("account-a", now);
        List<DemoTrip> second = DemoTrip.examples("account-b", now);
        assertEquals(4, first.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).id, again.get(i).id);
            assertEquals(first.get(i).startedAt, again.get(i).startedAt);
            assertNotEquals(first.get(i).id, second.get(i).id);
            assertNotEquals(first.get(i).startedAt, second.get(i).startedAt);
            assertTrue(first.get(i).time() < now);
            assertEquals(4 - i, first.get(i).number);
            assertTrue(first.get(i).averageSpeed() <= first.get(i).maxSpeed);
        }
    }
    @Test public void tripTimeDistanceAndAverageAgree() {
        DemoTrip trip = new DemoTrip(1, 1000);
        trip.tick(60); trip.tick(30);
        assertEquals(60, trip.seconds);
        assertEquals(61000, trip.time());
        assertEquals(0.75, trip.kilometers, 0.00001);
        assertEquals(45, trip.averageSpeed(), 0.00001);
        assertEquals(60, trip.maxSpeed, 0);
    }
    @Test public void tripHasNoDestinationLimitAndStopsOnlyWhenEnded() {
        DemoTrip trip = new DemoTrip(1, 1000);
        for (int i = 0; i < 100; i++) trip.tick(60);
        assertFalse(trip.ended); assertEquals(50, trip.kilometers, 0.00001);
        trip.finish(); long end = trip.time(); trip.tick(90); assertEquals(end, trip.time());
    }
    @Test public void manualEndKeepsStableTripNumber() {
        DemoTrip trip = new DemoTrip(3, 1000); trip.tick(30); assertTrue(trip.finish());
        assertEquals(3, trip.number);
    }
    @Test public void stationaryTimeCountsWithoutInventingDistance() {
        DemoTrip trip = new DemoTrip(1, 1000); trip.tick(0);
        assertEquals(30, trip.seconds); assertEquals(0, trip.kilometers, 0);
        assertEquals(0, trip.averageSpeed(), 0); assertTrue(trip.finish());
    }
    @Test public void emptyTripCannotBecomeHistoryRecord() {
        assertFalse(new DemoTrip(1, 1000).finish());
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidSpeed() {
        new DemoTrip(1, 1000).tick(Double.NaN);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInconsistentRestoredDistanceAndSpeed() {
        new DemoTrip("bad", 1, 1000, 30, 10, 20, true);
    }
    @Test public void stopsAtOneDayEvenForAnIdleTrip() {
        DemoTrip trip = new DemoTrip("idle", 1, 1000, 86399, 0, 0, false);
        trip.tick(0); assertTrue(trip.ended); assertEquals(86400, trip.seconds);
    }
    @Test public void telemetryProfilesVaryByAccount() {
        DriveSimulation first = new DriveSimulation(), second = new DriveSimulation();
        first.profilePhase = Math.floorMod(DemoTrip.accountSeed("a"), 360) / 30.0;
        second.profilePhase = Math.floorMod(DemoTrip.accountSeed("b"), 360) / 30.0;
        for (DriveSimulation simulation : new DriveSimulation[]{first, second}) {
            simulation.plugIn(); simulation.pair(); simulation.advanceConnection(); simulation.advanceConnection();
            for (int i = 0; i < 10; i++) simulation.tick();
        }
        assertNotEquals(first.reading(1000).rpm, second.reading(1000).rpm);
    }
}
