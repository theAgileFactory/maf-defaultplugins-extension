package services.plugins.system.widgetkit1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import framework.security.ISecurityService;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.ILinkGenerator;
import framework.services.ext.api.WebControllerPath;
import framework.services.notification.INotificationManagerPlugin;
import framework.services.plugins.api.WidgetController;
import framework.utils.Table;
import models.framework_models.account.Notification;
import models.framework_models.plugin.DashboardWidgetColor;
import play.Logger;
import play.Play;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import services.tableprovider.ITableProvider;
import utils.table.NotificationListView;

/**
 * Widget which displays the notifications which are unread
 * 
 * @author Pierre-Yves Cloux
 */
@WebControllerPath(path = "/notification")
public class UnreadNotificationsWidgetController extends WidgetController {
    private static Logger.ALogger log = Logger.of(UnreadNotificationsWidgetController.class);

    private INotificationManagerPlugin notificationManagerPlugin;
    private ISecurityService securityService;

    @Inject
    public UnreadNotificationsWidgetController(ILinkGenerationService linkGenerationService, ISecurityService securityService,
            INotificationManagerPlugin notificationManagerPlugin, II18nMessagesPlugin i18nMessagePlugin) {
        super(linkGenerationService, i18nMessagePlugin);
        this.securityService = securityService;
        this.notificationManagerPlugin = notificationManagerPlugin;
    }

    @Override
    public Promise<Result> display(Long widgetId) {
        final ILinkGenerator tempLinkGenerator = this;
        return Promise.promise(new Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                try {
                    Set<String> hideColumnsForNotifications = new HashSet<String>();
                    hideColumnsForNotifications.add("deleteActionLink");
                    hideColumnsForNotifications.add("isRead");
                    String loggedUser = getSecurityService().getCurrentUser().getUid();

                    List<NotificationListView> notificationListViews = new ArrayList<NotificationListView>();
                    for (Notification notification : getNotificationManagerPlugin().getNotReadNotificationsForUid(loggedUser)) {
                        notificationListViews.add(new NotificationListView(notification));
                    }

                    Table<NotificationListView> notificationsTable = getTableProvider().get().notification.templateTable.fill(notificationListViews,
                            hideColumnsForNotifications);
                    return ok(views.html.plugins.system.widgetkit1.unread_notification_widget.render(widgetId, DashboardWidgetColor.DEFAULT.getColor(),
                            tempLinkGenerator, getNotificationManagerPlugin().isInternalSendingSystem(), notificationsTable));
                } catch (Exception e) {
                    log.error("Error while displaying the notifications", e);
                    return displayErrorWidget(widgetId);
                }
            }
        });
    }

    /**
     * Get the notification manager service.
     */
    private INotificationManagerPlugin getNotificationManagerPlugin() {
        return notificationManagerPlugin;
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

}
