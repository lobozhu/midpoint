/*
 * Copyright (c) 2010-2013 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.notifications.impl;

import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.events.*;
import com.evolveum.midpoint.notifications.impl.events.*;
import com.evolveum.midpoint.notifications.impl.util.EventHelper;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.api.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.datatype.Duration;
import java.util.List;

/**
 * Listener that accepts events generated by workflow module. These events are related to processes and work items.
 *
 * TODO what about tasks? Should the task (wfTask) be passed to the notification module?
 *
 * @author mederly
 */
@Component
public class WorkflowListenerImpl implements WorkflowListener {

    private static final Trace LOGGER = TraceManager.getTrace(WorkflowListenerImpl.class);

    //private static final String DOT_CLASS = WorkflowListener.class.getName() + ".";

    @Autowired private NotificationManager notificationManager;
    @Autowired private NotificationFunctionsImpl functions;
    @Autowired private LightweightIdentifierGenerator identifierGenerator;
    @Autowired private EventHelper eventHelper;

    // WorkflowManager is not required, because e.g. within model-test and model-intest we have no workflows.
    // However, during normal operation, it is expected to be available.

    @Autowired(required = false) private WorkflowManager workflowManager;

    @PostConstruct
    public void init() {
        if (workflowManager != null) {
            workflowManager.registerWorkflowListener(this);
        } else {
            LOGGER.warn("WorkflowManager not present, notifications for workflows will not be enabled.");
        }
    }

    //region Process-level notifications
    @Override
    public void onProcessInstanceStart(CaseType aCase, Task task, OperationResult result) {
        WorkflowProcessEventImpl event = new WorkflowProcessEventImpl(identifierGenerator, ChangeType.ADD, aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onProcessInstanceEnd(CaseType aCase, Task task, OperationResult result) {
        WorkflowProcessEventImpl event = new WorkflowProcessEventImpl(identifierGenerator, ChangeType.DELETE, aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }
    //endregion

    //region WorkItem-level notifications
    @Override
    public void onWorkItemCreation(ObjectReferenceType assignee, @NotNull CaseWorkItemType workItem,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemEventImpl event = new WorkItemLifecycleEventImpl(identifierGenerator, ChangeType.ADD, workItem,
                SimpleObjectRefImpl.create(functions, assignee), null, null, null,
                aCase.getApprovalContext(), aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onWorkItemDeletion(ObjectReferenceType assignee, @NotNull CaseWorkItemType workItem,
            WorkItemOperationInfo operationInfo, WorkItemOperationSourceInfo sourceInfo,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemEventImpl event = new WorkItemLifecycleEventImpl(identifierGenerator, ChangeType.DELETE, workItem,
                SimpleObjectRefImpl.create(functions, assignee),
                getInitiator(sourceInfo), operationInfo, sourceInfo, aCase.getApprovalContext(), aCase);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onWorkItemCustomEvent(ObjectReferenceType assignee, @NotNull CaseWorkItemType workItem,
            @NotNull WorkItemNotificationActionType notificationAction, WorkItemEventCauseInformationType cause,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemEventImpl event = new WorkItemCustomEventImpl(identifierGenerator, ChangeType.ADD, workItem,
                SimpleObjectRefImpl.create(functions, assignee),
                new WorkItemOperationSourceInfo(null, cause, notificationAction),
                aCase.getApprovalContext(), aCase, notificationAction.getHandler());
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    @Override
    public void onWorkItemAllocationChangeCurrentActors(@NotNull CaseWorkItemType workItem,
            @NotNull WorkItemAllocationChangeOperationInfo operationInfo,
            @Nullable WorkItemOperationSourceInfo sourceInfo,
            Duration timeBefore, CaseType aCase,
            Task task, OperationResult result) {
        checkOids(operationInfo.getCurrentActors());
        for (ObjectReferenceType currentActor : operationInfo.getCurrentActors()) {
            onWorkItemAllocationModifyDelete(currentActor, workItem, operationInfo, sourceInfo, timeBefore, aCase, task, result);
        }
    }

    @Override
    public void onWorkItemAllocationChangeNewActors(@NotNull CaseWorkItemType workItem,
            @NotNull WorkItemAllocationChangeOperationInfo operationInfo,
            @Nullable WorkItemOperationSourceInfo sourceInfo,
            CaseType aCase, Task task, OperationResult result) {
        Validate.notNull(operationInfo.getNewActors());

        checkOids(operationInfo.getCurrentActors());
        checkOids(operationInfo.getNewActors());
        for (ObjectReferenceType newActor : operationInfo.getNewActors()) {
            onWorkItemAllocationAdd(newActor, workItem, operationInfo, sourceInfo, aCase, task, result);
        }
    }

    private void checkOids(List<ObjectReferenceType> refs) {
        refs.forEach(r -> Validate.notNull(r.getOid(), "No OID in actor object reference " + r));
    }

    private void onWorkItemAllocationAdd(ObjectReferenceType newActor, @NotNull CaseWorkItemType workItem,
            @Nullable WorkItemOperationInfo operationInfo, @Nullable WorkItemOperationSourceInfo sourceInfo,
            CaseType aCase, Task task, OperationResult result) {
        WorkItemAllocationEventImpl event = new WorkItemAllocationEventImpl(identifierGenerator, ChangeType.ADD, workItem,
                SimpleObjectRefImpl.create(functions, newActor),
                getInitiator(sourceInfo), operationInfo, sourceInfo,
                aCase.getApprovalContext(), aCase, null);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }

    private SimpleObjectRef getInitiator(WorkItemOperationSourceInfo sourceInfo) {
        return sourceInfo != null ?
                SimpleObjectRefImpl.create(functions, sourceInfo.getInitiatorRef()) : null;
    }

    private void onWorkItemAllocationModifyDelete(ObjectReferenceType currentActor, @NotNull CaseWorkItemType workItem,
            @Nullable WorkItemOperationInfo operationInfo, @Nullable WorkItemOperationSourceInfo sourceInfo,
            Duration timeBefore, CaseType aCase,
            Task task, OperationResult result) {
        WorkItemAllocationEventImpl event = new WorkItemAllocationEventImpl(identifierGenerator,
                timeBefore != null ? ChangeType.MODIFY : ChangeType.DELETE, workItem,
                SimpleObjectRefImpl.create(functions, currentActor),
                getInitiator(sourceInfo), operationInfo, sourceInfo,
                aCase.getApprovalContext(), aCase, timeBefore);
        initializeWorkflowEvent(event, aCase);
        eventHelper.processEvent(event, task, result);
    }
    //endregion

    private void initializeWorkflowEvent(WorkflowEventImpl event, CaseType aCase) {
        event.setRequester(SimpleObjectRefImpl.create(functions, aCase.getRequestorRef()));
        event.setRequestee(SimpleObjectRefImpl.create(functions, aCase.getObjectRef()));
        // TODO what if requestee is yet to be created?
    }

}
