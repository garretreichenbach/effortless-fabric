package dev.huskcasaca.effortless.building;

/**
 * Current build operation: break, place or scan
 */
public enum BuildOp {
    BREAK("break"),
    PLACE("place"),
    SCAN("scan");;

    private final String name;

    BuildOp(String name) {
        this.name = name;
    }
}
