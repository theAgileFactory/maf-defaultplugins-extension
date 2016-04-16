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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.tuple.Pair;

import constants.IMafConstants;
import constants.MafDataType;
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
import framework.utils.DefaultSelectableValueHolder;
import framework.utils.DefaultSelectableValueHolderCollection;
import framework.utils.ISelectableValueHolderCollection;
import framework.utils.Msg;
import framework.utils.PickerHandler;
import framework.utils.PickerHandler.Handle;
import framework.utils.PickerHandler.Parameters;
import framework.utils.Utilities;
import models.delivery.Requirement;
import models.pmo.PortfolioEntry;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.libs.F.Promise;
import play.libs.ws.WSClient;
import play.mvc.Call;
import play.mvc.Result;
import services.plugins.atlassian.jira.jira1.client.JiraService;
import services.plugins.atlassian.jira.jira1.client.model.Project;
import services.plugins.legacy.LegacyUtils;

/**
 * Manage the actions for the jira configuration of a portfolio entry.
 * 
 * @author Johann Kohler
 * 
 */
@WebControllerPath(path = "/jira")
public class PortfolioEntryRegistrationController extends AbstractRegistrationConfiguratorController<JiraPluginRunner> {
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

    private WSClient wsClient;
    private ISecurityService securityService;
    private IAttachmentManagerPlugin attachmentManagerPlugin;

