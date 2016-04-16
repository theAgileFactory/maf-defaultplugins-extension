package extension.controllers;

import java.util.Date;

import javax.inject.Inject;

import framework.services.configuration.II18nMessagesPlugin;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.WebCommandPath;
import framework.services.ext.api.WebControllerPath;
import framework.services.ext.api.WebParameter;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.WidgetController;
import models.framework_models.plugin.DashboardWidgetColor;
import play.libs.F.Promise;
import play.mvc.Result;

@WebControllerPath(path = "/sample")
public class SampleWidgetController extends WidgetController {
    private IPluginContext pluginContext;

    @Inject
    public SampleWidgetController(ILinkGenerationService linkGenerationService, II18nMessagesPlugin i18nMessagePlugin, IPluginContext pluginContext) {
        super(linkGenerationService, i18nMessagePlugin);
        this.pluginContext=pluginContext;
    }

    public Promise<Result> display(Long id) {
        return Promise.promise(() -> ok(views.html.extensions.standard.sample_widget.render(
                id, 
                "index.notifications.title", DashboardWidgetColor.PRIMARY.getColor(), this)));
    }
    
    @WebCommandPath(path="/action/:id",id="action")
    public Promise<Result> action(@WebParameter(name="id") Long id){
        return Promise.promise(() -> ok(String.valueOf(new Date())));
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }
}
