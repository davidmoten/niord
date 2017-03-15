/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.promulgation;

import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.PromulgationServiceVo;
import org.niord.core.util.CdiUtils;
import org.slf4j.Logger;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the list of PromulgationServices, such as MailingListPromulgationService, NavtexPromulgationService, etc.
 * <p>
 * The PromulgationManager facilitates a plug-in architecture where all promulgation services must register
 * in a @PostConstruct method.
 * <p>
 * Each promulgation service is associated with a list of {@linkplain PromulgationType} entities.
 * As an example, there may be "NAVTEX-DK" and "NAVTEX-GL" types managed by NAVTEX promulgation service.
 * <p>
 * In turn, messages are associated with a list of {@linkplain BaseMessagePromulgation}-derived entities that
 * are each tied to a promulgation type.
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class PromulgationManager {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;

    @Inject
    PromulgationTypeService promulgationTypeService;

    Map<String, Class<? extends BasePromulgationService>> services = new ConcurrentHashMap<>();


    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/


    /**
     * Registers the promulgation service
     * @param service the promulgation service to register
     */
    public void registerPromulgationService(BasePromulgationService service) {
        services.put(service.getServiceId(), service.getClass());
        log.info("Registered promulgation service " + service.getServiceId());
    }


    /**
     * Returns the registered set of promulgation services
     * @return the registered set of promulgation services
     */
    public List<PromulgationServiceVo> promulgationServices() {
        return services.entrySet().stream()
                .map(s -> {
                    BasePromulgationService service = instantiatePromulgationService(s.getValue());
                    String serviceName = service == null ? s.getKey() : service.getServiceName();
                    return new PromulgationServiceVo(s.getKey(), serviceName);
                })
                .collect(Collectors.toList());
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /**
     * Updates and adds promulgations to the message value object
     * @param message the message value object
     */
    public void onLoadSystemMessage(SystemMessageVo message) throws PromulgationException {

        // The set of promulgation types to consider is the union between the currently active
        // services for the current domain and the promulgations already defined for the message.
        Map<String, PromulgationType> types = new HashMap<>();
        promulgationTypeService.getActivePromulgationTypes().forEach(pt -> types.put(pt.getTypeId(), pt));
        promulgationTypeService.getPromulgationTypes(message).forEach(pt -> types.put(pt.getTypeId(), pt));

        // Let the associated services process the message
        for (PromulgationType type : types.values()) {
            BasePromulgationService service = instantiatePromulgationService(type.getServiceId());
            if (service != null) {
                service.onLoadSystemMessage(message, type);
            }
        }

        // Sort the promulgations according to priority
        if (message.getPromulgations() != null) {
            message.getPromulgations()
                    .sort(Comparator.comparingInt(p -> p.getType().getPriority()));
        }
    }


    /**
     * Prior to creating a new message, let the registered promulgation services check up on promulgations.
     * @param message the message about to be created
     */
    public void onCreateMessage(Message message) throws PromulgationException {
        for (PromulgationType type : persistedPromulgationTypes(message)) {
            BasePromulgationService service = instantiatePromulgationService(type.getServiceId());
            if (service != null) {
                service.onCreateMessage(message, type);
            } else {
                log.warn("Unable to instantiate promulgation service for type " + type.getTypeId());
            }
        }
    }


    /**
     * Prior to updating an existing message, let the registered promulgation services check up on promulgations.
     * @param message the message about to be updated
     */
    public void onUpdateMessage(Message message) throws PromulgationException {
        for (PromulgationType type : persistedPromulgationTypes(message)) {
            BasePromulgationService service = instantiatePromulgationService(type.getServiceId());
            if (service != null) {
                service.onUpdateMessage(message, type);
            } else {
                log.warn("Unable to instantiate promulgation service for type " + type.getTypeId());
            }
        }
    }


    /**
     * Prior to changing status of an existing message, let the registered promulgation services check up on promulgations.
     * @param message the message about to be updated
     */
    public void onUpdateMessageStatus(Message message) throws PromulgationException {
        for (PromulgationType type : persistedPromulgationTypes(message)) {
            BasePromulgationService service = instantiatePromulgationService(type.getServiceId());
            if (service != null) {
                service.onUpdateMessageStatus(message, type);
            } else {
                log.warn("Unable to instantiate promulgation service for type " + type.getTypeId());
            }
        }
    }


    /**
     * Generates a message promulgation record for the given type and message
     * @param typeId the type of promulgation to generate
     * @param message the message template to generate a promulgation for
     * @return the promulgation
     */
    public BaseMessagePromulgationVo generateMessagePromulgation(String typeId, SystemMessageVo message) throws PromulgationException {
        PromulgationType type = promulgationTypeService.getPromulgationType(typeId);
        return instantiatePromulgationService(type.getServiceId()).generateMessagePromulgation(message, type);
    }


    /**
     * Resets the message promulgations
     * @param message the message template to reset promulgation for
     */
    public void resetMessagePromulgations(SystemMessageVo message) throws PromulgationException {
        if (message.getPromulgations() != null) {
            for (BaseMessagePromulgationVo promulgation : message.getPromulgations()) {
                PromulgationType type = promulgationTypeService.getPromulgationType(promulgation.getType().getTypeId());
                instantiatePromulgationService(type.getServiceId()).resetMessagePromulgation(message, type);

            }
        }
    }


    /**
     * Updates all referenced promulgation types with the persisted versions and returns the set of types
     * @param message the message to update
     * @return the associated promulgation types
     */
    private Set<PromulgationType> persistedPromulgationTypes(Message message) {
        Set<PromulgationType> types = new HashSet<>();
        message.getPromulgations()
                .forEach(p -> {
                    p.setType(promulgationTypeService.getPromulgationType(p.getType().getTypeId()));
                    types.add(p.getType());
                });
        return types;
    }

    /***************************************/
    /** Utility function                  **/
    /***************************************/


    /**
     * Instantiates the promulgation service bean for the promulgation type.
     * Returns null if the class could not be instantiated
     * @param type the promulgation service type
     */
    private BasePromulgationService instantiatePromulgationService(String type) {
        return instantiatePromulgationService(services.get(type));
    }


    /**
     * Instantiates the promulgation service bean for the given class.
     * Returns null if the class could not be instantiated
     * @param promulgationServiceClass the promulgation service class
     */
    private <T extends BasePromulgationService> T instantiatePromulgationService(Class<T> promulgationServiceClass) {
        try {
            return CdiUtils.getBean(promulgationServiceClass);
        } catch (NamingException e) {
            log.warn("Could not instantiate promulgation service for class " + promulgationServiceClass);
            return null;
        }
    }

}
