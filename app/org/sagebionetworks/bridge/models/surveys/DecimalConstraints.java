package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.json.UnitDeserializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DecimalConstraints extends Constraints {
    
    private Unit unit;
    private Double minValue;
    private Double maxValue;
    private Double step;
    
    public DecimalConstraints() {
        setDataType(DataType.DECIMAL);
        setSupportedHints(EnumSet.of(UIHint.NUMBERFIELD, UIHint.SLIDER));
    }
    
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public Unit getUnit() {
        return unit;
    }
    public String getShortUnit() {
        return (unit == null) ? null : unit.getAbbreviation();
    }
    @JsonDeserialize(using = UnitDeserializer.class)
    public void setUnit(Unit unit) {
        this.unit = unit;
    }
    public Double getMinValue() {
        return minValue;
    }
    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }
    public Double getMaxValue() {
        return maxValue;
    }
    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }
    public Double getStep() {
        return step;
    }
    public void setStep(Double step) {
        this.step = step;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((maxValue == null) ? 0 : maxValue.hashCode());
        result = prime * result + ((minValue == null) ? 0 : minValue.hashCode());
        result = prime * result + ((step == null) ? 0 : step.hashCode());
        result = prime * result + ((unit == null) ? 0 : unit.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DecimalConstraints other = (DecimalConstraints) obj;
        if (maxValue == null) {
            if (other.maxValue != null)
                return false;
        } else if (!maxValue.equals(other.maxValue))
            return false;
        if (minValue == null) {
            if (other.minValue != null)
                return false;
        } else if (!minValue.equals(other.minValue))
            return false;
        if (step == null) {
            if (other.step != null)
                return false;
        } else if (!step.equals(other.step))
            return false;
        if (unit != other.unit)
            return false;
        return true;
    }

}
