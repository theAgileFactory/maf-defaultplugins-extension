/*! LICENSE
 *
 * Copyright (c) 2015, The Agile Factory SA and/or its affiliates. All rights
 * reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package services.plugins.atlassian.jira.jira1.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import framework.utils.Msg;
import models.framework_models.common.CustomAttributeDefinition;
import models.framework_models.common.ICustomAttributeValue;
import models.framework_models.common.ICustomAttributeValue.AttributeType;
import models.pmo.OrgUnit;
import models.pmo.Portfolio;
import models.pmo.PortfolioEntry;

/**
 * The request to get the issues of a Jira project.
 * 
 * @author Johann Kohler
 */
public class GetIssuesRequest {

    public String projectRefId;

    public PortfolioEntryData parameters;

    /**
     * Construct the issue request.
     * 
     * @param projectRefId
     *            the Jira project id
     * @param portfolioEntry
     *            the portfolio entry
     */
    public GetIssuesRequest(String projectRefId, PortfolioEntry portfolioEntry) {
        this.projectRefId = projectRefId;
        this.parameters = new PortfolioEntryData(portfolioEntry);
    }

    /**
     * A portfolio entry data is used to forward the main information of a
     * portfolio entry (including the custom attributes) to a Jira API action.
     * 
     * @author Johann Kohler
     */
    public static class PortfolioEntryData {

        private static Set<AttributeType> authorizedAttributeTypes = new HashSet<AttributeType>(
                Arrays.asList(AttributeType.BOOLEAN, AttributeType.INTEGER, AttributeType.DECIMAL, AttributeType.STRING, AttributeType.DATE));

        public Long id;
        public String refId;
        public String governanceId;
        public String erpRefId;
        public String name;
        public String description;
        public Date creationDate;
        public Boolean isPublic;
        public Boolean archived;
        public String manager;
        public String sponsoringUnit;
        public List<String> deliveryUnits;
        public String portfolioEntryType;
        public List<String> portfolios;

        public Map<String, Object> customAttributes;

        /**
         * Construct a portfolio entry data with a portfolio entry.
         * 
         * @param portfolioEntry
         *            the portfolio entry
         */
        public PortfolioEntryData(PortfolioEntry portfolioEntry) {

            this.id = portfolioEntry.id;
            this.refId = portfolioEntry.refId;
            this.governanceId = portfolioEntry.governanceId;
            this.erpRefId = portfolioEntry.erpRefId;
            this.name = portfolioEntry.name;
            this.description = portfolioEntry.description;
            this.creationDate = portfolioEntry.creationDate;
            this.isPublic = portfolioEntry.isPublic;
            this.archived = portfolioEntry.archived;
            this.manager = portfolioEntry.manager != null ? portfolioEntry.manager.getName() : null;
            this.sponsoringUnit = portfolioEntry.sponsoringUnit != null ? portfolioEntry.sponsoringUnit.getName() : null;

            this.deliveryUnits = new ArrayList<>();
            for (OrgUnit deliveryUnit : portfolioEntry.deliveryUnits) {
                this.deliveryUnits.add(deliveryUnit.name);
            }

            this.portfolioEntryType = portfolioEntry.portfolioEntryType.getName();

            this.portfolios = new ArrayList<>();
            for (Portfolio portfolio : portfolioEntry.portfolios) {
                this.portfolios.add(portfolio.name);
            }

            this.customAttributes = new HashMap<>();
            for (ICustomAttributeValue customAttributeValue : CustomAttributeDefinition.getOrderedCustomAttributeValues(PortfolioEntry.class,
                    portfolioEntry.id)) {
                if (authorizedAttributeTypes.contains(AttributeType.valueOf(customAttributeValue.getDefinition().attributeType))) {
                    this.customAttributes.put(Msg.get(customAttributeValue.getDefinition().uuid), customAttributeValue.getValueAsObject());
                }
            }
        }
    }

}
