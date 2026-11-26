package com.smartparking.config;

import com.smartparking.service.BillingService;
import com.smartparking.service.RegularBillingService;
import com.smartparking.service.WeekendBillingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Locale;

@Configuration
@ComponentScan(basePackages = "com.smartparking")
@PropertySource("classpath:application.properties")
public class AppConfig {

    @Bean
    public DataSource dataSource(Environment env) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(env.getRequiredProperty("db.driver"));
        ds.setUrl(env.getRequiredProperty("db.url"));
        ds.setUsername(env.getRequiredProperty("db.username"));
        ds.setPassword(env.getRequiredProperty("db.password"));
        return ds;
    }

    /**
     * Picks the active billing policy from application.properties (billing.policy).
     * ParkingService asks for this bean with @Qualifier("billingService"), so changing
     * the property and restarting switches the billing behaviour without touching code.
     */
    @Bean(name = "billingService")
    public BillingService billingService(Environment env,
                                         RegularBillingService regular,
                                         WeekendBillingService weekend) {
        String policy = env.getProperty("billing.policy", "regular").trim().toLowerCase(Locale.ROOT);
        switch (policy) {
            case "regular":
                return regular;
            case "weekend":
                return weekend;
            default:
                throw new IllegalStateException("Unknown billing.policy '" + policy + "' (use regular or weekend)");
        }
    }
}
