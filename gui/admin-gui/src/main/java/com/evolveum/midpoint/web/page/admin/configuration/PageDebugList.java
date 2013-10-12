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

package com.evolveum.midpoint.web.page.admin.configuration;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.match.PolyStringNormMatchingRule;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyStringNormalizer;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.SubstringFilter;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.button.AjaxLinkButton;
import com.evolveum.midpoint.web.component.button.ButtonType;
import com.evolveum.midpoint.web.component.data.RepositoryObjectDataProvider;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.data.column.*;
import com.evolveum.midpoint.web.component.dialog.ConfirmationDialog;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.page.admin.configuration.component.DebugButtonPanel;
import com.evolveum.midpoint.web.page.admin.configuration.component.HeaderMenuAction;
import com.evolveum.midpoint.web.page.admin.configuration.component.PageDebugDownloadBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.dto.DebugObjectItem;
import com.evolveum.midpoint.web.page.admin.configuration.dto.DebugSearchDto;
import com.evolveum.midpoint.web.session.ConfigurationStorage;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.*;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.*;

/**
 * @author lazyman
 * @author mserbak
 */

public class PageDebugList extends PageAdminConfiguration {

    private static final long serialVersionUID = 532615968316031794L;

    private static final Trace LOGGER = TraceManager.getTrace(PageDebugList.class);
    private static final String DOT_CLASS = PageDebugList.class.getName() + ".";
    private static final String OPERATION_DELETE_OBJECT = DOT_CLASS + "deleteObject";
    private static final String OPERATION_DELETE_OBJECTS = DOT_CLASS + "deleteObjects";

    private static final String OPERATION_SEARCH_ITERATIVE_TASK = DOT_CLASS + "searchIterativeTask";
    private static final String OPERATION_LAXATIVE_DELETE = DOT_CLASS + "laxativeDelete";

    private static final String ID_CONFIRM_DELETE_POPUP = "confirmDeletePopup";
    private static final String ID_CONFIRM_LAXATIVE_OPERATION = "confirmLaxativeOperation";
    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_ZIP_CHECK = "zipCheck";
    private static final String ID_SEARCH = "search";
    private static final String ID_CATEGORY = "category";
    private static final String ID_TABLE = "table";
    private static final String ID_CHOICE = "choice";
    private static final String ID_DELETE_SELECTED = "deleteSelected";
    private static final String ID_LAXATIVE_BUTTON = "laxativeButton";
    private static final String ID_EXPORT = "export";
    private static final String ID_EXPORT_ALL = "exportAll";
    private static final String ID_SEARCH_FORM = "searchForm";
    private static final String ID_SEARCH_TEXT = "searchText";
    private static final String ID_SEARCH_BUTTON = "searchButton";

    private static final String PRINT_LABEL_USER = "User ";
    private static final String PRINT_LABEL_SHADOW = "Shadow ";
    private static final String PRINT_LABEL_USER_DELETE = "Users to delete: ";
    private static final String PRINT_LABEL_SHADOW_DELETE = "Shadows to delete: ";
    private static final String PRINT_LABEL_HTML_NEWLINE = "<br>";
    private static final String PRINT_LABEL_ADMINISTRATOR_NOT_DELETED = "(User 'Administrator' will not be deleted)";

    private IModel<DebugSearchDto> searchModel;

    private boolean deleteSelected; //todo what is this used for?
    private IModel<ObjectTypes> choice = null;
    private DebugObjectItem object = null; //todo what is this used for?

    public PageDebugList() {
        searchModel = new LoadableModel<DebugSearchDto>(false) {

            @Override
            protected DebugSearchDto load() {
                DebugSearchDto dto = new DebugSearchDto();
                ConfigurationStorage storage = getSessionStorage().getConfiguration();
                dto.setType(storage.getDebugListCategory());

                return dto;
            }
        };

        initLayout();
    }

    private void initLayout() {
        //confirm delete
        add(new ConfirmationDialog(ID_CONFIRM_DELETE_POPUP,
                createStringResource("pageDebugList.dialog.title.confirmDelete"), createDeleteConfirmString()) {

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                close(target);
                //todo wtf
                if (deleteSelected) {
                    deleteSelected = false;
                    deleteSelectedConfirmedPerformed(target);
                } else {
                    deleteObjectConfirmedPerformed(target);
                }
            }
        });

        //Confirm laxative function
        ConfirmationDialog laxativeConfirmationDialog = prepareLaxativeConfirmationDialog();
        laxativeConfirmationDialog.setEscapeModelStringsByCaller(false);
        laxativeConfirmationDialog.setInitialWidth(500);
        add(laxativeConfirmationDialog);

