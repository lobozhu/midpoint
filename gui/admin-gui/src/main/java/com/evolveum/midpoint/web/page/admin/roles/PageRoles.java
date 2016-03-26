/*
 * Copyright (c) 2010-2015 Evolveum
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

package com.evolveum.midpoint.web.page.admin.roles;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.data.BoxedTablePanel;
import com.evolveum.midpoint.web.component.data.ObjectDataProvider;
import com.evolveum.midpoint.web.component.data.Table;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.InlineMenuHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.LinkColumn;
import com.evolveum.midpoint.web.component.dialog.ConfirmationDialog;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.search.Search;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchFormPanel;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.configuration.component.HeaderMenuAction;
import com.evolveum.midpoint.web.session.RolesStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
@PageDescriptor(url = "/admin/roles", action = {
        @AuthorizationAction(actionUri = PageAdminRoles.AUTH_ROLE_ALL,
                label = PageAdminRoles.AUTH_ROLE_ALL_LABEL,
                description = PageAdminRoles.AUTH_ROLE_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_ROLES_URL,
                label = "PageRoles.auth.roles.label",
                description = "PageRoles.auth.roles.description")})
public class PageRoles extends PageAdminRoles {

    private static final Trace LOGGER = TraceManager.getTrace(PageRoles.class);
    private static final String DOT_CLASS = PageRoles.class.getName() + ".";
    private static final String OPERATION_DELETE_ROLES = DOT_CLASS + "deleteRoles";

    private static final String DIALOG_CONFIRM_DELETE = "confirmDeletePopup";
    private static final String ID_TABLE = "table";
    private static final String ID_MAIN_FORM = "mainForm";

    private IModel<Search> searchModel;

    public PageRoles() {
        this(true);
    }

    public PageRoles(boolean clearPagingInSession) {
        searchModel = new LoadableModel<Search>(false) {

            @Override
            protected Search load() {
                RolesStorage storage = getSessionStorage().getRoles();
                Search dto = storage.getRolesSearch();

                if (dto == null) {
                    dto = SearchFactory.createSearch(RoleType.class, getPrismContext(), true);

                    SchemaRegistry registry = getPrismContext().getSchemaRegistry();
                    PrismObjectDefinition objDef = registry.findObjectDefinitionByCompileTimeClass(RoleType.class);
                    PrismPropertyDefinition def = objDef.findPropertyDefinition(RoleType.F_REQUESTABLE);

                    dto.addItem(def);
                }

                return dto;
            }
        };

        initLayout();
    }

    private void initLayout() {
        Form mainForm = new Form(ID_MAIN_FORM);
        add(mainForm);

        ObjectDataProvider provider = new ObjectDataProvider(PageRoles.this, RoleType.class) {

            @Override
            protected void saveProviderPaging(ObjectQuery query, ObjectPaging paging) {
                RolesStorage storage = getSessionStorage().getRoles();
                storage.setRolesPaging(paging);
            }
        };

        List<IColumn<RoleType, String>> columns = initColumns();

        BoxedTablePanel table = new BoxedTablePanel(ID_TABLE, provider, columns,
                UserProfileStorage.TableId.TABLE_ROLES,
                (int) getItemsPerPage(UserProfileStorage.TableId.TABLE_ROLES)) {

            @Override
            protected WebMarkupContainer createHeader(String headerId) {
                return new SearchFormPanel(headerId, searchModel) {

                    @Override
                    protected void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
                        PageRoles.this.listRolesPerformed(query, target);
                    }
                };
            }
        };
        table.setOutputMarkupId(true);

        RolesStorage storage = getSessionStorage().getRoles();
        table.setCurrentPage(storage.getRolesPaging());

        mainForm.add(table);

        add(new ConfirmationDialog(DIALOG_CONFIRM_DELETE, createStringResource("pageRoles.dialog.title.confirmDelete"),
                createDeleteConfirmString()) {

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                close(target);
                deleteConfirmedPerformed(target);
            }
        });
    }

    private List<IColumn<RoleType, String>> initColumns() {
        List<IColumn<RoleType, String>> columns = new ArrayList<>();

        IColumn column = new CheckBoxHeaderColumn<RoleType>();
        columns.add(column);

        column = new LinkColumn<SelectableBean<RoleType>>(createStringResource("ObjectType.name"), "name", "value.name") {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<RoleType>> rowModel) {
                RoleType role = rowModel.getObject().getValue();
                roleDetailsPerformed(target, role.getOid());
            }
        };
        columns.add(column);

        column = new PropertyColumn(createStringResource("OrgType.displayName"), "value.displayName");
        columns.add(column);

        column = new PropertyColumn(createStringResource("OrgType.identifier"), "value.identifier");
        columns.add(column);

        column = new PropertyColumn(createStringResource("ObjectType.description"), "value.description");
        columns.add(column);

        column = new InlineMenuHeaderColumn(initInlineMenu());
        columns.add(column);

        return columns;
    }

    private List<InlineMenuItem> initInlineMenu() {
        List<InlineMenuItem> headerMenuItems = new ArrayList<InlineMenuItem>();
        headerMenuItems.add(new InlineMenuItem(createStringResource("pageRoles.button.delete"), true,
                new HeaderMenuAction(this) {

                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        deletePerformed(target);
                    }
                }));

        return headerMenuItems;
    }

    private IModel<String> createDeleteConfirmString() {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                return createStringResource("pageRoles.message.deleteRoleConfirm",
                        getSelectedRoles().size()).getString();
            }
        };
    }

    private Table getRoleTable() {
        return (Table) get(createComponentPath(ID_MAIN_FORM, ID_TABLE));
    }

    private ObjectDataProvider<SelectableBean<RoleType>, RoleType> getRoleDataProvider() {
        DataTable table = getRoleTable().getDataTable();
        return (ObjectDataProvider<SelectableBean<RoleType>, RoleType>) table.getDataProvider();
    }

    private List<RoleType> getSelectedRoles() {
        ObjectDataProvider<SelectableBean<RoleType>, RoleType> provider = getRoleDataProvider();

        List<SelectableBean<RoleType>> rows = provider.getAvailableData();
        List<RoleType> selected = new ArrayList<RoleType>();
        for (SelectableBean<RoleType> row : rows) {
            if (row.isSelected()) {
                selected.add(row.getValue());
            }
        }

        return selected;
    }

    private void deletePerformed(AjaxRequestTarget target) {
        List<RoleType> selected = getSelectedRoles();
        if (selected.isEmpty()) {
            warn(getString("pageRoles.message.nothingSelected"));
            target.add(getFeedbackPanel());
            return;
        }

        ModalWindow dialog = (ModalWindow) get(DIALOG_CONFIRM_DELETE);
        dialog.show(target);
    }

    private void deleteConfirmedPerformed(AjaxRequestTarget target) {
        List<RoleType> selected = getSelectedRoles();

        OperationResult result = new OperationResult(OPERATION_DELETE_ROLES);
        for (RoleType role : selected) {
            try {
                Task task = createSimpleTask(OPERATION_DELETE_ROLES);

                ObjectDelta delta = ObjectDelta.createDeleteDelta(RoleType.class, role.getOid(), getPrismContext());
                getModelService().executeChanges(WebComponentUtil.createDeltaCollection(delta), null, task, result);
            } catch (Exception ex) {
                result.recordPartialError("Couldn't delete role.", ex);
                LoggingUtils.logException(LOGGER, "Couldn't delete role", ex);
            }
        }

        if (result.isUnknown()) {
            result.recomputeStatus("Error occurred during role deleting.");
        }

        if (result.isSuccess()) {
            result.recordStatus(OperationResultStatus.SUCCESS, "The role(s) have been successfully deleted.");
        }

        ObjectDataProvider provider = getRoleDataProvider();
        provider.clearCache();

        showResult(result);
        target.add(getFeedbackPanel());
        target.add((Component) getRoleTable());
    }

    private void roleDetailsPerformed(AjaxRequestTarget target, String oid) {
        PageParameters parameters = new PageParameters();
        parameters.add(OnePageParameterEncoder.PARAMETER, oid);
        setResponsePage(PageRole.class, parameters);
    }

    private void listRolesPerformed(ObjectQuery query, AjaxRequestTarget target) {
        ObjectDataProvider provider = getRoleDataProvider();
        provider.setQuery(query);

        RolesStorage storage = getSessionStorage().getRoles();
        storage.setRolesSearch(searchModel.getObject());
        storage.setRolesPaging(null);

        Table table = getRoleTable();
        table.setCurrentPage(null);
        target.add((Component) table);
        target.add(getFeedbackPanel());
    }
}
