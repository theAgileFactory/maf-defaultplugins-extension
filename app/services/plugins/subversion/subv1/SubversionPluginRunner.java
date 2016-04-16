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
package services.plugins.subversion.subv1;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;

import constants.IMafConstants;
import constants.MafDataType;
import framework.commons.DataType;
import framework.commons.message.EventMessage;
import framework.commons.message.EventMessage.MessageType;
import framework.commons.message.SystemLevelRoleTypeEventMessage;
import framework.commons.message.UserEventMessage;
import framework.security.ISecurityService;
import framework.services.account.AccountManagementException;
import framework.services.account.DefaultAuthenticationAccountWriterPlugin;
import framework.services.account.IAccountManagerPlugin;
import framework.services.account.IAuthenticationAccountWriterPlugin;
import framework.services.account.IUserAccount;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.PluginException;
import framework.utils.Utilities;
import models.framework_models.account.Principal;
import models.framework_models.account.SystemLevelRoleType;
import models.framework_models.account.SystemPermission;

/**
 * A plugin which manages the connection to an SVN server.<br/>
 * This "SVN server" must be implemented as an apache server with the
 * mod_dav_svn and mod_ldap for the authentication.<br/>
 * This plugin is able to "provision" the LDAP server to create additional
 * accounts.<br/>
 * Two groups are defined and must be configured on the server (see examples in
 * BizDock documentation):
 * <ul>
 * <li>subversion.developer.ldap.group : any user with the permission
 * SCM_DEVELOPER_PERMISSION will be set in this group.</li>
 * <li>subversion.delivery.manager.ldap.group : any user with the permission
 * SCM_ADMIN_PERMISSION will be set in this group. Admins are users who need to
 * access specific "restricted" configuration modules.</li>
 * </ul>
 * 
 * @author Pierre-Yves Cloux
 * 
 */
public class SubversionPluginRunner implements IPluginRunner {
    static final String MAIN_CONFIGURATION_NAME = "config";
    static final String LDAP_URL_PROPERTY = "ldap.url";
    static final String LDAP_USER_PROPERTY = "ldap.user";
    static final String LDAP_PASSWORD_PROPERTY = "ldap.password";
    static final String SUBVERSION_GUI_URL_PROPERTY = "subversion.gui.url";
    static final String SUBVERSION_DEVELOPER_LDAP_GROUP_PROPERTY = "subversion.developer.ldap.group";
    static final String SUBVERSION_DELIVERY_MANAGER_LDAP_GROUP_PROPERTY = "subversion.delivery.manager.ldap.group";

    private IPluginContext pluginContext;
    private ISecurityService securityService;
    private IAccountManagerPlugin accountManagerPlugin;
    private IAuthenticationAccountWriterPlugin authWriterPlugin;

    private String developerLdapGroup;
    private String deliveryManagerLdapGroup;
    private String subversionGuiUrl;

    /**
     * List of manual actions supported by this plugin.
     * 
     * @author Pierre-Yves Cloux
     */
    public static enum ActionMessage {
        RESYNC_ALL_ACTION;
    }

