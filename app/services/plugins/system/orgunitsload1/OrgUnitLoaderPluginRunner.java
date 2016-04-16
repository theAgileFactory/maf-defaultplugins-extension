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
package services.plugins.system.orgunitsload1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;

import dao.pmo.OrgUnitDao;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.loader.toolkit.AbstractJavaScriptFileLoaderMapper;
import framework.services.plugins.loader.toolkit.IGenericFileLoaderMapper;
import framework.services.plugins.loader.toolkit.LoadableObjectPluginRunner;
import framework.services.script.IScriptService;
import framework.services.system.ISysAdminUtils;

/**
 * Org unit loader plugin.
 * 
 * @author Johann Kohler
 */
public class OrgUnitLoaderPluginRunner extends LoadableObjectPluginRunner<OrgUnitLoadableObject> {
    private IScriptService scriptService;

    /*
     * Default constructor.
     */
    @Inject
    public OrgUnitLoaderPluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils, IScriptService scriptService) {
        super(pluginContext, sysAdminUtils);
        this.scriptService = scriptService;
    }

    @Override
    public List<String> getAllowedFieldsForUnactivationWhereClause() {
        return Arrays.asList("ref_id", "manager_ref_id", "type_ref_id");
    }

    @Override
    public IGenericFileLoaderMapper<OrgUnitLoadableObject> createGenericFileLoaderMapper(final String javaScriptMappingScript) {
        return new AbstractJavaScriptFileLoaderMapper<OrgUnitLoadableObject>(OrgUnitLoadableObject.class, javaScriptMappingScript, getScriptService()) {

            @Override
            public String getLoadedObjectName() {
                return "Org units loader";
            }

            @Override
            public Pair<String, List<String>> beforeSave(List<OrgUnitLoadableObject> listOfValidLoadedObjects) throws IOException {

                if (isUnactivateNotFoundObjects()) {
                    String unactivationSelectionClause = getUnactivationSelectionClause();
                    unactivationSelectionClause = unactivationSelectionClause
                            .replace("type_ref_id", "(select org_unit_type.ref_id from org_unit_type where org_unit_type.id=org_unit_type_id)")
                            .replace("manager_ref_id", "(select actor.ref_id from actor where actor.id=manager_id)");
                    OrgUnitDao.unactivateOrgUnits(unactivationSelectionClause);
                }

                List<String> notFoundRelations = new ArrayList<String>();
                for (OrgUnitLoadableObject orgUnitLoadObject : listOfValidLoadedObjects) {
                    if (orgUnitLoadObject.getManagerRefId() != null && !orgUnitLoadObject.getManagerRefId().equals("")
                            && orgUnitLoadObject.getManager() == null) {
                        notFoundRelations.add("Org unit " + orgUnitLoadObject.getRefId() + ": "
                                + String.format("manager %s does not exist", orgUnitLoadObject.getManagerRefId()));
                    }
                }

                return Pair.of("Not found relations", notFoundRelations);
            }

            @Override
            public Pair<String, List<String>> afterSave(List<OrgUnitLoadableObject> listOfValidLoadedObjects) throws IOException {
                List<String> managerNotFound = new ArrayList<String>();
                for (OrgUnitLoadableObject orgUnitLoadObject : listOfValidLoadedObjects) {
                    Pair<String, String> pair = orgUnitLoadObject.addParent();
                    if (pair != null) {
                        managerNotFound.add("Org unit " + pair.getLeft() + ": impossible to find his parent with refId " + pair.getRight());
                    }
                }
                return Pair.of("Parent association", managerNotFound);
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
