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

import services.plugins.atlassian.jira.jira1.JiraPluginRunner;

/**
 * A Jira issue.
 * 
 * @author Johann Kohler
 * 
 */
public class Issue {

    private String id;
    private Boolean defect;
    private String name;
    private String description;
    private String category;
    private String status;
    private String priority;
    private String severity;
    private String authorEmail;
    private Integer storyPoints;
    private Integer estimation;
    private Boolean inScope;

    /**
     * Default constructor.
     */
    public Issue() {
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id
     *            the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the defect
     */
    public Boolean getDefect() {
        return defect;
    }

    /**
     * @param defect
     *            the defect to set
     */
    public void setDefect(Boolean defect) {
        this.defect = defect;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description
     *            the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * @param category
     *            the category to set
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * @return the status
     */
    public String getStatus() {
        return JiraPluginRunner.getIdFromValue(status);
    }

    /**
     * @param status
     *            the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return the priority
     */
    public String getPriority() {
        return JiraPluginRunner.getIdFromValue(priority);
    }

    /**
     * @param priority
     *            the priority to set
     */
    public void setPriority(String priority) {
        this.priority = priority;
    }

    /**
     * @return the severity
     */
    public String getSeverity() {
        return JiraPluginRunner.getIdFromValue(severity);
    }

    /**
     * @param severity
     *            the severity to set
     */
    public void setSeverity(String severity) {
        this.severity = severity;
    }

    /**
     * @return the authorEmail
     */
    public String getAuthorEmail() {
        return authorEmail;
    }

    /**
     * @param authorEmail
     *            the authorEmail to set
     */
    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    /**
     * @return the storyPoints
     */
    public Integer getStoryPoints() {
        return storyPoints;
    }

    /**
     * @param storyPoints
     *            the storyPoints to set
     */
    public void setStoryPoints(Integer storyPoints) {
        this.storyPoints = storyPoints;
    }

    /**
     * @return the estimation
     */
    public Integer getEstimation() {
        return estimation;
    }

    /**
     * @param estimation
     *            the estimation to set
     */
    public void setEstimation(Integer estimation) {
        this.estimation = estimation;
    }

    /**
     * @return the inScope
     */
    public Boolean getInScope() {
        return inScope;
    }

    /**
     * @param inScope
     *            the inScope to set
     */
    public void setInScope(Boolean inScope) {
        this.inScope = inScope;
    }

}