    /**
     * The actions implemented by the plugin see {@link ActionMessage}
     */
    private static Map<String, IPluginActionDescriptor> pluginActions = Collections.synchronizedMap(new HashMap<String, IPluginActionDescriptor>() {
        private static final long serialVersionUID = 1L;

        {
            this.put(ActionMessage.RESYNC_ALL_ACTION.name(), new IPluginActionDescriptor() {

                @Override
                public Object getPayLoad(Long id) {
                    return ActionMessage.RESYNC_ALL_ACTION;
                }

                @Override
                public String getLabel() {
                    return "Resync with LDAP";
                }

                @Override
                public String getIdentifier() {
                    return ActionMessage.RESYNC_ALL_ACTION.name();
                }

                @Override
                public DataType getDataType() {
                    return MafDataType.getUser();
                }

                @Override
                public Object getPayLoad(Long arg0, Map<String, Object> arg1) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    });

    /**
     * Default constructor.
     */
    @Inject
    public SubversionPluginRunner(IPluginContext pluginContext, ISecurityService securityService, IAccountManagerPlugin accountManagerPlugin) {
        this.pluginContext = pluginContext;
        this.securityService = securityService;
        this.accountManagerPlugin = accountManagerPlugin;
    }

    @Override
    public void start() throws PluginException {
        PropertiesConfiguration properties = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(MAIN_CONFIGURATION_NAME)));
        properties.setThrowExceptionOnMissing(true);
        this.authWriterPlugin = new DefaultAuthenticationAccountWriterPlugin(properties.getString(LDAP_URL_PROPERTY),
                properties.getString(LDAP_USER_PROPERTY), properties.getString(LDAP_PASSWORD_PROPERTY));
        this.developerLdapGroup = properties.getString(SUBVERSION_DEVELOPER_LDAP_GROUP_PROPERTY);
        this.deliveryManagerLdapGroup = properties.getString(SUBVERSION_DELIVERY_MANAGER_LDAP_GROUP_PROPERTY);
        this.subversionGuiUrl = properties.getString(SUBVERSION_GUI_URL_PROPERTY);
        getPluginContext().log(LogLevel.INFO, "Subversion plugin started");
    }

    @Override
    public void stop() {
        getPluginContext().log(LogLevel.INFO, "Subversion plugin started");
    }

    @Override
    public synchronized void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {
        getPluginContext().log(LogLevel.INFO, "Receive notification for handling event " + eventMessage.getTransactionId());
        getPluginContext().log(LogLevel.DEBUG, "Receive notification for handling event " + eventMessage);
        try {
            // Deal with Roles modifications
            if (eventMessage.getDataType().equals(MafDataType.getSystemLevelRoleType()) && eventMessage.getMessageType().equals(MessageType.OBJECT_UPDATED)
                    && eventMessage.getPayload() != null && eventMessage.getPayload() instanceof SystemLevelRoleTypeEventMessage.PayLoad) {
                checkImpactOfSystemLevelRoleUpdate(eventMessage);
            }

            // Deal with User data notifications
            if (eventMessage.getDataType().equals(MafDataType.getUser())) {
                // Find the user account associated with this id
                // Get the user account
                IUserAccount userAccount = null;
                switch (eventMessage.getMessageType()) {
                case OBJECT_CREATED:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    notifyAccountCreated(eventMessage.getTransactionId(), userAccount);
                    break;
                case OBJECT_STATUS_CHANGED:
                    break;
                case OBJECT_DELETED:
                    if (eventMessage instanceof UserEventMessage && eventMessage.getPayload() instanceof UserEventMessage.PayLoad
                            && eventMessage.getPayload() != null) {
                        notifyAccountDeleted(eventMessage.getTransactionId(), ((UserEventMessage.PayLoad) eventMessage.getPayload()).getDeletedUid());
                    } else {
                        getPluginContext().log(LogLevel.ERROR, "Invalid event type received " + eventMessage);
                    }
                    break;
                case OBJECT_UPDATED:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    notifyAccountUpdated(eventMessage.getTransactionId(), userAccount, true);
                    break;
                case RESYNC:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    resync(eventMessage.getTransactionId(), userAccount);
                    break;
                default:
                    performResyncAllIfRequested(eventMessage);
                    break;
                }
            }
        } catch (PluginException e) {
            getPluginContext().log(LogLevel.INFO, "Failure of the subversion plugin", e);
            getPluginContext().reportOnEventHandling(eventMessage.getTransactionId(), true, eventMessage, "Failure of the subversion plugin", e);
            throw e;
        }
    }

    @Override
    public void handleInProvisioningMessage(EventMessage eventMessage) throws PluginException {
    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        final String menuLabel = getPluginContext().getPluginConfigurationName();
        return new IPluginMenuDescriptor() {

            @Override
            public String getPath() {
                return getSubversionGuiUrl();
            }

            @Override
            public String getLabel() {
                return menuLabel;
            }
        };
    }

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return pluginActions;
    }

