package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;

/** Validator for CompoundActivityDefinition. */
public class CompoundActivityDefinitionValidator implements Validator {
    private final Set<String> taskIdSet;

    /** Constructor for validator. Takes set of valid task IDs from the study. */
    public CompoundActivityDefinitionValidator(Set<String> taskIdSet) {
        this.taskIdSet = taskIdSet;
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return CompoundActivityDefinition.class.isAssignableFrom(clazz);
    }

    /**
     * <p>
     * Compound activity definition must have a task ID that's in the study's task ID list, and it must have at least
     * one schema or at least one survey.
     * </p>
     * <p>
     * Study ID need not be specified, as the DAO will fill this in before saving it to the back-end store.
     * </p>
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("compoundActivityDefinition", "cannot be null");
        } else if (!(target instanceof CompoundActivityDefinition)) {
            errors.rejectValue("compoundActivityDefinition", "is the wrong type");
        } else {
            CompoundActivityDefinition compoundActivityDef = (CompoundActivityDefinition) target;

            // taskIdentifier must be specified and must be in the Study's list
            String taskId = compoundActivityDef.getTaskId();
            if (isBlank(taskId)) {
                errors.rejectValue("taskId", "must be specified");
            } else if (!taskIdSet.contains(taskId)) {
                errors.rejectValue("taskId", taskId + " not in enumeration: " +
                        BridgeUtils.COMMA_SPACE_JOINER.join(taskIdSet));
            }

            // must have at least one item in surveys and/or schemas
            if (BridgeUtils.isEmpty(compoundActivityDef.getSchemaList()) &&
                    BridgeUtils.isEmpty(compoundActivityDef.getSurveyList())) {
                errors.rejectValue("compoundActivityDefinition",
                        "must have at least one schema or at least one survey");
            }
        }
    }
}
