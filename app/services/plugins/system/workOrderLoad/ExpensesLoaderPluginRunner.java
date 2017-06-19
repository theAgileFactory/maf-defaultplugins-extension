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
package services.plugins.system.workOrderLoad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;

import dao.pmo.ActorDao;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.loader.toolkit.AbstractJavaScriptFileLoaderMapper;
import framework.services.plugins.loader.toolkit.IGenericFileLoaderMapper;
import framework.services.plugins.loader.toolkit.LoadableObjectPluginRunner;
import framework.services.script.IScriptService;
import framework.services.system.ISysAdminUtils;

/**
 * WorkOrder loader plugin.
 * 
 */
public class ExpensesLoaderPluginRunner extends LoadableObjectPluginRunner<ExpensesLoadableObject> {
    private IScriptService scriptService;

    /**
     * Default constructor.
     */
    @Inject
    public ExpensesLoaderPluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils, IScriptService scriptService) {
        super(pluginContext, sysAdminUtils);
        this.scriptService = scriptService;
    }

    @Override
    public List<String> getAllowedFieldsForUnactivationWhereClause() {
        return Arrays.asList("name", "description");
    }

    @Override
    public IGenericFileLoaderMapper<ExpensesLoadableObject> createGenericFileLoaderMapper(final String javaScriptMappingScript) {
        return new AbstractJavaScriptFileLoaderMapper<ExpensesLoadableObject>(ExpensesLoadableObject.class, javaScriptMappingScript, getScriptService()) {

            @Override
            public String getLoadedObjectName() {
                return "WorkOrder loader";
            }

            @Override
            public Pair<String, List<String>> beforeSave(List<ExpensesLoadableObject> listOfValidLoadedObjects) throws IOException {
            	 return null;
            }

            @Override
            public Pair<String, List<String>> afterSave(List<ExpensesLoadableObject> listOfValidLoadedObjects) throws IOException {
            	 return null;
            }
        };
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        return null;
    }

    private IScriptService getScriptService() {
        return scriptService;
    }

}
