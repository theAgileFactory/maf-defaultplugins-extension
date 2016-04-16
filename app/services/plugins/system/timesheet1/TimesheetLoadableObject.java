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
package services.plugins.system.timesheet1;

import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.tuple.Pair;

import controllers.admin.UserManager;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import dao.pmo.PortfolioEntryPlanningPackageDao;
import dao.timesheet.TimesheetDao;
import framework.services.plugins.loader.toolkit.ILoadableObject;
import framework.utils.Msg;
import models.framework_models.parent.IModelConstants;
import models.pmo.Actor;
import models.pmo.ActorType;
import models.pmo.OrgUnit;
import models.pmo.PortfolioEntry;
import models.pmo.PortfolioEntryPlanningPackage;
import models.timesheet.TimesheetEntry;
import models.timesheet.TimesheetLog;
import models.timesheet.TimesheetReport;
import models.timesheet.TimesheetReport.Status;
import models.timesheet.TimesheetReport.Type;
import play.data.Form;
import play.data.validation.Constraints.MaxLength;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;

/**
 * An object structure which is to be used to load timesheet data.<br/>
 * This structure is JAXB enabled so that it could support a direct XML load.
 * 
 * @author Pierre-Yves Cloux
 * @author Johann Kohler
 */
public class TimesheetLoadableObject implements ILoadableObject {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    
    private static Form<TimesheetLoadableObject> fakeFormTemplate = Form.form(TimesheetLoadableObject.class);

    private long sourceRowNumber;
    
    @Required(message = "governanceId must not be null or blank")
    @MaxLength(value = IModelConstants.SMALL_STRING)
    private String governanceId;  
    
    @Required(message = "refId must not be null or blank")
    @MaxLength(value = IModelConstants.LARGE_STRING, message = "employeeRefId is too long " + IModelConstants.LARGE_STRING + " chars max")
    private String employeeRefId;
    
    @Required
    private String logDate;
    
    @Required
    private Double hours;
    
    @MaxLength(value = IModelConstants.MEDIUM_STRING, message = "packageRefId is too long " + IModelConstants.MEDIUM_STRING + " chars max")
    private String packageRefId;

