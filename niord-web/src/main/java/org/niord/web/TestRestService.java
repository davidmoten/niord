package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.domain.KeycloakIntegrationService;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.FeatureService;
import org.niord.model.vo.geojson.FeatureCollectionVo;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test.
 */
@Path("/test")
@Singleton
@Startup
@SecurityDomain("keycloak")
@PermitAll
public class TestRestService {

    @Inject
    FeatureService featureService;

    @Inject
    KeycloakIntegrationService keycloakIntegrationService;

    @GET
    @Path("/shapes")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<FeatureCollectionVo> getAllFeatureCollections() throws Exception {
        return featureService.loadAllFeatureCollections().stream()
                .map(FeatureCollection::toGeoJson)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/shapes")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FeatureCollectionVo createFeatureCollection(FeatureCollectionVo fc) throws Exception {
        return featureService.createFeatureCollection(FeatureCollection.fromGeoJson(fc))
                .toGeoJson();
    }

    @PUT
    @Path("/shapes")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FeatureCollectionVo updateFeatureCollection(FeatureCollectionVo fc) throws Exception {
        return featureService.updateFeatureCollection(FeatureCollection.fromGeoJson(fc))
                .toGeoJson();
    }

    @GET
    @Path("/xxx")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public String test() {

        try {
            System.out.println("PUBLIC KEY " + keycloakIntegrationService.resolveKeycloakPublicKey());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return System.currentTimeMillis() + "";
    }

}
