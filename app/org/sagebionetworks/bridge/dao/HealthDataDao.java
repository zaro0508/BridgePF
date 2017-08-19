package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;
import java.util.List;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

/** DAO for health data records. */
public interface HealthDataDao {
    /**
     * DAO method used by worker apps to creating (or updating) a health data record and persisting it, generally from
     * unpacking uploads.
     *
     * @param record
     *         health data record prototype, from which the record should be created from, must be non-null
     * @return the unique ID of the created record
     */
    String createOrUpdateRecord(@Nonnull HealthDataRecord record);

    /**
     * DAO method user by admin to delete all health data records for a health code (user in study). This is generally
     * used through the user admin service, when the admin deletes a user.
     *
     * @param healthCode
     *         health code of the health data records to exist, keyed to a particular user in a study
     * @return number of records deleted
     */
    int deleteRecordsForHealthCode(@Nonnull String healthCode);

    /**
     * DAO method used by worker apps to fetch a health data record by the record ID.
     *
     * @param id
     *         record ID
     * @return health data record
     */
    HealthDataRecord getRecordById(@Nonnull String id);

    /**
     * DAO method used by worker apps to query all health data records uploaded for a specific date, generally used for
     * export.
     *
     * @param uploadDate
     *         upload date in YYYY-MM-DD format, must be non-null, non-empty, and must represent a valid date
     * @return list of all health records uploaded on that date
     */
    List<HealthDataRecord> getRecordsForUploadDate(@Nonnull String uploadDate);

    /**
     * Get a list of records with the same healthCode and schemaId that are within an hour of the createdOn. For
     * performance reasons, this caps the number of results returned to 10.
     *
     * @param healthCode
     *      healthCode in String format
     * @param createdOn
     *      createdOn in Long format -- same as in ddb
     * @param schemaId
     *      schemaId in String format
     * @return list of all health records matching criterion
     */
    List<HealthDataRecord> getRecordsByHealthCodeCreatedOnSchemaId(@Nonnull String healthCode, @Nonnull Long createdOn, @Nonnull String schemaId);
}
