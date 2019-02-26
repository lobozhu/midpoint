/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.impl.processes.itemApproval;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.builder.S_ItemEntry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.ObjectTreeDeltas;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.WfContextUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.schema.util.WorkItemId;
import com.evolveum.midpoint.wf.impl.processes.common.WfTimedActionTriggerHandler;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.wf.impl.processes.common.SpringApplicationContextHolder.getCacheRepositoryService;
import static com.evolveum.midpoint.wf.impl.processes.common.SpringApplicationContextHolder.getPrismContext;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.CaseType.F_EVENT;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType.F_WORKFLOW_CONTEXT;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WfContextType.F_PROCESSOR_SPECIFIC_STATE;

/**
 * @author mederly
 */
public class MidpointUtil {

	private static final Trace LOGGER = TraceManager.getTrace(MidpointUtil.class);

	// additional delta is a bit hack ... TODO refactor (but without splitting the modify operation!)
	public static void recordEventInCase(CaseEventType event, ObjectDeltaType additionalDelta, String caseOid, OperationResult result) {
		RepositoryService cacheRepositoryService = getCacheRepositoryService();
		PrismContext prismContext = getPrismContext();
		try {
			S_ItemEntry deltaBuilder = getPrismContext().deltaFor(CaseType.class)
					.item(F_EVENT).add(event);

			if (additionalDelta != null) {
				PrismObject<CaseType> aCase = cacheRepositoryService.getObject(CaseType.class, caseOid, null, result);
				WfPrimaryChangeProcessorStateType state = WfContextUtil
						.getPrimaryChangeProcessorState(aCase.asObjectable().getWorkflowContext());
				ObjectTreeDeltasType updatedDelta = ObjectTreeDeltas.mergeDeltas(state.getDeltasToProcess(),
						additionalDelta, prismContext);
				ItemPath deltasToProcessPath = ItemPath.create(F_WORKFLOW_CONTEXT, F_PROCESSOR_SPECIFIC_STATE,
						WfPrimaryChangeProcessorStateType.F_DELTAS_TO_PROCESS);		// assuming it already exists!
				ItemDefinition<?> deltasToProcessDefinition = getPrismContext().getSchemaRegistry()
						.findContainerDefinitionByCompileTimeClass(WfPrimaryChangeProcessorStateType.class)
						.findItemDefinition(WfPrimaryChangeProcessorStateType.F_DELTAS_TO_PROCESS);
				deltaBuilder = deltaBuilder.item(deltasToProcessPath, deltasToProcessDefinition)
						.replace(updatedDelta);
			}
			cacheRepositoryService.modifyObject(CaseType.class, caseOid, deltaBuilder.asItemDeltas(), result);
		} catch (ObjectNotFoundException | SchemaException | ObjectAlreadyExistsException e) {
			throw new SystemException("Couldn't record decision to the case " + caseOid + ": " + e.getMessage(), e);
		}
	}

	public static Set<ObjectReferenceType> expandGroups(Set<ObjectReferenceType> approverRefs) {
		PrismContext prismContext = getPrismContext();
		Set<ObjectReferenceType> rv = new HashSet<>();
		for (ObjectReferenceType approverRef : approverRefs) {
			@SuppressWarnings({ "unchecked", "raw" })
			Class<? extends Containerable> clazz = (Class<? extends Containerable>)
					prismContext.getSchemaRegistry().getCompileTimeClassForObjectType(approverRef.getType());
			if (clazz == null) {
				throw new IllegalStateException("Unknown object type " + approverRef.getType());
			}
			if (UserType.class.isAssignableFrom(clazz)) {
				rv.add(approverRef.clone());
			} else if (AbstractRoleType.class.isAssignableFrom(clazz)) {
				rv.addAll(expandAbstractRole(approverRef, prismContext));
			} else {
				LOGGER.warn("Unexpected type {} for approver: {}", clazz, approverRef);
				rv.add(approverRef.clone());
			}
		}
		return rv;
	}

	private static Collection<ObjectReferenceType> expandAbstractRole(ObjectReferenceType approverRef, PrismContext prismContext) {
		ObjectQuery query = prismContext.queryFor(UserType.class)
				.item(FocusType.F_ROLE_MEMBERSHIP_REF).ref(approverRef.asReferenceValue())
				.build();
		try {
			return getCacheRepositoryService()
					.searchObjects(UserType.class, query, null, new OperationResult("dummy"))
					.stream()
					.map(o -> ObjectTypeUtil.createObjectRef(o, prismContext))
					.collect(Collectors.toList());
		} catch (SchemaException e) {
			throw new SystemException("Couldn't resolve " + approverRef + ": " + e.getMessage(), e);
		}
	}

