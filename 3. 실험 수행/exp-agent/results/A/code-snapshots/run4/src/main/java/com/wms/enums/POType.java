package com.wms.enums;

public enum POType {
    NORMAL(1.0),
    URGENT(2.0),
    IMPORT(1.5);

    private final double multiplier;

    POType(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