    /**
     * Parse all the system accounts and synchronize with the LDAP server (if
     * required)
     * 
     * @param eventMessage
     *            an event message
     * @throws PluginException
     */
    private void performResyncAllIfRequested(EventMessage eventMessage) throws PluginException {
        if (eventMessage.getMessageType().equals(MessageType.CUSTOM) && eventMessage.getPayload() != null
                && eventMessage.getPayload() instanceof ActionMessage) {
            switch ((ActionMessage) eventMessage.getPayload()) {
            case RESYNC_ALL_ACTION:
                getPluginContext().reportMessage(eventMessage.getTransactionId(), false, "Resync started...");
                StringBuffer resyncLog = new StringBuffer();
                List<IUserAccount> userAccounts = null;
                try {
                    userAccounts = getAccountManagerPlugin().getUserAccountsFromName("*");
                } catch (AccountManagementException e) {
                    throw new PluginException("Error while looking for the user accounts", e);
                }
                if (userAccounts != null) {
                    for (IUserAccount userAccount : userAccounts) {
                        resyncLog.append("Account ").append(userAccount.getUid()).append(" checked\n");
                        try {
                            notifyAccountUpdated(eventMessage.getTransactionId(), userAccount, false);
                        } catch (PluginException e) {
                            resyncLog.append(">>Error with account ").append(userAccount.getUid()).append(", exception is : ")
                                    .append(Utilities.getExceptionAsString(e)).append('\n');
                        }
                    }
                    String fileNamePrefix = getPluginContext().getPluginConfigurationName().replaceAll("[^\\p{L}\\p{Nd}]+", "").toLowerCase();
                    String resyncReportFilePath = "/" + IMafConstants.OUTPUT_FOLDER_NAME + "/" + fileNamePrefix + "_resync.log";
                    OutputStreamWriter outWriter = null;
                    try {
                        OutputStream outStream = getPluginContext().writeFileInSharedStorage(resyncReportFilePath, true);
                        outWriter = new OutputStreamWriter(outStream);
                        outWriter.write(resyncLog.toString());
                        outWriter.flush();
                        getPluginContext().reportMessage(eventMessage.getTransactionId(), false,
                                "Resync report written to " + resyncReportFilePath + " in the shared storage");
                    } catch (IOException e) {
                        throw new PluginException("Error while writing the resync report file", e);
                    } finally {
                        IOUtils.closeQuietly(outWriter);
                    }
                }
                getPluginContext().reportMessage(eventMessage.getTransactionId(), false, "...Resync complete");
                break;
            }
        } else {
            getPluginContext().log(LogLevel.DEBUG, "Attempt to send a custom message to Subversion while it is not supported " + eventMessage);
        }
    }

    /**
     * Check if the update of the system role may have an impact on the
     * subversion LDAP configuration.
     * 
     * @param eventMessage
     *            the event message
     */
    private void checkImpactOfSystemLevelRoleUpdate(EventMessage eventMessage) {
        // A role was updated, check if this role was associated with
        // the permission SCM_DEVELOPER_PERMISSION
        // or SCM_ADMIN
        Long changedRole = eventMessage.getInternalId();
        List<String> previousListOfPermissions = ((SystemLevelRoleTypeEventMessage.PayLoad) eventMessage.getPayload()).getPreviousPermissionNames();

        // Check if the permission is new or removed
        List<String> currentPermissions = new ArrayList<String>();
        SystemLevelRoleType roleType = SystemLevelRoleType.getActiveRoleFromId(changedRole);
        if (roleType.systemPermissions != null) {
            for (SystemPermission systemPermission : roleType.systemPermissions) {
                currentPermissions.add(systemPermission.name);
            }
        }

        Collection<?> changedPermissions = CollectionUtils.disjunction(previousListOfPermissions, currentPermissions);
        if (changedPermissions.contains(IMafConstants.SCM_DEVELOPER_PERMISSION) || changedPermissions.contains(IMafConstants.SCM_ADMIN_PERMISSION)) {
            List<Principal> principals = Principal.getPrincipalsWithSystemLevelRoleId(changedRole);

            // Part of removed permissions
            Collection<?> removedPermissions = CollectionUtils.subtract(previousListOfPermissions, currentPermissions);
            if (removedPermissions.contains(IMafConstants.SCM_DEVELOPER_PERMISSION)) {
                removeUsersFromLdapGroupFollowingRoleUpdate(principals, getDeveloperLdapGroup());
            }
            if (removedPermissions.contains(IMafConstants.SCM_ADMIN_PERMISSION)) {
                removeUsersFromLdapGroupFollowingRoleUpdate(principals, getDeliveryManagerLdapGroup());
            }

            // Part of added permission
            Collection<?> addedPermissions = CollectionUtils.subtract(currentPermissions, previousListOfPermissions);
            if (addedPermissions.contains(IMafConstants.SCM_DEVELOPER_PERMISSION)) {
                addUsersToLdapGroupFollowingRoleUpdate(principals, getDeveloperLdapGroup());
            }
            if (addedPermissions.contains(IMafConstants.SCM_ADMIN_PERMISSION)) {
                addUsersToLdapGroupFollowingRoleUpdate(principals, getDeliveryManagerLdapGroup());
            }
        }
    }

