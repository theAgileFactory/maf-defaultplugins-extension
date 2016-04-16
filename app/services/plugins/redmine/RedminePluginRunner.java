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
package services.plugins.redmine;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.User;
import com.taskadapter.redmineapi.bean.Version;

import akka.actor.Cancellable;
import constants.MafDataType;
import dao.delivery.IterationDAO;
import dao.delivery.RequirementDAO;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import framework.commons.message.EventMessage;
import framework.commons.message.EventMessage.MessageType;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.services.system.ISysAdminUtils;
import models.delivery.Iteration;
import models.delivery.Requirement;
import models.framework_models.common.CustomAttributeDefinition;
import models.framework_models.common.ICustomAttributeValue;
import models.pmo.Actor;
import models.pmo.PortfolioEntry;
import play.Logger;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * The abstract redmine plugin runner. It allows to manage the synchronisation
 * between redmine issues and portfolio entries.
 * 
 * @author Pierre-Yves Cloux
 * @author Johann Kohler
 */
public abstract class RedminePluginRunner implements IPluginRunner {

    private static final int REDMINE_API_ISSUES_LIMIT = 100;

    /**
     * List of actions.
     * 
     * @author Johann Kohler
     * 
     */
    public static enum ActionMessage {
        TRIGGER_PE_LOAD;
    }

    public static final String REQUIREMENT_STATUS_MAPPING_CONFIGURATION_NAME = "requirement_status_mapping";
    public static final String REQUIREMENT_PRIORITY_MAPPING_CONFIGURATION_NAME = "requirement_priority_mapping";
    public static final String REQUIREMENT_SEVERITY_MAPPING_CONFIGURATION_NAME = "requirement_severity_mapping";

    public static final String MAIN_CONFIGURATION_NAME = "config";
    public static final String REDMINE_HOST_URL_PROPERTY = "redmine.host.url";
    public static final String REDMINE_API_KEY_PROPERTY = "redmine.api.key";
    public static final String IS_SCOPED_CUSTOM_FIELD_ID = "is_scoped.custom_field.id";
    public static final String STORY_POINTS_CUSTOM_FIELD_ID = "story_points.custom_field.id";
    public static final String REMAINING_EFFORT_CUSTOM_FIELD_ID = "remaining_effort.custom_field.id";

    // properties for the portfolio entries
    public static final String PE_ITERATIONS_FILTER_PROPERTY = "pe.iterations.filter";
    public static final String PE_NEEDS_FILTER_PROPERTY = "pe.needs.filter";
    public static final String PE_NEEDS_TRACKERS_PROPERTY = "pe.needs.trackers";
    public static final String PE_DEFECTS_FILTER_PROPERTY = "pe.defects.filter";
    public static final String PE_DEFECTS_TRACKERS_PROPERTY = "pe.defects.trackers";
    public static final String PE_LOAD_START_TIME_PROPERTY = "pe.load.start.time";
    public static final String PE_LOAD_FREQUENCY_PROPERTY = "pe.load.frequency";

    protected IPluginContext pluginContext;

    protected String redmineHost;
    protected String apiAccessKey;

    protected Integer isScopedCustomFieldId;
    protected Integer storyPointsCustomFieldId;
    protected Integer remainingEffortFieldId;

    protected String peLoadStartTime;
    protected FiniteDuration peLoadFrequency;
    protected Cancellable currentScheduler;

    protected String[] peIterationsFilter;

    protected String[] peNeedsFilter;
    protected String[] peNeedsTrackers;

    protected String[] peDefectsFilter;
    protected String[] peDefectsTrackers;

    protected String peIssuesPatternUrl;

    protected Map<Integer, Long> requirementStatusMapping;
    protected Map<Integer, Long> requirementPriorityMapping;
    protected Map<Integer, Long> requirementSeverityMapping;

    public static final String PORTFOLIO_ENTRY_LINK_TYPE = "PORTFOLIO_ENTRY";
    public static final String PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE = "PORTFOLIO_ENTRY_REQUIREMENT";
    public static final String PORTFOLIO_ENTRY_ITERATION_LINK_TYPE = "PORTFOLIO_ENTRY_ITERATION";

