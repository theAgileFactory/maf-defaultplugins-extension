package services.plugins.system;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Expr;
import com.avaje.ebean.Expression;

import dao.pmo.PortfolioEntryDao;
import dao.pmo.PortfolioEntryEventDao;
import framework.commons.DataType;
import framework.commons.message.EventMessage;
import framework.commons.message.EventMessage.MessageType;
import framework.services.custom_attribute.ICustomAttributeManagerService;
import framework.services.custom_attribute.ICustomAttributeManagerService.CustomAttributeValueObject;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginContext.LogLevel;
import framework.services.plugins.api.PluginException;
import framework.utils.Msg;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import models.pmo.PortfolioEntryEvent;
import play.Logger;
import play.libs.F.Callback;
import play.libs.F.Promise;
import play.libs.ws.WSClient;
import play.libs.ws.WSCookie;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

/**
 * A class provided to the hook scripts which gathers various utilities to be
 * used from JavaScript
 *
 * @author Pierre-Yves Cloux
 */
public class HookScriptUtils {
	private static Logger.ALogger log = Logger.of(HookScriptUtils.class);
	
    public enum EventType {
        CREATE, UPDATE, DELETE;
    }

    private IPluginContext pluginContext;
    private WSClient wsClient;
    private HookStateObject hookStateObject;
    private ICustomAttributeManagerService customAttributeManagerService;

    public HookScriptUtils(ICustomAttributeManagerService customAttributeManagerService, IPluginContext pluginContext, WSClient wsClient) {
        super();
        this.pluginContext = pluginContext;
        this.wsClient = wsClient;
        this.customAttributeManagerService = customAttributeManagerService;
        this.hookStateObject=new HookStateObject(pluginContext);
    }
    
    /**
     * Wait the number of seconds
     * @param seconds a time in seconds
     */
    public void sleep(int seconds){
    	try {
			Thread.sleep(seconds*1000);
		} catch (InterruptedException e) {
			log.error("Thread error",e);
		}
    }

    /**
     * Return the object associated with the specified key
     *
     * @param dataTypeName
     *            the name of the data type
     * @param objectId
     *            a unique id
     * @return
     * @throws HookScriptException
     */
    public Object getObjectFromId(String dataTypeName, long objectId) throws HookScriptException {
        try {
        	if(log.isDebugEnabled()){
        		log.debug("Looking for dataType "+dataTypeName+" with id "+objectId);
        	}
            Class<?> dataTypeClass = getDataModelClass(dataTypeName);
        	if(log.isDebugEnabled()){
        		log.debug("Found class name "+dataTypeClass);
        	}
            return Ebean.getReference(dataTypeClass, objectId);
        } catch (Exception e) {
            throw new HookScriptException("Exception while looking for object " + dataTypeName + " for the id " + objectId);
        }
    }

    /**
     * Return a data query object
     * @param dataTypeName
     *            the name of the data type
     * @return
     * @throws ClassNotFoundException
     */
    public HookDataQuery createQuery(String dataTypeName) throws ClassNotFoundException{
        return new HookDataQuery(getDataModelClass(dataTypeName));
    }
    
    /**
     * Get the custom attributes values associated with the specified object
     * @param objectId the unique id of an object
     * @param dataTypeName the standard object type
     * @return an map of custom attributes (id of the attribute, value of type {@link CustomAttributeValueObject})
     */
    public Map<String, Object> getCustomAttributes(String dataTypeName, long objectId)
            throws HookScriptException{
        try{
            Class<?> dataTypeClass = getDataModelClass(dataTypeName);
            List<CustomAttributeValueObject> customAttributeValues= getCustomAttributeManagerService().getSerializableValues(dataTypeClass, objectId);
            Map<String, Object> customAttributesMap=new HashMap<String, Object>();
            if(customAttributeValues!=null){
                for(CustomAttributeValueObject customAttributeValueObject : customAttributeValues){
                    customAttributesMap.put(customAttributeValueObject.getUuid(), customAttributeValueObject);
                }
            }
            return customAttributesMap;
        } catch (Exception e) {
            throw new HookScriptException("Exception while looking for custom attributes from object " + dataTypeName + " for the id " + objectId);
        }
    }
    

