/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.web.rest.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.w3c.dom.Document;

import com.sitewhere.SiteWhere;
import com.sitewhere.Tracer;
import com.sitewhere.common.MarshalUtils;
import com.sitewhere.rest.model.search.tenant.TenantSearchCriteria;
import com.sitewhere.rest.model.tenant.Tenant;
import com.sitewhere.rest.model.tenant.request.TenantCreateRequest;
import com.sitewhere.security.LoginManager;
import com.sitewhere.server.lifecycle.LifecycleProgressContext;
import com.sitewhere.server.lifecycle.LifecycleProgressMonitor;
import com.sitewhere.server.tenant.TenantUtils;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.SiteWhereSystemException;
import com.sitewhere.spi.command.CommandResult;
import com.sitewhere.spi.command.ICommandResponse;
import com.sitewhere.spi.error.ErrorCode;
import com.sitewhere.spi.error.ErrorLevel;
import com.sitewhere.spi.monitoring.IProgressMessage;
import com.sitewhere.spi.resource.IResource;
import com.sitewhere.spi.search.ISearchResults;
import com.sitewhere.spi.server.debug.TracerCategory;
import com.sitewhere.spi.server.tenant.ISiteWhereTenantEngine;
import com.sitewhere.spi.server.tenant.ITenantTemplate;
import com.sitewhere.spi.tenant.ITenant;
import com.sitewhere.spi.user.SiteWhereRoles;
import com.sitewhere.web.configuration.ConfigurationContentParser;
import com.sitewhere.web.configuration.content.ElementContent;
import com.sitewhere.web.rest.RestController;
import com.sitewhere.web.rest.annotations.Concerns;
import com.sitewhere.web.rest.annotations.Concerns.ConcernType;
import com.sitewhere.web.rest.annotations.Documented;
import com.sitewhere.web.rest.annotations.DocumentedController;
import com.sitewhere.web.rest.annotations.Example;
import com.sitewhere.web.rest.annotations.Example.Stage;
import com.sitewhere.web.rest.documentation.Tenants;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

/**
 * Controller for tenant operations.
 * 
 * @author Derek Adams
 */
@Controller
@CrossOrigin
@RequestMapping(value = "/tenants")
@Api(value = "tenants", description = "Operations related to SiteWhere tenants.")
@DocumentedController(name = "Tenants")
public class TenantsController extends RestController {

    /** Static logger instance */
    private static Logger LOGGER = LogManager.getLogger();

