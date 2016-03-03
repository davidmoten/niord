/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.aton;

import org.apache.commons.lang.StringUtils;
import org.niord.core.chart.Chart;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.db.SpatialWithinPredicate;
import org.niord.core.service.BaseService;
import org.niord.model.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interface for handling AtoNs
 * <p>
 * TODO: Make it a singleton and cache the list of AtoNs
 * or create a Lucene index with AtoNs
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class AtonService extends BaseService {

    @Inject
    private Logger log;

    /*************************/
    /** NEW Aton Model      **/
    /*************************/

    /**
     * Returns the AtoNs with the given tag key-value
     *
     * @param key   the tag key
     * @param value the tag value
     * @return the AtoNs with the given tag key-value
     */
    public List<AtonNode> findByTag(String key, String value) {
        return em
                .createNamedQuery("AtonNode.findByTag", AtonNode.class)
                .setParameter("key", key)
                .setParameter("value", value)
                .getResultList();
    }


    /**
     * Returns the AtoNs with the given tag key and values
     *
     * @param key   the tag key
     * @param values the tag values
     * @return the AtoNs with the given tag key and values
     */
    public List<AtonNode> findByTagValues(String key, String... values) {
        Set<String> valueSet = new HashSet<>(Arrays.asList(values));
        return em
                .createNamedQuery("AtonNode.findByTagValues", AtonNode.class)
                .setParameter("key", key)
                .setParameter("values", valueSet)
                .getResultList();
    }


    /**
     * Returns the AtoNs with the given AtoN UID
     * @param atonUid the AtoN UID
     * @return the AtoNs with the given AtoN UID or null if not found
     */
    public AtonNode findByAtonUid(String atonUid) {
        return findByTag(AtonTag.CUST_TAG_ATON_UID, atonUid).stream()
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the AtoNs with the given AtoN UIDs
     * @param atonUids the AtoN UIDs
     * @return the AtoNs with the given AtoN UIDs
     */
    public List<AtonNode> findByAtonUids(String... atonUids) {
        return findByTagValues(AtonTag.CUST_TAG_ATON_UID, atonUids);
    }


    /**
     * Replaces the AtoN DB
     * @param atons the new AtoNs
     */
    @Lock(LockType.WRITE)
    public void updateAtons(List<AtonNode> atons) {

        // Persist new list of AtoNs
        long t0 = System.currentTimeMillis();
        int created = 0, updated = 0, unchanged = 0;
        for (AtonNode aton : atons) {

            AtonNode orig = findByAtonUid(aton.getAtonUid());
            if (orig == null) {
                em.persist(aton);
                created++;

            } else if (orig.hasChanged(aton)) {
                orig.updateNode(aton);
                em.persist(orig);
                updated++;

            } else {
                unchanged++;
            }
            log.info("XX " + aton);

            if ((created + updated + unchanged) % 100 == 0) {
                em.flush();
            }
        }
        log.info(String.format("Updated %s AtoNs (created %d, updated %d, ignored %d) in %d ms",
                atons.size(), created, updated, unchanged, System.currentTimeMillis() - t0));
    }


    /**
     * Computes the list of AtoNs that matches the search parameters.<br>
     *
     * @return the AtoNs within that matches the search parameters
     */
    public PagedSearchResultVo<AtonNode> search(AtonSearchParams param) {
        try {
            //"select count(a) from AtonNode a, Chart c where c.chartNumber in ('101') and within(a.geometry, c.geometry) = true";

            PagedSearchResultVo<AtonNode> result = new PagedSearchResultVo<>();

            // First fetch the ID's of the of all matching AtoNs
            CriteriaHelper<Tuple> criteriaHelper = CriteriaHelper.initWithTupleQuery(em);

            Root<AtonNode> atonRoot = buildSearchCriteria(criteriaHelper, param);

            criteriaHelper.getCriteriaQuery()
                    .multiselect(atonRoot.get("id"))
                    .distinct(true)
                    .where(criteriaHelper.where());

            List<Integer> atonIds = em
                    .createQuery(criteriaHelper.getCriteriaQuery())
                    .getResultList()
                    .stream()
                    .map(t -> (Integer)t.get(0))
                    .collect(Collectors.toList());

            result.setTotal(atonIds.size());

            // For efficiency reasons the callee may not want any data returned if the
            // result is larger than maxAtonNo
            if (param.isEmptyOnOverflow() && param.getMaxSize() < atonIds.size()) {
                return result;
            }

            // Get the ID's of the sub-list to fetch
            atonIds = atonIds.subList(0, Math.min(atonIds.size(), param.getMaxSize()));

            // TODO: When cache is implemented, look up AtoNs via cache

            List<AtonNode> atons = em.createNamedQuery("AtonNode.findByIds", AtonNode.class)
                    .setParameter("ids", atonIds)
                    .getResultList();

            result.setData(atons);
            result.updateSize();
            return result;

        } catch (RuntimeException e) {
            log.error("Error searching for AtoNs with params " + param, e);
            return new PagedSearchResultVo<>();
        }
    }


    /**
     * Computes the list of AtoN lon-lat positions that matches the search parameters.<br>
     *
     * @return the AtoN lon-lat positions
     */
    public List<double[]> searchPositions(AtonSearchParams param) {
        try {
            CriteriaHelper<Tuple> criteriaHelper = CriteriaHelper.initWithTupleQuery(em);

            Root<AtonNode> atonRoot = buildSearchCriteria(criteriaHelper, param);

            criteriaHelper.getCriteriaQuery()
                    .multiselect(atonRoot.get("lon"), atonRoot.get("lat"))
                    .distinct(true)
                    .where(criteriaHelper.where());

            return em
                    .createQuery(criteriaHelper.getCriteriaQuery())
                    .setMaxResults(param.getMaxSize())
                    .getResultList()
                    .stream()
                    .map(t -> new double[] { (double)t.get(0), (double)t.get(1) })
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            log.error("Error searching for AtoNs positions with params " + param, e);
            return Collections.emptyList();
        }
    }

    /**
     * Computes the list of AtoNs that matches the search parameters.<br>
     *
     * @return the AtoNs within that matches the search parameters
     */
    public <T> Root<AtonNode> buildSearchCriteria(CriteriaHelper<T> criteriaHelper, AtonSearchParams param) {

        CriteriaBuilder cb = criteriaHelper.getCriteriaBuilder();
        CriteriaQuery<T> c = criteriaHelper.getCriteriaQuery();

        Root<AtonNode> atonRoot = c.from(AtonNode.class);

        //Fetch<AtonNode, AtonTag> fetchTags = atonRoot.fetch("tags", JoinType.LEFT);
        if (StringUtils.isNotBlank(param.getName())) {
            Join<AtonNode, AtonTag> tags = atonRoot.join("tags", JoinType.LEFT);
            criteriaHelper
                    .equals(tags.get("k"), AtonTag.CUST_TAG_ATON_UID)
                    .like(tags.get("v"), param.getName());
        }

        if (param.getExtent() != null) {
            criteriaHelper.add(new SpatialWithinPredicate(cb, atonRoot.get("geometry"), param.getExtent()));
        }

        if (param.getChartNumbers() != null && param.getChartNumbers().length > 0) {
            Root<Chart> chartRoot = c.from(Chart.class);
            criteriaHelper
                    .add(new SpatialWithinPredicate(cb, atonRoot.get("geometry"), chartRoot.get("geometry")))
                    .in(chartRoot.get("chartNumber"), Arrays.asList(param.getChartNumbers()));
        }

        return atonRoot;
    }

}
