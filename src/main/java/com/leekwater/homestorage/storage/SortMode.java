package com.leekwater.homestorage.storage;

public enum SortMode {
    NAME,
    COUNT_DESC,
    COUNT_ASC;

    /** Safe against any number a client might send. */
    public static SortMode byId(int id) {
        SortMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /** The next mode in the cycle, for the sort button. */
    public SortMode next() {
        return byId(ordinal() + 1);
    }
}
