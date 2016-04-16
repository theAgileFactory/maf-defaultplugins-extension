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
package services.plugins.atlassian.jira.jira1;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;

import akka.actor.Cancellable;
import constants.MafDataType;
import dao.delivery.RequirementDAO;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import framework.commons.DataType;
import framework.commons.message.EventMessage;
import framework.commons.message.EventMessage.MessageType;
import framework.services.ext.api.IExtensionDescriptor.IPluginConfigurationBlockDescriptor;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.services.system.ISysAdminUtils;
import models.delivery.Requirement;
import models.pmo.Actor;
import models.pmo.PortfolioEntry;
import play.Logger;
import play.libs.ws.WSClient;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import services.plugins.atlassian.jira.jira1.client.JiraService;
import services.plugins.atlassian.jira.jira1.client.JiraService.JiraServiceException;
import services.plugins.atlassian.jira.jira1.client.model.Issue;
import services.plugins.atlassian.jira.jira1.client.model.JiraConfig;

/**
 * A plugin implementing the integration between BizDock and JIRA.
 * 
 * @author Pierre-Yves Cloux
 */
public class JiraPluginRunner implements IPluginRunner {

    private IPluginContext pluginContext;
    private ISysAdminUtils sysAdminUtils;
    private WSClient wsClient;

    private String hostUrl;
    private String authenticationKey;
    private String apiVersion;

    private String peLoadStartTime;
    private FiniteDuration peLoadFrequency;
    private Cancellable currentScheduler;

    private String peIssuesPatternUrl;

    private Map<String, Long> requirementStatusMapping;
    private Map<String, Long> requirementPriorityMapping;
    private Map<String, Long> requirementSeverityMapping;

