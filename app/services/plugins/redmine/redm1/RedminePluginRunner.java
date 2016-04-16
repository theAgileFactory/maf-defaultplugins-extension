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
package services.plugins.redmine.redm1;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.RandomStringUtils;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.User;
import com.taskadapter.redmineapi.bean.UserFactory;

import constants.MafDataType;
import framework.commons.message.EventMessage;
import framework.security.ISecurityService;
import framework.services.account.IUserAccount;
import framework.services.plugins.api.IPluginActionDescriptor;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.IPluginMenuDescriptor;
import framework.services.plugins.api.PluginException;
import framework.services.system.ISysAdminUtils;

/**
 * The DevDock redmine plugin runner. It provides the standard redmine
 * synchronization capabilities plus the management of ther users.
 * 
 * @author Pierre-Yves Cloux
 */
public class RedminePluginRunner extends services.plugins.redmine.RedminePluginRunner {

    public static final String USER_LINK_TYPE = "USER";

    private ISecurityService securityService;

    /**
     * Default constructor.
     */
    @Inject
    public RedminePluginRunner(IPluginContext pluginContext, ISecurityService securityService, ISysAdminUtils sysAdminUtils) {
        super(pluginContext, sysAdminUtils);
        this.securityService = securityService;
    }

    @Override
    public void start() throws PluginException {
        getPluginContext().log(LogLevel.DEBUG, "Redmine plugin "+getPluginContext().getPluginConfigurationName()+" is starting : loading properties");
        PropertiesConfiguration properties = getPluginContext()
                .getPropertiesConfigurationFromByteArray(getPluginContext().getConfigurationAndMergeWithDefault(
                        getPluginContext().getPluginDescriptor().getConfigurationBlockDescriptors().get(RedminePluginRunner.MAIN_CONFIGURATION_NAME)));
        getPluginContext().log(LogLevel.DEBUG, "Redmine plugin "+getPluginContext().getPluginConfigurationName()+" is starting : calling parent class");
        start(properties);
    }

    @Override
    public synchronized void handleOutProvisioningMessage(EventMessage eventMessage) throws PluginException {

        getPluginContext().log(LogLevel.INFO, "Receive notification for handling event " + eventMessage.getTransactionId());
        getPluginContext().log(LogLevel.DEBUG, "Receive notification for handling event " + eventMessage);
        RedmineManager redmineManager = null;
        try {
            // Create a RedmineManager
            redmineManager = getRedmineManager();

            if (eventMessage.getDataType() != null && eventMessage.getDataType().equals(MafDataType.getUser())) {
                IUserAccount userAccount = null;
                // Find the user account associated with this id
                switch (eventMessage.getMessageType()) {
                case OBJECT_CREATED:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    notifyAccountCreated(eventMessage.getTransactionId(), redmineManager, userAccount, eventMessage.getInternalId());
                    break;
                case OBJECT_STATUS_CHANGED:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    notifyActivationStatusChanged(eventMessage.getTransactionId(), redmineManager, eventMessage.getInternalId());
                    break;
                case OBJECT_DELETED:
                    notifyAccountDeleted(eventMessage.getTransactionId(), redmineManager, eventMessage.getInternalId());
                    break;
                case OBJECT_UPDATED:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    notifyAccountUpdated(eventMessage.getTransactionId(), redmineManager, userAccount, eventMessage.getInternalId());
                    break;
                case RESYNC:
                    userAccount = getUserAccountFromInternalId(eventMessage.getInternalId());
                    resync(eventMessage.getTransactionId(), redmineManager, userAccount, eventMessage.getInternalId());
                    break;
                default:
                    getPluginContext().log(LogLevel.DEBUG, "Attempt to send a custom message to Redmine while it is not supported " + eventMessage);
                    break;
                }
            }
        } catch (PluginException e) {
            getPluginContext().log(LogLevel.INFO, "Failure of the redmine plugin", e);
            getPluginContext().reportOnEventHandling(eventMessage.getTransactionId(), true, eventMessage, "Failure of the redmine plugin", e);
            throw e;
        } finally {
            shutDownRedmineManager(redmineManager);
        }

        super.handleOutProvisioningMessage(eventMessage);

    }

