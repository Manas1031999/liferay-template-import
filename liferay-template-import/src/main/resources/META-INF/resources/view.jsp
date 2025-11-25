<%@ page import="com.liferay.portal.kernel.util.ParamUtil" %>
<%@ include file="init.jsp" %>
<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<%@ taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %>


<portlet:defineObjects />

<portlet:actionURL name="/import/templates" var="importActionURL" />

<div class="container-fluid container-form">
    <h3>Bulk Import Templates</h3>

    <aui:form action="<%= importActionURL %>" method="post" enctype="multipart/form-data" name="fm">
        <aui:fieldset>
            <aui:input type="file" name="meta_csv" label="CSV Metadata File (meta.csv)" />
            <aui:input type="file" name="templates_zip" label="ZIP of Templates (templates.zip)" />
        </aui:fieldset>

        <aui:button-row>
            <aui:button type="submit" value="Import FTLs" cssClass="btn-primary" />
        </aui:button-row>
    </aui:form>
</div>

<%
    // show import result if present
    String importResult = (String)request.getAttribute("importResult");
    if (importResult != null) {
%>
    <div class="alert alert-info" style="margin-top:20px;">
        <strong>Import Results:</strong>
        <pre><%= importResult %></pre>
    </div>
<%
    }
%>
<liferay-ui:success key="import-success" message="Templates imported successfully." />
<liferay-ui:error key="import-failed" message="<%= (String)request.getAttribute(\"importExceptionMessage\") %>" />