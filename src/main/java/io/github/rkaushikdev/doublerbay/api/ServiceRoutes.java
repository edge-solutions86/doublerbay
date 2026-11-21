package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The service talking about itself: whether it is up and what it is reading. */
@RestController
@Tag(name = "service")
final class ServiceRoutes {

    private final Deps deps;

    ServiceRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/health")
    @Operation(summary = "Whether the service is up and what it is reading.")
    Map<String, String> health() {
        Map<String, String> answer = new LinkedHashMap<>();
        answer.put("service", DoublerbayApi.TITLE);
        answer.put("version", DoublerbayApi.VERSION);
        answer.put(
                "chapters",
                String.join(
                        ",",
                        deps.book().chapters().stream()
                                .map(Chapter::toString)
                                .sorted()
                                .toList()));
        return answer;
    }
}
