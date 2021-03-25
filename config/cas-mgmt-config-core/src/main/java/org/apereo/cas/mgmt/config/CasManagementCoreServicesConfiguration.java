package org.apereo.cas.mgmt.config;

import org.apereo.cas.authentication.attribute.DefaultAttributeDefinitionStore;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.CasManagementConfigurationProperties;
import org.apereo.cas.configuration.support.Beans;
import org.apereo.cas.mgmt.ContactLookup;
import org.apereo.cas.mgmt.MgmtManagerFactory;
import org.apereo.cas.mgmt.NoOpContactLookup;
import org.apereo.cas.mgmt.authentication.CasUserProfileFactory;
import org.apereo.cas.mgmt.controller.ApplicationDataController;
import org.apereo.cas.mgmt.controller.AttributesController;
import org.apereo.cas.mgmt.controller.ContactLookupController;
import org.apereo.cas.mgmt.controller.DomainController;
import org.apereo.cas.mgmt.controller.ForwardingController;
import org.apereo.cas.mgmt.controller.ServiceController;
import org.apereo.cas.mgmt.factory.FormDataFactory;
import org.apereo.cas.mgmt.factory.ServicesManagerFactory;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServiceRegistry;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.ServicesManagerConfigurationContext;
import org.apereo.cas.services.ServicesManagerRegisteredServiceLocator;
import org.apereo.cas.services.domain.DefaultDomainAwareServicesManager;
import org.apereo.cas.services.domain.DefaultRegisteredServiceDomainExtractor;
import org.apereo.cas.services.resource.DefaultRegisteredServiceResourceNamingStrategy;
import org.apereo.cas.services.resource.RegisteredServiceResourceNamingStrategy;
import org.apereo.cas.support.oauth.services.OAuth20ServicesManagerRegisteredServiceLocator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import org.apereo.services.persondir.IPersonAttributeDao;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

/**
 * Configuration class for core services.
 *
 * @author Travis Schmidt
 * @since 6.0
 */
@Configuration("casManagementCoreServicesConfiguration")
@EnableConfigurationProperties({CasConfigurationProperties.class, CasManagementConfigurationProperties.class})
@Slf4j
public class CasManagementCoreServicesConfiguration {

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    private CasManagementConfigurationProperties managementProperties;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    @Qualifier("namingStrategy")
    private ObjectProvider<RegisteredServiceResourceNamingStrategy> namingStrategy;


    @Autowired
    @Qualifier("casUserProfileFactory")
    private ObjectProvider<CasUserProfileFactory> casUserProfileFactory;

    @Autowired
    @Qualifier("serviceRegistry")
    private ObjectProvider<ServiceRegistry> serviceRegistry;

    @ConditionalOnMissingBean(name = "attributeDefinitionStore")
    @Bean
    @RefreshScope
    @SneakyThrows
    public DefaultAttributeDefinitionStore attributeDefinitionStore() {
        val resource = casProperties.getPersonDirectory().getAttributeDefinitionStore().getJson().getLocation();
        val store = new DefaultAttributeDefinitionStore(resource);
        store.setScope(casProperties.getServer().getScope());
        return store;
    }

    @Bean
    @ConditionalOnMissingBean(name = "managerFactory")
    public MgmtManagerFactory managerFactory() {
        return new ServicesManagerFactory(servicesManager(), namingStrategy());
    }

    @Bean
    public FormDataFactory formDataFactory() {
        return new FormDataFactory(casProperties, managementProperties, attributeDefinitionStore());
    }

    @ConditionalOnMissingBean(name = "attributeRepository")
    @Bean
    public IPersonAttributeDao attributeRepository() {
        return Beans.newStubAttributeRepository(casProperties.getAuthn().getAttributeRepository());
    }

    @ConditionalOnMissingBean(name = "namingStrategy")
    @Bean
    public RegisteredServiceResourceNamingStrategy namingStrategy() {
        return new DefaultRegisteredServiceResourceNamingStrategy();
    }

    @Bean
    public ApplicationDataController applicationDataController() {
        return new ApplicationDataController(formDataFactory(), casUserProfileFactory.getIfAvailable(),
                managementProperties, casProperties, contactLookup());
    }

    @Bean
    public ServiceController serviceController() {
        return new ServiceController(casUserProfileFactory.getIfAvailable(), managerFactory());
    }

    @Bean
    public DomainController domainController() {
        return new DomainController(casUserProfileFactory.getIfAvailable(), managerFactory());
    }

    @Bean
    public AttributesController attributesController() {
        return new AttributesController(casUserProfileFactory.getIfAvailable(), attributeDefinitionStore(), casProperties);
    }

    @ConditionalOnMissingBean(name ="contactLookup")
    @Bean
    public ContactLookup contactLookup() {
        return new NoOpContactLookup();
    }

    @Bean
    public ContactLookupController contactLookupController() {
        return new ContactLookupController(contactLookup(), casUserProfileFactory.getIfAvailable());
    }

    @RefreshScope
    @Bean
    @ConditionalOnMissingBean(name = "servicesManagerCache")
    public Cache<Long, RegisteredService> servicesManagerCache() {
        val registry = casProperties.getServiceRegistry();
        val duration = Beans.newDuration(registry.getCache());
        return Caffeine.newBuilder()
                .initialCapacity(registry.getCacheCapacity())
                .maximumSize(registry.getCacheSize())
                .expireAfterWrite(duration)
                .recordStats()
                .build();
    }

    @Bean(name = "servicesManager")
    @RefreshScope
    public ServicesManager servicesManager() {
        val activeProfiles = new HashSet<String>();
        val context = ServicesManagerConfigurationContext.builder()
                .serviceRegistry(serviceRegistry.getIfAvailable())
                .applicationContext(applicationContext)
                .environments(activeProfiles)
                .servicesCache(servicesManagerCache())
                .build();
        val cm = new DefaultDomainAwareServicesManager(context, new DefaultRegisteredServiceDomainExtractor());
        cm.load();
        return cm;
    }

    @Bean(name = "forwarding")
    @RefreshScope
    public ForwardingController forwarding() {
        return new ForwardingController();
    }

    @Bean
    @ConditionalOnMissingBean(name = "oauthServicesManagerRegisteredServiceLocator")
    public ServicesManagerRegisteredServiceLocator oauthServicesManagerRegisteredServiceLocator() {
        return new OAuth20ServicesManagerRegisteredServiceLocator();
    }
}