    /**
     * Default constructor.
     */
    @Inject
    public PortfolioEntryRegistrationController(ILinkGenerationService linkGenerationService, IPluginRunner pluginRunner, IPluginContext pluginContext,
            WSClient wsClient, ISecurityService securityService, IAttachmentManagerPlugin attachmentManagerPlugin) {
        super(linkGenerationService, pluginRunner, pluginContext);
        this.wsClient = wsClient;
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

    public Result doGet(Long objectId, String actionId) {
        ISecurityService securityService = this.getSecurityService();
        // security check
        if (!securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION, "", objectId)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        switch (actionId) {

        /*
         * If the portfolio entry is registered: display its configuration and
         * the related Jira projects with update capabilities.<br/>
         * 
         * Else: the user can either create a Jira Project with the portfolio
         * entry data, or simply select an existing Jira project and associate
         * it to the portfolio entry.
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
                        .fill(new ConfigurationFormData(JiraPluginRunner.getBooleanProperty(propertiesConfiguration, "needs"),
                                JiraPluginRunner.getBooleanProperty(propertiesConfiguration, "defects")));

                // get the related Jira projects
                Map<String, Project> projects = new HashMap<>();
                try {
                    for (String externalId : getPluginContext().getMultipleExternalId(objectId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {
                        Project project = getJiraService().getProject(externalId);
                        projects.put(externalId, project);
                    }
                } catch (Exception e) {
                    Logger.error("impossible to get the Jira projects of the portfolio entry " + objectId, e);
                }

                return ok(views.html.plugins.atlassian.jira.jira1.portfolioentry_index.render(getRoutes(objectId), objectId, MafDataType.getPortfolioEntry(),
                        getPluginContext().getPluginConfigurationName(), configurationForm, projects, getPluginContext().getPluginDescriptor(),
                        getJiraHostUrl()));

            } else {
                return ok(views.html.plugins.atlassian.jira.jira1.portfolioentry_not_registered.render(getRoutes(objectId), objectId,
                        MafDataType.getPortfolioEntry(), getPluginContext().getPluginConfigurationName()));
            }

            /*
             * Process the creation of a Jira project and associate it to the
             * portfolio entry. So the portfolio entry becomes "registered".
             */
        case REGISTRATION_CREATE_PROJECT_ACTION:

            // get the portfolio entry
            PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(objectId);

            // create the project in Jira
            Project jiraProject = new Project();
            jiraProject.setKey(convertIdToAlpha(objectId));
            jiraProject.setDescription(portfolioEntry.getDescription());
            jiraProject.setName(portfolioEntry.getName());

            try {

                String createdProjectId = getJiraService().createProject(jiraProject);

                if (createdProjectId == null) {
                    Logger.warn("Unable to create the Jira project for the portfolio entry " + objectId + ", because it is already existing");
                    Utilities.sendInfoFlashMessage(Msg.get("plugin.jira.portfolio_entry.not_registered.create.warn.already_existing"));
                    return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId,
                            SELECT_PROJECT_ACTION));
                }

                // add the link beetween the created project and the
                // portfolio entry
                getPluginContext().createLink(objectId, createdProjectId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

                // create the registration entry (to specify explicitly that
                // the portfolio entry is registered)
                getPluginContext().setRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId,
                        JiraPluginRunner.getPropertiesForPortfolioEntryAsByte(false, false));

                Utilities.sendSuccessFlashMessage(Msg.get("plugin.jira.portfolio_entry.not_registered.create.successfull"));

            } catch (Exception e) {

                Logger.error("Unable to create the Jira project for the portfolio entry " + objectId, e);

                Utilities.sendErrorFlashMessage(Msg.get("plugin.jira.portfolio_entry.not_registered.create.error"));

            }

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Display the form to select a Jira project in order to associate it to
         * the portfolio entry.
         */
        case SELECT_PROJECT_ACTION:
            return ok(views.html.plugins.atlassian.jira.jira1.portfolioentry_select_project.render(getRoutes(objectId), objectId,
                    MafDataType.getPortfolioEntry(), getPluginContext().getPluginConfigurationName(), selectProjectFormTemplate));

        /*
         * Trigger the load job of the Jira plugin for the portfolio entry.
         */
        case TRIGGER_LOAD_ACTION:

            EventMessage eventMessage = new EventMessage(objectId, null, MessageType.CUSTOM, getPluginContext().getPluginConfigurationId(),
                    JiraPluginRunner.ActionMessage.TRIGGER_PE_LOAD);
            getPluginContext().postOutMessage(eventMessage);

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.jira.portfolio_entry.trigger_load.successfull"));

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Remove the link with a Jira project.
         */
        case REMOVE_PROJECT_LINK_ACTION:

            String externalId = request().getQueryString("externalId");

            // remove the requirements
            for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE,
                    JiraPluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {
                Requirement requirement = RequirementDAO.getRequirementById(child.getLeft());
                if (requirement != null) {
                    requirement.doDelete();
                }
            }

            getPluginContext().deleteLink(objectId, externalId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.jira.portfolio_entry.remove_project_link.successfull"));

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Remove the registration of a portfolio entry.
         */
        case REMOVE_REGISTRATION_ACTION:

            for (String externalIdToRemove : getPluginContext().getMultipleExternalId(objectId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {

                for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalIdToRemove,
                        JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE, JiraPluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {
                    Requirement requirement = RequirementDAO.getRequirementById(child.getLeft());
                    if (requirement != null) {
                        requirement.doDelete();
                    }
                }

                getPluginContext().deleteLink(objectId, externalIdToRemove, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

            }

            getPluginContext().removeRegistration(MafDataType.getPortfolioEntry(), objectId);

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.jira.portfolio_entry.remove_registration.successfull"));

            return redirect(controllers.core.routes.PortfolioEntryController.pluginConfig(objectId));

        default:
            return notFound(views.html.error.not_found.render(""));

        }

    }

    public Result doPost(Long objectId, String actionId) {
        ISecurityService securityService = this.getSecurityService();
        // security check
        if (!securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION, "", objectId)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        switch (actionId) {

        /*
         * Save the properties of the registration configuration for the
         * portfolio entry.
         */
        case SAVE_CONFIGURATION_ACTION:

            Form<ConfigurationFormData> configurationBoundForm = configurationFormTemplate.bindFromRequest();
            ConfigurationFormData configurationFormData = configurationBoundForm.get();

            getPluginContext().setRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId,
                    JiraPluginRunner.getPropertiesForPortfolioEntryAsByte(configurationFormData.needs, configurationFormData.defects));

            // if necessary, remove the existing requirements
            if (configurationFormData.needs == false || configurationFormData.defects == false) {

                for (String externalId : getPluginContext().getMultipleExternalId(objectId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE)) {

                    for (Pair<Long, String> child : getPluginContext().getChildrenIdOfLink(objectId, externalId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE,
                            JiraPluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE)) {

                        Requirement requirement = RequirementDAO.getRequirementById(child.getLeft());
                        if (requirement != null) {
                            if (configurationFormData.needs == false && requirement.isDefect == false) {
                                requirement.doDelete();
                                getPluginContext().deleteLink(child.getLeft(), child.getRight(), JiraPluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE);
                            }
                            if (configurationFormData.defects == false && requirement.isDefect == true) {
                                requirement.doDelete();
                                getPluginContext().deleteLink(child.getLeft(), child.getRight(), JiraPluginRunner.PORTFOLIO_ENTRY_REQUIREMENT_LINK_TYPE);
                            }
                        }

                    }

                }
            }

            Utilities.sendSuccessFlashMessage(Msg.get("plugin.jira.portfolio_entry.save_configuration.successfull"));

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        /*
         * Search a Jira project (for the picker).
         */
        case SEARCH_PROJECT_ACTION:

            PickerHandler<String> pickerTemplate = new PickerHandler<String>(this.getAttachmentManagerPlugin(), String.class, new Handle<String>() {

                @Override
                public Map<Parameters, String> config(Map<Parameters, String> defaultParameters) {
                    defaultParameters.put(Parameters.SEARCH_ENABLED, "true");
                    return defaultParameters;
                }

                @Override
                public ISelectableValueHolderCollection<String> getInitialValueHolders(List<String> values, Map<String, String> context) {

                    try {

                        ISelectableValueHolderCollection<String> projects = new DefaultSelectableValueHolderCollection<String>();

                        for (Project project : getJiraService().getProjects()) {
                            projects.add(new DefaultSelectableValueHolder<String>(project.getProjectRefId(), project.getName()));
                        }

                        return projects;

                    } catch (Exception e) {
                        Logger.error("error when searching projects in jira", e);
                        return null;
                    }

                }

                @Override
                public ISelectableValueHolderCollection<String> getFoundValueHolders(String searchString, Map<String, String> context) {

                    searchString = searchString.replaceAll("\\*", "");

                    try {

                        ISelectableValueHolderCollection<String> projects = new DefaultSelectableValueHolderCollection<String>();

                        for (Project project : getJiraService().getProjects()) {
                            if (project.getName().toLowerCase().contains(searchString.toLowerCase())) {
                                projects.add(new DefaultSelectableValueHolder<String>(project.getProjectRefId(), project.getName()));
                            }
                        }

                        return projects;

                    } catch (Exception e) {
                        Logger.error("error when searching projects in jira", e);
                        return null;
                    }
                }

            });

            return pickerTemplate.handle(request());

        /*
         * Process the association between a Jira project and the portfolio
         * entry.
         */
        case ADD_PROJECT_ACTION:

            Form<SelectProjectFormData> selectProjectBoundForm = selectProjectFormTemplate.bindFromRequest();

            if (selectProjectBoundForm.hasErrors()) {
                return ok(views.html.plugins.atlassian.jira.jira1.portfolioentry_select_project.render(getRoutes(objectId), objectId,
                        MafDataType.getPortfolioEntry(), getPluginContext().getPluginConfigurationName(), selectProjectBoundForm));
            }

            SelectProjectFormData selectProjectFormData = selectProjectBoundForm.get();

            // check the project is not already linked
            List<String> externalIds = getPluginContext().getMultipleExternalId(objectId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);
            if (externalIds.contains(selectProjectFormData.jiraProjectId)) {
                selectProjectBoundForm.reject("jiraProjectId", Msg.get("plugin.jira.portfolio_entry.select_project.error.already_associated"));
                return ok(views.html.plugins.atlassian.jira.jira1.portfolioentry_select_project.render(getRoutes(objectId), objectId,
                        MafDataType.getPortfolioEntry(), getPluginContext().getPluginConfigurationName(), selectProjectBoundForm));
            }

            try {

                // if the initiative is not yet registered, then we do it
                if (!getPluginContext().isRegistered(MafDataType.getPortfolioEntry(), objectId)) {
                    getPluginContext().setRegistrationConfiguration(MafDataType.getPortfolioEntry(), objectId,
                            JiraPluginRunner.getPropertiesForPortfolioEntryAsByte(false, false));
                }

                getPluginContext().createLink(objectId, selectProjectFormData.jiraProjectId, JiraPluginRunner.PORTFOLIO_ENTRY_LINK_TYPE);

                Utilities.sendSuccessFlashMessage(Msg.get("plugin.jira.portfolio_entry.select_project.successfull"));
            } catch (Exception e) {
                Logger.error("unable to associate the Jira project for the portfolio entry " + objectId, e);
            }

            return redirect(this.getRouteForRegistrationController(IPluginContext.HttpMethod.GET, MafDataType.getPortfolioEntry(), objectId, INITIAL_ACTION));

        default:
            return notFound(views.html.error.not_found.render(""));
        }
    }

    /**
     * Get the jira service.
     */
    private JiraService getJiraService() {
        JiraService jiraService = null;
        try {
            PropertiesConfiguration pluginProperties = getPluginContext().getPropertiesConfigurationFromByteArray(getPluginContext()
                    .getConfiguration(
                            getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(JiraPluginRunner.MAIN_CONFIGURATION_NAME), true)
                    .getRight());
            jiraService = JiraService.get(getWsClient(), pluginProperties.getString(JiraPluginRunner.API_VERSION),
                    JiraPluginRunner.removeLastSlash(pluginProperties.getString(JiraPluginRunner.HOST_URL_PROPERTY)),
                    pluginProperties.getString(JiraPluginRunner.AUTHENTICATION_KEY));
        } catch (Exception e) {
            Logger.error("Impossible to instanciate the jira service", e);
        }
        return jiraService;
    }

    /**
     * Get the jira host url.
     */
    private String getJiraHostUrl() {
        String url = null;
        try {
            PropertiesConfiguration pluginProperties = getPluginContext().getPropertiesConfigurationFromByteArray(getPluginContext()
                    .getConfiguration(
                            getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(JiraPluginRunner.MAIN_CONFIGURATION_NAME), true)
                    .getRight());
            url = JiraPluginRunner.removeLastSlash(pluginProperties.getString(JiraPluginRunner.HOST_URL_PROPERTY));
        } catch (Exception e) {
            Logger.error("Impossible to get the jira host url", e);
        }
        return url;
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
         */
        public ConfigurationFormData(boolean needs, boolean defects) {
            this.needs = needs;
            this.defects = defects;
        }

    }

    /**
     * The select project form object.
     * 
     * @author Johann Kohler
     */
    public static class SelectProjectFormData {

        @Required
        public String jiraProjectId;

        /**
         * Default constructor.
         */
        public SelectProjectFormData() {
        }

    }

    private static final Map<String, String> MAP_INT_TO_CHAR;

    static {
        MAP_INT_TO_CHAR = new HashMap<String, String>();
        MAP_INT_TO_CHAR.put("0", "A");
        MAP_INT_TO_CHAR.put("1", "B");
        MAP_INT_TO_CHAR.put("2", "C");
        MAP_INT_TO_CHAR.put("3", "D");
        MAP_INT_TO_CHAR.put("4", "E");
        MAP_INT_TO_CHAR.put("5", "F");
        MAP_INT_TO_CHAR.put("6", "G");
        MAP_INT_TO_CHAR.put("7", "H");
        MAP_INT_TO_CHAR.put("8", "I");
        MAP_INT_TO_CHAR.put("9", "J");
    }

    /**
     * Convert a numerical ID to a alpha string.
     * 
     * @param id
     *            the id to convert
     */
    private String convertIdToAlpha(Long id) {
        String idString = String.valueOf(id);
        String r = "";
        for (int i = 0; i < idString.length(); i++) {
            r += MAP_INT_TO_CHAR.get(String.valueOf(idString.charAt(i)));
        }
        return r;
    }

    private WSClient getWsClient() {
        return wsClient;
    }

    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }

    private IAttachmentManagerPlugin getAttachmentManagerPlugin() {
        return this.attachmentManagerPlugin;
    }
}
