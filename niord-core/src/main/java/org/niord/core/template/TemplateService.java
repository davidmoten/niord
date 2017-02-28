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

package org.niord.core.template;

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.fm.FmTemplateService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.service.BaseService;
import org.niord.core.template.FieldTemplateProcessor.FieldTemplate;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main interface for accessing and processing message templates
 */
@Stateless
@SuppressWarnings("unused")
public class TemplateService extends BaseService {

    @Inject
    CategoryService categoryService;

    @Inject
    DomainService domainService;

    @Inject
    FmTemplateService templateService;

    @Inject
    NiordApp app;

    @Inject
    Logger log;

    /**
     * Returns the template with the given ID, or null if not found
     * @param id the template id
     * @return the template with the given ID, or null if not found
     */
    public Template findById(Integer id) {
        return getByPrimaryKey(Template.class, id);
    }


    /**
     * Returns the paged set of templates matching the search criteria
     * @param params the search criteria
     * @return the paged search result
     */
    public PagedSearchResultVo<Template> searchTemplates(TemplateSearchParams params) {

        long t0 = System.currentTimeMillis();

        PagedSearchResultVo<Template> result = new PagedSearchResultVo<>();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // First compute the total number of matching mails
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Template> countTemplatesRoot = countQuery.from(Template.class);

        countQuery.select(cb.count(countTemplatesRoot))
                .where(buildQueryPredicates(cb, countQuery, countTemplatesRoot, params))
                .orderBy(cb.desc(countTemplatesRoot.get("created")));

        result.setTotal(em.createQuery(countQuery).getSingleResult());


        // Then, extract the current page of matches
        CriteriaQuery<Template> query = cb.createQuery(Template.class);
        Root<Template> templateRoot = query.from(Template.class);
        query.select(templateRoot)
                .where(buildQueryPredicates(cb, query, templateRoot, params))
                .orderBy(cb.desc(countTemplatesRoot.get("created")));

        List<Template> mails = em.createQuery(query)
                .setMaxResults(params.getMaxSize())
                .setFirstResult(params.getPage() * params.getMaxSize())
                .getResultList();
        result.setData(mails);
        result.updateSize();

        log.info("Search [" + params + "] returned " + result.getSize() + " of " + result.getTotal() + " in "
                + (System.currentTimeMillis() - t0) + " ms");

        return result;
    }


