package com.carmind.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DriveSimulationTest {
    private DriveSimulation connected() {
        DriveSimulation model = new DriveSimulation();
        model.plugIn(); model.pair(); model.advanceConnection(); model.advanceConnection();
        assertEquals(DriveSimulation.Stage.CONNECTED, model.stage);
        return model;
    }
    @Test public void requiresPowerPairingAndNegotiationBeforeReadings() {
        DriveSimulation model = new DriveSimulation();
        model.pair(); model.advanceConnection();
        assertEquals(DriveSimulation.Stage.UNPLUGGED, model.stage);
        assertFalse(model.tick());
        model.plugIn(); model.pair();
        assertEquals(DriveSimulation.Stage.PAIRING, model.stage);
        assertFalse(model.tick());
        model.advanceConnection();
        assertEquals(DriveSimulation.Stage.NEGOTIATING, model.stage);
        assertFalse(model.tick());
        model.advanceConnection();
        assertTrue(model.tick());
    }
    @Test(expected = IllegalStateException.class) public void cannotProduceReadingBeforeConnection() {
        new DriveSimulation().reading(1);
    }
    @Test public void drivingValuesChangeSmoothlyAndStayWithinPlausibleRanges() {
        for (DriveSimulation.Mode mode : DriveSimulation.Mode.values()) {
            DriveSimulation model = connected(); model.mode = mode;
            double previous = model.speed;
            for (int i = 0; i < 600; i++) {
                model.tick();
                assertTrue(Math.abs(model.speed - previous) <= 4.0001);
                assertTrue(model.speed >= 0 && model.speed <= 100);
                assertTrue(model.rpm >= 750 && model.rpm <= 3500);
                assertTrue(model.coolant >= 75 && model.coolant <= 91);
                previous = model.speed;
            }
        }
    }
    @Test public void pauseAndDisconnectFreezeReadingsUntilExplicitResumeOrReconnect() {
        DriveSimulation model = connected(); model.tick();
        double speed = model.speed;
        model.paused = true;
        assertFalse(model.tick()); assertEquals(speed, model.speed, 0);
        model.paused = false; assertTrue(model.tick());
        model.disconnect(); speed = model.speed;
        assertFalse(model.tick()); assertEquals(speed, model.speed, 0);
        model.pair(); model.advanceConnection(); model.advanceConnection();
        assertTrue(model.tick());
    }
    @Test public void idleSettlesAtZeroWithEngineRunning() {
        DriveSimulation model = connected(); model.mode = DriveSimulation.Mode.HIGHWAY;
        for (int i = 0; i < 60; i++) model.tick();
        model.mode = DriveSimulation.Mode.IDLE;
        for (int i = 0; i < 30; i++) model.tick();
        assertEquals(0, model.speed, 0); assertTrue(model.rpm > 750);
    }
    @Test public void backgroundPausesAndCancelsIncompleteConnections() {
        DriveSimulation model = connected(); model.background();
        assertFalse(model.tick()); assertEquals(DriveSimulation.Stage.CONNECTED, model.stage);
        model.disconnect(); model.pair(); model.background();
        assertEquals(DriveSimulation.Stage.POWERED, model.stage);
        model.advanceConnection(); assertEquals(DriveSimulation.Stage.POWERED, model.stage);
    }
    @Test public void snapshotsKeepSimulationSourceAndAcquisitionTime() {
        DriveSimulation model = connected(); model.tick();
        VehicleReading first = model.reading(1000); model.tick();
        VehicleReading second = model.reading(2000);
        assertEquals(1000, first.recordedAt);
        assertTrue(first.deviceName.startsWith("SIMULATED"));
        assertNotEquals(first.speed, second.speed);
    }
}
