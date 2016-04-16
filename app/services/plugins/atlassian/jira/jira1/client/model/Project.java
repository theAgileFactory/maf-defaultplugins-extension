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


/**
 * A Jira project.
 * 
 * @author Johann Kohler
 * 
 */
public class Project {

    private String projectRefId;
    private String key;
    private String name;
    private String description;

    /**
     * Default constructor.
     */
    public Project() {
    }

    /**
     * @return the projectRefId
     */
    public String getProjectRefId() {
        return projectRefId;
    }

    /**
     * @param projectRefId
     *            the projectRefId to set
     */
    public void setProjectRefId(String projectRefId) {
        this.projectRefId = projectRefId;
    }

    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * @param key
     *            the key to set
     */
    public void setKey(String key) {
        this.key = key;
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

}
