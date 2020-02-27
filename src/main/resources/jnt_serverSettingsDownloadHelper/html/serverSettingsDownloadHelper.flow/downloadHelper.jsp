<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="flowRequestContext" type="org.springframework.webflow.execution.RequestContext"--%>
<template:addResources type="javascript"
                       resources="jquery.min.js,jquery-ui.min.js,admin-bootstrap.js,bootstrapSwitch.js,regexValidation.js"/>
<template:addResources type="css" resources="jquery-ui.smoothness.css,jquery-ui.smoothness-jahia.css,bootstrapSwitch.css"/>

<script type="text/javascript">
    $(document).ready(function () {
        $('[data-toggle="tooltip"]').tooltip();
    });
</script>


<h2>
    <fmt:message key="downloadHelper.settings"/>
</h2>
<p>
    <c:forEach items="${flowRequestContext.messageContext.allMessages}" var="message">
        <c:if test="${message.severity eq 'ERROR'}">
        <div class="alert alert-error">
            <button type="button" class="close" data-dismiss="alert">&times;</button>
            ${message.text}
        </div>
    </c:if>
</c:forEach>
</p>
<c:if test="${downloadStarted}">
    <div class="alert alert-success">
        <button type="button" class="close" data-dismiss="alert">&times;</button>
        <fmt:message key="label.download.started"/>
    </div>
</c:if>
<div class="box-1">
    You have <b>${availableSpace}</b> available in the folder <b>${downloadFolderPath}</b>.

    <form class="form-horizontal" id="downloadHelperSettings" name="downloadHelperSettings" action='${flowExecutionUrl}' method="post">
        <input type="hidden" name="_eventId" value="submitDownloadHelperSettings"/>
        <div class="control-group" id="group-serviceActivated" data-error="<fmt:message key="downloadHelper.errors.activated"/>">
            <label class="control-label"><fmt:message key="downloadHelper.protocol"/>:</label>
            <div class="controls">
                <select id="protocol" name="protocol">
                    <option value="https">https://</option>
                    <option value="ftp">ftp://</option>
                </select> 
            </div>
            <label class="control-label"><fmt:message key="downloadHelper.url"/>:</label>
            <div class="controls">
                <input type="text" name="url" id="url"/>
            </div>
            <label class="control-label"><fmt:message key="downloadHelper.filename"/>:</label>
            <div class="controls">
                <input type="text" name="filename" id="filename"/>
            </div>
            <label class="control-label"><fmt:message key="downloadHelper.login"/>:</label>
            <div class="controls">
                <input type="text" name="login" id="login"/>
            </div>
            <label class="control-label"><fmt:message key="downloadHelper.password"/>:</label>
            <div class="controls">
                <input type="password" name="password" id="password" value="${password}"/>
            </div>
            <label class="control-label"><fmt:message key="downloadHelper.email"/>:</label>
            <div class="controls">
                <input type="text" name="email" id="email"/>
            </div>
        </div>
        <div class="control-group">
            <div class="controls">
                <button class="btn btn-primary" type="submit">
                    <i class="icon-${'share'} icon-white"></i>
                    &nbsp;<fmt:message key="label.${'update'}"/>
                </button>
            </div>
        </div>
    </form>
</div>
