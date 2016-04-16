package extension.controllers;

import javax.inject.Inject;

import constants.IMafConstants;
import framework.services.ext.ILinkGenerationService;
import framework.services.ext.api.AbstractExtensionController;
import framework.services.ext.api.WebCommandPath;
import framework.services.ext.api.WebControllerPath;
import play.mvc.Result;

/**
 * A standard extension which display an about page
 * 
 * @author Pierre-Yves Cloux
 */
@WebControllerPath(path = "/standard")
public class StandardPluginsAboutExtensionController extends AbstractExtensionController {

    @Inject
    public StandardPluginsAboutExtensionController(ILinkGenerationService linkGenerationService) {
        super(linkGenerationService);
    }

    @WebCommandPath(id = "about", path = "/about", permissions = { IMafConstants.ADMIN_PLUGIN_MANAGER_PERMISSION })
    public Result about() {
        return ok(views.html.extensions.standard.about.render(this));
    }
}
