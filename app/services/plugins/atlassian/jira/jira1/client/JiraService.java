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
package services.plugins.atlassian.jira.jira1.client;

import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import models.pmo.PortfolioEntry;
import play.Logger;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import services.plugins.atlassian.jira.jira1.client.model.CreateProjectResponse;
import services.plugins.atlassian.jira.jira1.client.model.ErrorResponse;
import services.plugins.atlassian.jira.jira1.client.model.GetIssuesRequest;
import services.plugins.atlassian.jira.jira1.client.model.Issue;
import services.plugins.atlassian.jira.jira1.client.model.JiraConfig;
import services.plugins.atlassian.jira.jira1.client.model.Project;

/**
 * The Jira service for the Jira BizDock plugin.
 * 
 * @author Johann Kohler
 * 
 */
public class JiraService {
    private static Logger.ALogger log = Logger.of(JiraService.class);

    private WSClient wsClient;
    private String hostUrl;
    private String key;
    private String version;

    private static final long WS_TIMEOUT = 10000;

    private static final String API_PATH = "/rest/taf_api/{version}/api";

    private static final String TIMESTAMP_HEADER = "x-jira-bizdock-timestamp";
    private static final String AUTHENTICATION_DIGEST_HEADER = "x-jira-bizdock-auth";

    private static final String PING_ACTION = "/ping";
    private static final String CONFIG_ACTION = "/config";
    private static final String CREATE_PROJECT_ACTION = "/projects/create";
    private static final String GET_PROJECTS_ACTION = "/projects/all";
    private static final String GET_PROJECT_ACTION = "/projects/find";
    private static final String GET_NEEDS_ACTION = "/needs/find";
    private static final String GET_DEFECTS_ACTION = "/defects/find";

    /**
     * Get an instance of the Jira service.
     * 
     * @param wsClient
     *            a Web Service client instance to be used for WS calls
     * @param version
     *            the plugin version
     * @param hostUrl
     *            the Jira host URL
     * @param key
     *            the Jira plugin key
     */
    public static JiraService get(
            WSClient wsClient,
            String version, 
            String hostUrl, 
            String key) {

        JiraService jiraService = new JiraService();

        jiraService.wsClient=wsClient;
        jiraService.hostUrl = hostUrl;
        jiraService.key = key;
        jiraService.version = version;

        return jiraService;
    }

    /**
     * Constructor.
     */
    public JiraService() {
    }

    /**
     * Get the API URL.
     */
    private String getApiUrl() {
        return this.hostUrl + API_PATH.replace("{version}", version);
    }

    /**
     * Get the URL of an action.
     * 
     * @param action
     *            the action name
     */
    private String getActionUrl(String action) {
        return this.getApiUrl() + action;
    }

    /**
     * Get the URI of an action.
     * 
     * @param action
     *            the action name
     * @param queryParams
     *            the query params
     */
    private String getActionUri(String action, List<NameValuePair> queryParams) {
        try {
            URL url = new URL(getActionUrl(action));
            URI uri = url.toURI();

            String queryParamsString = "";
            if (queryParams != null && queryParams.size() > 0) {
                queryParamsString = "?" + URLEncodedUtils.format(queryParams, "UTF-8");
            }

            return uri.getPath() + queryParamsString;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Return true if the connection with Jira is OK, including the
     * authentication.
     */
    public boolean isAvailable() {

        try {
            JsonNode response = this.callGet(PING_ACTION);
            return response.get("authenticated").asBoolean();
        } catch (JiraServiceException e) {
            log.warn("Jira service not available", e);
            return false;
        }

    }

    /**
     * Get the configuration (list of status priorities and severities).
     */
    public JiraConfig getConfig() throws JiraServiceException {

        JsonNode response = this.callGet(CONFIG_ACTION);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.treeToValue(response, JiraConfig.class);
        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/getConfig: error when processing the reponse to JiraConfig.class", e);
        }

    }

    /**
     * Create a Jira project.
     * 
     * Return the id of the created project.
     * 
     * Return null if the project is already existing in Jira.
     * 
     * @param project
     *            the project to create
     */
    public String createProject(Project project) throws JiraServiceException {

        JsonNode content = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
            content = mapper.valueToTree(project);
            Logger.debug("project: " + mapper.writeValueAsString(project));
        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/createProject: error when processing the project to a Json node", e);
        }

        JsonNode response = this.callPost(CREATE_PROJECT_ACTION, content);

        CreateProjectResponse createProjectResponse = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            createProjectResponse = objectMapper.treeToValue(response, CreateProjectResponse.class);
        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/createProject: error when processing the response to CreateProjectResponse.class", e);
        }

