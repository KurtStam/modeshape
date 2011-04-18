package org.modeshape.web.jcr.rest;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.modeshape.common.annotation.Immutable;
import org.modeshape.web.jcr.RepositoryFactory;
import org.modeshape.web.jcr.rest.model.RepositoryEntry;

/**
 * Resource handler that implements REST methods for servers.
 */
@Immutable
class ServerHandler extends AbstractHandler {

    /**
     * Returns the list of JCR repositories available on this server
     * 
     * @param request the servlet request; may not be null
     * @return the list of JCR repositories available on this server
     */
    public Map<String, RepositoryEntry> getRepositories( HttpServletRequest request ) {
        assert request != null;

        Map<String, RepositoryEntry> repositories = new HashMap<String, RepositoryEntry>();
        String uri = request.getRequestURI();

        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        for (String name : RepositoryFactory.getJcrRepositoryNames()) {
            if (name.trim().length() == 0) {
                name = EMPTY_REPOSITORY_NAME;
            }
            name = URL_ENCODER.encode(name);
            repositories.put(name, new RepositoryEntry(uri, name));
        }

        return repositories;
    }

}
