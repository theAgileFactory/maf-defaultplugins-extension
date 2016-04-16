package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Expression;
import com.avaje.ebean.ExpressionList;

import dao.pmo.ActorDao;
import framework.security.ISecurityService;
import framework.services.account.IPreferenceManagerPlugin;
import framework.services.account.IUserAccount;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebCommandPath;
import framework.services.ext.api.WebControllerPath;
import framework.services.ext.api.WebParameter;
import framework.services.plugins.api.WidgetController;
import framework.utils.Pagination;
import framework.utils.Table;
import models.finance.BudgetBucket;
import models.framework_models.plugin.DashboardWidgetColor;
import models.pmo.Actor;
import models.sql.ActorHierarchy;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import security.dynamic.BudgetBucketDynamicHelper;
import services.tableprovider.ITableProvider;
import utils.table.BudgetBucketListView;

/**
 * Widget which displays the active budget buckets (as owner and as responsible)
 * of the current sign-in user.
 * 
 * @author Johann Kohler
 */
@WebControllerPath(path = "/my-budget-buckets")
public class MyBudgetBucketsWidgetController extends WidgetController {

    private static Logger.ALogger log = Logger.of(MyBudgetBucketsWidgetController.class);

    private ISecurityService securityService;

    private IPreferenceManagerPlugin preferenceManagePlugin;

    @Inject
    public MyBudgetBucketsWidgetController(ILinkGenerationService linkGenerationService, ISecurityService securityService,
            II18nMessagesPlugin i18nMessagePlugin, IPreferenceManagerPlugin preferenceManagePlugin) {
        super(linkGenerationService, i18nMessagePlugin);
        this.securityService = securityService;
        this.preferenceManagePlugin = preferenceManagePlugin;
    }

