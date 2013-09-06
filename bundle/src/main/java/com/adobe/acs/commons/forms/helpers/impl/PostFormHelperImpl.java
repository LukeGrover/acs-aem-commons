package com.adobe.acs.commons.forms.helpers.impl;

import com.adobe.acs.commons.forms.Form;
import com.adobe.acs.commons.forms.helpers.FormHelper;
import com.adobe.acs.commons.forms.helpers.PostFormHelper;
import com.adobe.granite.xss.XSSAPI;
import com.day.cq.wcm.api.Page;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

@Component(label = "ACS AEM Commons - Abstract POST Form Helper", description = "Abstract Form Helper. Do not use directly; instead use the PostRedirectGetFormHelper or ForwardAsGetFormHelper.", enabled = true, metatype = false, immediate = false)
@Properties({ @Property(label = "Vendor", name = Constants.SERVICE_VENDOR, value = "ACS", propertyPrivate = true) })
@Service(value = PostFormHelper.class)
public class PostFormHelperImpl implements PostFormHelper {
    private static final Logger log = LoggerFactory.getLogger(PostFormHelperImpl.class);

    @Reference
    protected ResourceResolverFactory resourceResolverFactory;

    @Reference
    protected XSSAPI xssApi;

    /**
     * OSGi Properties *
     */
    private static final String DEFAULT_SUFFIX = "/submit/form";
    private String suffix = DEFAULT_SUFFIX;
    @Property(label = "Suffix", description = "Forward-as-GET Request Suffix used to identify Forward-as-GET POST Request", value = DEFAULT_SUFFIX)
    private static final String PROP_SUFFIX = "prop.form-suffix";

    @Override
    public Form getForm(String formName, SlingHttpServletRequest request) {
        throw new UnsupportedOperationException("Do not call AbstractFormHelper.getForm(..) direct. This is an abstract service.");
    }

    @Override
	public String getFormInputsHTML(final Form form, final String... keys) {
        // The form objects data and errors should be xssProtected before being passed into this method
		String html = "";

        html += "<input type=\"hidden\" name=\"" + FORM_NAME_INPUT + "\" value=\""
                + xssApi.encodeForHTMLAttr(form.getName()) + "\"/>\n";

        final String resourcePath = form.getResourcePath();
        html += "<input type=\"hidden\" name=\"" + FORM_RESOURCE_INPUT + "\" value=\""
                + xssApi.encodeForHTMLAttr(resourcePath) + "\"/>\n";

		for (final String key : keys) {
			if (form.has(key)) {
				html += "<input type=\"hidden\" name=\"" + key + "\" value=\""
						+ form.get(key) + "\"/>\n";
			}
		}

		return html;
	}

    @Override
    public String getFormSelectorInputHTML(final String selector) {
        return "<input type=\"hidden\" name=\"" + FORM_SELECTOR_INPUT + "\" value=\""
                + xssApi.encodeForHTMLAttr(selector) + "\"/>\n";
    }

    @Override
    public String getAction(final String path) {
        String actionPath = path;

        ResourceResolver adminResourceResolver = null;
        try {
            adminResourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            actionPath = adminResourceResolver.map(path);
        } catch (LoginException e) {
            log.error("Could not attain an admin ResourceResolver to map the Form's Action URI");
            // Use the unmapped ActionPath
        } finally {
            if(adminResourceResolver != null && adminResourceResolver.isLive()) {
                adminResourceResolver.close();
            }
        }

        return actionPath + FormHelper.EXTENSION + this.getSuffix();

    }

