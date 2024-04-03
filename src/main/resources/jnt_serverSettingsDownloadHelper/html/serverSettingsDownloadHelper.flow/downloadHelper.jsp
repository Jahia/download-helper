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

<div class="page-header">
    <h2>
        <fmt:message key="downloadHelper.settings"/>
    </h2>
</div>
<c:choose>
    <c:when test="${processingServer}">
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
        <div class="panel panel-default">
            <div class="panel-heading">
                You have <b>${availableSpace}</b> available in the folder <b>${downloadFolderPath}</b>.
            </div>
            <div class="panel-body">
                <div class="row">
                    <div class=" col-md-offset-2 col-md-6">
                        <form class="form-horizontal" id="downloadHelperSettings" name="downloadHelperSettings"
                              action='${flowExecutionUrl}' method="post">
                            <input type="hidden" name="_eventId" value="submitDownloadHelperSettings"/>
                            <div class="form-group"
                                 data-error="<fmt:message key="downloadHelper.errors.activated"/>">
                                <label for="protocol" class="col-sm-2 control-label"><fmt:message
                                        key="downloadHelper.protocol"/>:</label>
                                <div class="col-sm-10">
                                    <select id="protocol" name="protocol" class="form-control">
                                        <option value="https">https://</option>
                                        <option value="ftp">ftp://</option>
                                    </select>
                                </div>
                            </div>
                            <div class="form-group">
                                <label class="col-sm-2 control-label" for="url"><fmt:message
                                        key="downloadHelper.url"/>:</label>
                                <div class="col-sm-10">
                                    <input type="text" name="url" id="url" class="form-control"/>
                                </div>
                            </div>
                            <div class="form-group">
                                <label class="col-sm-2 control-label" for="filename"><fmt:message
                                        key="downloadHelper.filename"/>:</label>
                                <div class="col-sm-10">
                                    <input type="text" name="filename" id="filename" class="form-control"/>
                                </div>
                            </div>
                            <div class="form-group">
                                <label class="col-sm-2 control-label" for="login"><fmt:message
                                        key="downloadHelper.login"/>:</label>
                                <div class="col-sm-10">
                                    <input type="text" name="login" id="login" class="form-control"/>
                                </div>
                            </div>
                            <div class="form-group">
                                <label class="col-sm-2 control-label" for="password"><fmt:message
                                        key="downloadHelper.password"/>:</label>
                                <div class="col-sm-10">
                                    <input type="password" name="password" id="password" value="${password}"
                                           class="form-control"/>
                                </div>
                            </div>
                            <div class="form-group">
                                <label class="col-sm-2 control-label" for="email"><fmt:message
                                        key="downloadHelper.email"/>:</label>
                                <div class="col-sm-10">
                                    <input type="text" name="email" id="email" class="form-control"/>
                                </div>
                            </div>
                            <div class="form-group">
                                <div class="col-sm-offset-2 col-sm-10">
                                    <button class="btn btn-primary" type="submit">
                                        <i class="icon-${'share'} icon-white"></i>
                                        &nbsp;<fmt:message key="label.${'update'}"/>
                                    </button>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </c:when>
    <c:otherwise>
        This module can only be used on the processing server.
    </c:otherwise>
</c:choose>
