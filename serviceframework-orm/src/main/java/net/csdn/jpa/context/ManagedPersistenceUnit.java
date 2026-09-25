package net.csdn.jpa.context;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * In-memory persistence unit. It does not write persistence.xml onto a classpath directory.
 */
public final class ManagedPersistenceUnit implements PersistenceUnitInfo {

    private final String name;
    private final List<String> managedClassNames;
    private final Properties properties;
    private final ClassLoader classLoader;
    private final URL root;

    public ManagedPersistenceUnit(String name, List<String> managedClassNames, Properties properties, ClassLoader classLoader, URL root) {
        this.name = name;
        this.managedClassNames = new ArrayList<String>(managedClassNames);
        this.properties = properties;
        this.classLoader = classLoader;
        this.root = root;
    }

    @Override
    public String getPersistenceUnitName() {
        return name;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return "org.hibernate.jpa.HibernatePersistenceProvider";
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return PersistenceUnitTransactionType.RESOURCE_LOCAL;
    }

    @Override
    public DataSource getJtaDataSource() {
        return null;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return null;
    }

    @Override
    public List<String> getMappingFileNames() {
        return Collections.emptyList();
    }

    @Override
    public List<URL> getJarFileUrls() {
        return Collections.emptyList();
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return root;
    }

    @Override
    public List<String> getManagedClassNames() {
        return Collections.unmodifiableList(managedClassNames);
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return true;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return SharedCacheMode.UNSPECIFIED;
    }

    @Override
    public ValidationMode getValidationMode() {
        return ValidationMode.NONE;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return "2.1";
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void addTransformer(ClassTransformer transformer) {
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return null;
    }
}