    private Map<String, IPluginActionDescriptor> pluginActions = Collections.synchronizedMap(new HashMap<String, IPluginActionDescriptor>() {
        private static final long serialVersionUID = 1L;

        {
            this.put(JiraPluginRunner.ActionMessage.LOAD_JIRA_CONFIG.name(), new IPluginActionDescriptor() {

                @Override
                public Object getPayLoad(Long id) {
                    return JiraPluginRunner.ActionMessage.LOAD_JIRA_CONFIG;
                }

                @Override
                public String getLabel() {
                    return "plugin.jira.load.action.name";
                }

                @Override
                public String getIdentifier() {
                    return JiraPluginRunner.ActionMessage.LOAD_JIRA_CONFIG.name();
                }

                @Override
                public DataType getDataType() {
                    return null;
                }

                @Override
                public Object getPayLoad(Long arg0, Map<String, Object> arg1) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    });

    /**
     * List of actions.
     * 
     * @author Johann Kohler
     * 
     */
    public static enum ActionMessage {
        TRIGGER_PE_LOAD, LOAD_JIRA_CONFIG;
    }

    public static final String MAIN_CONFIGURATION_NAME = "config";
    public static final String REQUIREMENT_STATUS_MAPPING_CONFIGURATION_NAME = "requirement_status_mapping";
    public static final String REQUIREMENT_PRIORITY_MAPPING_CONFIGURATION_NAME = "requirement_priority_mapping";
    public static final String REQUIREMENT_SEVERITY_MAPPING_CONFIGURATION_NAME = "requirement_severity_mapping";

    public static final String HOST_URL_PROPERTY = "host.url";
    public static final String AUTHENTICATION_KEY = "authentication.key";
    public static final String API_VERSION = "api.version";

    // properties for the portfolio entries
    public static final String PE_LOAD_START_TIME_PROPERTY = "pe.load.start_time";
    public static final String PE_LOAD_FREQUENCY_PROPERTY = "pe.load.frequency";

    public static final String PORTFOLIO_ENTRY_LINK_TYPE = "PORTFOLIO_ENTRY";
    public static final String PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE = "PORTFOLIO_ENTRY_REQUIREMENT";

    private static final int MINIMAL_FREQUENCY = 5;

    /**
     * Default constructor.
     */
    @Inject
    public JiraPluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils, WSClient wsClient) {
        this.pluginContext = pluginContext;
        this.sysAdminUtils = sysAdminUtils;
        this.wsClient = wsClient;
    }

    @Override
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {

        if (eventMessage.getMessageType().equals(MessageType.CUSTOM) && eventMessage.getPayload() != null
                && eventMessage.getPayload() instanceof ActionMessage) {
            switch ((ActionMessage) eventMessage.getPayload()) {
            case TRIGGER_PE_LOAD:
                runPortfolioEntryLoad(eventMessage.getInternalId());
                break;
            case LOAD_JIRA_CONFIG:
                runLoadJiraConfig();
                break;
            }

        }

    }

    @Override
    public void start() throws PluginException {

        PropertiesConfiguration properties = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(MAIN_CONFIGURATION_NAME)));

        properties.setThrowExceptionOnMissing(true);

        this.hostUrl = removeLastSlash(properties.getString(HOST_URL_PROPERTY));

        this.authenticationKey = properties.getString(AUTHENTICATION_KEY);
        this.apiVersion = properties.getString(API_VERSION);

        if (!isAvailable()) {
            throw new PluginException("Server not available : stopping");
        }

        this.peLoadStartTime = properties.getString(PE_LOAD_START_TIME_PROPERTY);
        if (this.peLoadStartTime == null || !this.peLoadStartTime.matches("^([01]?[0-9]|2[0-3])h[0-5][0-9]$")) {
            throw new IllegalArgumentException("Invalid time format for the " + PE_LOAD_START_TIME_PROPERTY + " parameter");
        }
        this.peLoadFrequency = FiniteDuration.create(properties.getLong(PE_LOAD_FREQUENCY_PROPERTY), TimeUnit.MINUTES);
        if (properties.getLong(PE_LOAD_FREQUENCY_PROPERTY) < MINIMAL_FREQUENCY) {
            throw new IllegalArgumentException("Invalid frequency " + PE_LOAD_FREQUENCY_PROPERTY + " must be more than 5 minutes");
        }

        this.peIssuesPatternUrl = this.hostUrl + "/browse/{externalId}";

        try {
            long howMuchMinutesUntilStartTime = howMuchMinutesUntilStartTime();
            currentScheduler = getSysAdminUtils().scheduleRecurring(true, "JiraPlugin PortfolioEntry load " + getPluginContext().getPluginConfigurationName(),
                    Duration.create(howMuchMinutesUntilStartTime, TimeUnit.MINUTES), this.peLoadFrequency, new Runnable() {
                        @Override
                        public void run() {
                            runPortfolioEntryLoad();
                        }
                    });
            String startTimeMessage = String.format("Scheduler programmed to run in %d minutes", howMuchMinutesUntilStartTime);
            getPluginContext().log(LogLevel.INFO, startTimeMessage);
        } catch (Exception e) {
            if (e instanceof PluginException) {
                throw (PluginException) e;
            }
            throw new PluginException(e);
        }

        // get the requirement status mapping
        this.requirementStatusMapping = new HashMap<String, Long>();
        PropertiesConfiguration statusMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(REQUIREMENT_STATUS_MAPPING_CONFIGURATION_NAME)));
        for (Iterator<String> iter = statusMapping.getKeys(); iter.hasNext();) {
            String key = iter.next();
            if (!statusMapping.getString(key).equals("")) {
                this.requirementStatusMapping.put(key, statusMapping.getLong(key));
            }
        }

        // get the requirement priority mapping
        this.requirementPriorityMapping = new HashMap<String, Long>();
        PropertiesConfiguration priorityMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(REQUIREMENT_PRIORITY_MAPPING_CONFIGURATION_NAME)));
        for (Iterator<String> iter = priorityMapping.getKeys(); iter.hasNext();) {
            String key = iter.next();
            if (!priorityMapping.getString(key).equals("")) {
                this.requirementPriorityMapping.put(key, priorityMapping.getLong(key));
            }
        }

        // get the requirement severity mapping
        this.requirementSeverityMapping = new HashMap<String, Long>();
        PropertiesConfiguration severityMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(REQUIREMENT_SEVERITY_MAPPING_CONFIGURATION_NAME)));
        for (Iterator<String> iter = severityMapping.getKeys(); iter.hasNext();) {
            String key = iter.next();
            if (!severityMapping.getString(key).equals("")) {
                this.requirementSeverityMapping.put(key, severityMapping.getLong(key));
            }
        }

        getPluginContext().log(LogLevel.INFO, "Jira plugin started");

    }

    @Override
    public void stop() {
        try {
            if (this.currentScheduler != null) {
                this.currentScheduler.cancel();
                getPluginContext().log(LogLevel.INFO, "Scheduler stopped");
            }
        } catch (Exception e) {
            Logger.error("Error when stopping the jira plugin scheduler", e);
        }
        getPluginContext().log(LogLevel.INFO, "Jira plugin stopped");
    }

    /**
     * Run the load for all active portfolio entries.
     */
    private void runPortfolioEntryLoad() {
        try {
            List<PortfolioEntry> portfolioEntries = PortfolioEntryDao.getPEAsExpr(false).findList();
            for (PortfolioEntry portfolioEntry : portfolioEntries) {
                if (getPluginContext().isRegistered(MafDataType.getPortfolioEntry(), portfolioEntry.id)) {
                    runPortfolioEntryLoad(portfolioEntry.id);
                }
            }
        } catch (Exception e) {
            Logger.error("error when running runPortfolioEntryLoad from the scheduler", e);
        }
    }

    /**
     * Run the load for a portfolio entry.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     */
    private void runPortfolioEntryLoad(Long portfolioEntryId) throws PluginException {

        Logger.debug("START runPortfolioEntryLoad for portfolio entry " + portfolioEntryId);

        if (getPluginContext().isRegistered(MafDataType.getPortfolioEntry(), portfolioEntryId)) {

            // get the registration configuration properties
            PropertiesConfiguration registrationProperties = getPluginContext()
                    .getPropertiesConfigurationFromByteArray(pluginContext.getRegistrationConfiguration(MafDataType.getPortfolioEntry(), portfolioEntryId));

            // get the portfolio entry
            PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(portfolioEntryId);

            // load the needs
            if (getBooleanProperty(registrationProperties, "needs")) {
                loadRequirements(portfolioEntry, false);
            }

            // load the defects
            if (getBooleanProperty(registrationProperties, "defects")) {
                loadRequirements(portfolioEntry, true);
            }

        } else {
            Logger.warn("runPortfolioEntryLoad: the portfolio entry " + portfolioEntryId + " is not registered");
        }

        Logger.debug("END runPortfolioEntryLoad");

    }

    /**
     * Load the requirements of a portfolio entry.
     * 
     * @param portfolioEntry
     *            the portfolio entry
     * @param isDefect
     *            set to true for the defects, set to false for the needs
     */
    private void loadRequirements(PortfolioEntry portfolioEntry, boolean isDefect) throws PluginException {

        for (String projectId : getPluginContext().getMultipleExternalId(portfolioEntry.id, PORTFOLIO_ENTRY_LINK_TYPE)) {

            // initialize the requirements to remove
            Map<String, Long> requirementsToRemove = new HashMap<String, Long>();
            for (Pair<Long, String> child : pluginContext.getChildrenIdOfLink(portfolioEntry.id, projectId, PORTFOLIO_ENTRY_LINK_TYPE,
                    PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {
                requirementsToRemove.put(child.getRight(), child.getLeft());
            }

            JiraService jiraService = getJiraService();

            // get the issues
            List<Issue> issues = null;
            try {
                if (isDefect) {
                    issues = jiraService.getDefects(projectId, portfolioEntry);
                } else {
                    issues = jiraService.getNeeds(projectId, portfolioEntry);
                }
            } catch (JiraServiceException e) {
                throw new PluginException(e);
            }

            for (Issue issue : issues) {

                String issueId = String.valueOf(issue.getId());

                requirementsToRemove.remove(issueId);

                Logger.debug("runPortfolioEntryLoad: requirement [isDefect=" + isDefect + "] '" + issue.getName() + "' found");

                // try to get the requirement id
                Long requirementId = pluginContext.getUniqueInternalIdWithParent(issueId, PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE, portfolioEntry.id, projectId,
                        PORTFOLIO_ENTRY_LINK_TYPE);

                Requirement requirement = null;
                if (requirementId != null) {
                    requirement = RequirementDAO.getRequirementById(requirementId);
                } else {
                    requirement = new Requirement();
                    requirement.isDefect = isDefect;
                    requirement.portfolioEntry = portfolioEntry;
                    requirement.iteration = null;
                }

                Actor author = null;
                if (issue.getAuthorEmail() != null) {
                    author = ActorDao.getActorByEmail(issue.getAuthorEmail());
                }
                requirement.author = author;

                requirement.externalRefId = issueId;
                requirement.externalLink = this.peIssuesPatternUrl.replace("{externalId}", requirement.externalRefId);
                requirement.name = issue.getName();
                requirement.description = issue.getDescription();
                requirement.category = issue.getCategory();

                requirement.requirementStatus = this.requirementStatusMapping.containsKey(issue.getStatus())
                        ? RequirementDAO.getRequirementStatusById(this.requirementStatusMapping.get(issue.getStatus())) : null;
                requirement.requirementPriority = this.requirementPriorityMapping.containsKey(issue.getPriority())
                        ? RequirementDAO.getRequirementPriorityById(this.requirementPriorityMapping.get(issue.getPriority())) : null;
                requirement.requirementSeverity = this.requirementSeverityMapping.containsKey(issue.getSeverity())
                        ? RequirementDAO.getRequirementSeverityById(this.requirementSeverityMapping.get(issue.getSeverity())) : null;

                requirement.initialEstimation = Double.valueOf(issue.getEstimation());
                requirement.storyPoints = issue.getStoryPoints();
                requirement.isScoped = issue.getInScope();

                requirement.save();

                // save the link
                if (requirementId == null) {
                    try {
                        getPluginContext().createLink(requirement.id, issueId, PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE, portfolioEntry.id, projectId,
                                PORTFOLIO_ENTRY_LINK_TYPE);
                    } catch (PluginException e) {
                        Logger.error("impossible to create the requirement link", e);
                        requirement.doDelete();
                    }
                }

            }

            // remove the requirements
            for (Map.Entry<String, Long> entry : requirementsToRemove.entrySet()) {
                Requirement requirement = RequirementDAO.getRequirementById(entry.getValue());
                if (requirement != null && requirement.isDefect == isDefect) {
                    Logger.debug("runPortfolioEntryLoad: remove the requirement " + entry.getValue());
                    requirement.doDelete();
                    pluginContext.deleteLink(entry.getValue(), entry.getKey(), PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE);
                }
            }

        }

    }

    /**
     * Load the Jira configuration (statuses, priorities, severities).
     * 
     * The current mappings will be erased.
     */
    private void runLoadJiraConfig() throws PluginException {
        try {
            JiraConfig jiraConfig = getJiraService().getConfig();

            reloadConfiguration(REQUIREMENT_STATUS_MAPPING_CONFIGURATION_NAME, jiraConfig.getStatuses(), "status");

            reloadConfiguration(REQUIREMENT_PRIORITY_MAPPING_CONFIGURATION_NAME, jiraConfig.getPriorities(), "priority");

            reloadConfiguration(REQUIREMENT_SEVERITY_MAPPING_CONFIGURATION_NAME, jiraConfig.getSeverities(), "severity");

        } catch (Exception e) {
            throw new PluginException(e);
        }
    }

    /**
     * Reload the configuration of a mapping block.
     * 
     * The current values that are still existing are kept.
     * 
     * @param pluginConfigurationBlockDescriptor
     *            the plugin configuration block descriptor
     * @param values
     *            the new values
     * @param label
     *            the label
     */
    private void reloadConfiguration(String pluginConfigurationBlockDescriptorIdentifier, List<String> values, String label) throws PluginException {

        IPluginConfigurationBlockDescriptor pluginConfigurationBlockDescriptor = getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors()
                .get(pluginConfigurationBlockDescriptorIdentifier);
        PropertiesConfiguration currentMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(pluginConfigurationBlockDescriptor));

        String mapping = "#pre-loaded configuration on " + new Date() + "\n";
        mapping += "#format: {jira_" + label + "_id}={bizdock_requirement_" + label + "_id}\n";

        for (String value : values) {
            String key = getIdFromValue(value);
            if (!currentMapping.containsKey(key)) {
                mapping += key + "=\n";
            } else {
                mapping += key + "=" + currentMapping.getString(key) + "\n";
            }
        }

        getPluginContext().setConfiguration(pluginConfigurationBlockDescriptor, mapping.getBytes());
    }

    /**
     * Get the plugin context.
     */
    protected IPluginContext getPluginContext() {
        return pluginContext;
    }

    /**
     * Return true if the server is available.
     */
    private boolean isAvailable() {
        return getJiraService().isAvailable();
    }

    /**
     * Return the number of minutes until the next "start time".
     */
    private long howMuchMinutesUntilStartTime() {
        String time = this.peLoadStartTime;
        Date today = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(time.substring(0, 2)));
        calendar.set(Calendar.MINUTE, Integer.valueOf(time.substring(3, 5)));
        if (calendar.getTime().before(today)) {
            calendar.add(Calendar.DATE, 1);
        }
        long diff = calendar.getTime().getTime() - today.getTime();
        return diff / (60 * 1000);
    }

    /**
     * Get an instance of the Jira service.
     */
    private JiraService getJiraService() {
        return JiraService.get(getWsClient(), this.apiVersion, this.hostUrl, this.authenticationKey);
    }

    /* HELPERS */

    /**
     * Get the properties for a portfolio entry registration as a byte array.
     * 
     * @param needs
     *            set to true if the needs should be synchronized
     * @param defects
     *            set to true if the defects should be synchronized
     */
    public static byte[] getPropertiesForPortfolioEntryAsByte(boolean needs, boolean defects) {

        PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration();
        propertiesConfiguration.addProperty("needs", needs);
        propertiesConfiguration.addProperty("defects", defects);

        Properties properties = ConfigurationConverter.getProperties(propertiesConfiguration);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            properties.store(buffer, "properties");
            return buffer.toByteArray();
        } catch (IOException e) {
            Logger.error("impossible to store the properties in the buffer", e);
            return null;
        }

    }

    /**
     * Get the value of a boolean property from a list of properties.
     * 
     * If the property doesn't exist then return false.
     * 
     * @param propertiesConfiguration
     *            the available properties
     * @param propertyName
     *            the property name
     */
    public static boolean getBooleanProperty(PropertiesConfiguration propertiesConfiguration, String propertyName) {
        try {
            return propertiesConfiguration.getBoolean(propertyName);
        } catch (Exception e) {
            return false;
        }

    }

    /**
     * Get the id from a value.
     * 
     * This method could be used to convert a status string value to an id, for
     * example "In progress" to "IN_PROGRESS".
     * 
     * @param value
     *            the value
     */
    public static String getIdFromValue(String value) {
        if (value != null) {
            value = value.toUpperCase();
            value = value.replaceAll("[^A-Z]+", "_");
        }
        return value;
    }

    /**
     * Remove the last character of an url if it is a slash.
     * 
     * @param hostUrl
     *            the url
     */
    public static String removeLastSlash(String hostUrl) {
        if (hostUrl.length() > 0 && hostUrl.charAt(hostUrl.length() - 1) == '/') {
            hostUrl = hostUrl.substring(0, hostUrl.length() - 1);
        }
        return hostUrl;
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return this.pluginActions;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        final String menuLabel = getPluginContext().getPluginConfigurationName();
        return new IPluginMenuDescriptor() {

            @Override
            public String getPath() {
                return hostUrl;
            }

            @Override
            public String getLabel() {
                return menuLabel;
            }
        };
    }

    private ISysAdminUtils getSysAdminUtils() {
        return sysAdminUtils;
    }

    private WSClient getWsClient() {
        return wsClient;
    }

}