	public static void createTriggersForTimedActions(WorkItemId workItemId, int escalationLevel, Date workItemCreateTime,
			Date workItemDeadline, CaseType wfCase, List<WorkItemTimedActionsType> timedActionsList, OperationResult result) {
		LOGGER.trace("Creating triggers for timed actions for work item {}, escalation level {}, create time {}, deadline {}, {} timed action(s)",
				workItemId, escalationLevel, workItemCreateTime, workItemDeadline, timedActionsList.size());
		try {
			PrismContext prismContext = getPrismContext();
			List<TriggerType> triggers = WfContextUtil.createTriggers(escalationLevel, workItemCreateTime, workItemDeadline,
					timedActionsList, prismContext, LOGGER, workItemId.asString(), WfTimedActionTriggerHandler.HANDLER_URI);
			LOGGER.trace("Adding {} triggers to {}:\n{}", triggers.size(), wfCase,
					PrismUtil.serializeQuietlyLazily(prismContext, triggers));
			if (!triggers.isEmpty()) {
				List<ItemDelta<?, ?>> itemDeltas = prismContext.deltaFor(TaskType.class)
						.item(TaskType.F_TRIGGER).add(PrismContainerValue.toPcvList(triggers))
						.asItemDeltas();
				getCacheRepositoryService().modifyObject(CaseType.class, wfCase.getOid(), itemDeltas, result);
			}
		} catch (ObjectNotFoundException | SchemaException | ObjectAlreadyExistsException | RuntimeException e) {
			throw new SystemException("Couldn't add trigger(s) to " + wfCase + ": " + e.getMessage(), e);
		}
	}

	public static void removeTriggersForWorkItem(CaseType aCase, WorkItemId workItemId, OperationResult result) {
		List<PrismContainerValue<TriggerType>> toDelete = new ArrayList<>();
		for (TriggerType triggerType : aCase.getTrigger()) {
			if (WfTimedActionTriggerHandler.HANDLER_URI.equals(triggerType.getHandlerUri())) {
				PrismProperty workItemIdProperty = triggerType.getExtension().asPrismContainerValue()
						.findProperty(SchemaConstants.MODEL_EXTENSION_WORK_ITEM_ID);
				if (workItemIdProperty != null && workItemId.asString().equals(workItemIdProperty.getRealValue())) {
					toDelete.add(triggerType.clone().asPrismContainerValue());
				}
			}
		}
		removeSelectedTriggers(aCase, toDelete, result);
	}

	// not necessary any more, as work item triggers are deleted when the work item (task) is deleted
	// (and there are currently no triggers other than work-item-related)
	public static void removeAllStageTriggersForWorkItem(CaseType aCase, OperationResult result) {
		List<PrismContainerValue<TriggerType>> toDelete = new ArrayList<>();
		for (TriggerType triggerType : aCase.getTrigger()) {
			if (WfTimedActionTriggerHandler.HANDLER_URI.equals(triggerType.getHandlerUri())) {
				toDelete.add(triggerType.clone().asPrismContainerValue());
			}
		}
		removeSelectedTriggers(aCase, toDelete, result);
	}

	private static void removeSelectedTriggers(CaseType aCase, List<PrismContainerValue<TriggerType>> toDelete, OperationResult result) {
		LOGGER.trace("About to delete {} triggers from {}: {}", toDelete.size(), aCase, toDelete);
		if (!toDelete.isEmpty()) {
			try {
				List<ItemDelta<?, ?>> itemDeltas = getPrismContext().deltaFor(TaskType.class)
						.item(TaskType.F_TRIGGER).delete(toDelete)
						.asItemDeltas();
				getCacheRepositoryService().modifyObject(CaseType.class, aCase.getOid(), itemDeltas, result);
			} catch (SchemaException|ObjectNotFoundException|ObjectAlreadyExistsException|RuntimeException e) {
				LoggingUtils.logUnexpectedException(LOGGER, "Couldn't remove triggers from {}", e, aCase);
			}
		}
	}
}
