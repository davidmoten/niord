/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.domain.DomainService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.core.user.vo.GroupVo;
import org.niord.core.user.vo.UserVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST interface for accessing users.
 */
@Path("/users")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class UserRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    UserService userService;

    @Inject
    DomainService domainService;

    /** Returns all users that matches the given name */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "editor" })
    @GZIP
    @NoCache
    public List<UserVo> search(@QueryParam("name") String name) {
        return userService.searchUsers(name).stream()
                .map(User::toVo)
                .collect(Collectors.toList());
    }


    /** Returns all users that matches the given name */
    @GET
    @Path("/groups")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public List<GroupVo> groups() {
        return userService.getGroups();
    }


    /** Returns the domain roles assigned to the given group */
    @GET
    @Path("/group/{groupId}/roles")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public List<String> roles(@PathParam("groupId") String groupId) {
        return userService.getGroupRoles(domainService.currentDomain(), groupId);
    }


}
