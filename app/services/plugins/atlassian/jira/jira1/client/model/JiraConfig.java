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

import java.util.List;
import java.util.Map;

/**
 * The Jira configuration.
 * 
 * @author Johann Kohler
 * 
 */
public class JiraConfig {

    private List<String> statuses;
    private List<String> priorities;
    private List<String> severities;
    private Map<String, String> allPossibleJiraFields;

    /**
     * Default constructor.
     */
    public JiraConfig() {
    }

    /**
     * @return the statuses
     */
    public List<String> getStatuses() {
        return statuses;
    }

    /**
     * @param statuses
     *            the statuses to set
     */
    public void setStatuses(List<String> statuses) {
        this.statuses = statuses;
    }

    /**
     * @return the priorities
     */
    public List<String> getPriorities() {
        return priorities;
    }

    /**
     * @param priorities
     *            the priorities to set
     */
    public void setPriorities(List<String> priorities) {
        this.priorities = priorities;
    }

    /**
     * @return the severities
     */
    public List<String> getSeverities() {
        return severities;
    }

    /**
     * @param severities
     *            the severities to set
     */
    public void setSeverities(List<String> severities) {
        this.severities = severities;
    }

    /**
     * @return the allPossibleJiraFields
     */
    public Map<String, String> getAllPossibleJiraFields() {
        return allPossibleJiraFields;
    }

    /**
     * @param allPossibleJiraFields
     *            the allPossibleJiraFields to set
     */
    public void setAllPossibleJiraFields(Map<String, String> allPossibleJiraFields) {
        this.allPossibleJiraFields = allPossibleJiraFields;
    }

}
