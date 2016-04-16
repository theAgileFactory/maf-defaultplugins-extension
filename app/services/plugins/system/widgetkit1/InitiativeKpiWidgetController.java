package services.plugins.system.widgetkit1;

import javax.inject.Inject;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Expression;

import constants.IMafConstants;
import dao.pmo.PortfolioEntryDao;
import framework.security.ISecurityService;
import framework.services.account.AccountManagementException;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebCommandPath;
import framework.services.ext.api.WebCommandPath.HttpMethod;
import framework.services.ext.api.WebControllerPath;
import framework.services.ext.api.WebParameter;
import framework.services.kpi.IKpiService;
import framework.services.kpi.Kpi;
import framework.services.kpi.Kpi.DataType;
import framework.services.plugins.api.WidgetController;
import framework.utils.DefaultSelectableValueHolder;
import framework.utils.DefaultSelectableValueHolderCollection;
import framework.utils.ISelectableValueHolder;
import framework.utils.ISelectableValueHolderCollection;
import framework.utils.Msg;
import framework.utils.Utilities;
import models.framework_models.plugin.DashboardWidget;
import models.framework_models.plugin.DashboardWidgetColor;
import models.pmo.PortfolioEntry;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Result;
import security.dynamic.PortfolioEntryDynamicHelper;

/**
 * Widget which displays a specific KPI for a selected initiative.
 * 
 * @author Pierre-Yves Cloux
 */
@WebControllerPath(path = "/kpi")
public class InitiativeKpiWidgetController extends WidgetController {

    private static Logger.ALogger log = Logger.of(InitiativeKpiWidgetController.class);

    private static Form<KpiSelectorFormData> configureFormTemplate = Form.form(KpiSelectorFormData.class);

    private IKpiService kpiService;
    private ISecurityService securityService;

    @Inject
    public InitiativeKpiWidgetController(ILinkGenerationService linkGenerationService, IKpiService kpiService, II18nMessagesPlugin i18nMessagePlugin,
            ISecurityService securityService) {
        super(linkGenerationService, i18nMessagePlugin);
        this.kpiService = kpiService;
        this.securityService = securityService;
    }

    @Override
    public Promise<Result> display(Long widgetId) {

        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    DashboardWidget widget = DashboardWidget.find.where().eq("id", widgetId).findUnique();

                    /**
                     * If the configuration doesn't exist, then load the
                     * configuration fragment.
                     */
                    if (widget.config == null) {
                        return ok(views.html.plugins.system.widgetkit1.initiative_kpi_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                                tempLinkGenerator, null));
                    }

