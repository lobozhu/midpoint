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

package com.evolveum.midpoint.wf.impl.policy;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelState;
import com.evolveum.midpoint.model.api.hooks.HookOperationMode;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.impl.AbstractModelImplementationIntegrationTest;
import com.evolveum.midpoint.model.impl.lens.Clockwork;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.DeltaFactory;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.S_AtomicFilterExit;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.RelationRegistry;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.*;
import com.evolveum.midpoint.security.api.SecurityUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskExecutionStatus;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.test.AbstractIntegrationTest;
import com.evolveum.midpoint.test.Checker;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.wf.api.WorkflowManager;
import com.evolveum.midpoint.wf.impl.WfTestHelper;
import com.evolveum.midpoint.wf.impl.WfTestUtil;
import com.evolveum.midpoint.wf.impl.engine.WorkflowEngine;
import com.evolveum.midpoint.wf.impl.WorkflowResult;
import com.evolveum.midpoint.wf.impl.processors.MiscHelper;
import com.evolveum.midpoint.wf.impl.processors.general.GeneralChangeProcessor;
import com.evolveum.midpoint.wf.impl.processors.primary.PrimaryChangeProcessor;
import com.evolveum.midpoint.wf.impl._temp.TemporaryHelper;
import com.evolveum.midpoint.wf.impl.util.MiscDataUtil;
import com.evolveum.midpoint.wf.util.QueryUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.evolveum.midpoint.prism.PrismConstants.T_PARENT;
import static com.evolveum.midpoint.schema.GetOperationOptions.createRetrieve;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.CaseType.*;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType.F_WORKFLOW_CONTEXT;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WfContextType.F_PROCESSOR_SPECIFIC_STATE;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WfPrimaryChangeProcessorStateType.F_DELTAS_TO_PROCESS;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.CaseWorkItemType.*;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.testng.AssertJUnit.*;

