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
package services.plugins.nexus.nex1;

import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;

import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;

/**
 * A plugin for managing the nexus integration.
 * 
 * @author Pierre-Yves Cloux
 */
public class NexusPluginRunner implements IPluginRunner {
    static final String MAIN_CONFIGURATION_NAME = "config";
    static final String NEXUS_GUI_URL_PROPERTY = "nexus.gui.url";

    private IPluginContext pluginContext;
    private String nexusGuiUrl;

    /**
     * Default constructor.
     */
    @Inject
    public NexusPluginRunner(IPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    @Override
    public void start() throws PluginException {
        PropertiesConfiguration properties = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(MAIN_CONFIGURATION_NAME)));
        properties.setThrowExceptionOnMissing(true);
        this.nexusGuiUrl = properties.getString(NEXUS_GUI_URL_PROPERTY);
        getPluginContext().log(LogLevel.INFO, "Nexus plugin started");
    }

    @Override
    public void stop() {
        getPluginContext().log(LogLevel.INFO, "Nexus plugin started");
    }

    @Override
    public void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    /**
     * Get the plugin context.
     */
    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        final String menuLabel = getPluginContext().getPluginConfigurationName();
        return new IPluginMenuDescriptor() {

            @Override
            public String getPath() {
                return getNexusGuiUrl();
            }

            @Override
            public String getLabel() {
                return menuLabel;
            }
        };
    }

    private String getNexusGuiUrl() {
        return nexusGuiUrl;
    }

}
