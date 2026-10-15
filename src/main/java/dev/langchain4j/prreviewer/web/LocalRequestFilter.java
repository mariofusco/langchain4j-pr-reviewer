package dev.langchain4j.prreviewer.web;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * The web interface serves this machine only. Besides binding to 127.0.0.1, it refuses requests whose Host is not a
 * local name (DNS rebinding) and requests coming from another origin, so that no web page visited in the browser can
 * start reviews, which cost money, or read reports.
 */
public class LocalRequestFilter {

    static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @ServerRequestFilter(preMatching = true)
    public Optional<Response> filter(ContainerRequestContext request) {
        String host = request.getHeaderString("Host");
        if (host == null || !LOCAL_HOSTS.contains(hostName(host))) {
            return Optional.of(Response.status(Response.Status.FORBIDDEN).entity("Local requests only").build());
        }
        String origin = request.getHeaderString("Origin");
        if (origin != null && !LOCAL_HOSTS.contains(originHost(origin))) {
            return Optional.of(Response.status(Response.Status.FORBIDDEN).entity("Cross-origin requests refused").build());
        }
        return Optional.empty();
    }

    static String hostName(String hostHeader) {
        String host = hostHeader.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end < 0 ? host : host.substring(0, end + 1);
        }
        int colon = host.lastIndexOf(':');
        return colon < 0 ? host : host.substring(0, colon);
    }

    private static String originHost(String origin) {
        try {
            String host = URI.create(origin).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