    /**
     * Notify the creation of an account.
     * 
     * @param transactionId
     *            the transaction id
     * @param userAccount
     *            the user account
     */
    private void notifyAccountCreated(String transactionId, IUserAccount userAccount) throws PluginException {
        try {
            // Attempt to add the user to the group depending on its permissions
            IAuthenticationAccountWriterPlugin authWriterPlugin = getAuthWriterPlugin();
            if (userAccount.getSystemPermissionNames().contains(IMafConstants.SCM_DEVELOPER_PERMISSION)) {
                getPluginContext().reportMessage(transactionId, false, "User " + userAccount.getUid() + " added to " + getDeveloperLdapGroup() + " group");
                authWriterPlugin.addUserToGroup(userAccount.getUid(), getDeveloperLdapGroup());
            }
            if (userAccount.getSystemPermissionNames().contains(IMafConstants.SCM_ADMIN_PERMISSION)) {
                getPluginContext().reportMessage(transactionId, false,
                        "User " + userAccount.getUid() + " added to " + getDeliveryManagerLdapGroup() + " group");
                authWriterPlugin.addUserToGroup(userAccount.getUid(), getDeliveryManagerLdapGroup());
            }

            getPluginContext().log(LogLevel.INFO,
                    String.format("[Transaction %s] Subversion account %s added to the LDAP groups", transactionId, userAccount.getUid()));
        } catch (AccountManagementException e) {
            throw new PluginException(String.format("Error while adding user %s to the LDAP groups", userAccount.getUid()), e);
        }
    }

    /**
     * Notifiy the update of an account.
     * 
     * @param transactionId
     *            the transaction id
     * @param userAccount
     *            the user account
     * @param withLogs
     *            if true log the changes performed
     */
    private void notifyAccountUpdated(String transactionId, IUserAccount userAccount, boolean withLogs) throws PluginException {
        try {
            // Attempt to add the user to the group depending on its permissions
            IAuthenticationAccountWriterPlugin authWriterPlugin = getAuthWriterPlugin();
            if (userAccount.getSystemPermissionNames().contains(IMafConstants.SCM_DEVELOPER_PERMISSION)) {
                if (withLogs)
                    getPluginContext().reportMessage(transactionId, false,
                            "User " + userAccount.getUid() + " added to " + getDeveloperLdapGroup() + " group");
                authWriterPlugin.addUserToGroup(userAccount.getUid(), getDeveloperLdapGroup());
            } else {
                if (withLogs)
                    getPluginContext().reportMessage(transactionId, false,
                            "User " + userAccount.getUid() + " removed from " + getDeveloperLdapGroup() + " group");
                authWriterPlugin.removeUserFromGroup(userAccount.getUid(), getDeveloperLdapGroup());
            }
            if (userAccount.getSystemPermissionNames().contains(IMafConstants.SCM_ADMIN_PERMISSION)) {
                if (withLogs)
                    getPluginContext().reportMessage(transactionId, false,
                            "User " + userAccount.getUid() + " added to " + getDeliveryManagerLdapGroup() + " group");
                authWriterPlugin.addUserToGroup(userAccount.getUid(), getDeliveryManagerLdapGroup());
            } else {
                if (withLogs)
                    getPluginContext().reportMessage(transactionId, false,
                            "User " + userAccount.getUid() + " removed from " + getDeliveryManagerLdapGroup() + " group");
                authWriterPlugin.removeUserFromGroup(userAccount.getUid(), getDeliveryManagerLdapGroup());
            }
            if (withLogs)
                getPluginContext().log(LogLevel.INFO, String.format("[Transaction %s] Subversion account %s changed (added or removed from LDAP group)",
                        transactionId, userAccount.getUid()));
        } catch (AccountManagementException e) {
            throw new PluginException(String.format("Error while changing user %s (added or removed from LDAP group)", userAccount.getUid()), e);
        }
    }