    /**
     * Add an event to the specified portfolio entry
     * @param portfolioEntryId a portfolio entry Id
     * @param portfolioEntryEventTypeId the type of the portfolio entry event
     * @param eventMessage an event message to be added to the entry
     * @param jsCreationDate a javascript creation date
     */
    public void addPortfolioEntryEvent(Long portfolioEntryId, Long portfolioEntryEventTypeId, String eventMessage, Object jsCreationDate){
    	PortfolioEntryEvent event=new PortfolioEntryEvent();
    	if(jsCreationDate!=null){
	    	ScriptObjectMirror jsDate = (ScriptObjectMirror) jsCreationDate;
			long timestampLocalTime = ((Double) jsDate.callMember("getTime")).longValue(); 
			//js date returns timestamp in local time so you need to adjust it...
			int timezoneOffsetMinutes = ((Double)  jsDate.callMember("getTimezoneOffset")).intValue();
	    	event.creationDate=new Date(timestampLocalTime + timezoneOffsetMinutes * 60 * 1000);
    	}else{
    		event.creationDate=new Date();
    	}
    	event.portfolioEntry=PortfolioEntryDao.getPEById(portfolioEntryId);
    	event.portfolioEntryEventType=PortfolioEntryEventDao.getPEEventTypeById(portfolioEntryEventTypeId);
    	event.message=eventMessage;
    	event.save();
    }


    /**
     * Send an email
     *
     * @param subject
     * @param body
     * @param to
     */
    public void sendMail(String subject, String body, String... to) {
        getPluginContext().sendEmail(subject, body, to);
    }

    /**
     * Send an email
     *
     * @param subject
     * @param body
     * @param to
     */
    public void sendMail(String subject, String body, String[] to, String[] cc) {
        getPluginContext().sendEmail(subject, body, to, cc);
    }

    /**
     * Send a BizDock notification
     *
     * Send a message to one or many principals.
     *
     * @param title
     *            the message title
     * @param message
     *            the message content
     * @param actionLink
     *            an URL to be associated with the message
     * @param uids
     *            the list of principal uid to be notified
     */
    public void sendNotification(String title, String message, String actionLink, String... uids) {
        getPluginContext().sendNotification(title, message, actionLink, uids);
    }

    /**
     * Log an INFO or ERROR message associated with an event
     *
     * @param isError
     *            true if the message is an error
     * @param objectType
     *            the object type (as passed to notify)
     * @param objectId
     *            the object id (as passed to notify)
     * @param eventType
     *            the event type (as passed to notify)
     * @param message
     *            a String message
     */
    public void logEventMessage(boolean isError, String objectType, long objectId, String eventType, String message) {
        logMessage(isError, objectType, objectId, eventType, message);
    }

    /**
     * Store a Json string in the context
     * @param key a unique key
     * @param value a Json string
     * @throws HookScriptException
     */
    public void putJsonString(String key, String value) throws HookScriptException {
        getHookStateObject().putJsonString(key, value);
    }

    /**
     * Get a Json string from the context
     * @param key a unique key
     * @throws HookScriptException
     */
    public String getJsonString(String key) throws HookScriptException {
        return getHookStateObject().getJsonString(key);
    }

    /**
     * Log an INFO or ERROR message
     *
     * @param isError
     *            true if the message is an error
     * @param message
     *            a String message
     */
    public void logMessage(boolean isError, String message) {
        getPluginContext().reportMessage(null, isError, message);
    }