    protected static final int MINIMAL_FREQUENCY = 5;

    private ISysAdminUtils sysAdminUtils;

    public RedminePluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils) {
        this.pluginContext = pluginContext;
        this.sysAdminUtils = sysAdminUtils;
    }

    /**
     * Start the plugin.
     * 
     * @param properties
     *            the main properties
     */
    public void start(PropertiesConfiguration properties) throws PluginException {
        getPluginContext().log(LogLevel.DEBUG, "Redmine plugin checking properties");
        properties.setThrowExceptionOnMissing(true);
        this.apiAccessKey = properties.getString(REDMINE_API_KEY_PROPERTY);
        this.redmineHost = properties.getString(REDMINE_HOST_URL_PROPERTY);

        try {
            this.isScopedCustomFieldId = properties.getInt(IS_SCOPED_CUSTOM_FIELD_ID);
        } catch (Exception e) {
            this.isScopedCustomFieldId = null;
        }
        try {
            this.storyPointsCustomFieldId = properties.getInt(STORY_POINTS_CUSTOM_FIELD_ID);
        } catch (Exception e) {
            this.storyPointsCustomFieldId = null;
        }
        try {
            this.remainingEffortFieldId = properties.getInt(REMAINING_EFFORT_CUSTOM_FIELD_ID);
        } catch (Exception e) {
            this.remainingEffortFieldId = null;
        }

        this.peLoadStartTime = properties.getString(PE_LOAD_START_TIME_PROPERTY);
        if (this.peLoadStartTime == null || !this.peLoadStartTime.matches("^([01]?[0-9]|2[0-3])h[0-5][0-9]$")) {
            throw new IllegalArgumentException("Invalid time format for the " + PE_LOAD_START_TIME_PROPERTY + " parameter");
        }
        this.peLoadFrequency = FiniteDuration.create(properties.getLong(PE_LOAD_FREQUENCY_PROPERTY), TimeUnit.MINUTES);
        if (properties.getLong(PE_LOAD_FREQUENCY_PROPERTY) < MINIMAL_FREQUENCY) {
            throw new IllegalArgumentException("Invalid frequency " + PE_LOAD_FREQUENCY_PROPERTY + " must be more than 5 minutes");
        }

        this.peIterationsFilter = null;
        String peIterationsFilterString = properties.getString(PE_ITERATIONS_FILTER_PROPERTY);
        if (peIterationsFilterString != null && !peIterationsFilterString.equals("")) {
            String[] peIterationsFilter = peIterationsFilterString.split("=");
            if (peIterationsFilter.length == 2) {
                this.peIterationsFilter = peIterationsFilter;
            }
        }

        this.peNeedsFilter = null;
        String peNeedsFilterString = properties.getString(PE_NEEDS_FILTER_PROPERTY);
        if (peNeedsFilterString != null && !peNeedsFilterString.equals("")) {
            String[] peNeedsFilter = peNeedsFilterString.split("=");
            if (peNeedsFilter.length == 2) {
                this.peNeedsFilter = peNeedsFilter;
            }
        }

        this.peNeedsTrackers = null;
        String peNeedsTrackersString = properties.getString(PE_NEEDS_TRACKERS_PROPERTY);
        if (peNeedsTrackersString != null && !peNeedsTrackersString.equals("")) {
            this.peNeedsTrackers = peNeedsTrackersString.split(";");
        }

        this.peDefectsFilter = null;
        String peDefectsFilterString = properties.getString(PE_DEFECTS_FILTER_PROPERTY);
        if (peDefectsFilterString != null && !peDefectsFilterString.equals("")) {
            String[] peDefectsFilter = peDefectsFilterString.split("=");
            if (peDefectsFilter.length == 2) {
                this.peDefectsFilter = peDefectsFilter;
            }
        }

        this.peDefectsTrackers = null;
        String peDefectsTrackersString = properties.getString(PE_DEFECTS_TRACKERS_PROPERTY);
        if (peDefectsTrackersString != null && !peDefectsTrackersString.equals("")) {
            this.peDefectsTrackers = peDefectsTrackersString.split(";");
        }

        this.peIssuesPatternUrl = properties.getString(REDMINE_HOST_URL_PROPERTY) + "/issues/{externalId}";

        if (!isAvailable()) {
            throw new PluginException("Server not available : stopping");
        }

        getPluginContext().log(LogLevel.DEBUG, "Preparing the scheduler");
        try {
            long howMuchMinutesUntilStartTime = howMuchMinutesUntilStartTime();
            currentScheduler = getSysAdminUtils().scheduleRecurring(true,
                    "RedminePlugin PortfolioEntry load " + getPluginContext().getPluginConfigurationName(),
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
        this.requirementStatusMapping = new HashMap<Integer, Long>();
        PropertiesConfiguration statusMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(REQUIREMENT_STATUS_MAPPING_CONFIGURATION_NAME)));
        for (Iterator<String> iter = statusMapping.getKeys(); iter.hasNext();) {
            String key = iter.next();
            Integer redmineStatusId = Integer.parseInt(key);
            this.requirementStatusMapping.put(redmineStatusId, statusMapping.getLong(key));
        }

        // get the requirement priority mapping
        this.requirementPriorityMapping = new HashMap<Integer, Long>();
        PropertiesConfiguration priorityMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(REQUIREMENT_PRIORITY_MAPPING_CONFIGURATION_NAME)));
        for (Iterator<String> iter = priorityMapping.getKeys(); iter.hasNext();) {
            String key = iter.next();
            Integer redminePriorityId = Integer.parseInt(key);
            this.requirementPriorityMapping.put(redminePriorityId, priorityMapping.getLong(key));
        }

        // get the requirement severity mapping
        this.requirementSeverityMapping = new HashMap<Integer, Long>();
        PropertiesConfiguration severityMapping = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(REQUIREMENT_SEVERITY_MAPPING_CONFIGURATION_NAME)));
        for (Iterator<String> iter = severityMapping.getKeys(); iter.hasNext();) {
            String key = iter.next();
            Integer redminePriorityId = Integer.parseInt(key);
            this.requirementSeverityMapping.put(redminePriorityId, severityMapping.getLong(key));
        }

        getPluginContext().log(LogLevel.INFO, "Redmine plugin started");

    }

    /**
     * Return true if the server is available.
     */
    private boolean isAvailable() {
        RedmineManager redmineManager = null;
        try {
            redmineManager = getRedmineManager();
            User user = redmineManager.getUserManager().getCurrentUser();
            return user != null;
        } catch (Exception e) {
            getPluginContext().log(LogLevel.ERROR, "The server seems not accessible", e);

            return false;
        } finally {
            if (redmineManager != null) {
                redmineManager.shutdown();
            }
        }
    }

    @Override
    public void stop() {
        try {
            if (this.currentScheduler != null) {
                this.currentScheduler.cancel();
                getPluginContext().log(LogLevel.INFO, "Scheduler stopped");
            }
        } catch (Exception e) {
            getPluginContext().log(LogLevel.ERROR, "Error when stopping the redmine plugin scheduler", e);
        }
        getPluginContext().log(LogLevel.INFO, "Redmine plugin stopped");
    }

    @Override
    public synchronized void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {

        if (eventMessage.getMessageType().equals(MessageType.CUSTOM) && eventMessage.getPayload() != null
                && eventMessage.getPayload() instanceof ActionMessage) {
            switch ((ActionMessage) eventMessage.getPayload()) {
            case TRIGGER_PE_LOAD:
                runPortfolioEntryLoad(eventMessage.getInternalId());
                break;
            }

        }
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
            this.getPluginContext().log(LogLevel.ERROR, "error when running runPortfolioEntryLoad from the scheduler", e);
        }
    }

    /**
     * Run the load for a portfolio entry.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     */
    private void runPortfolioEntryLoad(Long portfolioEntryId) throws PluginException {

        if (getPluginContext().isRegistered(MafDataType.getPortfolioEntry(), portfolioEntryId)) {

            // get the registration configuration properties
            PropertiesConfiguration registrationProperties = getPluginContext()
                    .getPropertiesConfigurationFromByteArray(pluginContext.getRegistrationConfiguration(MafDataType.getPortfolioEntry(), portfolioEntryId));

            // get the portfolio entry
            PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(portfolioEntryId);

            // load the iterations
            if (getBooleanProperty(registrationProperties, "iterations")) {
                loadIterations(portfolioEntry);
            }

            // load the needs
            if (getBooleanProperty(registrationProperties, "needs")) {
                loadRequirements(portfolioEntry, false);
            }

            // load the defects
            if (getBooleanProperty(registrationProperties, "defects")) {
                loadRequirements(portfolioEntry, true);
            }

        } else {
            this.getPluginContext().log(LogLevel.ERROR, "runPortfolioEntryLoad: the portfolio entry " + portfolioEntryId + " is not registered");
        }

    }

    /**
     * Load the iterations of a portfolio entry.
     * 
     * @param portfolioEntry
     *            the portfolio entry
     */
    private void loadIterations(PortfolioEntry portfolioEntry) {

        // load the filter
        Integer customFieldIdFilter = null;
        String peValueFilter = null;
        if (this.peIterationsFilter != null) {

            try {
                // extract the id of the custom field from the pattern cf_{id}
                String[] tmp = peIterationsFilter[0].split("_");
                customFieldIdFilter = Integer.valueOf(tmp[1]);
            } catch (Exception e) {
                Logger.warn("loadIterations: impossible to identify the redmine custom field for " + peIterationsFilter[0]);
                EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
                this.getPluginContext().reportOnEventHandling("", true, eventMessage,
                        "An error has occured when loading the iterations: impossible to identify the Redmine custom field with id " + peIterationsFilter[0]);
            }

            if (customFieldIdFilter != null) {

                try {

                    // try with direct PE attributes
                    Class<?> c = portfolioEntry.getClass();
                    Field f = c.getDeclaredField(peIterationsFilter[1]);
                    f.setAccessible(true);
                    if (f.get(portfolioEntry) != null) {
                        peValueFilter = f.get(portfolioEntry).toString().trim().toLowerCase();
                    } else {
                        peValueFilter = "";
                    }

                } catch (Exception e) {

                    // else, try with custom attributes of the PE
                    ICustomAttributeValue caValue = null;
                    try {
                        caValue = CustomAttributeDefinition.getCustomAttributeValue(peIterationsFilter[1], PortfolioEntry.class, portfolioEntry.id);
                    } catch (Exception e1) {
                    }

                    if (caValue != null) {
                        if (caValue.getValueAsObject() != null) {
                            peValueFilter = caValue.getValueAsObject().toString().trim().toLowerCase();
                        } else {
                            peValueFilter = "";
                        }
                    } else {
                        Logger.warn("loadIterations: impossible to load the PE field or custom attribute for the filter " + peIterationsFilter[1]);
                        EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
                        this.getPluginContext().reportOnEventHandling("", true, eventMessage,
                                "An error has occured when loading the iterations: impossible to identify the BizDock attribute with id "
                                        + peIterationsFilter[1]);

                    }

                }

            }
        }

        // get the redmine manager
        RedmineManager redmineManager = getRedmineManager();

        try {

            for (String externalId : getPluginContext().getMultipleExternalId(portfolioEntry.id, PORTFOLIO_ENTRY_LINK_TYPE)) {

                // initialize the iterations to remove
                Map<String, Long> iterationsToRemove = new HashMap<String, Long>();
                for (Pair<Long, String> child : pluginContext.getChildrenIdOfLink(portfolioEntry.id, externalId, PORTFOLIO_ENTRY_LINK_TYPE,
                        PORTFOLIO_ENTRY_ITERATION_LINK_TYPE)) {
                    iterationsToRemove.put(child.getRight(), child.getLeft());
                }

                Project project = redmineManager.getProjectManager().getProjectByKey(externalId);
                List<Version> versions = redmineManager.getProjectManager().getVersions(Integer.parseInt(externalId));

                for (Version version : versions) {

                    if (peValueFilter == null || customFieldIdFilter == null
                            || (version.getCustomFieldById(customFieldIdFilter) != null
                                    && ((version.getCustomFieldById(customFieldIdFilter).getValue() != null
                                            && version.getCustomFieldById(customFieldIdFilter).getValue().trim().toLowerCase().equals(peValueFilter))
                                            || (version.getCustomFieldById(customFieldIdFilter).getValue() == null && peValueFilter.equals(""))))) {

                        String versionId = String.valueOf(version.getId());

                        iterationsToRemove.remove(versionId);

                        // try to get the iteration id
                        Long iterationId = pluginContext.getUniqueInternalIdWithParent(versionId, PORTFOLIO_ENTRY_ITERATION_LINK_TYPE, portfolioEntry.id,
                                externalId, PORTFOLIO_ENTRY_LINK_TYPE);

                        Iteration iteration = null;
                        if (iterationId != null) {
                            iteration = IterationDAO.getIterationById(iterationId);
                        } else {
                            iteration = new Iteration();
                            iteration.portfolioEntry = portfolioEntry;
                            iteration.storyPoints = null;
                        }

                        iteration.description = version.getDescription();
                        iteration.endDate = version.getDueDate();
                        if (version.getStatus() != null && version.getStatus().equals("closed")) {
                            iteration.isClosed = true;
                        } else {
                            iteration.isClosed = false;
                        }
                        iteration.name = version.getName();
                        iteration.startDate = null;
                        iteration.source = "Redmine - " + project.getName();

                        iteration.save();

                        // save the link
                        if (iterationId == null) {
                            try {
                                getPluginContext().createLink(iteration.id, versionId, PORTFOLIO_ENTRY_ITERATION_LINK_TYPE, portfolioEntry.id, externalId,
                                        PORTFOLIO_ENTRY_LINK_TYPE);
                            } catch (PluginException e) {
                                this.getPluginContext().log(LogLevel.ERROR, "impossible to create the iteration link", e);
                                iteration.doDelete();
                            }
                        }

                    }

                }

                // remove the iterations
                for (Map.Entry<String, Long> entry : iterationsToRemove.entrySet()) {
                    Iteration iteration = IterationDAO.getIterationById(entry.getValue());
                    if (iteration != null) {
                        iteration.doDelete();
                        pluginContext.deleteLink(entry.getValue(), entry.getKey(), PORTFOLIO_ENTRY_ITERATION_LINK_TYPE);
                    }
                }

            }
        } catch (RedmineException e) {
            Logger.warn("runPortfolioEntryLoad: impossible to get the versions for the portfolio entry " + portfolioEntry.id, e);
            EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
            this.getPluginContext().reportOnEventHandling("", true, eventMessage, "An error has occurred when loading the versions for the Initiative "
                    + portfolioEntry.id + ". The message returned by Redmine is: " + e.getMessage());
        }
    }

    /**
     * Load the requirements of a portfolio entry.
     * 
     * @param portfolioEntry
     *            the portfolio entry
     * @param isDefect
     *            set to true for the defects, set to false for the needs
     */
    private void loadRequirements(PortfolioEntry portfolioEntry, boolean isDefect) {

        String[] trackers = null;
        String[] filter = null;

        if (isDefect) {
            trackers = this.peDefectsTrackers;
            filter = this.peDefectsFilter;
        } else {
            trackers = this.peNeedsTrackers;
            filter = this.peNeedsFilter;
        }

        // get the redmine manager
        RedmineManager redmineManager = getRedmineManager();

        if (trackers != null) {

            Map<String, String> params = new HashMap<String, String>();
            params.put("status_id", "*");

            String categoryFilter = null;
            if (filter != null) {

                String peValueFilter = null;

                try {

                    // try with direct PE attributes
                    Class<?> c = portfolioEntry.getClass();
                    Field f = c.getDeclaredField(filter[1]);
                    f.setAccessible(true);

                    if (f.get(portfolioEntry) != null) {
                        peValueFilter = f.get(portfolioEntry).toString().trim();
                    } else {
                        peValueFilter = "";
                    }

                } catch (Exception e) {

                    // else, try with custom attributes of the PE
                    ICustomAttributeValue caValue = null;
                    try {
                        caValue = CustomAttributeDefinition.getCustomAttributeValue(filter[1], PortfolioEntry.class, portfolioEntry.id);
                    } catch (Exception e1) {
                    }

                    if (caValue != null) {
                        if (caValue.getValueAsObject() != null) {
                            peValueFilter = caValue.getValueAsObject().toString().trim();
                        } else {
                            peValueFilter = "";
                        }

                    } else {
                        Logger.warn("loadRequirements: impossible to load the PE field or custom attribute for the filter " + filter[1]);
                        EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
                        this.getPluginContext().reportOnEventHandling("", true, eventMessage,
                                "An error has occured when loading the requirements: impossible to identify the BizDock attribute with id " + filter[1]);

                    }

                }

                if (peValueFilter != null) {
                    if (filter[0].equals("category")) {
                        categoryFilter = peValueFilter.toLowerCase();
                    } else {
                        params.put(filter[0], peValueFilter);
                    }
                }

            }

            // get all Redmine users and create a map userId <=> login
            Map<Integer, String> users = new HashMap<Integer, String>();
            try {
                for (User user : redmineManager.getUserManager().getUsers()) {
                    users.put(user.getId(), user.getLogin());
                }
            } catch (RedmineException e) {
                Logger.warn("loadRequirements: impossible to get the Redmine users", e);
                EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
                this.getPluginContext().reportOnEventHandling("", true, eventMessage,
                        "An error has occured when loading the requirements: impossible to get the Redmine users");

            }

            try {

                for (String externalId : getPluginContext().getMultipleExternalId(portfolioEntry.id, PORTFOLIO_ENTRY_LINK_TYPE)) {

                    // initialize the requirements to remove
                    Map<String, Long> requirementsToRemove = new HashMap<String, Long>();
                    for (Pair<Long, String> child : pluginContext.getChildrenIdOfLink(portfolioEntry.id, externalId, PORTFOLIO_ENTRY_LINK_TYPE,
                            PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {
                        requirementsToRemove.put(child.getRight(), child.getLeft());
                    }

                    for (String trackerId : trackers) {

                        params.put("project_id", externalId);
                        params.put("tracker_id", trackerId);

                        List<Issue> issues = new ArrayList<>();
                        int loop = 0;
                        boolean stop = false;
                        while (!stop) {

                            String limit = String.valueOf(REDMINE_API_ISSUES_LIMIT);
                            String offset = String.valueOf(loop * REDMINE_API_ISSUES_LIMIT);

                            Logger.debug("loop: " + loop + ", offset: " + offset + ", limit:" + limit);

                            params.put("limit", limit);
                            params.put("offset", offset);

                            List<Issue> i = redmineManager.getIssueManager().getIssues(params);

                            if (i != null && i.size() > 0) {

                                Logger.debug("i: " + i);

                                issues.addAll(i);

                                if (i.size() < REDMINE_API_ISSUES_LIMIT) {
                                    Logger.debug("no more issue!");
                                    stop = true;
                                }
                            } else {
                                Logger.debug("no issue found!");
                                stop = true;
                            }

                            loop++;

                            /**
                             * Safety control: if there are more than 100*10000
                             * issues for one tracker and one issue, then this
                             * script is not compatible.
                             */
                            if (loop > 10000) {
                                stop = true;
                            }

                        }

                        for (Issue issue : issues) {

                            if (categoryFilter == null
                                    || (issue.getCategory() != null && categoryFilter.equals(issue.getCategory().getName().trim().toLowerCase()))) {

                                String issueId = String.valueOf(issue.getId());

                                requirementsToRemove.remove(issueId);

                                // try to get the requirement id
                                Long requirementId = pluginContext.getUniqueInternalIdWithParent(issueId, PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE,
                                        portfolioEntry.id, externalId, PORTFOLIO_ENTRY_LINK_TYPE);

                                Requirement requirement = null;
                                if (requirementId != null) {
                                    requirement = RequirementDAO.getRequirementById(requirementId);
                                } else {
                                    requirement = new Requirement();
                                    requirement.isDefect = isDefect;
                                    requirement.portfolioEntry = portfolioEntry;
                                }

                                // get the author
                                Actor author = null;
                                if (users.containsKey(issue.getAuthor().getId())) {
                                    author = ActorDao.getActorByUid(users.get(issue.getAuthor().getId()));
                                }

                                requirement.externalRefId = issueId;
                                requirement.externalLink = this.peIssuesPatternUrl.replace("{externalId}", requirement.externalRefId);
                                requirement.name = issue.getSubject();
                                requirement.description = issue.getDescription();
                                requirement.category = issue.getCategory() != null ? issue.getCategory().getName() : null;
                                requirement.requirementStatus = this.requirementStatusMapping.containsKey(issue.getStatusId())
                                        ? RequirementDAO.getRequirementStatusById(this.requirementStatusMapping.get(issue.getStatusId())) : null;
                                requirement.requirementPriority = this.requirementPriorityMapping.containsKey(issue.getPriorityId())
                                        ? RequirementDAO.getRequirementPriorityById(this.requirementPriorityMapping.get(issue.getPriorityId())) : null;
                                requirement.requirementSeverity = this.requirementSeverityMapping.containsKey(issue.getPriorityId())
                                        ? RequirementDAO.getRequirementSeverityById(this.requirementSeverityMapping.get(issue.getPriorityId())) : null;
                                requirement.author = author;

                                requirement.initialEstimation = issue.getEstimatedHours() != null
                                        ? new BigDecimal(issue.getEstimatedHours()).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue() : null;

                                if (this.storyPointsCustomFieldId != null && issue.getCustomFieldById(storyPointsCustomFieldId) != null) {
                                    requirement.storyPoints = convertIntegerFromRedmineToJava(issue.getCustomFieldById(storyPointsCustomFieldId).getValue());
                                } else {
                                    requirement.storyPoints = null;
                                }

                                if (this.isScopedCustomFieldId != null && issue.getCustomFieldById(isScopedCustomFieldId) != null) {
                                    requirement.isScoped = convertBooleanFromRedmineToJava(issue.getCustomFieldById(isScopedCustomFieldId).getValue());
                                } else {
                                    requirement.isScoped = null;
                                }

                                if (this.remainingEffortFieldId != null && issue.getCustomFieldById(remainingEffortFieldId) != null) {
                                    requirement.remainingEffort = convertDoubleFromRedmineToJava(issue.getCustomFieldById(remainingEffortFieldId).getValue());
                                } else {
                                    requirement.remainingEffort = null;
                                }

                                List<TimeEntry> timeEntries = redmineManager.getIssueManager().getTimeEntriesForIssue(issue.getId());
                                requirement.effort = 0d;
                                for (TimeEntry timeEntry : timeEntries) {
                                    requirement.effort += (double) timeEntry.getHours();
                                }

                                requirement.iteration = null;
                                if (issue.getTargetVersion() != null) {
                                    String versionId = String.valueOf(issue.getTargetVersion().getId());
                                    Long iterationId = pluginContext.getUniqueInternalIdWithParent(versionId, PORTFOLIO_ENTRY_ITERATION_LINK_TYPE,
                                            portfolioEntry.id, externalId, PORTFOLIO_ENTRY_LINK_TYPE);
                                    if (iterationId != null) {
                                        Iteration iteration = IterationDAO.getIterationById(iterationId);
                                        requirement.iteration = iteration;
                                    }
                                }

                                requirement.save();

                                // save the link
                                if (requirementId == null) {
                                    try {
                                        getPluginContext().createLink(requirement.id, issueId, PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE, portfolioEntry.id,
                                                externalId, PORTFOLIO_ENTRY_LINK_TYPE);
                                    } catch (PluginException e) {
                                        this.getPluginContext().log(LogLevel.ERROR, "impossible to create the requirement link", e);
                                        requirement.doDelete();
                                    }
                                }

                            }

                        }

                    }

                    // remove the requirements
                    for (Map.Entry<String, Long> entry : requirementsToRemove.entrySet()) {
                        Requirement requirement = RequirementDAO.getRequirementById(entry.getValue());
                        if (requirement != null && requirement.isDefect == isDefect) {
                            requirement.doDelete();
                            pluginContext.deleteLink(entry.getValue(), entry.getKey(), PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE);
                        }
                    }

                }
            } catch (RedmineException e) {
                Logger.warn("loadRequirements: impossible to get the requirements [isDefect=" + isDefect + "] for the portfolio entry " + portfolioEntry.id,
                        e);
                EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
                this.getPluginContext().reportOnEventHandling("", true, eventMessage,
                        "An error has occurred when loading the requirements for the Initiative " + portfolioEntry.id
                                + ". The message returned by Redmine is: " + e.getMessage());
            }

        } else {
            Logger.warn("loadRequirements: There is no tracker for the requirements [isDefect=" + isDefect + "]");
            EventMessage eventMessage = new EventMessage(portfolioEntry.id, MafDataType.getPortfolioEntry(), MessageType.CUSTOM);
            this.getPluginContext().reportOnEventHandling("", true, eventMessage,
                    "An error has occured when loading the requirements: you need to configure trackers for needs and defects.");

        }

    }

    @Override
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    /**
     * Get the plugin context.
     */
    protected IPluginContext getPluginContext() {
        return pluginContext;
    }

    /**
     * Get the redmine host.
     */
    protected String getRedmineHost() {
        return redmineHost;
    }

    /**
     * Get the API access key.
     */
    private String getApiAccessKey() {
        return apiAccessKey;
    }

    /**
     * Return the object which can access the JSON endpoint of Redmine.
     * 
     * @return a manager
     */
    protected RedmineManager getRedmineManager() {
        return RedmineManagerFactory.createWithApiKey(getRedmineHost(), getApiAccessKey());
    }

    /**
     * Shut down (silently) the {@link RedmineManager}.
     * 
     * @param redmineManager
     *            the redmine manager
     */
    protected void shutDownRedmineManager(RedmineManager redmineManager) {
        try {
            if (redmineManager != null) {
                redmineManager.shutdown();
            }
        } catch (Exception e) {
            this.getPluginContext().log(LogLevel.ERROR, "Error when stopping the redmine manager", e);
        }
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

    /* HELPERS */

    /**
     * Get the properties for a portfolio entry registration as a byte array.
     * 
     * @param needs
     *            set to true if the needs should be synchronized
     * @param defects
     *            set to true if the defects should be synchronized
     * @param iterations
     *            set to true if the iterations should be synchronized
     */
    public static byte[] getPropertiesForPortfolioEntryAsByte(boolean needs, boolean defects, boolean iterations) {

        PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration();
        propertiesConfiguration.addProperty("needs", needs);
        propertiesConfiguration.addProperty("defects", defects);
        propertiesConfiguration.addProperty("iterations", iterations);

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
     * Convert a redmine Boolean (represented by a string) to a Java Boolean.
     * 
     * @param redmineBoolean
     *            the redmine boolean
     */
    private static Boolean convertBooleanFromRedmineToJava(String redmineBoolean) {
        if (redmineBoolean != null) {
            if (redmineBoolean.equals("1")) {
                return true;
            } else if (redmineBoolean.equals("0")) {
                return false;
            }
        }
        return null;
    }

    /**
     * Convert a redmine Integer (represented by a string) to a Java Integer.
     * 
     * @param redmineInteger
     *            the redmine integer
     * @return
     */
    private static Integer convertIntegerFromRedmineToJava(String redmineInteger) {
        try {
            return Integer.valueOf(redmineInteger);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert a redmine Double (represented by a string) to a Java Integer.
     * 
     * @param redmineDouble
     *            the redmine double
     * @return
     */
    private static Double convertDoubleFromRedmineToJava(String redmineDouble) {
        try {
            return Double.valueOf(redmineDouble);
        } catch (Exception e) {
            return null;
        }
    }

    private ISysAdminUtils getSysAdminUtils() {
        return sysAdminUtils;
    }
}
