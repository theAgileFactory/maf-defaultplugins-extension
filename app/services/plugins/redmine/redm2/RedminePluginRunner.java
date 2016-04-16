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
package services.plugins.redmine.redm2;

import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;

import framework.commons.message.EventMessage;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.PluginException;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.system.ISysAdminUtils;

/**
 * The standard redmine plugin runner.
 * 
 * @author Pierre-Yves Cloux
 */
public class RedminePluginRunner extends services.plugins.redmine.RedminePluginRunner {

    /**
     * Default constructor.
     */
    @Inject
    public RedminePluginRunner(IPluginContext pluginContext,ISysAdminUtils sysAdminUtils) {
        super(pluginContext, sysAdminUtils);
    }
    @Override
    public void start() throws PluginException {
        getPluginContext().log(LogLevel.DEBUG, "Redmine plugin "+getPluginContext().getPluginConfigurationName()+" is starting : loading properties");
        PropertiesConfiguration properties = getPluginContext().getPropertiesConfigurationFromByteArray(getPluginContext()
                .getConfigurationAndMergeWithDefault(getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(MAIN_CONFIGURATION_NAME)));
        getPluginContext().log(LogLevel.DEBUG, "Redmine plugin "+getPluginContext().getPluginConfigurationName()+" is starting : calling parent class");
        start(properties);
    }

    @Override
    public synchronized void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {
        super.handleOutProvisioningMessage(eventMessage);

    }
    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
    }
    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        final String menuLabel=getPluginContext().getPluginConfigurationName();
        return new IPluginMenuDescriptor() {
            
            @Override
            public String getPath() {
                return getRedmineHost();
            }
            
            @Override
            public String getLabel() {
                return menuLabel;
            }
        };
    }

}