    /**
     * Store some data into a persistent storage which is shared 
     * between the plugins.<br/>
     * If the record already exists it will be overwritten.
     * 
     * @param key a unique key identifying the record
     * @param stateObject
     *            an object which must be {@link Serializable}
     */
    public void setSharedRecord(String key, Object stateObject) throws HookScriptException{
    	try {
			getPluginContext().setSharedRecord(key, stateObject);
		} catch (PluginException e) {
			throw new HookScriptException("Error while saving some data in the shared storage",e);
		}
    }

    /**
     * Retrieve the data associated with the specified key.
     * If the record is not found, the method returns null.
     * 
     * @param key a unique key identifying the record
     * @return the state object
     * @throws PluginException 
     */
    public Object getSharedRecord(String key) throws HookScriptException{
    	try {
			return getPluginContext().getSharedRecord(key);
		} catch (PluginException e) {
			throw new HookScriptException("Error while getting some data from the shared storage",e);
		}
    }
    
    /**
     * Delete the object associated with the specified key
     * 
     * @param key a unique key identifying the record
     */
    public void deleteSharedRecord(String key) throws HookScriptException{
    	try {
			getPluginContext().deleteSharedRecord(key);
		} catch (PluginException e) {
			throw new HookScriptException("Error while deleting some data from the shared storage",e);
		}
    }

    public String getMessage(String key) {
        return Msg.get(key);
    }

    private void logMessage(boolean isError, String objectType, long objectId, String eventType, String message) {
        MessageType messageType = MessageType.CUSTOM;
        if (eventType.equals(EventType.CREATE.name())) {
            messageType = MessageType.OBJECT_CREATED;
        }
        if (eventType.equals(EventType.UPDATE.name())) {
            messageType = MessageType.OBJECT_UPDATED;
        }
        if (eventType.equals(EventType.DELETE.name())) {
            messageType = MessageType.OBJECT_DELETED;
        }
        EventMessage eventMessage = new EventMessage(objectId, DataType.getDataType(objectType), messageType);
        getPluginContext().reportOnEventHandling(null, isError, eventMessage, message);
    }

    /**
     * Perform a WS call to the specified URL
     *
     * @param url
     *            an URL
     * @return a HookWSRequest object to be configured to perform the WS call
     */
    public HookWSRequest wsCall(String url) {
        return new HookWSRequest(url, getPluginContext(), getWsClient());
    }

    /**
     * Return the data class associated with the specified BizDock data type
     * name
     *
     * @param dataTypeName
     *            a data type name (see {@link DataType})
     * @return a class
     * @throws ClassNotFoundException
     */
    private Class<?> getDataModelClass(String dataTypeName) throws ClassNotFoundException {
        String className = null;
        try {
            className = DataType.getDataType(dataTypeName).getDataTypeClassName();
        } catch (Exception e) {
            String message = "Data type name " + dataTypeName + " not found";
            getPluginContext().reportMessage(null, true, message);
            throw new ClassNotFoundException(message);
        }
        return Class.forName(className);
    }

    private IPluginContext getPluginContext() {
        return pluginContext;
    }

    private WSClient getWsClient() {
        return wsClient;
    }
    
    private ICustomAttributeManagerService getCustomAttributeManagerService() {
        return this.customAttributeManagerService;
    }
    
    /**
     * Convert some common script objects to their corresponding Java equivalent
     * @param value a value
     * @return a possibly converted value
     */
    private static Object convertFromScriptObject(Object value){
        if(value==null) return null;
        if(value instanceof ScriptObjectMirror){
            ScriptObjectMirror scriptObject = (ScriptObjectMirror) value;
            if(scriptObject.getClassName().equals("Date")){
                //This is a JS date convert it to Java
                return new Date(((Double) scriptObject.callMember("getTime")).longValue());
            }
        }
        return value;
    }
    
    private HookStateObject getHookStateObject() {
        return hookStateObject;
    }

    /**
     * An object which is managing a state persisted using the current
     * plugin context.
     */
    public static class HookStateObject{
        private IPluginContext pluginContext;
        
