package org.sagebionetworks.bridge.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeConfig implements Config {
    private static final Logger logger = LoggerFactory.getLogger(BridgeConfig.class);


    private static final String CONFIG_FILE = "bridge-server.conf";
    private static final String TEMPLATE_CONFIG = CONFIG_FILE;
    private static final String LOCAL_CONFIG = System.getProperty("user.home") + "/" + ".bridge" + "/" + CONFIG_FILE;

    private static final String CONSENTS_BUCKET = "consents.bucket";

    // Property for a token that is checked before user is unsubscribed from further emails
    private static final String EMAIL_UNSUBSCRIBE_TOKEN = "email.unsubscribe.token";

    private static final String HEALTHCODE_KEY = "bridge.healthcode.key";

    private static final String HOST_POSTFIX = "host.postfix";

    private static final String WEBSERVICES_URL = "webservices.url";

    private static final String EXPORTER_SYNAPSE_ID = "exporter.synapse.id";
    private static final String TEST_SYNAPSE_USER_ID = "test.synapse.user.id";

    private final Config config;

    BridgeConfig() {
        final ClassLoader classLoader = BridgeConfig.class.getClassLoader();

        final Path templateConfig;
        try {
            // URL -> URI -> Path is safer, and Windows doesn't work without it
            // see http://stackoverflow.com/questions/6164448/convert-url-to-normal-windows-filename-java
            templateConfig =
                    Paths.get(classLoader.getResource(TEMPLATE_CONFIG).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error loading config from classpath resource: " + CONFIG_FILE, e);
        }

        final Path localConfig = Paths.get(LOCAL_CONFIG);
        try {
            config = Files.exists(localConfig) ? new PropertiesConfig(templateConfig, localConfig)
                    : new PropertiesConfig(templateConfig);
        } catch (IOException e) {
            throw new RuntimeException("Error loading config from local file: " + localConfig, e);
        }
    }

    @Override
    public String getUser() {
        return config.getUser();
    }

    @Override
    public Environment getEnvironment() {
        return config.getEnvironment();
    }

    @Override
    public String get(String key) {
        return config.get(key);
    }

    @Override
    public int getInt(String key) {
        return config.getInt(key);
    }

    @Override
    public List<String> getList(String key) {
        return config.getList(key);
    }

    public boolean isLocal() {
        return Environment.LOCAL.equals(getEnvironment());
    }

    public boolean isDevelopment() {
        return Environment.DEV.equals(getEnvironment());
    }

    public boolean isProduction() {
        return Environment.PROD.equals(getEnvironment());
    }

    public String getEmailUnsubscribeToken() {
        return getProperty(EMAIL_UNSUBSCRIBE_TOKEN);
    }

    public String getProperty(String name) {
        return config.get(name);
    }

    public int getPropertyAsInt(String name) {
        return config.getInt(name);
    }

    public List<String> getPropertyAsList(String name) {
        return config.getList(name);
    }

    public String getHealthCodeKey() {
        return config.get(HEALTHCODE_KEY);
    }

    public String getConsentsBucket() {
        logger.info("getting key=" + "consents.bucket" + " got value=" + config.get("consents.bucket"));
        logger.info("getting key=" + "consents.bucket".toUpperCase() + " got value=" + config.get("consents.bucket".toUpperCase()));
        logger.info("getting key=" + "dev.consents.bucket" + " got value=" + config.get("dev.consents.bucket"));
        logger.info("getting key=" + "dev.consents.bucket".toUpperCase() + " got value=" + config.get("dev.consents.bucket".toUpperCase()));
        logger.info("getting key=" + "CONSENTS_BUCKET" + " got value=" + config.get("CONSENTS_BUCKET"));
        logger.info("getting key=" + "CONSENTS_BUCKET".toLowerCase() + " got value=" + config.get("CONSENTS_BUCKET".toLowerCase()));
        logger.info("getting key=" + "DEV_CONSENTS_BUCKET".toLowerCase() + " got value=" + config.get("DEV_CONSENTS_BUCKET".toLowerCase()));
        logger.info("getting key=" + "DEV_CONSENTS_BUCKET" + " got value=" + config.get("DEV_CONSENTS_BUCKET"));
        return config.get(CONSENTS_BUCKET);
    }

    public String getWebservicesURL() {
        return config.get(WEBSERVICES_URL);
    }

    public String getHostnameWithPostfix(String identifier) {
        checkNotNull(identifier);
        return identifier + config.get(HOST_POSTFIX);
    }

    public String getExporterSynapseId() {
        return config.get(EXPORTER_SYNAPSE_ID);
    }

    public String getTestSynapseUserId() {
        return config.get(TEST_SYNAPSE_USER_ID);
    }
}
