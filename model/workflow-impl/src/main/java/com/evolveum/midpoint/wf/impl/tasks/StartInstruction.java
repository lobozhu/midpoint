/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.wf.impl.tasks;

import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelState;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.LocalizationUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.impl.processors.BaseModelInvocationProcessingHelper;
import com.evolveum.midpoint.wf.impl.processors.ChangeProcessor;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.evolveum.midpoint.prism.xml.XmlTypeConverter.createXMLGregorianCalendar;
import static com.evolveum.midpoint.schema.util.ObjectTypeUtil.createObjectRef;

/**
 * A generic instruction to start a background task; with or without a workflow process instance.
 * May be subclassed in order to add further functionality.
 *
 * @author mederly
 */
public class StartInstruction implements DebugDumpable {

	private static final Trace LOGGER = TraceManager.getTrace(StartInstruction.class);

	protected final CaseType aCase;
	private final ChangeProcessor changeProcessor;

    //region Constructors
    protected StartInstruction(@NotNull ChangeProcessor changeProcessor) {
        this.changeProcessor = changeProcessor;
	    PrismContext prismContext = changeProcessor.getPrismContext();
	    aCase = new CaseType(prismContext);
		aCase.setWorkflowContext(new WfContextType(prismContext));
		aCase.setMetadata(new MetadataType(prismContext));
	    aCase.getMetadata().setCreateTimestamp(createXMLGregorianCalendar(new Date()));
    }

	@SuppressWarnings("unchecked")
	public static StartInstruction create(ChangeProcessor changeProcessor) {
		return new StartInstruction(changeProcessor);
	}
	//endregion

    // region Getters and setters
	public ChangeProcessor getChangeProcessor() {
		return changeProcessor;
	}

	protected PrismContext getPrismContext() { return changeProcessor.getPrismContext(); }

//	public void setProcessInstanceName(String name) {
//		aCase.getWorkflowContext().setProcessInstanceName(name);
//	}

	public void setLocalizableName(LocalizableMessage name) {
	    aCase.setLocalizableName(LocalizationUtil.createLocalizableMessageType(name));
	}

    public void setName(String name) {
    	aCase.setName(PolyStringType.fromOrig(name));
    }

    public boolean startsWorkflowProcess() {
        return getWfContext().getProcessSpecificState() != null;
    }

    public void setModelContext(ModelContext<?> context) throws SchemaException {
    	LensContextType bean;
	    if (context != null) {
		    boolean reduced = context.getState() == ModelState.PRIMARY;
		    bean = ((LensContext) context).toLensContextType(reduced);
	    } else {
	    	bean = null;
	    }
	    aCase.setModelContext(bean);
    }

	public void setObjectRef(ObjectReferenceType ref, OperationResult result) {
		ref = getChangeProcessor().getMiscDataUtil().resolveObjectReferenceName(ref, result);
		aCase.setObjectRef(ref);
	}

	public void setObjectRef(ModelContext<?> modelContext) {
		ObjectType focus = BaseModelInvocationProcessingHelper.getFocusObjectNewOrOld(modelContext);
		ObjectDelta<?> primaryDelta = modelContext.getFocusContext().getPrimaryDelta();
		ObjectReferenceType ref;
		if (primaryDelta != null && primaryDelta.isAdd()) {
			ref = ObjectTypeUtil.createObjectRefWithFullObject(focus, getPrismContext());
		} else {
			ref = ObjectTypeUtil.createObjectRef(focus, getPrismContext());
		}
		aCase.setObjectRef(ref);
	}

	public void setTargetRef(ObjectReferenceType ref, OperationResult result) {
		ref = getChangeProcessor().getMiscDataUtil().resolveObjectReferenceName(ref, result);
		aCase.setTargetRef(ref);
	}

    public void setRequesterRef(PrismObject<UserType> requester) {
		aCase.setRequestorRef(createObjectRef(requester, getPrismContext()));
    }

	public WfContextType getWfContext() {
		return aCase.getWorkflowContext();
	}

	//endregion

    //region Diagnostics

	@Override
	public String toString() {
		return "StartInstruction{" +
				"aCase=" + aCase +
				", changeProcessor=" + changeProcessor +
				'}';
	}

	@Override
    public String debugDump() {
        return debugDump(0);
    }

	@Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();

        DebugUtil.indentDebugDump(sb, indent);
        sb.append("Start instruction: ");
		sb.append(startsWorkflowProcess() ? "with-process" : "no-process").append(", model-context: ");
		sb.append(aCase.getModelContext() != null ? "YES" : "no").append("\n");
		DebugUtil.indentDebugDump(sb, indent+1);
		sb.append("Case:\n");
		sb.append(aCase.asPrismContainerValue().debugDump(indent+2)).append("\n");
        return sb.toString();
    }

    public void setParent(CaseType parent) {
    	aCase.setParentRef(ObjectTypeUtil.createObjectRef(parent, getPrismContext()));
    }
    //endregion

	//region "Output" methods
	public CaseType getCase() {
		return aCase;
	}

	protected void setProcessState(WfProcessSpecificStateType processState) {
		getWfContext().setProcessSpecificState(processState);
	}

	//endregion
}