        private HookStateObject(IPluginContext pluginContext){
            this.pluginContext = pluginContext;
        }
        
        /**
         * Store a JSON string into the plugin context
         * @param key a key
         * @param value a JSON String
         * @throws HookScriptException
         */
        public synchronized void putJsonString(String key, String value) throws HookScriptException{
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stateObject=(Map<String, Object>) getPluginContext().getState();
                if(stateObject==null){
                    stateObject=new HashMap<String, Object>();
                }
                stateObject.put(key, value);
                getPluginContext().setState(stateObject);
            } catch (PluginException e) {
                throw new HookScriptException("Error while storing an object", e);
            }
        }
        
        /**
         * Remove the specified key from the plugin context
         * @param key a key
         * @throws HookScriptException
         */
        public synchronized void removeJsonString(String key) throws HookScriptException{
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stateObject=(Map<String, Object>) getPluginContext().getState();
                if(stateObject==null){
                    stateObject=new HashMap<String, Object>();
                }
                stateObject.remove(key);
                getPluginContext().setState(stateObject);
            } catch (PluginException e) {
                throw new HookScriptException("Error while storing an object", e);
            }
        }
        
        /**
         * Return the JsonString associated with the specified key
         * @param key a unique key
         * @return a Json string
         * @throws HookScriptException
         */
        public synchronized String getJsonString(String key) throws HookScriptException{
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stateObject=(Map<String, Object>) getPluginContext().getState();
                if(stateObject==null){
                    return null;
                }
                return (String) stateObject.get(key);
            } catch (PluginException e) {
                throw new HookScriptException("Error while storing an object", e);
            }
        }

        private IPluginContext getPluginContext() {
            return pluginContext;
        }
    }

    /**
     * A hook request to perform a Web service request
     *
     * @author Pierre-Yves Cloux
     */
    public static class HookWSRequest {
        public static final long MAX_TIMEOUT = 10000l;
        public static final long TOO_LONG_RESPONSE_TIME = 5000l;
        private WSRequest wsRequest;
        private IPluginContext pluginContext;

        private HookWSRequest(String url, IPluginContext pluginContext, WSClient wsClient) {
            this.pluginContext = pluginContext;
            this.wsRequest = wsClient.url(url);
        }

        /**
         * Set the body this request should use.
         *
         * @param body
         * @return
         */
        public HookWSRequest setBody(String body) {
            getWsRequest().setBody(body);
            return this;
        }

        /**
         * Set the HTTP method this request should use, where the no args
         * execute() method is invoked.
         *
         * @param method
         *            an HTTP method (POST, GET, etc.)
         * @return
         */
        public HookWSRequest setMethod(String method) {
            getWsRequest().setMethod(method);
            return this;
        }

        /**
         * Sets the authentication header for the current request using BASIC
         * authentication.
         *
         * @param username
         * @param password
         * @return
         */
        public HookWSRequest setAuth(String username, String password) {
            getWsRequest().setAuth(username, password);
            return this;
        }

        /**
         * Set the content type. If the request body is a String, and no charset
         * parameter is included, then it will default to UTF-8.
         *
         * @param contentType
         *            the content type
         * @return
         */
        public HookWSRequest setContentType(String contentType) {
            getWsRequest().setContentType(contentType);
            return this;
        }

        /**
         * Sets whether redirects (301, 302) should be followed automatically.
         *
         * @param followRedirects
         * @return
         */
        public HookWSRequest setFollowRedirects(Boolean followRedirects) {
            getWsRequest().setFollowRedirects(true);
            return this;
        }

        /**
         * Sets a query parameter with the given name, this can be called
         * repeatedly. Duplicate query parameters are allowed.
         *
         * @param name
         * @param value
         * @return
         */
        public HookWSRequest setQueryParameter(String name, String value) {
            getWsRequest().setQueryParameter(name, value);
            return this;
        }

        /**
         * Adds a header to the request.
         *
         * @param name
         * @param value
         * @return
         */
        public HookWSRequest setHeader(String name, String value) {
            getWsRequest().setHeader(name, value);
            return this;
        }

        /**
         * Sets the request timeout in milliseconds.<br/>
         * This one cannot exceeds MAX_TIMEOUT
         *
         * @param timeout
         *            the request timeout in milliseconds.
         * @return
         */
        public HookWSRequest setRequestTimeout(long timeout) {
            if (timeout > MAX_TIMEOUT) {
                getWsRequest().setRequestTimeout(MAX_TIMEOUT);
            } else {
                getWsRequest().setRequestTimeout(timeout);
            }
            return this;
        }

        /**
         * Sets the virtual host as a "hostname:port" string.
         *
         * @param virtualHost
         * @return
         */
        public HookWSRequest setVirtualHost(String virtualHost) {
            getWsRequest().setVirtualHost(virtualHost);
            return this;
        }

        /**
         * Execute the request and callback the provided methods.<br/>
         *
         * @param successCallbackMethod
         *            the JS method to call if the WS call completes normally
         * @param errorCallbackMethod
         *            the JS method to call in case of unexpected error
         */
        public void execute(Object successCallbackMethod, Object errorCallbackMethod) {
            try {
                final long timestamp = System.currentTimeMillis();
                Promise<WSResponse> wsResponse = getWsRequest().execute();
                wsResponse.onRedeem(new Callback<WSResponse>() {
                    @Override
                    public void invoke(WSResponse response) throws Throwable {
                        if ((System.currentTimeMillis() - timestamp) > TOO_LONG_RESPONSE_TIME) {
                            getPluginContext().log(LogLevel.ERROR,
                                    "WS service call in Notification plugin is taking too much time " + response.getUri().toString());
                        }
                        ((ScriptObjectMirror) successCallbackMethod).call("", new HookWSResponse(response));
                    }
                });
            } catch (Exception e) {
                ((ScriptObjectMirror) errorCallbackMethod).call("", e.getMessage());
            }
        }

        private WSRequest getWsRequest() {
            return wsRequest;
        }

        private IPluginContext getPluginContext() {
            return pluginContext;
        }
    }

    /**
     * A WS response to be provided as a callback to JS code
     *
     * @author Pierre-Yves Cloux
     */
    public static class HookWSResponse {
        private WSResponse response;

        private HookWSResponse(WSResponse response) {
            super();
            this.response = response;
        }

        /**
         * Gets the body as a string.
         *
         * @return
         */
        public String getBody() {
            return getResponse().getBody();
        }

        /**
         * Gets a single header from the response.
         *
         * @param key
         * @return
         */
        public String getHeader(String key) {
            return getResponse().getHeader(key);
        }

        /**
         * Returns the HTTP status code from the response.
         *
         * @return an HTTP code
         */
        public int getStatus() {
            return getResponse().getStatus();
        }

        /**
         * Returns the text associated with the status code.
         *
         * @return an HTTP status text
         */
        public String getStatusText() {
            return getResponse().getStatusText();
        }

        /**
         * Gets a single cookie from the response, if any.
         *
         * @param name
         *            cookie name
         * @return a cookie structure
         */
        public WSCookie getCookie(String name) {
            return getResponse().getCookie(name);
        }

        private WSResponse getResponse() {
            return response;
        }
    }

    /**
     * An object to wrap an SQL query (read only) on a BizDock data object
     * @author Pierre-Yves Cloux
     */
    public static class HookDataQuery {
        private Class<?> dataTypeClass;

        /**
         * Creates a Data query
         * @param dataTypeClass a BizDock data object class
         */
        private HookDataQuery(Class<?> dataTypeClass) {
            this.dataTypeClass = dataTypeClass;
        }

        private Class<?> getDataTypeClass() {
            return dataTypeClass;
        }

        public HookDataQueryExpression expr(){
            return new HookDataQueryExpression();
        }

        /**
         * Execute the query and return one or more objects
         * @param expression an expression
         * @return
         */
        public List<?> executeQuery(HookDataQueryExpression expression) throws HookScriptException{
            try{
                return Ebean.createQuery(getDataTypeClass()).where().add(expression.getExpression()).findList();
            }catch(Exception e){
                throw new HookScriptException("Error while executing the query",e);
            }
        }
    }

    /**
     * An expression to be used with a {@link HookDataQuery}
     * @author Pierre-Yves Cloux
     */
    public static class HookDataQueryExpression {
        private Expression expression;

        public HookDataQueryExpression() {

        }

        /**
         * And - join two expressions with a logical and.
         *
         * @param exprOne
         * @param exprTwo
         * @return
         */
        public HookDataQueryExpression and(HookDataQueryExpression exprOne, HookDataQueryExpression exprTwo) {
            this.expression = Expr.and(exprOne.getExpression(), exprTwo.getExpression());
            return this;
        }

        /**
         * Not - negate the specified expression.
         *
         * @param exprToNegate
         * @return
         */
        public HookDataQueryExpression not(HookDataQueryExpression exprToNegate) {
            this.expression = Expr.not(exprToNegate.getExpression());
            return this;
        }

        /**
         * Between - property between the two given values.
         *
         * @param propertyName
         * @param value1
         * @param value2
         * @return
         */
        public HookDataQueryExpression between(String propertyName, Object value1, Object value2) {
            this.expression = Expr.between(propertyName, convertFromScriptObject(value1), convertFromScriptObject(value2));
            return this;
        }

        /**
         * Equal To - property equal to the given value.
         *
         * @param propertyName
         * @param value
         * @return
         */
        public HookDataQueryExpression eq(String propertyName, Object value) {
            this.expression = Expr.eq(propertyName, convertFromScriptObject(value));
            return this;
        }

        /**
         * Greater Than or Equal to - property greater than or equal to the
         * given value.
         *
         * @param propertyName
         * @param value
         * @return
         */
        public HookDataQueryExpression ge(String propertyName, Object value) {
            this.expression = Expr.ge(propertyName, convertFromScriptObject(value));
            return this;
        }

        /**
         * Greater Than - property greater than the given value.
         *
         * @param propertyName
         * @param value
         * @return
         */
        public HookDataQueryExpression gt(String propertyName, Object value) {
            this.expression = Expr.gt(propertyName, convertFromScriptObject(value));
            return this;
        }

        /**
         * Lesser Than - property lesser than the given value.
         *
         * @param propertyName
         * @param value
         * @return
         */
        public HookDataQueryExpression lt(String propertyName, Object value) {
            this.expression = Expr.lt(propertyName, convertFromScriptObject(value));
            return this;
        }

        /**
         * Lesser Than or Equal to - property greater than or equal to the given value.
         *
         * @param propertyName
         * @param value
         * @return
         */
        public HookDataQueryExpression le(String propertyName, Object value) {
            this.expression = Expr.le(propertyName, convertFromScriptObject(value));
            return this;
        }

        /**
         * Like - property like value where the value contains the SQL wild card
         * characters % (percentage) and _ (underscore).
         *
         * @param propertyName
         * @param value
         * @return
         */
        public HookDataQueryExpression like(String propertyName, String value) {
            this.expression = Expr.like(propertyName, value);
            return this;
        }

        /**
         * Or - join two expressions with a logical or.
         *
         * @param exprOne
         * @param exprTwo
         * @return
         */
        public HookDataQueryExpression or(HookDataQueryExpression exprOne, HookDataQueryExpression exprTwo) {
            this.expression = Expr.or(exprOne.getExpression(), exprTwo.getExpression());
            return this;
        }

        private Expression getExpression() {
            return expression;
        }
    }
}
