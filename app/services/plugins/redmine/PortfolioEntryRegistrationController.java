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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.tuple.Pair;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.RedmineProcessingException;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.ProjectFactory;

import constants.IMafConstants;
import constants.MafDataType;
import dao.delivery.IterationDAO;
import dao.delivery.RequirementDAO;
import dao.pmo.PortfolioEntryDao;
import framework.commons.DataType;
import framework.commons.message.EventMessage;
import framework.commons.message.EventMessage.MessageType;
import framework.security.ISecurityService;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.WebCommandPath;
import framework.services.ext.api.WebCommandPath.HttpMethod;
import framework.services.ext.api.WebControllerPath;
import framework.services.ext.api.WebParameter;
import framework.services.plugins.api.AbstractRegistrationConfiguratorController;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.services.storage.IAttachmentManagerPlugin;
import framework.services.system.ISysAdminUtils;
import framework.utils.DefaultSelectableValueHolder;
import framework.utils.DefaultSelectableValueHolderCollection;
import framework.utils.ISelectableValueHolderCollection;
import framework.utils.Msg;
import framework.utils.PickerHandler;
import framework.utils.PickerHandler.Handle;
import framework.utils.PickerHandler.Parameters;
import framework.utils.Utilities;
import models.delivery.Iteration;
import models.delivery.Requirement;
import models.pmo.PortfolioEntry;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.libs.F.Promise;
import play.mvc.Call;
import play.mvc.Result;
import scala.concurrent.duration.Duration;
import services.plugins.legacy.LegacyUtils;

/**
 * Manage the actions for the redmine configuration of a portfolio entry.
 * 
 * @author Pierre-Yves Cloux
 * @author Johann Kohler
 * 
 */
@WebControllerPath(path = "/redmine")
public class PortfolioEntryRegistrationController extends AbstractRegistrationConfiguratorController<RedminePluginRunner> {
    public static final String INITIAL_ACTION = "_root";
    public static Form<ConfigurationFormData> configurationFormTemplate = Form.form(ConfigurationFormData.class);
    public static Form<SelectProjectFormData> selectProjectFormTemplate = Form.form(SelectProjectFormData.class);

    // get actions
    public static final String REGISTRATION_CREATE_PROJECT_ACTION = "_registration_create_project";
    public static final String SELECT_PROJECT_ACTION = "_select_project";
    public static final String TRIGGER_LOAD_ACTION = "_trigger_load";
    public static final String REMOVE_PROJECT_LINK_ACTION = "remove_project_link";
    public static final String REMOVE_REGISTRATION_ACTION = "remove_registration";

    // post actions
    public static final String SAVE_CONFIGURATION_ACTION = "_save_configuration";
    public static final String SEARCH_PROJECT_ACTION = "_search_project";
    public static final String ADD_PROJECT_ACTION = "_add_project";

    private ISysAdminUtils sysAdminUtils;
    private ISecurityService securityService;
    private IAttachmentManagerPlugin attachmentManagerPlugin;

    /**
     * Default constructor.
     */
    @Inject
    public PortfolioEntryRegistrationController(ILinkGenerationService linkGenerationService, IPluginRunner pluginRunner, IPluginContext pluginContext,
            ISysAdminUtils sysAdminUtils, ISecurityService securityService, IAttachmentManagerPlugin attachmentManagerPlugin) {
        super(linkGenerationService, pluginRunner, pluginContext);
        this.sysAdminUtils = sysAdminUtils;
        this.securityService = securityService;
        this.attachmentManagerPlugin = attachmentManagerPlugin;
    }

    /**
     * Return the route to the registration configurator
     * 
     * @param method
     *            the HTTP method to be used
     * @param dataType
     *            the data type for the configurator
     * @param objectId
     *            the id of the object of the specified {@link DataType}
     * @param actionId
     *            the action Id to be passed to the controller
     * @return
     */
    public Call getRouteForRegistrationController(IPluginContext.HttpMethod method, DataType dataType, Long objectId, String actionId) {
        if (method.equals(IPluginContext.HttpMethod.GET)) {
            return LegacyUtils.callFromString(link("doGet", objectId, actionId));
        } else {
            return LegacyUtils.callFromString(link("doPost", objectId, actionId));
        }
    }