                    /**
                     * Get and verify the configuration.
                     */
                    InitiativeKpiWidgetConfiguration configuration = new InitiativeKpiWidgetConfiguration();
                    try {
                        configuration.deserialize(widget.config);
                        verifyConfiguration(configuration);
                    } catch (Exception e) {
                        Logger.warn("The configuration of the KPI widget with id " + widgetId + " is not correct", e);
                        return ok(views.html.plugins.system.widgetkit1.initiative_kpi_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                                tempLinkGenerator, null));
                    }

                    return ok(views.html.plugins.system.widgetkit1.initiative_kpi_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, configuration));

                } catch (Exception e) {
                    log.error("Error while displaying the KPI widget", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });

    }

    @WebCommandPath(path = "/configure/:id", id = "kpi-configure")
    public Promise<Result> configure(@WebParameter(name = "id") Long id) {

        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    ISelectableValueHolderCollection<String> selectableKpis = new DefaultSelectableValueHolderCollection<String>();
                    for (Kpi kpi : getKpiService().getActiveKpisOfObjectType(PortfolioEntry.class)) {
                        selectableKpis.add(new DefaultSelectableValueHolder<String>(kpi.getUid(), Msg.get(kpi.getValueName(DataType.MAIN))));
                    }

                    Form<KpiSelectorFormData> form = configureFormTemplate;
                    DashboardWidget widget = DashboardWidget.find.where().eq("id", id).findUnique();
                    if (widget.config != null) {
                        InitiativeKpiWidgetConfiguration configuration = new InitiativeKpiWidgetConfiguration();
                        try {
                            configuration.deserialize(widget.config);
                            verifyConfiguration(configuration);
                            form = configureFormTemplate.fill(new KpiSelectorFormData(configuration));
                        } catch (Exception e) {
                            Logger.warn("The configuration of the KPI widget with id " + id + " is not correct", e);
                        }
                    }

                    return ok(views.html.plugins.system.widgetkit1.initiative_kpi_configure_fragment_widget.render(id, tempLinkGenerator, form,
                            selectableKpis));

                } catch (Exception e) {
                    log.error("Error while displaying the configuration of KPI widget", e);
                    return displayErrorWidget(id);
                }
            }
        });

    }

    @WebCommandPath(path = "/process-configure/:id", id = "kpi-process-configure", httpMethod = HttpMethod.POST)
    public Promise<Result> processConfigure(@WebParameter(name = "id") Long id) {

        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    ISelectableValueHolderCollection<String> selectableKpis = new DefaultSelectableValueHolderCollection<String>();
                    for (Kpi kpi : getKpiService().getActiveKpisOfObjectType(PortfolioEntry.class)) {
                        selectableKpis.add(new DefaultSelectableValueHolder<String>(kpi.getUid(), Msg.get(kpi.getValueName(DataType.MAIN))));
                    }

                    // bind the form
                    Form<KpiSelectorFormData> boundForm = configureFormTemplate.bindFromRequest();

                    if (boundForm.hasErrors()) {
                        return ok(views.html.plugins.system.widgetkit1.initiative_kpi_configure_fragment_widget.render(id, tempLinkGenerator, boundForm,
                                selectableKpis));
                    }

                    KpiSelectorFormData kpiSelectorFormData = boundForm.get();
                    InitiativeKpiWidgetConfiguration configuration = kpiSelectorFormData.getConfiguration();

                    try {
                        verifyConfiguration(configuration);
                    } catch (Exception e) {
                        boundForm.reject("kpiUid", Msg.get(e.getMessage()));
                        return ok(views.html.plugins.system.widgetkit1.initiative_kpi_configure_fragment_widget.render(id, tempLinkGenerator, boundForm,
                                selectableKpis));
                    }

                    DashboardWidget widget = DashboardWidget.find.where().eq("id", id).findUnique();
                    widget.config = configuration.serialize();
                    widget.save();

                    return ok(views.html.plugins.system.widgetkit1.initiative_kpi_display_fragment_widget.render(id, configuration));

                } catch (Exception e) {
                    log.error("Error while processing the configuration of KPI widget", e);
                    return displayErrorWidget(id);
                }
            }
        });

    }

    @WebCommandPath(path = "/portfolio-entry/search", id = "kpi-search-portfolio-entry")
    public Result search() {

        try {

            String query = request().queryString().get("query") != null ? request().queryString().get("query")[0] : null;
            String value = request().queryString().get("value") != null ? request().queryString().get("value")[0] : null;

            if (query != null) {

                ISelectableValueHolderCollection<Long> portfolioEntries = new DefaultSelectableValueHolderCollection<Long>();

                Expression expression = Expr.or(Expr.ilike("name", query + "%"), Expr.ilike("governanceId", query + "%"));

                for (PortfolioEntry portfolioEntry : PortfolioEntryDynamicHelper.getPortfolioEntriesViewAllowedAsQuery(expression, getSecurityService())
                        .findList()) {
                    portfolioEntries.add(new DefaultSelectableValueHolder<Long>(portfolioEntry.id, portfolioEntry.getName()));
                }

                return ok(Utilities.marshallAsJson(portfolioEntries.getValues()));
            }

            if (value != null) {
                PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(Long.valueOf(value));
                ISelectableValueHolder<Long> portfolioEntryAsVH = new DefaultSelectableValueHolder<Long>(portfolioEntry.id, portfolioEntry.getName());
                return ok(Utilities.marshallAsJson(portfolioEntryAsVH, 0));
            }

            return ok(Json.newObject());

        } catch (AccountManagementException e) {
            return badRequest();
        }

    }

    /**
     * Get the KPI service.
     */
    private IKpiService getKpiService() {
        return this.kpiService;
    }

    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Verify if the KPI corresponding to the configuration is correct and
     * authorized.
     * 
     * -the KPI exists and is active<br/>
     * -the user is authorized for the corresponding initiative
     * 
     * @param configuration
     *            the configuration
     */
    private void verifyConfiguration(InitiativeKpiWidgetConfiguration configuration) throws Exception {

        Kpi kpi = getKpiService().getKpi(configuration.kpiUid);

        if (kpi == null) {
            throw new Exception("plugin.widget_kit.initiative_kpi.form.error.unknown_kpi");
        }

        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(configuration.portfolioEntryId);

        if (portfolioEntry == null) {
            throw new Exception("plugin.widget_kit.initiative_kpi.form.error.unknown_pe");
        }

        if (!getSecurityService().dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION, "", configuration.portfolioEntryId)) {
            throw new Exception("plugin.widget_kit.initiative_kpi.form.error.unauthorized");
        }

        if (!kpi.isDisplayed() && (!getSecurityService().restrict(IMafConstants.PORTFOLIO_ENTRY_VIEW_FINANCIAL_INFO_ALL_PERMISSION)
                || !getSecurityService().restrict(IMafConstants.PORTFOLIO_ENTRY_VIEW_DETAILS_ALL_PERMISSION))) {
            throw new Exception("plugin.widget_kit.initiative_kpi.form.error.unauthorized");
        }
    }

    /**
     * The form data to edit the configuration if the widget.
     * 
     * @author Johann Kohler
     *
     */
    public static class KpiSelectorFormData {

        @Required
        public String kpiUid;

        @Required
        public Long portfolioEntryId;

        /**
         * Default constructor.
         */
        public KpiSelectorFormData() {
        }

        /**
         * Construct with initial values.
         * 
         * @param configuration
         *            the configuration
         */
        public KpiSelectorFormData(InitiativeKpiWidgetConfiguration configuration) {
            this.kpiUid = configuration.kpiUid;
            this.portfolioEntryId = configuration.portfolioEntryId;
        }

        /**
         * Get the configuration according to the form value.
         */
        public InitiativeKpiWidgetConfiguration getConfiguration() {
            InitiativeKpiWidgetConfiguration configuration = new InitiativeKpiWidgetConfiguration();
            configuration.kpiUid = this.kpiUid;
            configuration.portfolioEntryId = this.portfolioEntryId;
            return configuration;
        }

    }

}