    /**
     * Notify the deletion of an account.
     * 
     * @param transactionId
     *            the transaction id
     * @param previousUid
     *            the previous uid
     */
    private void notifyAccountDeleted(String transactionId, String previousUid) throws PluginException {
        try {
            // Remove the user from both groups (just in case)
            IAuthenticationAccountWriterPlugin authWriterPlugin = getAuthWriterPlugin();
            authWriterPlugin.removeUserFromGroup(previousUid, getDeveloperLdapGroup());
            getPluginContext().reportMessage(transactionId, false, "User " + previousUid + " removed from " + getDeveloperLdapGroup() + " group");
            authWriterPlugin.removeUserFromGroup(previousUid, getDeliveryManagerLdapGroup());
            getPluginContext().reportMessage(transactionId, false, "User " + previousUid + " removed from " + getDeliveryManagerLdapGroup() + " group");
            getPluginContext().log(LogLevel.INFO,
                    String.format("[Transaction %s] Subversion account %s removed from LDAP groups", transactionId, previousUid));
        } catch (AccountManagementException e) {
            getPluginContext().log(LogLevel.ERROR, String.format("Unable to remove the account %s from the LDAP groups", previousUid), e);
        }
    }

    /**
     * Resync.
     * 
     * @param transactionId
     *            the transaction id
     * @param userAccount
     *            the user account
     */
    private void resync(String transactionId, IUserAccount userAccount) throws PluginException {
        notifyAccountUpdated(transactionId, userAccount, true);
    }

    /**
     * Remove the specified users from the specified LDAP group.
     * 
     * @param principals
     *            the list of principals to remove
     * @param ldapGroup
     *            a LDAP group
     */
    private void removeUsersFromLdapGroupFollowingRoleUpdate(List<Principal> principals, String ldapGroup) {
        IAuthenticationAccountWriterPlugin authWriterPlugin = getAuthWriterPlugin();
        if (principals != null) {
            for (Principal principal : principals) {
                String uid = principal.uid;
                try {
                    authWriterPlugin.removeUserFromGroup(uid, ldapGroup);
                } catch (AccountManagementException e) {
                    getPluginContext().log(LogLevel.ERROR, String.format("Unable to remove the user %s from the LDAP group %s", uid, ldapGroup), e);
                }
            }
        }
    }

    /**
     * Add the specified users to the specified LDAP group.
     * 
     * @param principals
     *            the list of principals to add
     * @param ldapGroup
     *            a LDAP group
     */
    private void addUsersToLdapGroupFollowingRoleUpdate(List<Principal> principals, String ldapGroup) {
        IAuthenticationAccountWriterPlugin authWriterPlugin = getAuthWriterPlugin();
        if (principals != null) {
            for (Principal principal : principals) {
                String uid = principal.uid;
                try {
                    authWriterPlugin.addUserToGroup(uid, ldapGroup);
                } catch (AccountManagementException e) {
                    getPluginContext().log(LogLevel.ERROR, String.format("Unable to add the user %s to the LDAP group %s", uid, ldapGroup), e);
                }
            }
        }
    }

    /**
     * Return the {@link IUserAccount} associated with the specified id.
     * 
     * @param id
     *            the user id
     * 
     * @return a user account object
     */
    private IUserAccount getUserAccountFromInternalId(Long id) throws PluginException {
        try {
            IUserAccount userAccount = getSecurityService().getUserFromId(id);
            if (userAccount == null) {
                throw new PluginException("User account not found for id = " + id);
            }
            return userAccount;
        } catch (Exception e) {
            if (e instanceof PluginException) {
                throw (PluginException) e;
            }
            throw new PluginException(e);
        }
    }

    /**
     * Get the plugin context.
     */
    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    /**
     * Get the developer ldap group.
     */
    private String getDeveloperLdapGroup() {
        return developerLdapGroup;
    }

    /**
     * Get the delivery manager ldap group.
     */
    private String getDeliveryManagerLdapGroup() {
        return deliveryManagerLdapGroup;
    }

    private ISecurityService getSecurityService() {
        return securityService;
    }

    private IAuthenticationAccountWriterPlugin getAuthWriterPlugin() {
        return authWriterPlugin;
    }

    private String getSubversionGuiUrl() {
        return subversionGuiUrl;
    }

    private IAccountManagerPlugin getAccountManagerPlugin() {
        return accountManagerPlugin;
    }
}