/**
 * @author mederly
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-workflow-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class AbstractWfTestPolicy extends AbstractModelImplementationIntegrationTest {

	protected static final File TEST_RESOURCE_DIR = new File("src/test/resources/policy");
	private static final File SYSTEM_CONFIGURATION_FILE = new File(TEST_RESOURCE_DIR, "system-configuration.xml");
	public static final File ROLE_SUPERUSER_FILE = new File(TEST_RESOURCE_DIR, "role-superuser.xml");
	public static final File USER_ADMINISTRATOR_FILE = new File(TEST_RESOURCE_DIR, "user-administrator.xml");

	protected static final File USER_JACK_FILE = new File(TEST_RESOURCE_DIR, "user-jack.xml");
	protected static final File USER_JACK_DEPUTY_FILE = new File(TEST_RESOURCE_DIR, "user-jack-deputy.xml");        // delegation is created only when needed
	protected static final File USER_BOB_FILE = new File(TEST_RESOURCE_DIR, "user-bob.xml");
	protected static final File USER_CHUCK_FILE = new File(TEST_RESOURCE_DIR, "user-chuck.xml");
	protected static final File USER_LEAD1_FILE = new File(TEST_RESOURCE_DIR, "user-lead1.xml");
	protected static final File USER_LEAD1_DEPUTY_1_FILE = new File(TEST_RESOURCE_DIR, "user-lead1-deputy1.xml");
	protected static final File USER_LEAD1_DEPUTY_2_FILE = new File(TEST_RESOURCE_DIR, "user-lead1-deputy2.xml");
	protected static final File USER_LEAD2_FILE = new File(TEST_RESOURCE_DIR, "user-lead2.xml");
	protected static final File USER_LEAD3_FILE = new File(TEST_RESOURCE_DIR, "user-lead3.xml");
	protected static final File USER_LEAD10_FILE = new File(TEST_RESOURCE_DIR, "user-lead10.xml");
	protected static final File USER_LEAD15_FILE = new File(TEST_RESOURCE_DIR, "user-lead15.xml");
	protected static final File USER_SECURITY_APPROVER_FILE = new File(TEST_RESOURCE_DIR, "user-security-approver.xml");
	protected static final File USER_SECURITY_APPROVER_DEPUTY_FILE = new File(TEST_RESOURCE_DIR, "user-security-approver-deputy.xml");
	protected static final File USER_SECURITY_APPROVER_DEPUTY_LIMITED_FILE = new File(TEST_RESOURCE_DIR, "user-security-approver-deputy-limited.xml");

	protected static final File ROLE_APPROVER_FILE = new File(TEST_RESOURCE_DIR, "041-role-approver.xml");
	protected static final File METAROLE_DEFAULT_FILE = new File(TEST_RESOURCE_DIR, "metarole-default.xml");
	protected static final File METAROLE_SECURITY_FILE = new File(TEST_RESOURCE_DIR, "metarole-security.xml");
	// following 2 are not used by default (assigned when necessary)
	protected static final File METAROLE_PRUNE_TEST2X_ROLES_FILE = new File(TEST_RESOURCE_DIR, "metarole-prune-test2x-roles.xml");
	protected static final File METAROLE_APPROVE_UNASSIGN_FILE = new File(TEST_RESOURCE_DIR, "metarole-approve-unassign.xml");
	protected static final File ROLE_ROLE1_FILE = new File(TEST_RESOURCE_DIR, "role-role1.xml");
	protected static final File ROLE_ROLE1A_FILE = new File(TEST_RESOURCE_DIR, "role-role1a.xml");
	protected static final File ROLE_ROLE1B_FILE = new File(TEST_RESOURCE_DIR, "role-role1b.xml");
	protected static final File ROLE_ROLE2_FILE = new File(TEST_RESOURCE_DIR, "role-role2.xml");
	protected static final File ROLE_ROLE2A_FILE = new File(TEST_RESOURCE_DIR, "role-role2a.xml");
	protected static final File ROLE_ROLE2B_FILE = new File(TEST_RESOURCE_DIR, "role-role2b.xml");
	protected static final File ROLE_ROLE3_FILE = new File(TEST_RESOURCE_DIR, "role-role3.xml");
	protected static final File ROLE_ROLE3A_FILE = new File(TEST_RESOURCE_DIR, "role-role3a.xml");
	protected static final File ROLE_ROLE3B_FILE = new File(TEST_RESOURCE_DIR, "role-role3b.xml");
	protected static final File ROLE_ROLE4_FILE = new File(TEST_RESOURCE_DIR, "role-role4.xml");
	protected static final File ROLE_ROLE4A_FILE = new File(TEST_RESOURCE_DIR, "role-role4a.xml");
	protected static final File ROLE_ROLE4B_FILE = new File(TEST_RESOURCE_DIR, "role-role4b.xml");
	protected static final File ROLE_ROLE10_FILE = new File(TEST_RESOURCE_DIR, "role-role10.xml");
	protected static final File ROLE_ROLE10A_FILE = new File(TEST_RESOURCE_DIR, "role-role10a.xml");
	protected static final File ROLE_ROLE10B_FILE = new File(TEST_RESOURCE_DIR, "role-role10b.xml");
	protected static final File ROLE_ROLE15_FILE = new File(TEST_RESOURCE_DIR, "role-role15.xml");
	protected static final File ROLE_FOCUS_ASSIGNMENT_MAPPING = new File(TEST_RESOURCE_DIR, "role-focus-assignment-mapping.xml");

	protected static final File USER_TEMPLATE_ASSIGNING_ROLE_1A = new File(TEST_RESOURCE_DIR, "user-template-assigning-role1a.xml");
	protected static final File USER_TEMPLATE_ASSIGNING_ROLE_1A_AFTER = new File(TEST_RESOURCE_DIR, "user-template-assigning-role1a-after.xml");

	protected static final String USER_ADMINISTRATOR_OID = SystemObjectsType.USER_ADMINISTRATOR.value();

	protected String userJackOid;
	protected String userJackDeputyOid;
	protected String userBobOid;
	protected String userChuckOid;
	protected String userLead1Oid;
	protected String userLead1Deputy1Oid;
	protected String userLead1Deputy2Oid;
	protected String userLead2Oid;
	protected String userLead3Oid;
	protected String userLead10Oid;			// imported later
	protected String userLead15Oid;
	protected String userSecurityApproverOid;
	protected String userSecurityApproverDeputyOid;
	protected String userSecurityApproverDeputyLimitedOid;

	protected String roleApproverOid;
	protected String metaroleDefaultOid;
	protected String metaroleSecurityOid;
	protected String metarolePruneTest2xRolesOid;
	protected String metaroleApproveUnassign;
	protected String roleRole1Oid;
	protected String roleRole1aOid;
	protected String roleRole1bOid;
	protected String roleRole2Oid;
	protected String roleRole2aOid;
	protected String roleRole2bOid;
	protected String roleRole3Oid;
	protected String roleRole3aOid;
	protected String roleRole3bOid;
	protected String roleRole4Oid;
	protected String roleRole4aOid;
	protected String roleRole4bOid;
	protected String roleRole10Oid;
	protected String roleRole10aOid;
	protected String roleRole10bOid;
	protected String roleRole15Oid;
	protected String roleFocusAssignmentMapping;

	protected String userTemplateAssigningRole1aOid;
	protected String userTemplateAssigningRole1aOidAfter;

	@Autowired protected Clockwork clockwork;
	@Autowired protected TaskManager taskManager;
	@Autowired protected WorkflowManager workflowManager;
	@Autowired protected WorkflowEngine workflowEngine;
	@Autowired protected TemporaryHelper temporaryHelper;
	@Autowired protected MiscDataUtil miscDataUtil;
	@Autowired protected PrimaryChangeProcessor primaryChangeProcessor;
	@Autowired protected GeneralChangeProcessor generalChangeProcessor;
	@Autowired protected SystemObjectCache systemObjectCache;
	@Autowired protected RelationRegistry relationRegistry;
	@Autowired protected WfTestHelper testHelper;
	@Autowired protected MiscHelper miscHelper;

	protected PrismObject<UserType> userAdministrator;

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
		modelService.postInit(initResult);

		PrismObject<SystemConfigurationType> sysconfig = prismContext.parseObject(getSystemConfigurationFile());
		updateSystemConfiguration(sysconfig.asObjectable());
		repoAddObject(sysconfig, initResult);

		repoAddObjectFromFile(ROLE_SUPERUSER_FILE, initResult);
		userAdministrator = repoAddObjectFromFile(USER_ADMINISTRATOR_FILE, initResult);
		login(userAdministrator);

		roleApproverOid = repoAddObjectFromFile(ROLE_APPROVER_FILE, initResult).getOid();
		metaroleDefaultOid = repoAddObjectFromFile(METAROLE_DEFAULT_FILE, initResult).getOid();
		metaroleSecurityOid = repoAddObjectFromFile(METAROLE_SECURITY_FILE, initResult).getOid();
		metarolePruneTest2xRolesOid = repoAddObjectFromFile(METAROLE_PRUNE_TEST2X_ROLES_FILE, initResult).getOid();
		metaroleApproveUnassign = repoAddObjectFromFile(METAROLE_APPROVE_UNASSIGN_FILE, initResult).getOid();

		userJackOid = repoAddObjectFromFile(USER_JACK_FILE, initResult).getOid();
		userJackDeputyOid = repoAddObjectFromFile(USER_JACK_DEPUTY_FILE, initResult).getOid();
		userBobOid = repoAddObjectFromFile(USER_BOB_FILE, initResult).getOid();
		userChuckOid = repoAddObjectFromFile(USER_CHUCK_FILE, initResult).getOid();
		roleRole1Oid = repoAddObjectFromFile(ROLE_ROLE1_FILE, initResult).getOid();
		roleRole1aOid = repoAddObjectFromFile(ROLE_ROLE1A_FILE, initResult).getOid();
		roleRole1bOid = repoAddObjectFromFile(ROLE_ROLE1B_FILE, initResult).getOid();
		roleRole2Oid = repoAddObjectFromFile(ROLE_ROLE2_FILE, initResult).getOid();
		roleRole2aOid = repoAddObjectFromFile(ROLE_ROLE2A_FILE, initResult).getOid();
		roleRole2bOid = repoAddObjectFromFile(ROLE_ROLE2B_FILE, initResult).getOid();
		roleRole3Oid = repoAddObjectFromFile(ROLE_ROLE3_FILE, initResult).getOid();
		roleRole3aOid = repoAddObjectFromFile(ROLE_ROLE3A_FILE, initResult).getOid();
		roleRole3bOid = repoAddObjectFromFile(ROLE_ROLE3B_FILE, initResult).getOid();
		roleRole4Oid = repoAddObjectFromFile(ROLE_ROLE4_FILE, initResult).getOid();
		roleRole4aOid = repoAddObjectFromFile(ROLE_ROLE4A_FILE, initResult).getOid();
		roleRole4bOid = repoAddObjectFromFile(ROLE_ROLE4B_FILE, initResult).getOid();
		roleRole10Oid = repoAddObjectFromFile(ROLE_ROLE10_FILE, initResult).getOid();
		roleRole10aOid = repoAddObjectFromFile(ROLE_ROLE10A_FILE, initResult).getOid();
		roleRole10bOid = repoAddObjectFromFile(ROLE_ROLE10B_FILE, initResult).getOid();
		roleRole15Oid = repoAddObjectFromFile(ROLE_ROLE15_FILE, initResult).getOid();
		roleFocusAssignmentMapping = repoAddObjectFromFile(ROLE_FOCUS_ASSIGNMENT_MAPPING, initResult).getOid();

		userLead1Oid = addAndRecomputeUser(USER_LEAD1_FILE, initTask, initResult);
		userLead2Oid = addAndRecomputeUser(USER_LEAD2_FILE, initTask, initResult);
		userLead3Oid = addAndRecomputeUser(USER_LEAD3_FILE, initTask, initResult);
		userLead15Oid = addAndRecomputeUser(USER_LEAD15_FILE, initTask, initResult);
		// LEAD10 will be imported later!
		userSecurityApproverOid = addAndRecomputeUser(USER_SECURITY_APPROVER_FILE, initTask, initResult);
		userSecurityApproverDeputyOid = addAndRecomputeUser(USER_SECURITY_APPROVER_DEPUTY_FILE, initTask, initResult);
		userSecurityApproverDeputyLimitedOid = addAndRecomputeUser(USER_SECURITY_APPROVER_DEPUTY_LIMITED_FILE, initTask, initResult);

		userTemplateAssigningRole1aOid = repoAddObjectFromFile(USER_TEMPLATE_ASSIGNING_ROLE_1A, initResult).getOid();
		userTemplateAssigningRole1aOidAfter = repoAddObjectFromFile(USER_TEMPLATE_ASSIGNING_ROLE_1A_AFTER, initResult).getOid();
	}

	@Override
	protected PrismObject<UserType> getDefaultActor() {
		return userAdministrator;
	}

	protected void updateSystemConfiguration(SystemConfigurationType systemConfiguration) throws SchemaException, IOException {
		// nothing to do by default
	}

	protected File getSystemConfigurationFile() {
		return SYSTEM_CONFIGURATION_FILE;
	}

	protected void importLead10(Task task, OperationResult result) throws Exception {
		userLead10Oid = addAndRecomputeUser(USER_LEAD10_FILE, task, result);
	}

	protected void importLead1Deputies(Task task, OperationResult result) throws Exception {
		userLead1Deputy1Oid = addAndRecomputeUser(USER_LEAD1_DEPUTY_1_FILE, task, result);
		userLead1Deputy2Oid = addAndRecomputeUser(USER_LEAD1_DEPUTY_2_FILE, task, result);
	}

	protected Map<String, WorkflowResult> createResultMap(String oid, WorkflowResult result) {
		Map<String, WorkflowResult> retval = new HashMap<>();
		retval.put(oid, result);
		return retval;
	}

	protected Map<String, WorkflowResult> createResultMap(String oid, WorkflowResult approved, String oid2,
			WorkflowResult approved2) {
		Map<String, WorkflowResult> retval = new HashMap<>();
		retval.put(oid, approved);
		retval.put(oid2, approved2);
		return retval;
	}

	protected Map<String, WorkflowResult> createResultMap(String oid, WorkflowResult approved, String oid2,
			WorkflowResult approved2, String oid3, WorkflowResult approved3) {
		Map<String, WorkflowResult> retval = new HashMap<>();
		retval.put(oid, approved);
		retval.put(oid2, approved2);
		retval.put(oid3, approved3);
		return retval;
	}

	protected void checkAuditRecords(Map<String, WorkflowResult> expectedResults) {
		checkWorkItemAuditRecords(expectedResults);
		checkWfProcessAuditRecords(expectedResults);
	}

	protected void checkWorkItemAuditRecords(Map<String, WorkflowResult> expectedResults) {
		WfTestUtil.checkWorkItemAuditRecords(expectedResults, dummyAuditService);
	}

	protected void checkWfProcessAuditRecords(Map<String, WorkflowResult> expectedResults) {
		WfTestUtil.checkWfProcessAuditRecords(expectedResults, dummyAuditService);
	}

	protected void removeAllAssignments(String oid, OperationResult result) throws Exception {
		PrismObject<UserType> user = repositoryService.getObject(UserType.class, oid, null, result);
		for (AssignmentType at : user.asObjectable().getAssignment()) {
			ObjectDelta delta = prismContext.deltaFactory().object()
					.createModificationDeleteContainer(UserType.class, oid, UserType.F_ASSIGNMENT,
							at.asPrismContainerValue().clone());
			repositoryService.modifyObject(UserType.class, oid, delta.getModifications(), result);
			display("Removed assignment " + at + " from " + user);
		}
	}

	public void createObject(final String TEST_NAME, ObjectType object, boolean immediate, boolean approve, String assigneeOid) throws Exception {
		ObjectDelta<RoleType> addObjectDelta = DeltaFactory.Object.createAddDelta((PrismObject) object.asPrismObject());

		executeTest(TEST_NAME, new TestDetails() {
			@Override
			protected LensContext createModelContext(OperationResult result) throws Exception {
				LensContext<RoleType> lensContext = createLensContext((Class) object.getClass());
				addFocusDeltaToContext(lensContext, addObjectDelta);
				return lensContext;
			}

			@Override
			protected void afterFirstClockworkRun(CaseType rootCase,
					CaseType case0, List<CaseType> subcases,
					List<CaseWorkItemType> workItems,
					Task opTask, OperationResult result) throws Exception {
				if (!immediate) {
//					ModelContext taskModelContext = temporaryHelper.getModelContext(rootCase, opTask, result);
//					ObjectDelta realDelta0 = taskModelContext.getFocusContext().getPrimaryDelta();
//					assertTrue("Non-empty primary focus delta: " + realDelta0.debugDump(), realDelta0.isEmpty());
					assertNoObject(object);
					ExpectedTask expectedTask = new ExpectedTask(null, "Adding role \"" + object.getName().getOrig() + "\"");
					ExpectedWorkItem expectedWorkItem = new ExpectedWorkItem(assigneeOid, null, expectedTask);
					assertWfContextAfterClockworkRun(rootCase, subcases, workItems, result,
							null,
							Collections.singletonList(expectedTask),
							Collections.singletonList(expectedWorkItem));
				}
			}

			@Override
			protected void afterCase0Finishes(CaseType rootCase, Task opTask, OperationResult result) throws Exception {
				assertNoObject(object);
			}

			@Override
			protected void afterRootCaseFinishes(CaseType rootCase, List<CaseType> subcases, Task opTask, OperationResult result) throws Exception {
				if (approve) {
					assertObject(object);
				} else {
					assertNoObject(object);
				}
			}

			@Override
			protected boolean executeImmediately() {
				return immediate;
			}

			@Override
			public List<ApprovalInstruction> getApprovalSequence() {
				return singletonList(new ApprovalInstruction(null, true, userLead1Oid, "creation comment"));
			}
		}, 1);
	}

	public <T extends ObjectType> void modifyObject(final String TEST_NAME, ObjectDelta<T> objectDelta,
			ObjectDelta<T> expectedDelta0, ObjectDelta<T> expectedDelta1,
			boolean immediate, boolean approve,
			String assigneeOid,
			List<ExpectedTask> expectedTasks, List<ExpectedWorkItem> expectedWorkItems,
			Runnable assertDelta0Executed,
			Runnable assertDelta1NotExecuted, Runnable assertDelta1Executed) throws Exception {

		executeTest(TEST_NAME, new TestDetails() {
			@Override
			protected LensContext createModelContext(OperationResult result) throws Exception {
				Class<T> clazz = objectDelta.getObjectTypeClass();
				//PrismObject<T> object = getObject(clazz, objectDelta.getOid());
				LensContext<T> lensContext = createLensContext(clazz);
				addFocusDeltaToContext(lensContext, objectDelta);
				return lensContext;
			}

			@Override
			protected void afterFirstClockworkRun(CaseType rootCase,
					CaseType case0, List<CaseType> subcases,
					List<CaseWorkItemType> workItems,
					Task opTask, OperationResult result) throws Exception {
				if (!immediate) {
//					ModelContext taskModelContext = temporaryHelper.getModelContext(rootCase, opTask, result);
//					ObjectDelta realDelta0 = taskModelContext.getFocusContext().getPrimaryDelta();
//					assertDeltasEqual("Wrong delta left as primary focus delta.", expectedDelta0, realDelta0);
					assertDelta1NotExecuted.run();
					assertWfContextAfterClockworkRun(rootCase, subcases, workItems, result,
							objectDelta.getOid(), expectedTasks, expectedWorkItems);
				}
			}

			@Override
			protected void afterCase0Finishes(CaseType rootCase, Task opTask, OperationResult result) throws Exception {
				assertDelta0Executed.run();
				assertDelta1NotExecuted.run();
			}

			@Override
			protected void afterRootCaseFinishes(CaseType rootCase, List<CaseType> subcases,
					Task opTask, OperationResult result) throws Exception {
				assertDelta0Executed.run();
				if (approve) {
					assertDelta1Executed.run();
				} else {
					assertDelta1NotExecuted.run();
				}
			}

			@Override
			protected boolean executeImmediately() {
				return immediate;
			}

			@Override
			public List<ApprovalInstruction> getApprovalSequence() {
				return singletonList(new ApprovalInstruction(null, approve, assigneeOid, "modification comment"));
			}
		}, 1);
	}

	public <T extends ObjectType> void deleteObject(final String TEST_NAME, Class<T> clazz, String objectOid,
			boolean immediate, boolean approve,
			String assigneeOid,
			List<ExpectedTask> expectedTasks, List<ExpectedWorkItem> expectedWorkItems) throws Exception {

		executeTest(TEST_NAME, new TestDetails() {
			@Override
			protected LensContext createModelContext(OperationResult result) throws Exception {
				LensContext<T> lensContext = createLensContext(clazz);
				ObjectDelta<T> deleteDelta = prismContext.deltaFactory().object().createDeleteDelta(clazz, objectOid
				);
				addFocusDeltaToContext(lensContext, deleteDelta);
				return lensContext;
			}

			@Override
			protected void afterFirstClockworkRun(CaseType rootCase,
					CaseType case0, List<CaseType> subcases,
					List<CaseWorkItemType> workItems,
					Task opTask, OperationResult result) throws Exception {
				if (!immediate) {
//					ModelContext taskModelContext = temporaryHelper.getModelContext(rootCase, opTask, result);
//					ObjectDelta realDelta0 = taskModelContext.getFocusContext().getPrimaryDelta();
//					assertTrue("Delta0 is not empty: " + realDelta0.debugDump(), realDelta0.isEmpty());
					assertWfContextAfterClockworkRun(rootCase, subcases, workItems, result,
							objectOid, expectedTasks, expectedWorkItems);
				}
			}

			@Override
			protected void afterCase0Finishes(CaseType rootCase, Task opTask, OperationResult result) throws Exception {
				assertObjectExists(clazz, objectOid);
			}

			@Override
			protected void afterRootCaseFinishes(CaseType rootCase, List<CaseType> subcases,
					Task opTask, OperationResult result) throws Exception {
				if (approve) {
					assertObjectDoesntExist(clazz, objectOid);
				} else {
					assertObjectExists(clazz, objectOid);
				}
			}

			@Override
			protected boolean executeImmediately() {
				return immediate;
			}

			@Override
			protected Boolean decideOnApproval(CaseWorkItemType caseWorkItem) throws Exception {
				return approve;
			}
		}, 1);
	}

	protected CaseWorkItemType getWorkItem(Task task, OperationResult result)
			throws SchemaException, SecurityViolationException, ConfigurationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException {
		//Collection<SelectorOptions<GetOperationOptions>> options = GetOperationOptions.resolveItemsNamed(CaseWorkItemType.F_TASK_REF);
		SearchResultList<CaseWorkItemType> itemsAll = modelService.searchContainers(CaseWorkItemType.class, getOpenItemsQuery(), null, task, result);
		if (itemsAll.size() != 1) {
			System.out.println("Unexpected # of work items: " + itemsAll.size());
			for (CaseWorkItemType workItem : itemsAll) {
				System.out.println(PrismUtil.serializeQuietly(prismContext, workItem));
			}
		}
		assertEquals("Wrong # of total work items", 1, itemsAll.size());
		return itemsAll.get(0);
	}

	protected SearchResultList<CaseWorkItemType> getWorkItems(Task task, OperationResult result) throws Exception {
		return modelService.searchContainers(CaseWorkItemType.class, getOpenItemsQuery(), null, task, result);
	}

	protected void displayWorkItems(String title, List<CaseWorkItemType> workItems) {
		workItems.forEach(wi -> display(title, wi));
	}

	protected ObjectReferenceType ort(String oid) {
		return ObjectTypeUtil.createObjectRef(oid, ObjectTypes.USER);
	}

	protected PrismReferenceValue prv(String oid) {
		return ObjectTypeUtil.createObjectRef(oid, ObjectTypes.USER).asReferenceValue();
	}

	protected PrismReference ref(List<ObjectReferenceType> orts) {
		PrismReference rv = prismContext.itemFactory().createReference(new QName("dummy"));
		orts.forEach(ort -> {
			try {
				rv.add(ort.asReferenceValue().clone());
			} catch (SchemaException e) {
				throw new IllegalStateException(e);
			}
		});
		return rv;
	}

	protected PrismReference ref(ObjectReferenceType ort) {
		return ref(Collections.singletonList(ort));
	}

	protected abstract class TestDetails {
		protected LensContext createModelContext(OperationResult result) throws Exception {
			return null;
		}

		protected void afterFirstClockworkRun(CaseType rootCase, CaseType case0, List<CaseType> subcases,
				List<CaseWorkItemType> workItems, Task opTask, OperationResult result) throws Exception {
		}

		protected void afterCase0Finishes(CaseType rootCase, Task opTask, OperationResult result) throws Exception {
		}

		protected void afterRootCaseFinishes(CaseType rootCase, List<CaseType> subcases, Task opTask,
				OperationResult result) throws Exception {
		}

		protected boolean executeImmediately() {
			return false;
		}

		protected Boolean decideOnApproval(CaseWorkItemType caseWorkItem) throws Exception {
			return null;
		}

		public boolean strictlySequentialApprovals() {
			return false;
		}

		public List<ApprovalInstruction> getApprovalSequence() {
			return null;
		}
	}

	protected <F extends FocusType> void executeTest(String testName, TestDetails testDetails, int expectedSubTaskCount)
			throws Exception {

		// GIVEN
		prepareNotifications();
		dummyAuditService.clear();

		Task opTask = taskManager.createTaskInstance(AbstractWfTestPolicy.class.getName() + "." + testName);
		opTask.setOwner(userAdministrator);
		OperationResult result = new OperationResult("execution");

		LensContext<F> modelContext = testDetails.createModelContext(result);
		display("Model context at test start", modelContext);

		// this has problems with deleting assignments by ID
		//assertFocusModificationSanity(modelContext);

		// WHEN

		HookOperationMode mode = clockwork.run(modelContext, opTask, result);

		// THEN

		display("Model context after first clockwork.run", modelContext);
		assertEquals("Unexpected state of the context", ModelState.PRIMARY, modelContext.getState());
		assertEquals("Wrong mode after clockwork.run in " + modelContext.getState(), HookOperationMode.BACKGROUND, mode);
		opTask.refresh(result);
		display("Model task after first clockwork.run", opTask);

		CaseType rootCase = testHelper.getRootCase(result);
		List<CaseType> subcases = miscHelper.getSubcases(rootCase, result);
		CaseType case0 = WfTestHelper.findAndRemoveCase0(subcases);

		assertEquals("Incorrect number of subtasks", expectedSubTaskCount, subcases.size());

		final Collection<SelectorOptions<GetOperationOptions>> options1 = schemaHelper.getOperationOptionsBuilder()
				.item(T_PARENT, F_OBJECT_REF).resolve()
				.item(T_PARENT, F_TARGET_REF).resolve()
				.item(F_ASSIGNEE_REF).resolve()
				.item(F_ORIGINAL_ASSIGNEE_REF).resolve()
				.item(T_PARENT, F_REQUESTOR_REF).resolve()
				.build();

		List<CaseWorkItemType> workItems = modelService.searchContainers(CaseWorkItemType.class, getOpenItemsQuery(), options1, opTask, result);

		testDetails.afterFirstClockworkRun(rootCase, case0, subcases, workItems, opTask, result);

		if (testDetails.executeImmediately()) {
			if (case0 != null) {
				testHelper.waitForCaseClose(case0, 20000);
			}
			testDetails.afterCase0Finishes(rootCase, opTask, result);
		}

		for (int i = 0; i < subcases.size(); i++) {
			CaseType subcase = subcases.get(i);
			PrismProperty<ObjectTreeDeltasType> deltas = subcase.asPrismObject()
					.findProperty(ItemPath.create(F_WORKFLOW_CONTEXT, F_PROCESSOR_SPECIFIC_STATE, F_DELTAS_TO_PROCESS));
			assertNotNull("There are no modifications in subcase #" + i + ": " + subcase, deltas);
			assertEquals("Incorrect number of modifications in subcase #" + i + ": " + subcase, 1, deltas.getRealValues().size());
			// todo check correctness of the modification?

			// now check the workflow state
			String caseOid = subcase.getOid();
			SearchResultList<CaseWorkItemType> caseWorkItems = workflowEngine.getWorkItemsForCase(caseOid, null, result);
			assertFalse("work item not found", caseWorkItems.isEmpty());

			for (CaseWorkItemType caseWorkItem : caseWorkItems) {
				Boolean approve = testDetails.decideOnApproval(caseWorkItem);
				if (approve != null) {
					workflowManager.completeWorkItem(WorkItemId.create(caseOid, caseWorkItem.getId()),
							approve, null, null, null, opTask, result);
					login(userAdministrator);
					break;
				}
			}
		}

		// alternative way of approvals executions
		if (CollectionUtils.isNotEmpty(testDetails.getApprovalSequence())) {
			List<ApprovalInstruction> instructions = new ArrayList<>(testDetails.getApprovalSequence());
			while (!instructions.isEmpty()) {
				List<CaseWorkItemType> currentWorkItems = modelService
						.searchContainers(CaseWorkItemType.class, getOpenItemsQuery(), options1, opTask, result);
				boolean matched = false;

				Collection<ApprovalInstruction> instructionsToConsider = testDetails.strictlySequentialApprovals()
						? singleton(instructions.get(0))
						: instructions;

				main:
				for (ApprovalInstruction approvalInstruction : instructionsToConsider) {
					for (CaseWorkItemType workItem : currentWorkItems) {
						if (approvalInstruction.matches(workItem)) {
							if (approvalInstruction.beforeApproval != null) {
								approvalInstruction.beforeApproval.run();
							}
							login(getUserFromRepo(approvalInstruction.approverOid));
							System.out.println("Completing work item " + WorkItemId.of(workItem) + " using " + approvalInstruction);
							workflowManager.completeWorkItem(WorkItemId.of(workItem), approvalInstruction.approval, approvalInstruction.comment,
									null, null, opTask, result);
							if (approvalInstruction.afterApproval != null) {
								approvalInstruction.afterApproval.run();
							}
							login(userAdministrator);
							matched = true;
							instructions.remove(approvalInstruction);
							break main;
						}
					}
				}
				if (!matched) {
					fail("None of approval instructions " + instructionsToConsider + " matched any of current work items: "
							+ currentWorkItems);
				}
			}
		}

		testHelper.waitForCaseClose(rootCase, 60000);

		subcases = miscHelper.getSubcases(rootCase, result);
		testHelper.findAndRemoveCase0(subcases);
		testDetails.afterRootCaseFinishes(rootCase, subcases, opTask, result);

		notificationManager.setDisabled(true);

		// Check audit
		display("Audit", dummyAuditService);
		display("Output context", modelContext);
	}

	protected void assertObjectInTaskTree(Task rootTask, String oid, boolean checkObjectOnSubtasks, OperationResult result)
			throws SchemaException {
		assertObjectInTask(rootTask, oid);
		if (checkObjectOnSubtasks) {
			for (Task task : rootTask.listSubtasks(result)) {
				assertObjectInTask(task, oid);
			}
		}
	}

	protected void assertObjectInTask(Task task, String oid) {
		assertEquals("Missing or wrong object OID in task " + task, oid, task.getObjectOid());
	}

	protected void waitForTaskClose(final Task task, final int timeout) throws Exception {
		final OperationResult waitResult = new OperationResult(AbstractIntegrationTest.class + ".waitForTaskClose");
		Checker checker = new Checker() {
			@Override
			public boolean check() throws CommonException {
				task.refresh(waitResult);
				OperationResult result = task.getResult();
				if (verbose)
					display("Check result", result);
				return task.getExecutionStatus() == TaskExecutionStatus.CLOSED;
			}

			@Override
			public void timeout() {
				try {
					task.refresh(waitResult);
				} catch (Throwable e) {
					display("Exception during task refresh", e);
				}
				OperationResult result = task.getResult();
				display("Result of timed-out task", result);
				assert false : "Timeout (" + timeout + ") while waiting for " + task + " to finish. Last result " + result;
			}
		};
		IntegrationTestTools.waitFor("Waiting for " + task + " finish", checker, timeout, 1000);
	}

	protected void assertWfContextAfterClockworkRun(CaseType rootCase, List<CaseType> subcases, List<CaseWorkItemType> workItems,
			OperationResult result,
			String objectOid,
			List<ExpectedTask> expectedTasks,
			List<ExpectedWorkItem> expectedWorkItems) throws Exception {

		final Collection<SelectorOptions<GetOperationOptions>> options =
				SelectorOptions.createCollection(prismContext.path(F_WORKFLOW_CONTEXT, F_WORK_ITEM), createRetrieve());

		Task opTask = taskManager.createTaskInstance();
		display("rootCase", rootCase);
		assertEquals("Wrong # of wf subcases (" + expectedTasks + ")", expectedTasks.size(), subcases.size());
		int i = 0;
		for (CaseType subcase : subcases) {
			display("Subcase #" + (i + 1) + ": ", subcase);
			checkCase(subcase, subcase.toString(), expectedTasks.get(i));
			WfTestUtil
					.assertRef("requester ref", subcase.getRequestorRef(), USER_ADMINISTRATOR_OID, false, false);
			i++;
		}

		assertEquals("Wrong # of work items", expectedWorkItems.size(), workItems.size());
		i = 0;
		for (CaseWorkItemType workItem : workItems) {
			display("Work item #" + (i + 1) + ": ", workItem);
			display("Case", CaseWorkItemUtil.getCase(workItem));
			if (objectOid != null) {
				WfTestUtil.assertRef("object reference", WfContextUtil.getObjectRef(workItem), objectOid, true, true);
			}

			String targetOid = expectedWorkItems.get(i).targetOid;
			if (targetOid != null) {
				WfTestUtil.assertRef("target reference", WfContextUtil.getTargetRef(workItem), targetOid, true, true);
			}
			WfTestUtil
					.assertRef("assignee reference", workItem.getOriginalAssigneeRef(), expectedWorkItems.get(i).assigneeOid, false, true);
			// name is not known
			//WfTestUtil.assertRef("task reference", workItem.getTaskRef(), null, false, true);
			final CaseType subcase = CaseWorkItemUtil.getCaseRequired(workItem);
			checkCase(subcase, "subcase in workItem", expectedWorkItems.get(i).task);
			WfTestUtil
					.assertRef("requester ref", subcase.getRequestorRef(), USER_ADMINISTRATOR_OID, false, true);

			i++;
		}
	}

	private void checkCase(CaseType subcase, String context, ExpectedTask expectedTask) {
		assertNull("Unexpected fetch result in wf subtask: " + context, subcase.getFetchResult());
		WfContextType wfc = subcase.getWorkflowContext();
		assertNotNull("Missing workflow context in wf subtask: " + context, wfc);
		// TODO-WF
//		assertNotNull("No process ID in wf subtask: " + subtaskName, wfc.getCaseOid());
		assertEquals("Wrong process ID name in subtask: " + context, expectedTask.processName, subcase.getName().getOrig());
		if (expectedTask.targetOid != null) {
			assertEquals("Wrong target OID in subtask: " + context, expectedTask.targetOid, subcase.getTargetRef().getOid());
		} else {
			assertNull("TargetRef in subtask: " + context + " present even if it shouldn't", subcase.getTargetRef());
		}
		assertNotNull("Missing process start time in subtask: " + context, CaseTypeUtil.getStartTimestamp(subcase));
		assertNull("Unexpected process end time in subtask: " + context, subcase.getCloseTimestamp());
		assertEquals("Wrong outcome", null, subcase.getOutcome());
		//assertEquals("Wrong state", null, wfc.getState());
	}

	protected String getTargetOid(CaseWorkItemType caseWorkItem) {
		ObjectReferenceType targetRef = CaseWorkItemUtil.getCaseRequired(caseWorkItem).getTargetRef();
		assertNotNull("targetRef not found", targetRef);
		String roleOid = targetRef.getOid();
		assertNotNull("requested role OID not found", roleOid);
		return roleOid;
	}

	protected void checkTargetOid(CaseWorkItemType caseWorkItem, String expectedOid)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
			SecurityViolationException {
		String realOid = getTargetOid(caseWorkItem);
		assertEquals("Unexpected target OID", expectedOid, realOid);
	}

	protected abstract class TestDetails2<F extends FocusType> {
		protected PrismObject<F> getFocus(OperationResult result) throws Exception { return null; }
		protected ObjectDelta<F> getFocusDelta() throws Exception { return null; }
		protected int getNumberOfDeltasToApprove() { return 0; }
		protected List<Boolean> getApprovals() { return null; }
		protected List<ObjectDelta<F>> getExpectedDeltasToApprove() {
			return null;
		}
		protected ObjectDelta<F> getExpectedDelta0() {
			return null;
		}
		protected String getObjectOid() {
			return null;
		}
		protected List<ExpectedTask> getExpectedTasks() { return null; }
		protected List<ExpectedWorkItem> getExpectedWorkItems() { return null; }

		protected void assertDeltaExecuted(int number, boolean yes, Task opTask, OperationResult result) throws Exception { }
		// mutually exclusive with getApprovalSequence
		protected Boolean decideOnApproval(CaseWorkItemType caseWorkItem) throws Exception { return true; }

		private void sortSubcases(List<CaseType> subtasks) {
			subtasks.sort(Comparator.comparing(this::getCompareKey));
		}

		private void sortWorkItems(List<CaseWorkItemType> workItems) {
			workItems.sort(Comparator.comparing(this::getCompareKey));
		}

		private String getCompareKey(CaseType aCase) {
			return aCase.getTargetRef().getOid();
		}

		private String getCompareKey(CaseWorkItemType workItem) {
			return workItem.getOriginalAssigneeRef().getOid();
		}

		public List<ApprovalInstruction> getApprovalSequence() {
			return null;
		}

		protected void afterFirstClockworkRun(CaseType rootCase, List<CaseType> subcases, List<CaseWorkItemType> workItems,
				OperationResult result) throws Exception { }
	}

	protected <F extends FocusType> void executeTest2(String testName, TestDetails2<F> testDetails2, int expectedSubTaskCount,
			boolean immediate) throws Exception {
		executeTest(testName, new TestDetails() {
			@Override
			protected LensContext<F> createModelContext(OperationResult result) throws Exception {
				PrismObject<F> focus = testDetails2.getFocus(result);
				// TODO "object create" context
				LensContext<F> lensContext = createLensContext(focus.getCompileTimeClass());
				fillContextWithFocus(lensContext, focus);
				addFocusDeltaToContext(lensContext, testDetails2.getFocusDelta());
				if (immediate) {
					lensContext.setOptions(ModelExecuteOptions.createExecuteImmediatelyAfterApproval());
				}
				return lensContext;
			}

			@Override
			protected void afterFirstClockworkRun(CaseType rootCase,
					CaseType case0, List<CaseType> subcases,
					List<CaseWorkItemType> workItems,
					Task opTask, OperationResult result) throws Exception {
				if (!immediate) {
//					ModelContext taskModelContext = temporaryHelper.getModelContext(rootCase, opTask, result);
//					ObjectDelta expectedDelta0 = testDetails2.getExpectedDelta0();
//					ObjectDelta realDelta0 = taskModelContext.getFocusContext().getPrimaryDelta();
//					assertDeltasEqual("Wrong delta left as primary focus delta. ", expectedDelta0, realDelta0);
					for (int i = 0; i <= testDetails2.getNumberOfDeltasToApprove(); i++) {
						testDetails2.assertDeltaExecuted(i, false, opTask, result);
					}
					testDetails2.sortSubcases(subcases);
					testDetails2.sortWorkItems(workItems);
					assertWfContextAfterClockworkRun(rootCase, subcases, workItems, result,
							testDetails2.getObjectOid(),
							testDetails2.getExpectedTasks(), testDetails2.getExpectedWorkItems());
					for (CaseType subcase : subcases) {
						if (subcase.getWorkflowContext() != null && subcase.getWorkflowContext().getProcessSpecificState() != null) {
							OperationResult opResult = new OperationResult("dummy");
							ApprovalSchemaExecutionInformationType info = workflowManager.getApprovalSchemaExecutionInformation(subcase.getOid(), opTask, opResult);
							display("Execution info for " + subcase, info);
							opResult.computeStatus();
							assertSuccess("Unexpected problem when looking at getApprovalSchemaExecutionInformation result", opResult);
						}
					}
				}
				testDetails2.afterFirstClockworkRun(rootCase, subcases, workItems, result);
			}

			@Override
			protected void afterCase0Finishes(CaseType rootCase, Task opTask, OperationResult result) throws Exception {
				if (!immediate) {
					return;
				}
				for (int i = 1; i <= testDetails2.getNumberOfDeltasToApprove(); i++) {
					testDetails2.assertDeltaExecuted(i, false, opTask, result);
				}
				testDetails2.assertDeltaExecuted(0, true, opTask, result);
			}

			@Override
			protected void afterRootCaseFinishes(CaseType rootCase, List<CaseType> subcases,
					Task opTask, OperationResult result) throws Exception {
				for (int i = 0; i <= testDetails2.getNumberOfDeltasToApprove(); i++) {
					testDetails2.assertDeltaExecuted(i, i == 0 || testDetails2.getApprovals().get(i-1), opTask, result);
				}
			}

			@Override
			protected boolean executeImmediately() {
				return immediate;
			}

			@Override
			protected Boolean decideOnApproval(CaseWorkItemType caseWorkItem) throws Exception {
				return testDetails2.decideOnApproval(caseWorkItem);
			}

			@Override
			public List<ApprovalInstruction> getApprovalSequence() {
				return testDetails2.getApprovalSequence();
			}
		}, expectedSubTaskCount);
	}

	protected void assertDeltasEqual(String message, ObjectDelta expectedDelta, ObjectDelta realDelta) {
//		removeOldValues(expectedDelta);
//		removeOldValues(realDelta);
		if (!expectedDelta.equivalent(realDelta)) {
			fail(message + "\nExpected:\n" + expectedDelta.debugDump() + "\nReal:\n" + realDelta.debugDump());
		}
	}

//	private void removeOldValues(ObjectDelta<?> delta) {
//		if (delta.isModify()) {
//			delta.getModifications().forEach(mod -> mod.setEstimatedOldValues(null));
//		}
//	}

	protected void assertNoObject(ObjectType object) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		assertNull("Object was created but it shouldn't be",
				searchObjectByName(object.getClass(), object.getName().getOrig()));
	}

	protected void assertNoObject(PrismObject<? extends ObjectType> object) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		assertNoObject(object.asObjectable());
	}

	protected <T extends ObjectType> void assertObject(T object) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		PrismObject<T> objectFromRepo = searchObjectByName((Class<T>) object.getClass(), object.getName().getOrig());
		assertNotNull("Object " + object + " was not created", objectFromRepo);
		objectFromRepo.removeItem(ObjectType.F_METADATA, Item.class);
		objectFromRepo.removeItem(ObjectType.F_OPERATION_EXECUTION, Item.class);
		assertEquals("Object is different from the one that was expected", object, objectFromRepo.asObjectable());
	}

	protected void checkVisibleWorkItem(ExpectedWorkItem expectedWorkItem, int count, Task task, OperationResult result)
			throws SchemaException, ObjectNotFoundException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, CommunicationException {
		S_AtomicFilterExit q = QueryUtils
				.filterForAssignees(prismContext.queryFor(CaseWorkItemType.class), SecurityUtil.getPrincipal(),
						OtherPrivilegesLimitationType.F_APPROVAL_WORK_ITEMS, relationRegistry);
		q = q.and().item(CaseWorkItemType.F_CLOSE_TIMESTAMP).isNull();
		List<CaseWorkItemType> currentWorkItems = modelService.searchContainers(CaseWorkItemType.class, q.build(), null, task, result);
		long found = currentWorkItems.stream().filter(wi -> expectedWorkItem == null || expectedWorkItem.matches(wi)).count();
		assertEquals("Wrong # of matching work items", count, found);
	}

	protected ObjectQuery getOpenItemsQuery() {
		return prismContext.queryFor(CaseWorkItemType.class)
				.item(CaseWorkItemType.F_CLOSE_TIMESTAMP).isNull()
				.build();
	}
}