        Form searchForm = new Form(ID_SEARCH_FORM);
        add(searchForm);
        initSearchForm(searchForm);

        final Form main = new Form(ID_MAIN_FORM);
        add(main);

        choice = new Model<ObjectTypes>() {

            @Override
            public ObjectTypes getObject() {
                ObjectTypes types = super.getObject();
                if (types == null) {
                    ConfigurationStorage storage = getSessionStorage().getConfiguration();
                    types = storage.getDebugListCategory();
                }

                return types;
            }
        };

        ConfigurationStorage storage = getSessionStorage().getConfiguration();
        Class type = storage.getDebugListCategory().getClassDefinition();
        addOrReplaceTable(new RepositoryObjectDataProvider(this, type));

        initButtonBar(main);
    }

    private void initButtonBar(Form main) {
        AjaxLinkButton delete = new AjaxLinkButton(ID_DELETE_SELECTED, ButtonType.NEGATIVE,
                createStringResource("pageDebugList.button.deleteSelected")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                deleteSelectedPerformed(target, choice);
            }
        };
        main.add(delete);

        AjaxLinkButton laxativeButton = new AjaxLinkButton(ID_LAXATIVE_BUTTON, ButtonType.NEGATIVE,
                createStringResource("pageDebugList.button.laxativeButton")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                laxativeActionPerformed(target);
            }
        };
        main.add(laxativeButton);


        final PageDebugDownloadBehaviour ajaxDownloadBehavior = new PageDebugDownloadBehaviour();
        main.add(ajaxDownloadBehavior);


        AjaxLinkButton export = new AjaxLinkButton(ID_EXPORT,
                createStringResource("pageDebugList.button.export")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                initDownload(ajaxDownloadBehavior, target, false);
            }
        };
        main.add(export);

        AjaxLinkButton exportAll = new AjaxLinkButton(ID_EXPORT_ALL,
                createStringResource("pageDebugList.button.exportAll")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                initDownload(ajaxDownloadBehavior, target, true);
            }
        };
        main.add(exportAll);

        AjaxCheckBox zipCheck = new AjaxCheckBox(ID_ZIP_CHECK, new Model<Boolean>(false)) {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
            }
        };
        main.add(zipCheck);
    }

    private void initDownload(PageDebugDownloadBehaviour downloadBehaviour, AjaxRequestTarget target, boolean all) {
        Class<? extends ObjectType> type = all ? ObjectType.class : getExportType();
        downloadBehaviour.setType(type);

        downloadBehaviour.setQuery(createExportQuery());
        downloadBehaviour.setUseZip(hasToZip());
        downloadBehaviour.initiate(target);
    }

    private Class<? extends ObjectType> getExportType() {
        Class type = getTableDataProvider().getType();
        return type == null ? ObjectType.class : type;
    }

    private ObjectQuery createExportQuery() {
        ObjectQuery query = getTableDataProvider().getQuery();

        ObjectQuery clonedQuery = null;
        if (query != null) {
            clonedQuery = new ObjectQuery();
            clonedQuery.setFilter(query.getFilter());
        }

        return clonedQuery;
    }

    private void addOrReplaceTable(RepositoryObjectDataProvider provider) {
        Form mainForm = (Form) get(ID_MAIN_FORM);

        TablePanel table = new TablePanel(ID_TABLE, provider, initColumns(provider.getType()));
        table.setOutputMarkupId(true);
        mainForm.addOrReplace(table);
    }

    private List<IColumn> initColumns(final Class<? extends ObjectType> type) {
        List<IColumn> columns = new ArrayList<IColumn>();

        IColumn column = new CheckBoxHeaderColumn<ObjectType>();
        columns.add(column);

        column = new LinkColumn<DebugObjectItem>(createStringResource("pageDebugList.name"),
                DebugObjectItem.F_NAME, DebugObjectItem.F_NAME) {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<DebugObjectItem> rowModel) {
                DebugObjectItem object = rowModel.getObject();
                objectEditPerformed(target, object.getOid(), type);
            }
        };
        columns.add(column);

        columns.add(new PropertyColumn(createStringResource("pageDebugList.description"),
                DebugObjectItem.F_DESCRIPTION));

        if (ShadowType.class.isAssignableFrom(type)) {
            columns.add(new PropertyColumn(createStringResource("pageDebugList.resourceName"),
                    DebugObjectItem.F_RESOURCE_NAME));
            columns.add(new PropertyColumn(createStringResource("pageDebugList.resourceType"),
                    DebugObjectItem.F_RESOURCE_TYPE));
        }

        column = new AbstractColumn<DebugObjectItem, String>(new Model(), null) {

            @Override
            public String getCssClass() {
                return "debug-list-buttons";
            }

            @Override
            public void populateItem(Item<ICellPopulator<DebugObjectItem>> cellItem, String componentId,
                                     IModel<DebugObjectItem> rowModel) {
                cellItem.add(new DebugButtonPanel<DebugObjectItem>(componentId, rowModel) {

                    @Override
                    public void deletePerformed(AjaxRequestTarget target, IModel<DebugObjectItem> model) {
                        DebugObjectItem object = model.getObject();
                        deleteObjectPerformed(target, choice, object);
                    }

                    @Override
                    public void exportPerformed(AjaxRequestTarget target, IModel<DebugObjectItem> model) {
                        //todo implement
                    }
                });
            }
        };
        columns.add(column);

        column = new InlineMenuHeaderColumn<InlineMenu>(initInlineMenu()) {

            @Override
            public void populateItem(Item<ICellPopulator<InlineMenu>> cellItem, String componentId,
                                     IModel<InlineMenu> rowModel) {
                //we don't need row inline menu
                cellItem.add(new Label(componentId));
            }
        };
        columns.add(column);

        return columns;
    }

    private List<InlineMenuItem> initInlineMenu() {
        List<InlineMenuItem> headerMenuItems = new ArrayList<InlineMenuItem>();
        headerMenuItems.add(new InlineMenuItem(createStringResource("pageDebugList.menu.exportSelected"), true,
                new HeaderMenuAction(this) {

                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        //todo implement
                    }
                }));

        headerMenuItems.add(new InlineMenuItem(createStringResource("pageDebugList.menu.exportAllSelectedType"), true,
                new HeaderMenuAction(this) {

                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        //todo implement
                    }
                }));

        headerMenuItems.add(new InlineMenuItem(createStringResource("pageDebugList.menu.exportAll"), true,
                new HeaderMenuAction(this) {

                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        //todo implement
                    }
                }));

        headerMenuItems.add(new InlineMenuItem());

        headerMenuItems.add(new InlineMenuItem(createStringResource("pageDebugList.menu.deleteSelected"), true,
                new HeaderMenuAction(this) {

                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        //todo implement
                    }
                }));

        headerMenuItems.add(new InlineMenuItem(createStringResource("pageDebugList.menu.deleteAllType"), true,
                new HeaderMenuAction(this) {

                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        //todo implement
                    }
                }));

        return headerMenuItems;
    }

    private boolean hasToZip() {
        AjaxCheckBox zipCheck = (AjaxCheckBox) get(createComponentPath(ID_MAIN_FORM, ID_ZIP_CHECK));
        return zipCheck.getModelObject();
    }

    private void initSearchForm(Form searchForm) {
        TextField search = new TextField(ID_SEARCH_TEXT, new PropertyModel(searchModel, DebugSearchDto.F_TEXT));
        searchForm.add(search);

        IChoiceRenderer<ObjectTypes> renderer = new IChoiceRenderer<ObjectTypes>() {

            @Override
            public Object getDisplayValue(ObjectTypes object) {
                return new StringResourceModel(object.getLocalizationKey(), PageDebugList.this, null).getString();
            }

            @Override
            public String getIdValue(ObjectTypes object, int index) {
                return object.getClassDefinition().getSimpleName();
            }
        };

        DropDownChoice choice = new DropDownChoice(ID_CHOICE, new PropertyModel(searchModel, DebugSearchDto.F_TYPE),
                createChoiceModel(renderer), renderer);
        searchForm.add(choice);
        choice.add(new OnChangeAjaxBehavior() {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                listObjectsPerformed(target);
            }
        });

        AjaxSubmitButton searchButton = new AjaxSubmitButton(ID_SEARCH_BUTTON,
                new StringResourceModel("pageDebugList.button.search", this, null)) {

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                listObjectsPerformed(target);
            }
        };
        searchForm.add(searchButton);
    }

    private IModel<List<ObjectTypes>> createChoiceModel(final IChoiceRenderer<ObjectTypes> renderer) {
        return new LoadableModel<List<ObjectTypes>>(false) {

            @Override
            protected List<ObjectTypes> load() {
                List<ObjectTypes> choices = new ArrayList<ObjectTypes>();

                Collections.addAll(choices, ObjectTypes.values());
                choices.remove(ObjectTypes.OBJECT);

                Collections.sort(choices, new Comparator<ObjectTypes>() {

                    @Override
                    public int compare(ObjectTypes o1, ObjectTypes o2) {
                        String str1 = (String) renderer.getDisplayValue(o1);
                        String str2 = (String) renderer.getDisplayValue(o2);
                        return String.CASE_INSENSITIVE_ORDER.compare(str1, str2);
                    }
                });

                return choices;
            }
        };
    }

    private TablePanel getListTable() {
        return (TablePanel) get(createComponentPath(ID_MAIN_FORM, ID_TABLE));
    }

    private void listObjectsPerformed(AjaxRequestTarget target) {
        DebugSearchDto dto = searchModel.getObject();
        String nameText = dto.getText();
        ObjectTypes selected = dto.getType();

        RepositoryObjectDataProvider provider = getTableDataProvider();
        if (StringUtils.isNotEmpty(nameText)) {
            try {
                PolyStringNormalizer normalizer = getPrismContext().getDefaultPolyStringNormalizer();
                String normalizedString = normalizer.normalize(nameText);

                ObjectFilter substring = SubstringFilter.createSubstring(ObjectType.class, getPrismContext(),
                        ObjectType.F_NAME, PolyStringNormMatchingRule.NAME.getLocalPart(), normalizedString);
                ObjectQuery query = new ObjectQuery();
                query.setFilter(substring);
                provider.setQuery(query);
            } catch (Exception ex) {
                LoggingUtils.logException(LOGGER, "Couldn't create substring filter", ex);
                error(getString("pageDebugList.message.queryException", ex.getMessage()));
                target.add(getFeedbackPanel());
            }
        } else {
            provider.setQuery(null);
        }

        if (selected != null) {
            provider.setType(selected.getClassDefinition());
            addOrReplaceTable(provider);
        }

        TablePanel table = getListTable();
        target.add(table);
    }

    private void objectEditPerformed(AjaxRequestTarget target, String oid, Class<? extends ObjectType> type) {
        PageParameters parameters = new PageParameters();
        parameters.add(PageDebugView.PARAM_OBJECT_ID, oid);
        parameters.add(PageDebugView.PARAM_OBJECT_TYPE, type.getSimpleName());
        setResponsePage(PageDebugView.class, parameters);
    }

    private RepositoryObjectDataProvider getTableDataProvider() {
        TablePanel tablePanel = getListTable();
        DataTable table = tablePanel.getDataTable();
        return (RepositoryObjectDataProvider) table.getDataProvider();
    }

    private IModel<String> createDeleteConfirmString() {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                if (deleteSelected) {
                    List<DebugObjectItem> selectedList = WebMiscUtil
                            .getSelectedData(getListTable());

                    if (selectedList.size() > 1) {
                        return createStringResource("pageDebugList.message.deleteSelectedConfirm",
                                selectedList.size()).getString();
                    }

                    DebugObjectItem selectedItem = selectedList.get(0);
                    return createStringResource("pageDebugList.message.deleteObjectConfirm",
                            selectedItem.getName()).getString();
                }

                return createStringResource("pageDebugList.message.deleteObjectConfirm", object.getName())
                        .getString();
            }
        };
    }

    private IModel<String> createLaxativeString() {
        return new AbstractReadOnlyModel<String>() {
            @Override
            public String getObject() {
                int userCount = 0;
                int shadowCount = 0;
                final StringBuilder sb = new StringBuilder();
                sb.append(createStringResource("pageDebugList.dialog.title.confirmLaxativeMessage").getString() + "<br><br>");


                Task task = createSimpleTask(OPERATION_SEARCH_ITERATIVE_TASK);
                OperationResult result = new OperationResult(OPERATION_SEARCH_ITERATIVE_TASK);

                Collection<SelectorOptions<GetOperationOptions>> options = new ArrayList<SelectorOptions<GetOperationOptions>>();
                GetOperationOptions opt = GetOperationOptions.createRaw();
                options.add(SelectorOptions.create(ItemPath.EMPTY_PATH, opt));

                ResultHandler<UserType> userHandler = new ResultHandler<UserType>() {
                    @Override
                    public boolean handle(PrismObject object, OperationResult parentResult) {
                        if (!SystemObjectsType.USER_ADMINISTRATOR.value().equals(object.asObjectable().getOid())) {
                            sb.append(PRINT_LABEL_USER).append(WebMiscUtil.getName(object)).append(PRINT_LABEL_HTML_NEWLINE);
                        }
                        return true;
                    }
                };

                ResultHandler<ShadowType> shadowHandler = new ResultHandler<ShadowType>() {
                    @Override
                    public boolean handle(PrismObject object, OperationResult parentResult) {
                        sb.append(PRINT_LABEL_SHADOW).append(WebMiscUtil.getName(object)).append(PRINT_LABEL_HTML_NEWLINE);
                        return true;
                    }
                };

                try {
                    userCount = getModelService().countObjects(UserType.class, null, options, task, result);

                    //We need to substract 1, because we are not deleting user 'Administrator'
                    userCount--;

                    shadowCount = getModelService().countObjects(ShadowType.class, null, options, task, result);

                    if (userCount >= 10 || shadowCount >= 10) {
                        sb.append(PRINT_LABEL_USER_DELETE).append(userCount).append(PRINT_LABEL_HTML_NEWLINE).append(PRINT_LABEL_SHADOW_DELETE).append(shadowCount).append(PRINT_LABEL_HTML_NEWLINE);
                        sb.append(PRINT_LABEL_ADMINISTRATOR_NOT_DELETED).append(PRINT_LABEL_HTML_NEWLINE);
                    } else {
                        sb.append(PRINT_LABEL_USER_DELETE).append(userCount).append(PRINT_LABEL_HTML_NEWLINE);
                        sb.append(PRINT_LABEL_ADMINISTRATOR_NOT_DELETED);
                        getModelService().searchObjectsIterative(UserType.class, null, userHandler, options, task, result);
                        sb.append(PRINT_LABEL_HTML_NEWLINE);
                        sb.append(PRINT_LABEL_SHADOW_DELETE).append(shadowCount).append(PRINT_LABEL_HTML_NEWLINE);
                        getModelService().searchObjectsIterative(ShadowType.class, null, shadowHandler, options, task, result);
                    }
                } catch (Exception ex) {
                    result.computeStatus(getString("pageDebugList.message.countSearchProblem"));
                    LoggingUtils.logException(LOGGER, getString("pageDebugList.message.countSearchProblem"), ex);
                }

                return sb.toString();
            }
        };
    }

    private void deleteSelectedConfirmedPerformed(AjaxRequestTarget target) {
        ObjectTypes type = choice.getObject();

        OperationResult result = new OperationResult(OPERATION_DELETE_OBJECTS);
        List<DebugObjectItem> beans = WebMiscUtil.getSelectedData(getListTable());
        for (DebugObjectItem bean : beans) {
            OperationResult subResult = result.createSubresult(OPERATION_DELETE_OBJECT);
            try {
                ObjectDelta delta = ObjectDelta.createDeleteDelta(type.getClassDefinition(), bean.getOid(), getPrismContext());

                getModelService().executeChanges(WebMiscUtil.createDeltaCollection(delta),
                        ModelExecuteOptions.createRaw(),
                        createSimpleTask(OPERATION_DELETE_OBJECT), subResult);
                subResult.recordSuccess();
            } catch (Exception ex) {
                subResult.recordFatalError("Couldn't delete objects.", ex);
                LoggingUtils.logException(LOGGER, "Couldn't delete objects", ex);
            }
        }
        result.recomputeStatus();

        RepositoryObjectDataProvider provider = getTableDataProvider();
        provider.clearCache();

        showResult(result);
        target.add(getListTable());
        target.add(getFeedbackPanel());
    }

    private void deleteObjectConfirmedPerformed(AjaxRequestTarget target) {
        OperationResult result = new OperationResult(OPERATION_DELETE_OBJECT);
        try {
            ObjectTypes type = choice.getObject();
            ObjectDelta delta = ObjectDelta.createDeleteDelta(type.getClassDefinition(), object.getOid(), getPrismContext());

            getModelService().executeChanges(WebMiscUtil.createDeltaCollection(delta),
                    ModelExecuteOptions.createRaw(),
                    createSimpleTask(OPERATION_DELETE_OBJECT), result);

            result.recordSuccess();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't delete object '" + object.getName() + "'.", ex);
        }

        RepositoryObjectDataProvider provider = getTableDataProvider();
        provider.clearCache();

        showResult(result);
        target.add(getListTable());
        target.add(getFeedbackPanel());
    }

    private void deleteSelectedPerformed(AjaxRequestTarget target, IModel<ObjectTypes> choice) {
        List<DebugObjectItem> selected = WebMiscUtil.getSelectedData(getListTable());
        if (selected.isEmpty()) {
            warn(getString("pageDebugList.message.nothingSelected"));
            target.add(getFeedbackPanel());
            return;
        }

        ModalWindow dialog = (ModalWindow) get(ID_CONFIRM_DELETE_POPUP);
        deleteSelected = true;
        this.choice = choice;
        dialog.show(target);
    }

    private void deleteObjectPerformed(AjaxRequestTarget target, IModel<ObjectTypes> choice, DebugObjectItem object) {
        ModalWindow dialog = (ModalWindow) get(ID_CONFIRM_DELETE_POPUP);
        this.choice = choice;
        this.object = object;
        dialog.show(target);
    }

    private void laxativeActionPerformed(AjaxRequestTarget target) {
        ModalWindow dialog = (ModalWindow) get(ID_CONFIRM_LAXATIVE_OPERATION);
        dialog.show(target);
        //dialog.close(target);
    }

    private ConfirmationDialog prepareLaxativeConfirmationDialog() {
        return new ConfirmationDialog(ID_CONFIRM_LAXATIVE_OPERATION, createStringResource("pageDebugList.dialog.title.confirmLaxative"),
                createLaxativeString()) {

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                close(target);
                Collection<SelectorOptions<GetOperationOptions>> options = new ArrayList<SelectorOptions<GetOperationOptions>>();
                GetOperationOptions opt = GetOperationOptions.createRaw();
                options.add(SelectorOptions.create(ItemPath.EMPTY_PATH, opt));

                Task laxativeTask = createSimpleTask(OPERATION_LAXATIVE_DELETE);
                OperationResult result = new OperationResult(OPERATION_LAXATIVE_DELETE);

                ResultHandler<UserType> userHandler = new ResultHandler<UserType>() {
                    @Override
                    public boolean handle(PrismObject object, OperationResult parentResult) {
                        if (!SystemObjectsType.USER_ADMINISTRATOR.value().equals(object.asObjectable().getOid())) {
                            ObjectDelta delta = ObjectDelta.createDeleteDelta(UserType.class, object.asObjectable().getOid(), getPrismContext());
                            Task task = createSimpleTask(OPERATION_LAXATIVE_DELETE);
                            OperationResult r = new OperationResult(OPERATION_LAXATIVE_DELETE);

                            try {
                                getModelService().executeChanges(WebMiscUtil.createDeltaCollection(delta), ModelExecuteOptions.createRaw(), task, r);
                                r.recordSuccess();
                            } catch (Exception ex) {
                                r.computeStatus(getString("pageDebugList.message.singleDeleteProblemUser"));
                                LoggingUtils.logException(LOGGER, getString("pageDebugList.message.singleDeleteProblemUser"), ex);
                            }
                            parentResult.addSubresult(r);
                        }
                        return true;
                    }

                };

                ResultHandler<ShadowType> shadowHandler = new ResultHandler<ShadowType>() {
                    @Override
                    public boolean handle(PrismObject object, OperationResult parentResult) {
                        ObjectDelta delta = ObjectDelta.createDeleteDelta(ShadowType.class, object.asObjectable().getOid(), getPrismContext());
                        Task task = createSimpleTask(OPERATION_LAXATIVE_DELETE);
                        OperationResult r = new OperationResult(OPERATION_LAXATIVE_DELETE);

                        try {
                            getModelService().executeChanges(WebMiscUtil.createDeltaCollection(delta), ModelExecuteOptions.createRaw(), task, r);
                            r.recordSuccess();
                        } catch (Exception ex) {
                            r.computeStatus(getString("pageDebugList.message.singleDeleteProblemShadow"));
                            LoggingUtils.logException(LOGGER, getString("pageDebugList.message.singleDeleteProblemShadow"), ex);
                        }
                        parentResult.addSubresult(r);
                        return true;
                    }
                };

                try {
                    getModelService().searchObjectsIterative(UserType.class, null, userHandler, options, laxativeTask, result);
                    getModelService().searchObjectsIterative(ShadowType.class, null, shadowHandler, options, laxativeTask, result);
                } catch (Exception ex) {
                    result.computeStatus(getString("pageDebugList.message.laxativeProblem"));
                    LoggingUtils.logException(LOGGER, getString("pageDebugList.message.laxativeProblem"), ex);
                }

                result.recomputeStatus();
                showResult(result);
                target.add(getListTable());
                target.add(getFeedbackPanel());
            }
        };
    }
}
