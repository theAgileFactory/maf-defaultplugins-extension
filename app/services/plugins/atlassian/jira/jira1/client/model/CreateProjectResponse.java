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
 * The response when getting the issues of a project.
 * 
 * @author Johann Kohler
 */
public class CreateProjectResponse {

    public String projectRefId;

    public boolean success = false;

    public boolean alreadyExists = false;

    /**
     * Default constructor.
     */
    public CreateProjectResponse() {
    }

}
