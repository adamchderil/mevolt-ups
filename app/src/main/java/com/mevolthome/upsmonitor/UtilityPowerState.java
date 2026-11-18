package com.mevolthome.upsmonitor;

final class UtilityPowerState {
    static final int GRID_AVAILABLE_CODE = 1;
    static final int GRID_FAILED_CODE = 2;

    enum State {
        GRID_AVAILABLE,
        GRID_FAILED,
        UNKNOWN
    }

    enum Transition {
        NONE,
        POWER_LOST,
        POWER_RESTORED
    }

    private UtilityPowerState() {
    }

    static State fromUtilityCode(int code) {
        if (code == GRID_AVAILABLE_CODE) {
            return State.GRID_AVAILABLE;
        }
        if (code == GRID_FAILED_CODE) {
            return State.GRID_FAILED;
        }
        return State.UNKNOWN;
    }

    static Transition transition(State previous, State current) {
        if (previous == State.GRID_AVAILABLE && current == State.GRID_FAILED) {
            return Transition.POWER_LOST;
        }
        if (previous == State.GRID_FAILED && current == State.GRID_AVAILABLE) {
            return Transition.POWER_RESTORED;
        }
        return Transition.NONE;
    }

    static int persistedCode(State state) {
        if (state == State.GRID_AVAILABLE) {
            return GRID_AVAILABLE_CODE;
        }
        if (state == State.GRID_FAILED) {
            return GRID_FAILED_CODE;
        }
        return -1;
    }
}
