package com.match.choms.util;

/** A drop-down choice: value is stored in Firestore, label is shown to the user. */
public class Option {
    public final String value;
    public final String label;

    public Option(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override public String toString() { return label; }
}