    /** Helper function that translates the search parameters into predicates */
    @SuppressWarnings("all")
    private <T> Predicate[] buildQueryPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<T> query,
            Root<Template> templateRoot,
            TemplateSearchParams params) {

        // Build the predicate
        CriteriaHelper<T> criteriaHelper = new CriteriaHelper<>(cb, query);

        // Match the name
        if (StringUtils.isNotBlank(params.getName())) {
            Join<Template, TemplateDesc> descs = templateRoot.join("descs", JoinType.LEFT);
            criteriaHelper.equals(descs.get("lang"), params.getLanguage());
            criteriaHelper.like(descs.get("name"), params.getName());
        }

        // Match category
        if (params.getCategory() != null) {
            Category category = categoryService.getCategoryDetails(params.getCategory());
            if (category != null) {
                Join<Template, Category> categories = templateRoot.join("category", JoinType.LEFT);
                criteriaHelper.add(cb.like(categories.get("lineage"), category.getLineage() + "%"));
            }
        }

        // Match the domain
        if (StringUtils.isNotBlank(params.getDomain())) {
            Domain domain = domainService.findByDomainId(params.getDomain());
            if (domain != null) {
                Join<Template, Domain> domains = templateRoot.join("domains", JoinType.LEFT);
                criteriaHelper.equals(domains.get("domainId"), params.getDomain());
            }
        }

        return criteriaHelper.where();
    }

    /**
     * Returns all templates
     * @return all templates
     */
    public List<Template> findAll() {
        return em.createNamedQuery("Template.findAll", Template.class)
                .getResultList();
    }


    /**
     * Creates a new template based on the template parameter
     * @param template the template to create
     * @return the created template
     */
    public Template createTemplate(Template template) {
        if (template.isPersisted()) {
            throw new IllegalArgumentException("Cannot create existing template " + template.getId());
        }

        // Replace category with persisted entity
        Category category = categoryService.getCategoryDetails(template.getCategory().getId());
        category.getTemplates().add(template);
        template.setCategory(category);

        // Replace domains with persisted entities
        template.setDomains(domainService.persistedDomains(template.getDomains()));

        return saveEntity(template);
    }


    /**
     * Updates the template data from the template parameter
     * @param template the template to update
     * @return the updated template
     */
    public Template updateTemplate(Template template) {
        Template original = findById(template.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing template " + template.getId());
        }

        // Copy the template data
        original.setTemplatePath(template.getTemplatePath());
        original.setMessageId(template.getMessageId());
        original.copyDescsAndRemoveBlanks(template.getDescs());

        // Replace category with persisted entity
        Category category = categoryService.getCategoryDetails(template.getCategory().getId());
        if (!category.getTemplates().contains(original)) {
            original.getCategory().getTemplates().remove(original);
            category.getTemplates().add(template);
        }
        original.setCategory(category);

        // Replace domains with persisted entities
        original.setDomains(domainService.persistedDomains(template.getDomains()));

        return saveEntity(original);
    }


    /**
     * Deletes the template with the given id
     * @param id the ID of the template to delete
     */
    public boolean deleteTemplate(Integer id) {

        Template template = findById(id);
        if (template != null) {
            template.getCategory().getTemplates().remove(template);
            remove(template);
            return true;
        }
        return false;
    }


    /**
     * Executes a message template on the given message
     * @param template the template to execute
     * @param message the message to apply the template to
     * @return the resulting message
     */
    public SystemMessageVo executeTemplate(Template template, SystemMessageVo message) throws Exception {

        long t0 = System.currentTimeMillis();

        // Ensure the presence of a DETAILS message part
        MessagePartVo part = message.checkCreatePart(MessagePartType.DETAILS);

        // Ensure that description records exists for all supported languages
        message.checkCreateDescs(app.getLanguages());

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("message", message);
        contextData.put("part", part);
        contextData.put("template", template.toVo());

        // Run the associated Freemarker template to get a result in the "FieldTemplates" format
        String fieldTemplateTxt = templateService.newFmTemplateBuilder()
                .templatePath(template.getTemplatePath())
                .data(contextData)
                .dictionaryNames("message")
                .process();

        List<FieldTemplate> fieldTemplates = FieldTemplateProcessor.parse(fieldTemplateTxt);

        applyFieldTemplates(fieldTemplates, contextData);

        log.info("Executed template " + template.getId() + " on message " + message.getId()
                + " in " + (System.currentTimeMillis() - t0) + " ms");

        return message;
    }


    /**
     * Updates the fields of the message using the FieldTemplate templates.
     *
     * A FieldTemplate field may have the format "message.promulgation('twitter').tweet"
     * and will be assigned the body content of the field template.
     *
     * @param fieldTemplates the field templates to apply
     */
    private void applyFieldTemplates(List<FieldTemplate> fieldTemplates, Map<String, Object> contextData) {

        ScriptEngine jsEngine = new ScriptEngineManager()
                .getEngineByName("Nashorn");

        // Update the JavaScript engine bindings from the context data
        Bindings bindings = new SimpleBindings();
        contextData.entrySet().forEach(e -> bindings.put(e.getKey(), e.getValue()));
        jsEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        for (FieldTemplate fieldTemplate : fieldTemplates) {
            bindings.put("content", fieldTemplate.getContent());
            String script = fieldTemplate.getField() + " = content;";
            try {
                jsEngine.eval(script);
            } catch (ScriptException e) {
                // Apply the result of the field templates
                log.error("Error applying field template " + fieldTemplate + ": " + e.getMessage());
            }
        }

    }

}