    /**
     * Validate the non-string attributes.
     */
    public List<ValidationError> validate() {
        List<ValidationError> errors = new ArrayList<>();
        
        if(this.logDate !=null && !this.logDate.equals("") && this.getLogDateAsDate()==null){
            errors.add(new ValidationError("logDate", "No valid timesheet date or invalid format (must be "+DATE_FORMAT+")"));
        }

        if (this.governanceId != null && !this.governanceId.equals("") && this.getPortfolioEntry()==null) {
            errors.add(new ValidationError("governanceId", String.format("GovernanceId %s does not exist", this.governanceId)));
        }
        
        if (this.employeeRefId != null && !this.employeeRefId.equals("") && this.getActor()==null) {
            errors.add(new ValidationError("employeeRefId", String.format("EmployeeRefId %s does not exist", this.employeeRefId)));
        }
        
        if(this.packageRefId != null && !this.packageRefId.equals("") && this.getPlanningPackage()==null){
            errors.add(new ValidationError("packageRefId", String.format("Planning package %s does not exist", this.packageRefId)));
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Default constructor.
     */
    public TimesheetLoadableObject() {
    }
    
    /**
     * Look for a portfolio entry associated with the current governanceId
     * @return
     */
    public PortfolioEntry getPortfolioEntry(){
        return PortfolioEntryDao.getPEByGovernanceId(getGovernanceId());
    }
    
    /**
     * Look for an actor associated with the provided refId
     */
    public Actor getActor(){
        return ActorDao.getActorByRefId(getEmployeeRefId());
    }
    
    /**
     * Look for a planning package associated with the current portfolioEntry and planningPackageRefId
     */
    public PortfolioEntryPlanningPackage getPlanningPackage(){
        return PortfolioEntryPlanningPackageDao.getPEPlanningPackageByPEIdAndRefId(getPortfolioEntry().id,getPackageRefId());
    }
    
    @Override
    public Pair<Long, String> updateOrCreate() {
        PortfolioEntry pe=getPortfolioEntry();
        PortfolioEntryPlanningPackage pp=getPlanningPackage();
        Date logDateAsDate=getLogDateAsDate();
        Date startDate=getTimesheetReportStartDate();
        
        //Look for an existing TimesheetReport (if any)
        TimesheetReport tsr=TimesheetDao.getTimesheetReportByActorAndStartDate(getActor().id, startDate);
        TimesheetEntry tse=null;
        TimesheetLog tsLog=new TimesheetLog();
        if(tsr==null){
            tsr=new TimesheetReport();
            tsr.startDate=startDate;
            tsr.type=Type.WEEKLY;
            tsr.status=Status.APPROVED;
            tsr.actor=getActor();
            tsr.orgUnit=tsr.actor.orgUnit;
            tsr.save();
        }
        
        //Look for an existing timesheet entry (if any)
        tse=TimesheetDao.getTimesheetEntryByPEandPEPlanningPackage(tsr.id, pe.id, pp!=null?pp.id:null);
        if(tse==null){
            tse=new TimesheetEntry();
            tse.timesheetReport=tsr;
            tse.portfolioEntry=pe;
            tse.portfolioEntryPlanningPackage=pp;
            tse.save();
            addLogsToTimeSheetEntry(tse,startDate);
        }
        
        //Look for an existing timesheet log for the same day (then update it)
        if(tse.timesheetLogs!=null){
            for(TimesheetLog aLog : tse.timesheetLogs){
                if(aLog.logDate.equals(logDateAsDate)){
                    tsLog=aLog;
                }
            }
        }
        
        //Approve the report anyway
        tsr.status=Status.APPROVED;
        tsr.save();
        
        //The log of timesheet
        tsLog.timesheetEntry=tse;
        tsLog.logDate=logDateAsDate;
        tsLog.hours=tsLog.hours+this.hours;
        if(tsLog.hours<0){
            //Negative records are not allowed
            tsLog.hours=0d;
        }
        tsLog.save();
        
        return Pair.of(tsLog.id, "Entry for "+tsLog.logDate);
    }

    /**
     * Validate the object consistency against various rules.<br/>
     * WARNING: these rules must be consistent with the {@link Actor} object as
     * well as {@link UserManager} ones. <br/>
     * This method also looks for {@link ActorType} and {@link OrgUnit}
     * according to the provided values.
     */
    public Pair<Boolean, String> validateAndComplete() {

        // Perform a validation using Play form features
        Form<TimesheetLoadableObject> fakeForm = null;

        try {
            Map<String,String> formData=BeanUtils.describe(this);
            fakeForm = fakeFormTemplate.bind(formData);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Unable to validate the structure of the object\n" + toString());
        }
        
        
        if (fakeForm.hasErrors()) {
            StringBuffer validationErrors = new StringBuffer();
            for (String fieldName : fakeForm.errors().keySet()) {
                validationErrors.append("Validation Error [").append(fieldName).append("]:");
                for (ValidationError error : fakeForm.errors().get(fieldName)) {
                    validationErrors.append('\t').append(Msg.get(error.message())).append('\n');
                }
            }
            String exceptionMessage = validationErrors.toString() + toString() + "\n";
            return Pair.of(false, exceptionMessage);
        }

        return Pair.of(true, "");
    }
    
    /**
     * Return the timesheet report start date matching with the current logDate
     * @return a timesheet report start date
     */
    private Date getTimesheetReportStartDate(){
        Calendar c = Calendar.getInstance();
        c.setTime(getLogDateAsDate());
        if (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                c.add(Calendar.DAY_OF_WEEK, -1);
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            } else {
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            }
        }
        return c.getTime();
    }
    
    /**
     * Add to the specified TimesheetEntry a list of TimesheetLog
     * following the specified start date (start date is MONDAY)
     * @param timesheetEntry
     * @param startDate
     */
    private void addLogsToTimeSheetEntry(TimesheetEntry timesheetEntry, Date startDate){
        TimesheetLog tsLog=new TimesheetLog();
        tsLog.timesheetEntry=timesheetEntry;
        tsLog.hours=0d;
        tsLog.logDate=startDate;
        tsLog.save();
        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        for(int i=0;i<6;i++){
            tsLog=new TimesheetLog();
            c.add(Calendar.DATE, 1);
            tsLog.timesheetEntry=timesheetEntry;
            tsLog.hours=0d;
            tsLog.logDate=c.getTime();
            tsLog.save();
        }
        timesheetEntry.refresh();
    }

    @Override
    public String toString() {
        return "TimesheetLoadableObject [sourceRowNumber=" + sourceRowNumber + ", governanceId=" + governanceId + ", employeeRefId=" + employeeRefId
                + ", logDate=" + logDate + ", hours=" + hours + ", packageRefId=" + packageRefId + "]";
    }
    
    @Override
    public long getSourceRowNumber() {
        return sourceRowNumber;
    }

    @Override
    public void setSourceRowNumber(long sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    public String getGovernanceId() {
        return governanceId;
    }

    public void setGovernanceId(String governanceId) {
        this.governanceId = governanceId;
    }

    public String getEmployeeRefId() {
        return employeeRefId;
    }

    public void setEmployeeRefId(String employeeRefId) {
        this.employeeRefId = employeeRefId;
    }

    public String getLogDate() {
        return logDate;
    }

    public Date getLogDateAsDate() {
        try {
            DateFormat format = new SimpleDateFormat(DATE_FORMAT);
            return format.parse(this.logDate);
        } catch (ParseException e) {
            return null;
        }
    }

    public void setLogDate(String logDateAsString) {
        this.logDate = logDateAsString;
    }

    public Double getHours() {
        return hours;
    }

    public void setHours(Double hours) {
        this.hours = hours;
    }

    public String getPackageRefId() {
        return packageRefId;
    }

    public void setPackageRefId(String packageRefId) {
        this.packageRefId = packageRefId;
    }

}