    @Override
    public Promise<Result> register(Long objectId) {
        return Promise.promise(() -> doGet(objectId, INITIAL_ACTION));
    }

    @WebCommandPath(httpMethod = HttpMethod.GET, id = "doGet", path = "/doGet/:objectId/:actionId")
    public Result performGet(@WebParameter(name = "objectId") Long objectId, @WebParameter(name = "actionId") String actionId) {
        return doGet(objectId, actionId);
    }

    @WebCommandPath(httpMethod = HttpMethod.POST, id = "doPost", path = "/doPost/:objectId/:actionId")
    public Result performPost(@WebParameter(name = "objectId") Long objectId, @WebParameter(name = "actionId") String actionId) {
        return doPost(objectId, actionId);
    }

    public Result doGet(final Long objectId, String actionId) {
        ISecurityService securityService = this.getSecurityService();
        // security check
        if (!securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION, "", objectId)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // get the instance of the redmine plugin
        switch (actionId) {

        /*
         * If the portfolio entry is registered: display its configuration and
         * the related Redmine projects with update capabilities.<br/>
         * 
         * Else: the user can either create a Redmine Project with the portfolio
         * entry data, or simply select an existing Redmine project and
         * associate it to the portfolio entry.
         */
        case INITIAL_ACTION:
            if (getPluginContext().isRegistered(MafDataType.getPortfolioEntry(), objectId)) {

                // get the registration configuration properties
                PropertiesConfiguration propertiesConfiguration = null;
                try {
                    propertiesConfiguration = getPluginContext().getPropertiesConfigurationFromByteArray(
                            getPluginContext().getRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId));
                } catch (PluginException e) {
                    Logger.error("impossible to load the registration configuration", e);
                }

                // initiate the form
                Form<ConfigurationFormData> configurationForm = configurationFormTemplate
                        .fill(new ConfigurationFormData(RedminePluginRunner.getBooleanProperty(propertiesConfiguration, "needs"),
                                RedminePluginRunner.getBooleanProperty(propertiesConfiguration, "defects"),
                                RedminePluginRunner.getBooleanProperty(propertiesConfiguration, "iterations")));

                // get the related Redmine projects
                Map<String, Project> projects = new HashMap<>();
                try {
                    for (String externalId : getPluginContext().getMultipleExternalId(objectId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {
                        Project project = getRedmineManager().getProjectManager().getProjectByKey(externalId);
                        projects.put(externalId, project);
                    }
                } catch (Exception e) {
                    Logger.error("impossible to get the Redmine projects of the portfolio entry " + objectId, e);
                }

                boolean isInitiative = PortfolioEntryDao.getPEById(objectId).portfolioEntryType != null
                        && !PortfolioEntryDao.getPEById(objectId).portfolioEntryType.isRelease;

                return ok(views.html.plugins.redmine.portfolioentry_index.render(getRoutes(objectId), objectId, MafDataType.getPortfolioEntry(),
                        getPluginContext().getPluginConfigurationName(), configurationForm, isInitiative, projects, getPluginContext().getPluginDescriptor(),
                        getRedmineHostUrl()));
            } else {
                return ok(views.html.plugins.redmine.portfolioentry_not_registered.render(getRoutes(objectId), objectId, MafDataType.getPortfolioEntry(),
                        getPluginContext().getPluginConfigurationName()));
            }

            /*
             * Process the creation of a Redmine project and associate it to the
             * portfolio entry. So the portfolio entry becomes "registered".
             */
        case REGISTRATION_CREATE_PROJECT_ACTION:

            try {

                // get the portfolio entry
                PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(objectId);

                // create the project in Redmine
                Project redmineProject = ProjectFactory.create(portfolioEntry.getName(), "pe" + String.valueOf(objectId));
                redmineProject.setDescription(portfolioEntry.getDescription());
                redmineProject.setCreatedOn(new Date());

                redmineProject = getRedmineManager().getProjectManager().createProject(redmineProject);
                if (redmineProject.getId() <= 0) {
                    throw new RedmineException("Project was not created (post creation id is invalid)");
                }

                // add the link beetween the created project and the portfolio
                // entry
                getPluginContext().createLink(objectId, String.valueOf(redmineProject.getId()), RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

                // create the registration entry (to specify explicitly that
                // the portfolio entry is registered)
                getPluginContext().setRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId,
                        RedminePluginRunner.getPropertiesForPortfolioEntryAsByte(false, false, false));

                Utilities.sendSuccessFlashMessage(Msg.get("plugin.redmine.portfolio_entry.not_registered.create.successfull"));

            } catch (Exception e) {

                boolean alreadyExisting = false;
                if (e instanceof RedmineProcessingException && ((RedmineProcessingException) e).getErrors().contains("Identifier has already been taken")) {
                    alreadyExisting = true;
                }

                if (alreadyExisting) {
                    Logger.warn("Unable to create the Redmine project for the portfolio entry " + objectId + ", because it is already existing", e);
                    Utilities.sendInfoFlashMessage(Msg.get("plugin.redmine.portfolio_entry.not_registered.create.warn.already_existing"));
                    return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId,
                            SELECT_PROJECT_ACTION));

                } else {
                    Logger.error("Unable to create the Redmine project for the portfolio entry " + objectId, e);
                    Utilities.sendErrorFlashMessage(Msg.get("plugin.redmine.portfolio_entry.not_registered.create.error"));
                }
            }

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Display the form to select a Redmine project in order to associate it
         * to the portfolio entry.
         */
        case SELECT_PROJECT_ACTION:
            return ok(views.html.plugins.redmine.portfolioentry_select_project.render(getRoutes(objectId), objectId, MafDataType.getPortfolioEntry(),
                    getPluginContext().getPluginConfigurationName(), selectProjectFormTemplate));

        /*
         * Trigger the load job of the Redmin plugin for the portfolio entry.
         */
        case TRIGGER_LOAD_ACTION:

            EventMessage eventMessage = new EventMessage(objectId, null, MessageType.CUSTOM, getPluginContext().getPluginConfigurationId(),
                    RedminePluginRunner.ActionMessage.TRIGGER_PE_LOAD);
            getPluginContext().postOutMessage(eventMessage);

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.redmine.portfolio_entry.trigger_load.successfull"));
            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Remove the link with a Redmine project.
         */
        case REMOVE_PROJECT_LINK_ACTION:

            final String externalId = request().getQueryString("externalId");

            // Execute asynchronously
            getSysAdminUtils().scheduleOnce(true, "REMOVE_PROJECT_LINK_ACTION", Duration.create(0, TimeUnit.MILLISECONDS), new Runnable() {
                @Override
                public void run() {

                    // remove the requirements
                    for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalId,
                            RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, RedminePluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {
                        Requirement requirement = RequirementDAO.getRequirementById(child.getLeft());
                        if (requirement != null) {
                            requirement.doDelete();
                        }
                    }

                    // delete the iterations
                    for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalId,
                            RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, RedminePluginRunner.PORTFOLIO_ENTRY_ITERATION_LINK_TYPE)) {
                        Iteration iteration = IterationDAO.getIterationById(child.getLeft());
                        if (iteration != null) {
                            iteration.doDelete();
                        }
                    }

                    getPluginContext().deleteLink(objectId, externalId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

                }
            });

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.redmine.portfolio_entry.remove_project_link.successfull"));
            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Remove the registration of a portfolio entry.
         */
        case REMOVE_REGISTRATION_ACTION:

            // Execute asynchronously
            getSysAdminUtils().scheduleOnce(true, "REMOVE_REGISTRATION_ACTION", Duration.create(0, TimeUnit.MILLISECONDS), new Runnable() {
                @Override
                public void run() {
                    for (String externalIdToRemove : getPluginContext().getMultipleExternalId(objectId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {

                        for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalIdToRemove,
                                RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, RedminePluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {
                            Requirement requirement = RequirementDAO.getRequirementById(child.getLeft());
                            if (requirement != null) {
                                requirement.doDelete();
                            }
                        }

                        for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalIdToRemove,
                                RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, RedminePluginRunner.PORTFOLIO_ENTRY_ITERATION_LINK_TYPE)) {
                            Iteration iteration = IterationDAO.getIterationById(child.getLeft());
                            if (iteration != null) {
                                iteration.doDelete();
                            }
                        }

                        getPluginContext().deleteLink(objectId, externalIdToRemove, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

                    }
                }
            });

            getPluginContext().removeRegistration(MafDataType.getPortfolioEntry(), objectId);

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.redmine.portfolio_entry.remove_registration.successfull"));
            return redirect(controllers.core.routes.PortfolioEntryController.pluginConfig(objectId));

        default:
            return notFound(views.html.error.not_found.render(""));

        }

    }

    public Result doPost(final Long objectId, String actionId) {
        ISecurityService securityService = this.getSecurityService();
        // security check
        if (!securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION, "", objectId)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // get the instance of the redmine plugin
        switch (actionId) {

        /*
         * Save the properties of the registration configuration for the
         * portfolio entry.
         */
        case SAVE_CONFIGURATION_ACTION:

            Form<ConfigurationFormData> configurationBoundForm = configurationFormTemplate.bindFromRequest();
            final ConfigurationFormData configurationFormData = configurationBoundForm.get();

            getPluginContext().setRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId, RedminePluginRunner
                    .getPropertiesForPortfolioEntryAsByte(configurationFormData.needs, configurationFormData.defects, configurationFormData.iterations));

            // Execute asynchronously
            getSysAdminUtils().scheduleOnce(true, "SAVE_CONFIGURATION_ACTION", Duration.create(0, TimeUnit.MILLISECONDS), new Runnable() {
                @Override
                public void run() {

                    // if necessary, remove the existing requirements
                    if (configurationFormData.needs == false || configurationFormData.defects == false) {

                        for (String externalId : getPluginContext().getMultipleExternalId(objectId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {

                            for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalId,
                                    RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, RedminePluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {

                                Requirement requirement = RequirementDAO.getRequirementById(child.getLeft());
                                if (requirement != null) {
                                    if (configurationFormData.needs == false && requirement.isDefect == false) {
                                        requirement.doDelete();
                                        getPluginContext().deleteLink(child.getLeft(), child.getRight(),
                                                RedminePluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE);
                                    }
                                    if (configurationFormData.defects == false && requirement.isDefect == true) {
                                        requirement.doDelete();
                                        getPluginContext().deleteLink(child.getLeft(), child.getRight(),
                                                RedminePluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE);
                                    }
                                }

                            }

                        }
                    }

                    // if necessary, delete the existing iterations
                    if (configurationFormData.iterations == false) {

                        for (String externalId : getPluginContext().getMultipleExternalId(objectId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {

                            for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalId,
                                    RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, RedminePluginRunner.PORTFOLIO_ENTRY_ITERATION_LINK_TYPE)) {

                                Iteration iteration = IterationDAO.getIterationById(child.getLeft());
                                if (iteration != null) {
                                    iteration.doDelete();
                                    getPluginContext().deleteLink(child.getLeft(), child.getRight(), RedminePluginRunner.PORTFOLIO_ENTRY_ITERATION_LINK_TYPE);

                                }

                            }

                        }
                    }

                }
            });

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.redmine.portfolio_entry.save_configuration.successfull"));
            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Search a Redmine project (for the picker).
         */
        case SEARCH_PROJECT_ACTION:

            PickerHandler<Long> pickerTemplate = new PickerHandler<Long>(this.getAttachmentManagerPlugin(), Long.class, new Handle<Long>() {

                @Override
                public Map<Parameters, String> config(Map<Parameters, String> defaultParameters) {
                    defaultParameters.put(Parameters.SEARCH_ENABLED, "true");
                    return defaultParameters;
                }

                @Override
                public ISelectableValueHolderCollection<Long> getInitialValueHolders(List<Long> values, Map<String, String> context) {

                    try {

                        ISelectableValueHolderCollection<Long> projects = new DefaultSelectableValueHolderCollection<Long>();

                        for (Project project : getRedmineManager().getProjectManager().getProjects()) {
                            projects.add(new DefaultSelectableValueHolder<Long>(Long.valueOf(project.getId()), project.getName()));
                        }

                        return projects;

                    } catch (Exception e) {
                        Logger.error("error when searching projects in redmine", e);
                        return null;
                    }

                }

                @Override
                public ISelectableValueHolderCollection<Long> getFoundValueHolders(String searchString, Map<String, String> context) {

                    searchString = searchString.replaceAll("\\*", "");

                    try {

                        ISelectableValueHolderCollection<Long> projects = new DefaultSelectableValueHolderCollection<Long>();

                        for (Project project : getRedmineManager().getProjectManager().getProjects()) {
                            if (project.getName().toLowerCase().contains(searchString.toLowerCase())) {
                                projects.add(new DefaultSelectableValueHolder<Long>(Long.valueOf(project.getId()), project.getName()));
                            }
                        }

                        return projects;

                    } catch (Exception e) {
                        Logger.error("error when searching projects in redmine", e);
                        return null;
                    }
                }

            });

            return pickerTemplate.handle(request());

        /*
         * Process the association between a Redmin project and the portfolio
         * entry.
         */
        case ADD_PROJECT_ACTION:

            Form<SelectProjectFormData> selectProjectBoundForm = selectProjectFormTemplate.bindFromRequest();

            if (selectProjectBoundForm.hasErrors()) {
                return ok(views.html.plugins.redmine.portfolioentry_select_project.render(getRoutes(objectId), objectId, MafDataType.getPortfolioEntry(),
                        getPluginContext().getPluginConfigurationName(), selectProjectBoundForm));
            }

            SelectProjectFormData selectProjectFormData = selectProjectBoundForm.get();

            String redmineProjectId = String.valueOf(selectProjectFormData.redmineProjectId);

            // check the project is not already linked
            List<String> externalIds = getPluginContext().getMultipleExternalId(objectId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);
            if (externalIds.contains(redmineProjectId)) {
                selectProjectBoundForm.reject("redmineProjectId", Msg.get("plugin.redmine.portfolio_entry.select_project.error.already_associated"));
                return ok(views.html.plugins.redmine.portfolioentry_select_project.render(getRoutes(objectId), objectId, MafDataType.getPortfolioEntry(),
                        getPluginContext().getPluginConfigurationName(), selectProjectBoundForm));
            }

            try {

                // if the initiative is not yet registered, then we do it
                if (!getPluginContext().isRegistered(MafDataType.getPortfolioEntry(), objectId)) {
                    getPluginContext().setRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId,
                            RedminePluginRunner.getPropertiesForPortfolioEntryAsByte(false, false, false));
                }

                getPluginContext().createLink(objectId, redmineProjectId, RedminePluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

                Utilities.sendSuccessFlashMessage(Msg.get("plugin.redmine.portfolio_entry.select_project.successfull"));
            } catch (Exception e) {
                Logger.error("unable to associate the Redmine project for the portfolio entry " + objectId, e);
            }

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        default:
            return notFound(views.html.error.not_found.render(""));
        }
    }

    /**
     * Get the routes.
     * 
     * @param objectId
     *            the object id
     */
    private Map<String, String> getRoutes(Long objectId) {
        Map<String, String> routes = new HashMap<String, String>();

        routes.put(INITIAL_ACTION,
                this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION).url());

        routes.put(REGISTRATION_CREATE_PROJECT_ACTION, this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(),
                objectId, REGISTRATION_CREATE_PROJECT_ACTION).url());
        routes.put(SELECT_PROJECT_ACTION, this
                .getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, SELECT_PROJECT_ACTION).url());
        routes.put(TRIGGER_LOAD_ACTION,
                this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, TRIGGER_LOAD_ACTION).url());
        routes.put(REMOVE_PROJECT_LINK_ACTION,
                this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, REMOVE_PROJECT_LINK_ACTION)
                        .url());
        routes.put(REMOVE_REGISTRATION_ACTION,
                this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, REMOVE_REGISTRATION_ACTION)
                        .url());

        routes.put(SAVE_CONFIGURATION_ACTION,
                this.getRouteForRegistrationController(IPluginContext.HttpMethod.POST, MafDataType.getPortfolioEntry(), objectId, SAVE_CONFIGURATION_ACTION)
                        .url());
        routes.put(ADD_PROJECT_ACTION,
                this.getRouteForRegistrationController(IPluginContext.HttpMethod.POST, MafDataType.getPortfolioEntry(), objectId, ADD_PROJECT_ACTION).url());
        routes.put(SEARCH_PROJECT_ACTION, this
                .getRouteForRegistrationController(IPluginContext.HttpMethod.POST, MafDataType.getPortfolioEntry(), objectId, SEARCH_PROJECT_ACTION).url());

        return routes;
    }

    /**
     * The configuration form object.
     * 
     * @author Johann Kohler
     */
    public static class ConfigurationFormData {

        @Required
        public boolean needs;

        @Required
        public boolean defects;

        @Required
        public boolean iterations;

        /**
         * Default constructor.
         */
        public ConfigurationFormData() {
        }

        /**
         * Construct with values.
         * 
         * @param needs
         *            set to true to activate the needs synchronization
         * @param defects
         *            set to true to activate the defects synchronization
         * @param iterations
         *            set to true to activate the iterations synchronization
         */
        public ConfigurationFormData(boolean needs, boolean defects, boolean iterations) {
            this.needs = needs;
            this.defects = defects;
            this.iterations = iterations;

        }

    }

    /**
     * The select project form object.
     * 
     * @author Johann Kohler
     */
    public static class SelectProjectFormData {

        @Required
        public Long redmineProjectId;

        /**
         * Default constructor.
         */
        public SelectProjectFormData() {
        }

    }

    /**
     * Get the redmine manager.
     */
    private RedmineManager getRedmineManager() {
        RedmineManager redmineManager = null;
        try {
            PropertiesConfiguration pluginProperties = getPluginContext().getPropertiesConfigurationFromByteArray(getPluginContext().getConfiguration(
                    getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(RedminePluginRunner.MAIN_CONFIGURATION_NAME), true)
                    .getRight());
            redmineManager = RedmineManagerFactory.createWithApiKey(pluginProperties.getString(RedminePluginRunner.REDMINE_HOST_URL_PROPERTY),
                    pluginProperties.getString(RedminePluginRunner.REDMINE_API_KEY_PROPERTY));
        } catch (Exception e) {
            Logger.error("Impossible to instanciate the redmine manager", e);
        }
        return redmineManager;
    }

    /**
     * Get the redmine host url.
     */
    private String getRedmineHostUrl() {
        String url = null;
        try {
            PropertiesConfiguration pluginProperties = getPluginContext().getPropertiesConfigurationFromByteArray(getPluginContext().getConfiguration(
                    getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(RedminePluginRunner.MAIN_CONFIGURATION_NAME), true)
                    .getRight());
            url = pluginProperties.getString(RedminePluginRunner.REDMINE_HOST_URL_PROPERTY);
        } catch (Exception e) {
            Logger.error("Impossible to get the redmine host url", e);
        }
        return url;
    }

    private ISysAdminUtils getSysAdminUtils() {
        return sysAdminUtils;
    }

    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Get the attachment plugin.
     */
    private IAttachmentManagerPlugin getAttachmentManagerPlugin() {
        return this.attachmentManagerPlugin;
    }
}