    @Override
    public IPluginMenuDescriptor getMenuDescriptor() {
        final String menuLabel = getPluginContext().getPluginConfigurationName();
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

    @Override
    public Map<String, IPluginActionDescriptor> getActionDescriptors() {
        return null;
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
     * Resync.
     * 
     * @param transactionId
     *            the transaction id
     * @param redmineManager
     *            the redmine manager
     * @param userAccount
     *            the user account
     * @param internalId
     *            the internal id
     */
    private void resync(String transactionId, RedmineManager redmineManager, IUserAccount userAccount, Long internalId) throws PluginException {
        // Check if an association already exists for the specified internalId
        String externalId = getPluginContext().getUniqueExternalId(internalId, USER_LINK_TYPE);
        if (externalId != null) {
            // An external id already exists, try to find the
            // corresponding
            // Redmine user
            User redmineUser = findRedmineUserFromExternalId(redmineManager, externalId);
            if (redmineUser != null) {
                // Overwrite the redmine user
                try {
                    updateRedmineUserWithUserAccountData(redmineManager, userAccount, redmineUser);
                } catch (RedmineException e) {
                    throw new PluginException(
                            String.format("Unable to update the Redmine user associated with the external id %d matching with the internal id %d",
                                    redmineUser.getId(), internalId),
                            e);
                }
            } else {
                // Check if a redmine user with the same uid or e-mail exists
                redmineUser = findRedmineUserFromEmailOrUid(redmineManager, userAccount.getMail(), userAccount.getUid());
                if (redmineUser != null) {
                    throw new PluginException(
                            String.format("[Transaction %s] A Redmine user associated with the mail %s or a uid %s was found which prevents the provisioning"
                                    + " of the internal user with id %d", userAccount.getMail(), userAccount.getUid(), internalId));
                } else {
                    // Strange ... create the missing user
                    notifyAccountCreated(transactionId, redmineManager, userAccount, internalId);
                }
            }
        } else {
            // No association is found, try to find a matching Redmine
            // user to
            // link it
            User redmineUser = findRedmineUserFromEmailOrUid(redmineManager, userAccount.getMail(), userAccount.getUid());
            if (redmineUser == null) {
                // Create the missing user
                notifyAccountCreated(transactionId, redmineManager, userAccount, internalId);
            } else {
                // Found an existing account which can match create a link and
                // then update it
                getPluginContext().createOneToOneLink(internalId, String.valueOf(redmineUser.getId()), USER_LINK_TYPE);
                notifyAccountUpdated(transactionId, redmineManager, userAccount, internalId);
            }
        }
    }

    /**
     * Used for resync, try to find a user in Redmine with the specified mail.
     * 
     * @param redmineManager
     *            the redmine manager
     * @param mail
     *            a mail address
     * @param uid
     *            the uid
     * 
     * @return a Redmine User object or null if not found
     */
    private User findRedmineUserFromEmailOrUid(RedmineManager redmineManager, String mail, String uid) throws PluginException {
        // Look for the matching Redmine user based on e-mail address or uid
        // (login in Redmine)
        try {
            User foundUser = null;
            List<User> allUsers = redmineManager.getUserManager().getUsers();
            for (User aUser : allUsers) {
                if (aUser.getMail().toLowerCase().equals(mail.toLowerCase())) {
                    foundUser = aUser;
                }
                if (aUser.getLogin().toLowerCase().equals(uid.toLowerCase())) {
                    foundUser = aUser;
                }
            }
            return foundUser;
        } catch (RedmineException e) {
            String message = String.format("Error while attempting to find a Redmine User associated with the mail %s or the login %s", mail, uid);
            throw new PluginException(message, e);
        }
    }

    /**
     * Used for create operation or resync, try to find a user in Redmine with
     * the specified external id.
     * 
     * @param redmineManager
     *            the redmine manager
     * @param externalId
     *            the user id in Redmine server
     * 
     * @return a Redmine User object or null if not found
     */
    private User findRedmineUserFromExternalId(RedmineManager redmineManager, String externalId) throws PluginException {
        // Look for the matching Redmine user
        try {
            return redmineManager.getUserManager().getUserById(Integer.parseInt(externalId));
        } catch (NumberFormatException e) {
            String message = String.format("Invalid external id format %s for a Redmine user", externalId);
            throw new PluginException(message);
        } catch (RedmineException e) {
            String message = String.format("Error while retreiving the Redmine User associated with the external id %s", externalId);
            throw new PluginException(message, e);
        }
    }

    /**
     * Notify the change of the activation status.
     * 
     * @param transactionId
     *            the transaction id
     * @param redmineManager
     *            the redmine manager
     * @param internalId
     *            the internal id
     */
    private void notifyActivationStatusChanged(String transactionId, RedmineManager redmineManager, Long internalId) throws PluginException {
        User redmineUser = getRedmineUserFromInternalId(redmineManager, internalId);
        getPluginContext().log(LogLevel.INFO,
                String.format("[Transaction %s] Redmine account activation status changed for Redmine user %d", transactionId, redmineUser.getId()));
    }

    /**
     * Notify the deletion of an account.
     * 
     * @param transactionId
     *            the transaction id
     * @param redmineManager
     *            the redmine manager
     * @param internalId
     *            the internal id
     */
    private void notifyAccountDeleted(String transactionId, RedmineManager redmineManager, Long internalId) throws PluginException {
        User redmineUser = getRedmineUserFromInternalId(redmineManager, internalId);
        try {
            String externalId = String.valueOf(redmineUser.getId());
            redmineManager.getUserManager().deleteUser(redmineUser.getId());
            getPluginContext().deleteOneToOneLink(internalId, externalId, USER_LINK_TYPE);
            getPluginContext().log(LogLevel.INFO, String.format("[Transaction %s] Redmine account %d deleted", transactionId, redmineUser.getId()));
        } catch (RedmineException e) {
            throw new PluginException(String.format("Unable to delete the Redmine account %d", redmineUser.getId()), e);
        }
    }

    /**
     * Notify the update of an account.
     * 
     * @param transactionId
     *            the transaction id
     * @param redmineManager
     *            the redmine manager
     * @param userAccount
     *            the user account
     * @param internalId
     *            the internal id
     */
    private void notifyAccountUpdated(String transactionId, RedmineManager redmineManager, IUserAccount userAccount, Long internalId) throws PluginException {
        User redmineUser = getRedmineUserFromInternalId(redmineManager, internalId);
        try {
            updateRedmineUserWithUserAccountData(redmineManager, userAccount, redmineUser);
            getPluginContext().log(LogLevel.INFO, String.format("[Transaction %s] Redmine account %d updated", transactionId, redmineUser.getId()));
        } catch (RedmineException e) {
            throw new PluginException(String.format("Unable to update the Redmine account %d", redmineUser.getId()), e);
        }
    }

    /**
     * Notify the creation of an account.
     * 
     * @param transactionId
     *            the transaction id
     * @param redmineManager
     *            the redmine manager
     * @param userAccount
     *            the user account
     * @param internalId
     *            the internal id
     */
    private void notifyAccountCreated(String transactionId, RedmineManager redmineManager, IUserAccount userAccount, Long internalId) throws PluginException {
        // Check if an association already exists (if this is the case, delete
        // it - creation override any existing links)
        String externalId = getPluginContext().getUniqueExternalId(internalId, USER_LINK_TYPE);
        if (externalId != null) {
            getPluginContext().deleteOneToOneLink(internalId, externalId, USER_LINK_TYPE);
        }

        // Create a new Redmine user
        try {
            User redmineUser = UserFactory.create();
            redmineUser.setPassword(RandomStringUtils.random(10));
            redmineUser.setFirstName(userAccount.getFirstName());
            redmineUser.setLogin(userAccount.getUid());
            redmineUser.setLastName(userAccount.getLastName());
            redmineUser.setMail(userAccount.getMail());
            redmineUser.setCreatedOn(new Date());
            redmineUser = redmineManager.getUserManager().createUser(redmineUser);
            if (redmineUser.getId() <= 0) {
                throw new RedmineException("User was not created (post creation id is invalid)");
            }
            getPluginContext().createOneToOneLink(internalId, String.valueOf(redmineUser.getId()), USER_LINK_TYPE);
            getPluginContext().log(LogLevel.INFO, String.format("[Transaction %s] Redmine account %d created", transactionId, redmineUser.getId()));
        } catch (RedmineException e) {
            throw new PluginException("Unable to update the Redmine account for the internal user account " + internalId, e);
        }
    }

    /**
     * Used for update operations, find the Redmine user associated with the
     * specified internalId.
     * 
     * @param redmineManager
     *            the redmine manager
     * @param internalId
     *            user internal Id
     * 
     * @return a Redmine User object
     */
    private User getRedmineUserFromInternalId(RedmineManager redmineManager, Long internalId) throws PluginException {
        String externalId = null;
        try {
            // Look for the identification link
            externalId = getPluginContext().getUniqueExternalId(internalId, USER_LINK_TYPE);

            // No external id, throw an exception
            if (externalId == null) {
                throw new PluginException("No redmine link found in the database for the internal id = " + internalId);
            }

            // Look for the matching Redmine user
            User redmineUser = redmineManager.getUserManager().getUserById(Integer.parseInt(externalId));
            if (redmineUser == null) {
                throw new PluginException("Association found but no redmine user found in RedmineServer for the external id = " + externalId);
            }

            return redmineUser;
        } catch (NumberFormatException e) {
            String message = String.format("Invalid external id format %s for matching internal id %d", externalId, internalId);
            throw new PluginException(message);
        } catch (RedmineException e) {
            String message = String.format("Error while retreiving the Redmine User associated with the external id %s", externalId);
            throw new PluginException(message, e);
        }
    }

    /**
     * Update a redmine user with the account data.
     * 
     * @param redmineManager
     *            the redmine manager
     * @param userAccount
     *            the user account
     * @param redmineUser
     *            the redminer user
     */
    private void updateRedmineUserWithUserAccountData(RedmineManager redmineManager, IUserAccount userAccount, User redmineUser) throws RedmineException {
        redmineUser.setFirstName(userAccount.getFirstName());
        redmineUser.setLastName(userAccount.getLastName());
        redmineUser.setMail(userAccount.getMail());
        redmineManager.getUserManager().update(redmineUser);
    }

    private ISecurityService getSecurityService() {
        return securityService;
    }

}
