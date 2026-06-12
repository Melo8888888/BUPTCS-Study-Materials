package com.evcharge.domain;

public enum ChargeMode {
    FAST("F", "快充", 30),
    SLOW("T", "慢充", 10);

    private final String prefix;
    private final String label;
    private final int powerKw;

    ChargeMode(String prefix, String label, int powerKw) {
        this.prefix = prefix;
        this.label = label;
        this.powerKw = powerKw;
    }

    public String prefix() {
        return prefix;
    }

    public String label() {
        return label;
    }

    public int powerKw() {
        return powerKw;
    }

    public static ChargeMode fromCode(String code) {
        if ("F".equalsIgnoreCase(code) || "FAST".equalsIgnoreCase(code)) return FAST;
        if ("T".equalsIgnoreCase(code) || "SLOW".equalsIgnoreCase(code)) return SLOW;
        throw new IllegalArgumentException("Unsupported charge mode: " + code);
    }
}
