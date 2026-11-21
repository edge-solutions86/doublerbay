package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * How the service reads and writes JSON, in one place for the application and the tests.
 *
 * <p>Field names are snake case, one word per capital, and days are ISO dates. A field the request shapes do not know is
 * ignored rather than refused. A text field takes text and nothing else, a whole number takes a
 * whole number or the digits of one, and a fraction sent for a whole number is refused rather than
 * cut short. A field sent as null reads as one left out.
 */
public final class Wire {

    /** Every property named the way the wire spells it. */
    private static final PropertyNamingStrategies.NamingBase NAMES = new PropertyNamingStrategies.NamingBase() {
        @Override
        public String translate(String name) {
            return snake(name);
        }
    };

    private Wire() {}

    /**
     * A Java name the way the wire spells it: every capital starts a new lower case word, so {@code
     * cyclesADay} is {@code cycles_a_day}.
     */
    public static String snake(String name) {
        StringBuilder written = new StringBuilder(name.length() + 4);
        for (int index = 0; index < name.length(); index++) {
            char at = name.charAt(index);
            if (Character.isUpperCase(at)) {
                if (index > 0) {
                    written.append('_');
                }
                written.append(Character.toLowerCase(at));
            } else {
                written.append(at);
            }
        }
        return written.toString();
    }

    /** Apply the service's JSON settings to a mapper being built. */
    public static void configure(Jackson2ObjectMapperBuilder builder) {
        builder.propertyNamingStrategy(NAMES)
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .postConfigurer(Wire::refuseLooseValues);
    }

    /** A mapper configured the way the service's own is. */
    public static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        configure(builder);
        return builder.build();
    }

    private static void refuseLooseValues(ObjectMapper mapper) {
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    }
}