    /**
     * Create a new tenant.
     * 
     * @param request
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value = "Create new tenant")
    @Documented(examples = {
	    @Example(stage = Stage.Request, json = Tenants.CreateTenantRequest.class, description = "createTenantRequest.md"),
	    @Example(stage = Stage.Response, json = Tenants.CreateTenantResponse.class, description = "createTenantResponse.md") })
    public ITenant createTenant(@RequestBody TenantCreateRequest request, HttpServletRequest servletRequest,
	    HttpServletResponse servletResponse) throws SiteWhereException {
	checkAuthAdminTenants(servletRequest, servletResponse);
	Tracer.start(TracerCategory.RestApiCall, "createTenant", LOGGER);
	try {
	    return SiteWhere.getServer().getTenantManagement().createTenant(request);
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Update an existing tenant.
     * 
     * @param tenantId
     * @param request
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/{tenantId}", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(value = "Update an existing tenant.")
    @Documented(examples = {
	    @Example(stage = Stage.Request, json = Tenants.UpdateTenantRequest.class, description = "updateTenantRequest.md"),
	    @Example(stage = Stage.Response, json = Tenants.UpdateTenantResponse.class, description = "updateTenantResponse.md") })
    public ITenant updateTenant(@ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId,
	    @RequestBody TenantCreateRequest request, HttpServletRequest servletRequest,
	    HttpServletResponse servletResponse) throws SiteWhereException {
	checkAuthAdminTenants(servletRequest, servletResponse);
	Tracer.start(TracerCategory.RestApiCall, "updateTenant", LOGGER);
	try {
	    assureAuthorizedTenantId(tenantId);
	    return SiteWhere.getServer().getTenantManagement().updateTenant(tenantId, request);
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Get a tenant by unique id.
     * 
     * @param tenantId
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/{tenantId}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "Get tenant by unique id")
    @Documented(examples = {
	    @Example(stage = Stage.Response, json = Tenants.CreateTenantResponse.class, description = "getTenantByIdResponse.md") })
    public ITenant getTenantById(@ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId,
	    @ApiParam(value = "Include runtime info", required = false) @RequestParam(required = false, defaultValue = "false") boolean includeRuntimeInfo,
	    HttpServletRequest request, HttpServletResponse servletResponse) throws SiteWhereException {
	checkAuthAdminTenants(request, servletResponse);
	Tracer.start(TracerCategory.RestApiCall, "getTenantById", LOGGER);
	try {
	    ITenant tenant = assureAuthorizedTenantId(tenantId);
	    if (includeRuntimeInfo) {
		ISiteWhereTenantEngine engine = SiteWhere.getServer().getTenantEngine(tenantId);
		if (engine != null) {
		    ((Tenant) tenant).setEngineState(engine.getEngineState());
		}
	    }
	    return tenant;
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    @RequestMapping(value = "/{tenantId}/engine/{command}", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value = "Send command to tenant engine")
    @Documented(examples = {
	    @Example(stage = Stage.Response, json = Tenants.IssueTenantEngineCommandResponse.class, description = "issueTenantEngineCommandResponse.md") })
    public ICommandResponse issueTenantEngineCommand(
	    @ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId,
	    @ApiParam(value = "Command", required = true) @PathVariable String command, HttpServletRequest request,
	    HttpServletResponse servletResponse) throws SiteWhereException {
	checkAuthAdminTenants(request, servletResponse);
	Tracer.start(TracerCategory.RestApiCall, "issueTenantEngineCommand", LOGGER);
	try {
	    // Verify authorization and engine exists.
	    assureAuthorizedTenantId(tenantId);
	    ISiteWhereTenantEngine engine = SiteWhere.getServer().getTenantEngine(tenantId);
	    if (engine == null) {
		throw new SiteWhereSystemException(ErrorCode.InvalidTenantEngineId, ErrorLevel.ERROR);
	    }

	    // Required to allow partial response processing in browser.
	    servletResponse.setContentType(MediaType.TEXT_HTML_VALUE);

	    // Issue command and monitor progress.
	    ICommandResponse response = engine.issueCommand(command,
		    new LifecycleProgressMonitor(new LifecycleProgressContext(1, "Issue tenant engine command")) {

			@Override
			public void reportProgress(IProgressMessage message) throws SiteWhereException {
			    try {
				servletResponse.getOutputStream().println(MarshalUtils.marshalJsonAsString(message));
				servletResponse.getOutputStream().flush();
			    } catch (IOException e) {
				LOGGER.warn("Unable to write progress to stream.", e);
			    }
			}
		    });
	    if (response.getResult() == CommandResult.Failed) {
		LOGGER.error("Tenant engine command failed: " + response.getMessage());
	    }
	    return response;
	} finally {
	    try {
		servletResponse.getOutputStream().flush();
	    } catch (IOException e) {
		LOGGER.warn("Unable to close output stream.");
	    }
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Get the current configuration for a tenant engine.
     * 
     * @param tenantId
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/{tenantId}/engine/configuration", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "Get tenant engine configuration")
    @Documented
    public String getTenantEngineConfiguration(
	    @ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId, HttpServletRequest request,
	    HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "getTenantEngineConfiguration", LOGGER);
	try {
	    assureAuthorizedTenantId(tenantId);
	    IResource configuration = TenantUtils.getActiveTenantConfiguration(tenantId);
	    if (configuration != null) {
		return new String(configuration.getContent());
	    }
	    throw new SiteWhereException("Tenant configuration resource not found.");
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Get the current configuration for a tenant engine formatted as JSON.
     * 
     * @param tenantId
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/{tenantId}/engine/configuration/json", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "Get tenant engine configuration as JSON")
    @Documented
    public ElementContent getTenantEngineConfigurationAsJson(
	    @ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId, HttpServletRequest request,
	    HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "getTenantEngineConfigurationAsJson", LOGGER);
	try {
	    assureAuthorizedTenantId(tenantId);
	    IResource configuration = TenantUtils.getActiveTenantConfiguration(tenantId);
	    if (configuration != null) {
		return ConfigurationContentParser.parse(configuration.getContent());
	    }
	    throw new SiteWhereException("Tenant configuration resource not found.");
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Stages a new tenant configuration based on a JSON representation of the
     * configuration. Returns the XML configuration that was staged.
     * 
     * @param tenantId
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/{tenantId}/engine/configuration/json", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value = "Stage tenant engine configuration from JSON")
    @Documented
    public ElementContent stageTenantEngineConfiguration(
	    @ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId, HttpServletRequest request,
	    HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "stageTenantEngineConfiguration", LOGGER);
	try {
	    assureAuthorizedTenantId(tenantId);
	    ServletInputStream inData = request.getInputStream();
	    ByteArrayOutputStream byteData = new ByteArrayOutputStream();
	    int data;
	    while ((data = inData.read()) != -1) {
		byteData.write(data);
	    }
	    byteData.close();
	    ElementContent content = MarshalUtils.unmarshalJson(byteData.toByteArray(), ElementContent.class);
	    Document document = ConfigurationContentParser.buildXml(content);
	    String xml = ConfigurationContentParser.format(document);
	    TenantUtils.stageTenantConfiguration(tenantId, xml);
	    return content;
	} catch (IOException e) {
	    throw new SiteWhereException("Error staging tenant configuration.", e);
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Get a tenant by unique authentication token.
     * 
     * @param authToken
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/authtoken/{authToken}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "Get tenant by authentication token")
    @Documented(examples = {
	    @Example(stage = Stage.Response, json = Tenants.CreateTenantResponse.class, description = "getTenantByAuthTokenResponse.md") })
    public ITenant getTenantByAuthToken(
	    @ApiParam(value = "Authentication token", required = true) @PathVariable String authToken,
	    HttpServletRequest request, HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "getTenantByAuthToken", LOGGER);
	try {
	    return assureAuthorizedTenant(
		    SiteWhere.getServer().getTenantManagement().getTenantByAuthenticationToken(authToken));
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * List tenants that match the given criteria.
     * 
     * @param criteria
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "List tenants that match criteria")
    @Documented(examples = {
	    @Example(stage = Stage.Response, json = Tenants.ListTenantsResponse.class, description = "listTenantsResponse.md") })
    public ISearchResults<ITenant> listTenants(
	    @ApiParam(value = "Text search (partial id or name)", required = false) @RequestParam(required = false) String textSearch,
	    @ApiParam(value = "Authorized user id", required = false) @RequestParam(required = false) String authUserId,
	    @ApiParam(value = "Include runtime info", required = false) @RequestParam(required = false, defaultValue = "false") boolean includeRuntimeInfo,
	    @ApiParam(value = "Page number", required = false) @RequestParam(required = false, defaultValue = "1") @Concerns(values = {
		    ConcernType.Paging }) int page,
	    @ApiParam(value = "Page size", required = false) @RequestParam(required = false, defaultValue = "100") @Concerns(values = {
		    ConcernType.Paging }) int pageSize,
	    HttpServletRequest request, HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "listTenants", LOGGER);
	try {
	    TenantSearchCriteria criteria = new TenantSearchCriteria(page, pageSize);
	    criteria.setTextSearch(textSearch);
	    criteria.setUserId(authUserId);
	    criteria.setIncludeRuntimeInfo(includeRuntimeInfo);
	    return SiteWhere.getServer().getTenantManagement().listTenants(criteria);
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Delete tenant by unique tenant id.
     * 
     * @param tenantId
     * @param force
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/{tenantId}", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(value = "Delete existing tenant")
    @Documented(examples = {
	    @Example(stage = Stage.Response, json = Tenants.CreateTenantResponse.class, description = "deleteTenantByIdResponse.md") })
    public ITenant deleteTenantById(@ApiParam(value = "Tenant id", required = true) @PathVariable String tenantId,
	    @ApiParam(value = "Delete permanently", required = false) @RequestParam(defaultValue = "false") boolean force,
	    HttpServletRequest request, HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "deleteTenantById", LOGGER);
	try {
	    assureAuthorizedTenantId(tenantId);
	    return SiteWhere.getServer().getTenantManagement().deleteTenant(tenantId, force);
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Lists all tenants that contain a device with the given hardware id.
     * 
     * @param hardwareId
     * @param servletRequest
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/device/{hardwareId}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "List tenants that contain a device")
    public List<ITenant> listTenantsForDevice(
	    @ApiParam(value = "Hardware id", required = true) @PathVariable String hardwareId,
	    HttpServletRequest request, HttpServletResponse response) throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "listTenantsForDevice", LOGGER);
	try {
	    List<ITenant> tenants = SiteWhere.getServer()
		    .getAuthorizedTenants(LoginManager.getCurrentlyLoggedInUser().getUsername(), true);
	    List<ITenant> matches = new ArrayList<ITenant>();
	    for (ITenant tenant : tenants) {
		if (SiteWhere.getServer().getDeviceManagement(tenant).getDeviceByHardwareId(hardwareId) != null) {
		    matches.add(tenant);
		}
	    }
	    return matches;
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Lists all available tenant templates.
     * 
     * @param servletRequest
     * @return
     * @throws SiteWhereException
     */
    @RequestMapping(value = "/templates", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "List templates available for creating tenants")
    public List<ITenantTemplate> listTenantTemplates(HttpServletRequest request, HttpServletResponse response)
	    throws SiteWhereException {
	checkAuthAdminTenants(request, response);
	Tracer.start(TracerCategory.RestApiCall, "listTenantTemplates", LOGGER);
	try {
	    return SiteWhere.getServer().getTenantTemplateManager().getTenantTemplates();
	} finally {
	    Tracer.stop(LOGGER);
	}
    }

    /**
     * Verifies that authorized user is allowed to administer tenants.
     * 
     * @param request
     * @param response
     * @throws SiteWhereException
     */
    public static void checkAuthAdminTenants(HttpServletRequest request, HttpServletResponse response)
	    throws SiteWhereException {
	if (!request.isUserInRole(SiteWhereRoles.AUTH_ADMINISTER_TENANTS)) {
	    try {
		response.sendError(HttpServletResponse.SC_FORBIDDEN);
	    } catch (IOException e) {
		LOGGER.error(e);
	    }
	}
    }
}