    @Override
    public void renderForm(Form form, String path, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException, JSONException {
        throw new UnsupportedOperationException("Use a specific Forms implementation helper.");
    }

    @Override
    public void renderForm(Form form, Page page, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException, JSONException {
        throw new UnsupportedOperationException("Use a specific Forms implementation helper.");
    }

    @Override
    public void renderForm(Form form, Resource resource, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException, JSONException {
        throw new UnsupportedOperationException("Use a specific Forms implementation helper.");
    }

    @Override
    public void renderOtherForm(Form form, String path, String selectors, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException, JSONException {
        throw new UnsupportedOperationException("Use a specific Forms implementation helper.");
    }

    @Override
    public void renderOtherForm(Form form, Page page, String selectors, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException, JSONException {
        throw new UnsupportedOperationException("Use a specific Forms implementation helper.");
    }

    @Override
    public void renderOtherForm(Form form, Resource resource, String selectors, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException, JSONException {
        throw new UnsupportedOperationException("Use a specific Forms implementation helper.");
    }

    @Override
    public String getAction(final Resource resource) {
        return getAction(resource.getPath());
    }

    @Override
    public String getAction(final Page page) {
        return getAction(page.getPath());
    }

    @Override
    public String getSuffix() {
        return this.suffix;
    }

    /**
     * Determines of this FormHelper should handle the POST request
     *
     * @param formName
     * @param request
     * @return
     */
    protected boolean doHandlePost(final String formName, final SlingHttpServletRequest request) {
        //noinspection SimplifiableIfStatement,SimplifiableIfStatement
        if(StringUtils.equalsIgnoreCase("POST", request.getMethod())) {
            // Form should have a hidden input with the name this.getLookupKey(..) and value formName
            return StringUtils.equals(formName, request.getParameter(this.getPostLookupKey(formName)));
        } else {
            return false;
        }
    }

    /**
     * Gets the Form from POST requests
     *
     * @param formName
     * @param request
     * @return
     */
    protected Form getPostForm(final String formName,
                            final SlingHttpServletRequest request) {
        final Map<String, String> map = new HashMap<String, String>();


        final RequestParameterMap requestMap = request.getRequestParameterMap();

        for (final String key : requestMap.keySet()) {
            // POST LookupKey formName param does not matter
            if(StringUtils.equals(key, this.getPostLookupKey(null))) { continue; }

            final RequestParameter[] values = requestMap.getValues(key);

            if (values == null || values.length == 0) {
                log.debug("Value did not exist for key: {}", key);
            } else if (values.length == 1) {
                log.debug("Adding to form data: {} ~> {}", key, values[0].toString());
                map.put(key, values[0].getString());
            } else {
                // TODO: Handle multi-value parameter values; Requires support for transporting them and re-writing them back into HTML Form on error
                for(final RequestParameter value : values) {
                    // Use the first non-blank value, or use the last value (which will be blank or not-blank)
                    final String tmp = value.toString();
                    map.put(key, tmp);

                    if(StringUtils.isNotBlank(tmp)) {
                        break;
                    }
                }
            }
        }

        return this.clean(new Form(formName, request.getResource().getPath(), map));
    }

    /**
     * Gets the Key used to look up the form during handling of POST requests
     *
     * @param formName
     * @return
     */
    protected String getPostLookupKey(final String formName) {
        // This may change; keeping as method call to ease future refactoring
        return FORM_NAME_INPUT;
    }

    /**
     * Removes unused Map entries from the provided map
     *
     * @param form
     * @return
     */
    protected Form clean(final Form form) {
        final Map<String, String> map = form.getData();
        final Map<String, String> cleanedMap = new HashMap<String, String>();

        for (final String key : map.keySet()) {
            if(!ArrayUtils.contains(FORM_INPUTS, key) && StringUtils.isNotBlank(map.get(key))) {
                cleanedMap.put(key, map.get(key));
            }
        }

        return new Form(form.getName(), form.getResourcePath(), cleanedMap, form.getErrors());
    }

    /**
     * Protect a Form in is entirety (data and errors)
     *
     * @param form
     * @return
     */
    protected Form getProtectedForm(final Form form) {
        return new Form(form.getName(),
                form.getResourcePath(),
                this.getProtectedData(form.getData()),
                this.getProtectedErrors(form.getErrors()));
    }

    /**
     * Protect a Map representing Form Data
     *
     * @param data
     * @return
     */
    protected Map<String, String> getProtectedData(final Map<String, String> data) {
        final Map<String, String> protectedData = new HashMap<String, String>();

        // Protect data for HTML Attributes
        for (final String key : data.keySet()) {
            protectedData.put(key, xssApi.encodeForHTMLAttr(data.get(key)));
        }

        return protectedData;
    }

    /**
     * Protect a Map representing Form Errors
     *
     * @param errors
     * @return
     */
    protected Map<String, String> getProtectedErrors(final Map<String, String> errors) {
        final Map<String, String> protectedErrors = new HashMap<String, String>();

        // Protect data for HTML
        for (final String key : errors.keySet()) {
            protectedErrors.put(key, xssApi.encodeForHTML(errors.get(key)));
        }

        return protectedErrors;
    }

    /**
     * Gets the Form Selector for the form POST request
     *
     * @param slingRequest
     * @return
     */
    protected String getFormSelector(final SlingHttpServletRequest slingRequest) {
        final RequestParameter requestParameter =
                slingRequest.getRequestParameter(FORM_SELECTOR_INPUT);
        if(requestParameter == null) { return null; }
        return StringUtils.stripToNull(requestParameter.getString());
    }

    /**
     * Encodes URL data
     *
     * @param unencoded
     * @return
     */
    protected String encode(String unencoded) {
        if(StringUtils.isBlank(unencoded)) {
            return "";
        }

        try {
            return java.net.URLEncoder.encode(unencoded, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return unencoded;
        }
    }

    /**
     * Decodes URL data
     *
     * @param encoded
     * @return
     */
    protected String decode(String encoded) {
        if(StringUtils.isBlank(encoded)) {
            return "";
        }

        try {
            return java.net.URLDecoder.decode((encoded), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return encoded;
        }
    }

    @Activate
    protected void activate(final Map<String, String> properties) {
        this.suffix = PropertiesUtil.toString(properties.get(PROP_SUFFIX), DEFAULT_SUFFIX);
        if(StringUtils.isBlank(this.suffix)) {
            // No whitespace please
            this.suffix = DEFAULT_SUFFIX;
        }
    }
}