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
package services.plugins.system.finance1;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;

import dao.finance.PurchaseOrderDAO;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.loader.toolkit.AbstractJavaScriptFileLoaderMapper;
import framework.services.plugins.loader.toolkit.IGenericFileLoaderMapper;
import framework.services.plugins.loader.toolkit.LoadableObjectPluginRunner;
import framework.services.script.IScriptService;
import framework.services.system.ISysAdminUtils;
import models.finance.PurchaseOrder;
import models.finance.PurchaseOrderLineItem;

/**
 * Financial plugin.
 * 
 * @author Johann Kohler
 */
public class FinanceErpIntegrationPluginRunner extends LoadableObjectPluginRunner<FinanceErpIntegrationLoadableObject> {
    private IScriptService scriptService;

    /**
     * Default constructor.
     */
    @Inject
    public FinanceErpIntegrationPluginRunner(IPluginContext pluginContext, ISysAdminUtils sysAdminUtils, IScriptService scriptService) {
        super(pluginContext, sysAdminUtils);
        this.scriptService = scriptService;
    }

    @Override
    public List<String> getAllowedFieldsForUnactivationWhereClause() {
        return Arrays.asList();
    }

    @Override
    public IGenericFileLoaderMapper<FinanceErpIntegrationLoadableObject> createGenericFileLoaderMapper(String javaScriptMappingScript) {

        return new AbstractJavaScriptFileLoaderMapper<FinanceErpIntegrationLoadableObject>(FinanceErpIntegrationLoadableObject.class, javaScriptMappingScript,
                getScriptService()) {
            @Override
            public String getLoadedObjectName() {
                return "Financial / Load purchase order line items";
            }

            @Override
            public Pair<String, List<String>> beforeSave(List<FinanceErpIntegrationLoadableObject> listOfValidLoadedObjects) throws IOException {
                return null;
            }

            @Override
            public Pair<String, List<String>> afterSave(List<FinanceErpIntegrationLoadableObject> listOfValidLoadedObjects) throws IOException {

                /*
                 * If necessary update the isCancelled flag of concerned
                 * purchase orders.
                 * 
                 * A purchase order is concerned if at least one of its line
                 * items has been imported (create/update).
                 * 
                 * We set the flag isCancelled to true if all line items of a
                 * concerned purchase order have isCancelled to true.
                 */
                for (FinanceErpIntegrationLoadableObject financeErpIntegrationLoadableObject : listOfValidLoadedObjects) {
                    PurchaseOrder purchaseOrder = PurchaseOrderDAO
                            .getPurchaseOrderLineItemByRefId(financeErpIntegrationLoadableObject.getRefId()).purchaseOrder;
                    boolean isCancelled = true;
                    for (PurchaseOrderLineItem lineItem : purchaseOrder.purchaseOrderLineItems) {
                        if (lineItem.isCancelled == false) {
                            isCancelled = false;
                            break;
                        }
                    }
                    if (isCancelled) {
                        purchaseOrder.isCancelled = true;
                        purchaseOrder.save();
                    }
                }

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
