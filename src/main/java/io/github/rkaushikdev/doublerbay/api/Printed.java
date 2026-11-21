package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A value read off a key file, put into words.
 *
 * <p>A key, a holder or a role written as something other than text is read as its written form,
 * and a refusal names what kind of value an entry was. The written form is: a whole number as its
 * digits; a fraction to the fewest digits that read back as the same value, with an exponent from
 * sixteen digits up or below a ten thousandth ({@code 1.5}, {@code 100.0}, {@code 1e+16},
 * {@code 1.5e-05}, {@code inf}, {@code nan}); true and false as {@code True} and {@code False}; null
 * as {@code null}; text quoted as a refusal quotes it; a list in square brackets and an object in
 * braces, the items separated by a comma and a space and each member written {@code 'name': value}.
 */
final class Printed {

    private Printed() {}

    /** Text as it stands, anything else in its written form. */
    static String of(JsonNode value) {
        return value.isTextual() ? value.textValue() : written(value);
    }

    /** The written form of any value, text quoted. */
    static String written(JsonNode value) {
        if (value.isTextual()) {
            return Literal.of(value.textValue());
        }
        if (value.isNull()) {
            return "null";
        }
        if (value.isBoolean()) {
            return value.booleanValue() ? "True" : "False";
        }
        if (value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        if (value.isNumber()) {
            return fraction(value.doubleValue());
        }
        List<String> items = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> items.add(written(item)));
            return "[" + String.join(", ", items) + "]";
        }
        for (Map.Entry<String, JsonNode> member : value.properties()) {
            items.add(Literal.of(member.getKey()) + ": " + written(member.getValue()));
        }
        return "{" + String.join(", ", items) + "}";
    }

    /** The name a refusal gives the kind of value an entry is. */
    static String kind(JsonNode value) {
        if (value.isObject()) {
            return "dict";
        }
        if (value.isArray()) {
            return "list";
        }
        if (value.isTextual()) {
            return "str";
        }
        if (value.isBoolean()) {
            return "bool";
        }
        if (value.isIntegralNumber()) {
            return "int";
        }
        if (value.isNumber()) {
            return "float";
        }
        return "null";
    }

    /** A fraction to the fewest digits that read back as the same value. */
    static String fraction(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0.0) {
            return Double.doubleToRawLongBits(value) == 0L ? "0.0" : "-0.0";
        }
        String sign = value < 0 ? "-" : "";
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = shortest.unscaledValue().toString();
        int exponent = digits.length() - 1 - shortest.scale();
        if (exponent < -4 || exponent >= 16) {
            String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
            return sign + mantissa + "e" + (exponent < 0 ? "-" : "+")
                    + String.format(Locale.ROOT, "%02d", Math.abs(exponent));
        }
        String plain = shortest.toPlainString();
        return sign + (plain.contains(".") ? plain : plain + ".0");
    }
}
