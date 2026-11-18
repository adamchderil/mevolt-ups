package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilityPowerStateTest {
    @Test
    public void recognizesDocumentedUtilityStates() {
        assertEquals(UtilityPowerState.State.GRID_AVAILABLE,
                UtilityPowerState.fromUtilityCode(1));
        assertEquals(UtilityPowerState.State.GRID_FAILED,
                UtilityPowerState.fromUtilityCode(2));
        assertEquals(UtilityPowerState.State.UNKNOWN,
                UtilityPowerState.fromUtilityCode(3));
    }

    @Test
    public void detectsBothMainsTransitions() {
        assertEquals(UtilityPowerState.Transition.POWER_LOST,
                UtilityPowerState.transition(
                        UtilityPowerState.State.GRID_AVAILABLE,
                        UtilityPowerState.State.GRID_FAILED));
        assertEquals(UtilityPowerState.Transition.POWER_RESTORED,
                UtilityPowerState.transition(
                        UtilityPowerState.State.GRID_FAILED,
                        UtilityPowerState.State.GRID_AVAILABLE));
    }

    @Test
    public void ignoresInitialAndUnknownStates() {
        assertEquals(UtilityPowerState.Transition.NONE,
                UtilityPowerState.transition(
                        UtilityPowerState.State.UNKNOWN,
                        UtilityPowerState.State.GRID_FAILED));
        assertEquals(UtilityPowerState.Transition.NONE,
                UtilityPowerState.transition(
                        UtilityPowerState.State.GRID_AVAILABLE,
                        UtilityPowerState.State.UNKNOWN));
    }
}