    @Override
    public Promise<Result> display(Long widgetId) {

        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    // get the current actor
                    IUserAccount userAccount = getSecurityService().getCurrentUser();
                    Actor actor = ActorDao.getActorByUid(userAccount.getUid());
                    if (actor == null) {
                        return ok(views.html.plugins.system.widgetkit1.no_actor.render(widgetId, DashboardWidgetColor.WARNING.getColor(), tempLinkGenerator,
                                getI18nMessagePlugin().get("plugin.widget_kit.my_budget_buckets.title")));
                    }
                    Long actorId = actor.id;

                    MyBudgetBucketsData myBudgetBucketsData = getTables(actorId, 0, 0);

                    return ok(views.html.plugins.system.widgetkit1.my_budget_buckets_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, "as-owner", myBudgetBucketsData.asOwnerTable, myBudgetBucketsData.asOwnerPagination,
                            myBudgetBucketsData.asResponsibleTable, myBudgetBucketsData.asResponsiblePagination));
                } catch (Exception e) {
                    log.error("Error while displaying the budget buckets of the user", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });

    }

    @WebCommandPath(path = "/paginate/:id", id = "my-budget-buckets-paginate")
    public Promise<Result> paginate(@WebParameter(name = "id") Long id) {

        // get the query params
        Integer asOwnerPage = getQueryParamAsPage("asOwnerPage");
        Integer asResponsiblePage = getQueryParamAsPage("asResponsiblePage");
        String tab = ctx().request().getQueryString("tab");

        final ILinkGenerator tempLinkGenerator = this;

        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {

                    // get the current actor
                    IUserAccount userAccount = getSecurityService().getCurrentUser();
                    Actor actor = ActorDao.getActorByUid(userAccount.getUid());
                    Long actorId = actor.id;

                    MyBudgetBucketsData myBudgetBucketsData = getTables(actorId, asOwnerPage, asResponsiblePage);

                    return ok(views.html.plugins.system.widgetkit1.my_budget_buckets_fragment_widget.render(id, tempLinkGenerator, tab,
                            myBudgetBucketsData.asOwnerTable, myBudgetBucketsData.asOwnerPagination, myBudgetBucketsData.asResponsibleTable,
                            myBudgetBucketsData.asResponsiblePagination));
                } catch (Exception e) {
                    log.error("Error while displaying the budget buckets of the user", e);
                    return displayErrorWidget(id);
                }
            }
        });

    }

    private MyBudgetBucketsData getTables(Long actorId, Integer asOwnerPage, Integer asResponsiblePage) {

        /**
         * get the budget buckets for which the current actor is the owner
         */

        Expression expressionAsOwner = Expr.eq("owner.id", actorId);
        expressionAsOwner = Expr.and(expressionAsOwner, Expr.eq("isActive", true));

        Pagination<BudgetBucket> asOwnerPagination = getBudgetBucketPagination(asOwnerPage, expressionAsOwner);
        asOwnerPagination.setPageQueryName("asOwnerPage");

        Set<String> hideColumnsForBudgetBucketTable = new HashSet<String>();
        hideColumnsForBudgetBucketTable.add("owner");

        Table<BudgetBucketListView> asOwnerTable = getBudgetBucketTable(asOwnerPagination, hideColumnsForBudgetBucketTable);

        /**
         * get the budget buckets for which the current actor is a responsible
         * of the owner
         */

        Set<Long> subordinatesId = ActorHierarchy.getSubordinatesAsId(actorId);

        Expression expressionAsResponsible = null;
        if (subordinatesId != null && !subordinatesId.isEmpty()) {
            expressionAsResponsible = Expr.in("owner.id", subordinatesId);
        } else {
            expressionAsResponsible = Expr.eq("1", "0");
        }
        expressionAsResponsible = Expr.and(expressionAsResponsible, Expr.eq("isActive", true));

        Pagination<BudgetBucket> asResponsiblePagination = getBudgetBucketPagination(asResponsiblePage, expressionAsResponsible);
        asResponsiblePagination.setPageQueryName("asResponsiblePage");

        Table<BudgetBucketListView> asResponsibleTable = getBudgetBucketTable(asResponsiblePagination, null);

        return new MyBudgetBucketsData(asOwnerTable, asOwnerPagination, asResponsibleTable, asResponsiblePagination);

    }

    /**
     * Get the table object for a budget bucket pagination object.
     * 
     * @param pagination
     *            the budget bucket pagination object
     * @param hideColumns
     *            the columns to hid
     */
    private Table<BudgetBucketListView> getBudgetBucketTable(Pagination<BudgetBucket> pagination, Set<String> hideColumns) {

        List<BudgetBucketListView> budgetBucketListView = new ArrayList<BudgetBucketListView>();
        for (BudgetBucket budgetBucket : pagination.getListOfObjects()) {
            budgetBucketListView.add(new BudgetBucketListView(budgetBucket));
        }

        Table<BudgetBucketListView> filledTable = null;

        if (hideColumns != null) {
            filledTable = this.getTableProvider().get().budgetBucket.templateTable.fill(budgetBucketListView, hideColumns);
        } else {
            filledTable = this.getTableProvider().get().budgetBucket.templateTable.fill(budgetBucketListView);
        }

        return filledTable;
    }

    /**
     * Get the pagination object for the authorized budget buckets according to
     * a filter on the owner.
     * 
     * @param page
     *            the current page
     * @param ownerIdExpression
     *            the filter expression on the owner
     */
    private Pagination<BudgetBucket> getBudgetBucketPagination(Integer page, Expression ownerIdExpression) {

        ExpressionList<BudgetBucket> query = null;
        try {
            query = BudgetBucketDynamicHelper.getBudgetBucketsViewAllowedAsQuery(ownerIdExpression, null, getSecurityService());
        } catch (Exception e) {
            log.error("impossible to construct the \"budget bucket view all\" query", e);
        }

        Pagination<BudgetBucket> pagination = new Pagination<BudgetBucket>(this.getPreferenceManagerPlugin(), query);
        pagination.setCurrentPage(page);

        return pagination;

    }

    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Get the table provider.
     */
    private ITableProvider getTableProvider() {
        return Play.application().injector().instanceOf(ITableProvider.class);
    }

    /**
     * Get the preferenc manager service.
     */
    private IPreferenceManagerPlugin getPreferenceManagerPlugin() {
        return this.preferenceManagePlugin;
    }

    /**
     * Get the corresponding page number according to a query param.
     * 
     * @param name
     *            the query param name
     */
    private int getQueryParamAsPage(String name) {

        String valueAsString = ctx().request().getQueryString(name);
        if (valueAsString != null && !valueAsString.equals("")) {
            try {
                return Integer.parseInt(valueAsString);
            } catch (Exception e) {
                Logger.warn("impossible to convert the string '" + valueAsString + "' to an integer", e);
            }
        }

        return 0;

    }

    /**
     * The data of MyBudgetBuckets widget (tables and paginations objects).
     * 
     * @author Johann Kohler
     *
     */
    private static class MyBudgetBucketsData {

        public Table<BudgetBucketListView> asOwnerTable;
        public Pagination<BudgetBucket> asOwnerPagination;
        public Table<BudgetBucketListView> asResponsibleTable;
        public Pagination<BudgetBucket> asResponsiblePagination;

        /**
         * Default constructor.
         * 
         * @param asOwnerTable
         *            the owner table
         * @param asOwnerPagination
         *            the owner pagination object
         * @param asResponsibleTable
         *            the responsible table
         * @param asResponsiblePagination
         *            the responsible pagination object
         */
        public MyBudgetBucketsData(Table<BudgetBucketListView> asOwnerTable, Pagination<BudgetBucket> asOwnerPagination,
                Table<BudgetBucketListView> asResponsibleTable, Pagination<BudgetBucket> asResponsiblePagination) {
            this.asOwnerTable = asOwnerTable;
            this.asOwnerPagination = asOwnerPagination;
            this.asResponsibleTable = asResponsibleTable;
            this.asResponsiblePagination = asResponsiblePagination;
        }

    }

}
