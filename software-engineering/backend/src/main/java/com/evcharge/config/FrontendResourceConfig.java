package com.evcharge.config;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FrontendResourceConfig implements WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(FrontendResourceConfig.class);

    private final String overridePath;

    public FrontendResourceConfig(@Value("${evcharge.frontend.path:}") String overridePath) {
        this.overridePath = overridePath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path frontendPath = resolveFrontendPath();
        log.info("Serving frontend assets from {}", frontendPath);
        registry.addResourceHandler("/**")
                .addResourceLocations(frontendPath.toUri().toString())
                .setCachePeriod(0);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/5_admin_queue.html");
    }

    /**
     * Resolve the frontend directory regardless of where the jar is started from.
     * Search order:
     *  1. `${evcharge.frontend.path}` / `EVCHARGE_FRONTEND_PATH` env override (absolute path)
     *  2. `./frontend` relative to current working directory
     *  3. `../frontend` relative to current working directory (handles `cd backend && java -jar`)
     *  4. `<jar-location>/frontend` (next to the running jar)
     *  5. `<jar-location>/../frontend` (when jar is in `backend/target/`)
     */
    private Path resolveFrontendPath() {
        if (overridePath != null && !overridePath.isBlank()) {
            Path explicit = Paths.get(overridePath).toAbsolutePath().normalize();
            if (Files.isDirectory(explicit)) return explicit;
            log.warn("evcharge.frontend.path={} is not a directory, falling back to defaults", explicit);
        }
        Path[] candidates = new Path[] {
                Paths.get("frontend").toAbsolutePath().normalize(),
                Paths.get("..", "frontend").toAbsolutePath().normalize(),
                jarRelative("frontend"),
                jarRelative("..", "frontend"),
                jarRelative("..", "..", "frontend")
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        // Fall back to the first candidate; the resource handler will simply 404 if missing.
        return candidates[0];
    }

    /** Resolve a path relative to the directory containing the running .jar (or class path). */
    private Path jarRelative(String... parts) {
        try {
            URL location = FrontendResourceConfig.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;
            String decoded = URLDecoder.decode(location.getPath(), StandardCharsets.UTF_8);
            // Spring Boot fat jar paths look like file:/foo/bar.jar!/BOOT-INF/classes!/
            int bang = decoded.indexOf('!');
            if (bang >= 0) decoded = decoded.substring(0, bang);
            Path jarPath = Paths.get(decoded);
            if (Files.isRegularFile(jarPath)) jarPath = jarPath.getParent();
            if (jarPath == null) return null;
            Path resolved = jarPath;
            for (String part : parts) resolved = resolved.resolve(part);
            return resolved.toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            log.warn("Could not derive jar-relative frontend path: {}", e.toString());
            return null;
        }
    }
}
