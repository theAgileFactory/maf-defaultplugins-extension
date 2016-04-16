package services.plugins.system.widgetkit1;

import java.util.Map;

import javax.inject.Inject;

import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.services.plugins.api.IPluginContext.LogLevel;

/**
 * A plugin which gathers the standard widgets.<br/>
 * @author Pierre-Yves Cloux
 */
public class StandardWidgetKitPlugin implements IPluginRunner {
    private IPluginContext pluginContext;
    
    /**
     * Default constructor.
     */
    @Inject
    public StandardWidgetKitPlugin(IPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        return null;
    }

    @Override
    public void handleInProvisioningMessage(EventMessage arg0) throws PluginException {
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage arg0) throws PluginException {
    }

    @Override
    public void start() throws PluginException {
        getPluginContext().log(LogLevel.INFO, "Standard widget kit plugin started");
    }

    @Override
    public void stop() {
        getPluginContext().log(LogLevel.INFO, "Standard widget kit plugin stopped");
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }
}
