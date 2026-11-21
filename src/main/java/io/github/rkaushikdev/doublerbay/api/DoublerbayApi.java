package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.Doublerbay;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.logging.Logs;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * The service over HTTP, built with its dependencies handed in rather than imported.
 *
 * <p>There is one factory, {@link #application}, and the service never builds a database, a book or
 * a catalogue of its own: whoever starts it hands them in as {@link Deps}, which become the beans
 * every router takes. The key ring is the one handed in, or the development ring with one key of
 * each role when none was.
 *
 * <p>Spring Boot's own data source, JPA and Flyway set up are switched off, because with H2 on the
 * class path and no data source of its own Boot would otherwise build an embedded database and
 * migrate that. Boot is also told to leave the logging alone, so the one logger this package writes
 * its refusals to stays configured the way {@link Logs#configure()} left it.
 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
public final class DoublerbayApi {

    /** The service's name, on the health route and the contract. */
    public static final String TITLE = Doublerbay.NAME;

    /** The release the contract describes. */
    public static final String VERSION = Doublerbay.VERSION;

    private DoublerbayApi() {}

    /** The application over these dependencies, configured and not yet started. */
    public static SpringApplicationBuilder application(Deps deps) {
        return application(deps, List.of());
    }

    /**
     * The application over these dependencies with more routes beside its own, configured and not
     * yet started.
     */
    public static SpringApplicationBuilder application(Deps deps, List<Class<?>> extra) {
        if (System.getProperty(LoggingSystem.SYSTEM_PROPERTY) == null) {
            System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);
        }
        Logs.configure();
        KeyRing keys = deps.keys().orElseGet(Security::openRing);
        List<Class<?>> sources = new ArrayList<>();
        sources.add(DoublerbayApi.class);
        sources.add(ApiWeb.class);
        sources.add(Refusals.class);
        sources.add(BodyKept.class);
        sources.addAll(Routers.ALL);
        sources.addAll(extra);
        return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new)).initializers(context -> {
            context.getBeanFactory().registerSingleton("deps", deps);
            context.getBeanFactory().registerSingleton("keyRing", keys);
        });
    }

    /** Build and start the application, serving on whatever port {@code args} name. */
    public static ConfigurableApplicationContext buildApp(Deps deps, String... args) {
        return application(deps).run(args);
    }

    /** The application as it runs, with the V320 book on the shelf, answering as of the real day. */
    public static ConfigurableApplicationContext defaultApp(Database database, String... args) {
        return buildApp(new Deps(database, Book.v320(), new TypeCatalogue()), args);
    }

    /** The application as it runs, with the V320 book on the shelf, answering as of a pinned day. */
    public static ConfigurableApplicationContext defaultApp(Database database, LocalDate today, String... args) {
        return buildApp(new Deps(database, Book.v320(), new TypeCatalogue(), today), args);
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer wire() {
        return Wire::configure;
    }

    /** The contract names every field the way the wire spells it, because it reads them off the same mapper. */
    @Bean
    ModelResolver contractNames(ObjectMapper mapper) {
        return new ModelResolver(mapper);
    }

    @Bean
    OpenAPI contract() {
        return new OpenAPI().info(new Info().title(TITLE).version(VERSION));
    }
}
