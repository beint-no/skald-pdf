package org.skaldpdf.layout.properties;

import java.util.Arrays;
import java.util.Objects;

public final class UnitValue {
    public enum UnitType {
        POINT,
        PERCENT,
        POINT_ARRAY,
        PERCENT_ARRAY
    }

    private final UnitType unitType;
    private final float[] values;

    private UnitValue(UnitType unitType, float... values) {
        this.unitType = Objects.requireNonNull(unitType, "unitType");
        if (values.length == 0) {
            throw new IllegalArgumentException("A unit value needs at least one value");
        }
        for (var value : values) {
            if (!(value > 0) || !Float.isFinite(value)) {
                throw new IllegalArgumentException("Unit values must be positive and finite");
            }
        }
        this.values = values.clone();
    }

    public static UnitValue createPointValue(float value) {
        return new UnitValue(UnitType.POINT, value);
    }

    public static UnitValue createPercentValue(float value) {
        return new UnitValue(UnitType.PERCENT, value);
    }

    public static UnitValue createPointArray(float[] values) {
        return new UnitValue(UnitType.POINT_ARRAY, values);
    }

    public static UnitValue createPercentArray(float[] values) {
        return new UnitValue(UnitType.PERCENT_ARRAY, values);
    }

    public static UnitValue createPercentArray(int numberOfColumns) {
        if (numberOfColumns < 1) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        var values = new float[numberOfColumns];
        Arrays.fill(values, 1f);
        return createPercentArray(values);
    }

    public UnitType unitType() {
        return unitType;
    }

    public float value() {
        return values[0];
    }

    public float[] values() {
        return values.clone();
    }

    @Override
    public String toString() {
        return unitType + " " + Arrays.toString(values);
    }
}
