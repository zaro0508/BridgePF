package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AppConfigValidator implements Validator {

    private boolean isNew;
    private Set<String> dataGroups;
    
    public AppConfigValidator(Set<String> dataGroups, boolean isNew) {
        this.dataGroups = dataGroups;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AppConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AppConfig appConfig = (AppConfig)object;
        
        if (!isNew && isBlank(appConfig.getGuid())) {
            errors.rejectValue("guid", "is required");
        }
        if (isBlank(appConfig.getLabel())) {
            errors.rejectValue("label", "is required");
        }
        if (isBlank(appConfig.getStudyId())) {
            errors.rejectValue("studyId", "is required");
        }
        if (appConfig.getCriteria() == null) {
            errors.rejectValue("criteria", "are required");
        } else {
            CriteriaUtils.validate(appConfig.getCriteria(), dataGroups, errors);    
        }
        if (appConfig.getSchemaReferences() != null) {
            for (int i=0; i < appConfig.getSchemaReferences().size(); i++) {
                SchemaReference ref = appConfig.getSchemaReferences().get(i);
                if (ref.getRevision() == null) {
                    errors.pushNestedPath("schemaReferences["+i+"]");
                    errors.rejectValue("revision", "is required");
                    errors.popNestedPath();
                }
            }
        }
        if (appConfig.getSurveyReferences() != null) {
            for (int i=0; i < appConfig.getSurveyReferences().size(); i++) {
                SurveyReference ref = appConfig.getSurveyReferences().get(i);
                if (ref.getCreatedOn() == null) {
                    errors.pushNestedPath("surveyReferences["+i+"]");
                    errors.rejectValue("createdOn", "is required");
                    errors.popNestedPath();
                }
            }
        }
    }
}
