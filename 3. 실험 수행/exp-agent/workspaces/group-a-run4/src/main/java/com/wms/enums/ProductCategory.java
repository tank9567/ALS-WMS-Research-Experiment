package com.wms.enums;

public enum ProductCategory {
    GENERAL(10.0),
    FRESH(5.0),
    HAZMAT(0.0),
    HIGH_VALUE(3.0);

    private final double overReceivingAllowancePct;

    ProductCategory(double overReceivingAllowancePct) {
        this.overReceivingAllowancePct = overReceivingAllowancePct;
    }

    public double getOverReceivingAllowancePct() {
        return overReceivingAllowancePct;
    }
}