        if (createProjectResponse.success) {
            return createProjectResponse.projectRefId;
        } else {
            if (createProjectResponse.alreadyExists) {
                return null;
            } else {
                throw new JiraServiceException("JiraService/createProject: the response is a 200 with an unknown error.");
            }
        }

    }

    /**
     * Get all Jira projects.
     */
    public List<Project> getProjects() throws JiraServiceException {

        JsonNode response = this.callGet(GET_PROJECTS_ACTION);

        List<Project> projects = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            for (final JsonNode projectNode : response) {
                projects.add(objectMapper.treeToValue(projectNode, Project.class));
            }

            return projects;

        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/getProjects: error when processing the response to Project.class", e);
        }

    }

    /**
     * Get a Jira project by id.
     * 
     * @param projectRefId
     *            the Jira project id
     */
    public Project getProject(String projectRefId) throws JiraServiceException {

        List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
        queryParams.add(new BasicNameValuePair("projectRefId", projectRefId));

        JsonNode response = this.callGet(GET_PROJECT_ACTION, queryParams);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.treeToValue(response, Project.class);
        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/getProject: error when processing the response to Project.class", e);
        }

    }

    /**
     * Get the needs of a project.
     * 
     * @param projectRefId
     *            the Jira project id
     * @param portfolioEntry
     *            the portfolio entry
     */
    public List<Issue> getNeeds(String projectRefId, PortfolioEntry portfolioEntry) throws JiraServiceException {
        return getIssues(false, projectRefId, portfolioEntry);
    }

    /**
     * Get the defects of a project.
     * 
     * @param projectRefId
     *            the Jira project id
     * @param portfolioEntry
     *            the portfolio entry
     */
    public List<Issue> getDefects(String projectRefId, PortfolioEntry portfolioEntry) throws JiraServiceException {
        return getIssues(true, projectRefId, portfolioEntry);
    }

    /**
     * Get the issues of a project.
     * 
     * @param isDefect
     *            set to true to get the defects, else the needs.
     * @param projectRefId
     *            the Jira project id
     * @param portfolioEntry
     *            the portfolio entry
     */
    private List<Issue> getIssues(boolean isDefect, String projectRefId, PortfolioEntry portfolioEntry) throws JiraServiceException {

        JsonNode content = null;

        GetIssuesRequest getIssuesRequest = new GetIssuesRequest(projectRefId, portfolioEntry);
        try {
            ObjectMapper mapper = new ObjectMapper();
            content = mapper.valueToTree(getIssuesRequest);
            Logger.debug("issueRequest: " + mapper.writeValueAsString(getIssuesRequest));
        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/getIssues: error when processing the getIssuesRequest to a Json node", e);
        }

        String action = null;
        if (isDefect) {
            action = GET_DEFECTS_ACTION;
        } else {
            action = GET_NEEDS_ACTION;
        }

        JsonNode response = this.callPost(action, content);

        List<Issue> issues = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            for (final JsonNode issueNode : response) {
                issues.add(objectMapper.treeToValue(issueNode, Issue.class));
            }

            return issues;

        } catch (JsonProcessingException e) {
            throw new JiraServiceException("JiraService/getIssues: error when processing the response to Issue.class", e);
        }
    }

    /**
     * Perform a call with GET method.
     * 
     * @param action
     *            the action name
     */
    private JsonNode callGet(String action) throws JiraServiceException {
        return callGet(action, new ArrayList<NameValuePair>());
    }

    /**
     * Perform a call with GET method.
     * 
     * @param action
     *            the action name
     * @param queryParams
     *            the query parameters
     */
    private JsonNode callGet(String action, List<NameValuePair> queryParams) throws JiraServiceException {
        return call(HttpMethod.GET, action, queryParams, null);
    }

    /**
     * Perform a call with POST method.
     * 
     * @param action
     *            the action name
     * @param content
     *            the request content
     */
    private JsonNode callPost(String action, JsonNode content) throws JiraServiceException {
        return callPost(action, new ArrayList<NameValuePair>(), content);
    }

    /**
     * Perform a call with POST method.
     * 
     * @param action
     *            the action name
     * @param queryParams
     *            the query parameters
     * @param content
     *            the request content
     */
    private JsonNode callPost(String action, List<NameValuePair> queryParams, JsonNode content) throws JiraServiceException {
        return call(HttpMethod.POST, action, queryParams, content);
    }

    /**
     * Perform a call.
     * 
     * @param httpMethod
     *            the HTTP method (GET, POST...)
     * @param action
     *            the action name
     * @param queryParams
     *            the query parameters
     * @param content
     *            the request content (for POST)
     */
    private JsonNode call(HttpMethod httpMethod, String action, List<NameValuePair> queryParams, JsonNode content) throws JiraServiceException {

        Date timestamp = new Date();

        Logger.debug("URL: " + this.getActionUrl(action));
        Logger.debug("URI: " + this.getActionUri(action, queryParams));
        Logger.debug(TIMESTAMP_HEADER + ": " + String.valueOf(timestamp.getTime()));
        Logger.debug(AUTHENTICATION_DIGEST_HEADER + ": " + getAuthenticationDigest(timestamp.getTime(), this.getActionUri(action, queryParams)));

        WSRequest request = getWsClient().url(this.getActionUrl(action));
        request.setHeader(TIMESTAMP_HEADER, String.valueOf(timestamp.getTime()));
        request.setHeader(AUTHENTICATION_DIGEST_HEADER, getAuthenticationDigest(timestamp.getTime(), this.getActionUri(action, queryParams)));

        for (NameValuePair param : queryParams) {
            request = request.setQueryParameter(param.getName(), param.getValue());
        }

        Promise<WSResponse> reponse = null;

        switch (httpMethod) {
        case GET:
            reponse = request.get();
            break;
        case POST:
            reponse = request.post(content);
            break;
        }

        Promise<Pair<Integer, JsonNode>> jsonPromise = reponse.map(new Function<WSResponse, Pair<Integer, JsonNode>>() {
            public Pair<Integer, JsonNode> apply(WSResponse response) {
                if (log.isDebugEnabled()) {
                    log.debug(response.getBody());
                }
                return Pair.of(response.getStatus(), response.asJson());
            }
        });

        Pair<Integer, JsonNode> response = jsonPromise.get(WS_TIMEOUT);

        Logger.debug("STATUS CODE: " + response.getLeft());

        if (response.getLeft().equals(200)) {
            return response.getRight();
        } else if (response.getLeft().equals(400)) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                ErrorResponse errorResponse = objectMapper.treeToValue(response.getRight(), ErrorResponse.class);
                Logger.error("API Message: " + errorResponse.message + " / API code: " + errorResponse.code);
                Logger.debug(errorResponse.trace);
                throw new JiraServiceException("JiraService: the call for the action '" + action + "' returns a 400, please refer above for more details.");
            } catch (JsonProcessingException e) {
                throw new JiraServiceException("JiraService: the call for the action '" + action
                        + "' returns a 400 but an error occurred when processing the response to ErrorResponse.class", e);
            }

        } else {
            throw new JiraServiceException("JiraService: the call for the action '" + action + "' returns a " + response.getLeft());
        }

    }

    /**
     * Get the authentication digest for a request.
     * 
     * The authentication is based on a hash:<br/>
     * Base64(SHA256([secret key]+"#" + requestUri + "#" + timestamp))
     * 
     * @param timestamp
     *            the timestamp
     * @param requestUri
     *            the request URI
     */
    private String getAuthenticationDigest(long timestamp, String requestUri) throws JiraServiceException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String clearHash = this.key + "#" + requestUri + "#" + timestamp;
            return new String(Base64.encodeBase64URLSafe(digest.digest(clearHash.getBytes())));
        } catch (NoSuchAlgorithmException e) {
            throw new JiraServiceException("JiraService/getAuthenticationDigest: error when creating the authentication digest", e);
        }
    }

    /**
     * The Jira service exception.
     * 
     * @author Johann Kohler
     * 
     */
    public static class JiraServiceException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Default constructor.
         * 
         * @param message
         *            the exception message
         */
        public JiraServiceException(String message) {
            super(message);
        }

        /**
         * Default constructor including the initial exception.
         * 
         * @param message
         *            the exception message
         * @param e
         *            the exception
         */
        public JiraServiceException(String message, Exception e) {
            super(message, e);
        }

    }

    /**
     * The possible HTTP method.
     * 
     * @author Johann Kohler
     * 
     */
    private static enum HttpMethod {
        GET, POST;
    }

    private WSClient getWsClient() {
        return wsClient;
    }

}
