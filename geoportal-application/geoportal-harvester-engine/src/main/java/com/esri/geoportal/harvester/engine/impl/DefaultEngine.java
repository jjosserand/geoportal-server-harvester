/*
 * Copyright 2016 Esri, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.geoportal.harvester.engine.impl;

import com.esri.geoportal.harvester.api.ProcessInstance;
import com.esri.geoportal.harvester.engine.support.BrokerReference;
import com.esri.geoportal.harvester.engine.managers.ProcessorRegistry;
import com.esri.geoportal.harvester.engine.managers.InboundConnectorRegistry;
import com.esri.geoportal.harvester.engine.managers.OutboundConnectorRegistry;
import com.esri.geoportal.harvester.engine.managers.TriggerManager;
import com.esri.geoportal.harvester.engine.managers.ProcessManager;
import com.esri.geoportal.harvester.engine.managers.TaskManager;
import com.esri.geoportal.harvester.engine.managers.TriggerRegistry;
import com.esri.geoportal.harvester.engine.managers.ReportBuilder;
import com.esri.geoportal.harvester.engine.managers.History;
import com.esri.geoportal.harvester.engine.managers.HistoryManager;
import com.esri.geoportal.harvester.engine.managers.BrokerDefinitionManager;
import com.esri.geoportal.harvester.engine.support.ProcessReference;
import com.esri.geoportal.harvester.api.defs.Task;
import com.esri.geoportal.harvester.api.defs.TaskDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.esri.geoportal.harvester.api.defs.EntityDefinition;
import com.esri.geoportal.harvester.api.defs.UITemplate;
import com.esri.geoportal.harvester.api.Processor;
import com.esri.geoportal.harvester.api.Trigger;
import com.esri.geoportal.harvester.api.TriggerInstance;
import com.esri.geoportal.harvester.api.defs.TriggerDefinition;
import com.esri.geoportal.harvester.api.ex.DataProcessorException;
import com.esri.geoportal.harvester.api.specs.InputBroker;
import com.esri.geoportal.harvester.api.specs.InputConnector;
import com.esri.geoportal.harvester.api.ex.InvalidDefinitionException;
import com.esri.geoportal.harvester.api.specs.OutputBroker;
import com.esri.geoportal.harvester.api.specs.OutputConnector;
import com.esri.geoportal.harvester.engine.Engine;
import com.esri.geoportal.harvester.engine.managers.TriggerInstanceManager;
import com.esri.geoportal.harvester.engine.support.BrokerReference.Category;
import static com.esri.geoportal.harvester.engine.support.BrokerReference.Category.INBOUND;
import static com.esri.geoportal.harvester.engine.support.BrokerReference.Category.OUTBOUND;
import com.esri.geoportal.harvester.engine.support.CrudsException;
import com.esri.geoportal.harvester.engine.support.HistoryManagerAdaptor;
import com.esri.geoportal.harvester.engine.support.ReportBuilderAdaptor;
import com.esri.geoportal.harvester.engine.support.TriggerReference;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Harvesting engine.
 */
public class DefaultEngine implements Engine {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultEngine.class);

  protected final ReportBuilder reportBuilder;
  protected final TaskManager taskManager;
  protected final ProcessManager processManager;
  protected final TriggerManager triggerManager;
  protected final TriggerInstanceManager triggerInstanceManager;
  protected final HistoryManager historyManager;
  protected final InboundConnectorRegistry inboundConnectorRegistry;
  protected final OutboundConnectorRegistry outboundConnectorRegistry;
  protected final TriggerRegistry triggerRegistry;
  protected final ProcessorRegistry processorRegistry;
  protected final BrokerDefinitionManager brokerDefinitionManager;

  /**
   * Creates instance of the engine.
   *
   * @param inboundConnectorRegistry inbound connector registry
   * @param outboundConnectorRegistry outbound connector registry
   * @param triggerRegistry trigger registry
   * @param processorRegistry processor registry
   * @param brokerDefinitionManager broker definition manager
   * @param taskManager task manager
   * @param processManager process manager
   * @param triggerManager trigger manager
   * @param triggerInstanceManager trigger instance manager
   * @param historyManager history manager
   * @param reportBuilder report builder
   */
  public DefaultEngine(
          InboundConnectorRegistry inboundConnectorRegistry,
          OutboundConnectorRegistry outboundConnectorRegistry,
          TriggerRegistry triggerRegistry,
          ProcessorRegistry processorRegistry,
          BrokerDefinitionManager brokerDefinitionManager,
          TaskManager taskManager,
          ProcessManager processManager,
          TriggerManager triggerManager,
          TriggerInstanceManager triggerInstanceManager,
          HistoryManager historyManager,
          ReportBuilder reportBuilder
  ) {
    this.inboundConnectorRegistry = inboundConnectorRegistry;
    this.outboundConnectorRegistry = outboundConnectorRegistry;
    this.triggerRegistry = triggerRegistry;
    this.processorRegistry = processorRegistry;
    this.taskManager = taskManager;
    this.processManager = processManager;
    this.brokerDefinitionManager = brokerDefinitionManager;
    this.triggerManager = triggerManager;
    this.triggerInstanceManager = triggerInstanceManager;
    this.historyManager = historyManager;
    this.reportBuilder = reportBuilder;
  }

  @Override
  public List<Trigger> listTriggers() {
    return new ArrayList<>(triggerRegistry.values());
  }
  
  @Override
  public List<UITemplate> getInboundConnectorTemplates() {
    return inboundConnectorRegistry.getTemplates();
  }

  @Override
  public List<UITemplate> getOutboundConnectorTemplates() {
    return outboundConnectorRegistry.getTemplates();
  }

  @Override
  public List<UITemplate> getTriggers() {
    return this.triggerRegistry.values().stream().map(v->v.getTemplate()).collect(Collectors.toList());
  }

  @Override
  public List<UITemplate> getProcessorsTemplates() {
    List<UITemplate> templates = new ArrayList<>();
    templates.add(processorRegistry.getDefaultProcessor().getTemplate());
    templates.addAll(processorRegistry.values().stream().map(p->p.getTemplate()).collect(Collectors.toList()));
    return templates;
  }

  @Override
  public List<BrokerReference> getBrokersDefinitions(Category category) throws DataProcessorException {
    if (category != null) {
      try {
        Set<String> brokerTypes = listTypesByCategory(category);
        return brokerDefinitionManager.select().stream()
                .filter(e -> brokerTypes.contains(e.getValue().getType()))
                .map(e -> new BrokerReference(e.getKey(), category, e.getValue()))
                .collect(Collectors.toList());
      } catch (CrudsException ex) {
        throw new DataProcessorException(String.format("Error getting brokers for category: %s", category), ex);
      }
    } else {
      return Stream.concat(getBrokersDefinitions(INBOUND).stream(), getBrokersDefinitions(OUTBOUND).stream()).collect(Collectors.toList());
    }
  }

  @Override
  public BrokerReference findBroker(UUID brokerId) throws DataProcessorException {
    try {
      EntityDefinition brokerDefinition = brokerDefinitionManager.read(brokerId);
      if (brokerDefinition != null) {
        Category category = getBrokerCategoryByType(brokerDefinition.getType());
        if (category != null) {
          return new BrokerReference(brokerId, category, brokerDefinition);
        }
      }
      return null;
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error finding broker: %s", brokerId), ex);
    }
  }

  @Override
  public BrokerReference createBroker(EntityDefinition brokerDefinition) throws DataProcessorException {
    Category category = getBrokerCategoryByType(brokerDefinition.getType());
    if (category != null) {
      try {
        UUID id = brokerDefinitionManager.create(brokerDefinition);
        return new BrokerReference(id, category, brokerDefinition);
      } catch (CrudsException ex) {
        throw new DataProcessorException(String.format("Error creating broker: %s", brokerDefinition), ex);
      }
    }
    return null;
  }

  @Override
  public BrokerReference updateBroker(UUID brokerId, EntityDefinition brokerDefinition) throws DataProcessorException {
    try {
      EntityDefinition oldBrokerDef = brokerDefinitionManager.read(brokerId);
      if (oldBrokerDef != null) {
        if (!brokerDefinitionManager.update(brokerId, brokerDefinition)) {
          oldBrokerDef = null;
        }
      }
      Category category = oldBrokerDef != null ? getBrokerCategoryByType(oldBrokerDef.getType()) : null;
      return category != null ? new BrokerReference(brokerId, category, brokerDefinition) : null;
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error updating broker: %s <-- %s", brokerId, brokerDefinition), ex);
    }
  }

  @Override
  public boolean deleteBroker(UUID brokerId) throws DataProcessorException {
    try {
      return brokerDefinitionManager.delete(brokerId);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error deleting broker: %s", brokerId), ex);
    }
  }

  @Override
  public ProcessInstance getProcess(UUID processId) throws DataProcessorException {
    try {
      return processManager.read(processId);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error getting process: %s", processId), ex);
    }
  }

  @Override
  public List<Map.Entry<UUID, ProcessInstance>> selectProcesses(Predicate<? super Map.Entry<UUID, ProcessInstance>> predicate) throws DataProcessorException {
    try {
      return processManager.select().stream().filter(predicate != null ? predicate : (Map.Entry<UUID, ProcessInstance> e) -> true).collect(Collectors.toList());
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error celecting processes."), ex);
    }
  }
  
  @Override
  public Task createTask(TaskDefinition taskDefinition) throws InvalidDefinitionException {
    InputConnector<InputBroker> dsFactory = inboundConnectorRegistry.get(taskDefinition.getSource().getType());

    if (dsFactory == null) {
      throw new IllegalArgumentException("Invalid data source init parameters");
    }

    InputBroker dataSource = dsFactory.createBroker(taskDefinition.getSource());

    ArrayList<OutputBroker> dataDestinations = new ArrayList<>();
    for (EntityDefinition def : taskDefinition.getDestinations()) {
      OutputConnector<OutputBroker> dpFactory = outboundConnectorRegistry.get(def.getType());
      if (dpFactory == null) {
        throw new IllegalArgumentException("Invalid data publisher init parameters");
      }

      OutputBroker dataPublisher = dpFactory.createBroker(def);
      dataDestinations.add(dataPublisher);
    }
    
    EntityDefinition processorDefinition = taskDefinition.getProcessor();
    Processor processor = processorDefinition == null
            ? processorRegistry.getDefaultProcessor()
            : processorRegistry.get(processorDefinition.getType()) != null
            ? processorRegistry.get(processorDefinition.getType())
            : null;
    if (processor == null) {
      throw new InvalidDefinitionException(String.format("Unable to select processor based on definition: %s", processorDefinition));
    }

    return new Task(processor, dataSource, dataDestinations);
  }

  @Override
  public ProcessReference createProcess(Task task) throws InvalidDefinitionException, DataProcessorException {
    try {
      ProcessInstance process = task.getProcessor().createProcess(task);
      UUID uuid = processManager.create(process);
      process.addListener(new ReportBuilderAdaptor(uuid, process, reportBuilder));
      return new ProcessReference(uuid, process);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error creating process: %s", task), ex);
    }
  }

  @Override
  public ProcessReference submitTaskDefinition(TaskDefinition taskDefinition) throws InvalidDefinitionException, DataProcessorException {
    Task task = createTask(taskDefinition);
    return createProcess(task);
  }
  
  @Override
  public TriggerReference scheduleTask(UUID taskId, TriggerDefinition trigDef) throws InvalidDefinitionException, DataProcessorException {
    try {
      UUID uuid = triggerManager.create(trigDef);
      Trigger trigger = triggerRegistry.get(trigDef.getType());
      TriggerInstance triggerInstance = trigger.createInstance(trigDef);
      triggerInstanceManager.put(uuid, triggerInstance);
      TriggerContext context = new TriggerContext(taskId);
      triggerInstance.activate(context);
      return new TriggerReference(uuid, trigDef);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error scheduling task: %s", trigDef.getTaskDefinition()), ex);
    }
  }
  
  @Override
  public TriggerReference deactivateTriggerInstance(UUID triggerInstanceUuid) throws InvalidDefinitionException, DataProcessorException {
    TriggerInstance triggerInstance = triggerInstanceManager.remove(triggerInstanceUuid);
    if (triggerInstance == null) {
      throw new InvalidDefinitionException(String.format("Invalid trigger id: %s", triggerInstanceUuid));
    }
    try {
      TriggerDefinition trigDef = triggerInstance.getTriggerDefinition();
      TriggerReference triggerReference = new TriggerReference(triggerInstanceUuid, trigDef);
      triggerInstance.close();
      return triggerReference;
    } catch (Exception ex) {
      throw new DataProcessorException(String.format("Error deactivating trigger: %s", triggerInstanceUuid), ex);
    } finally {
      try {
        triggerManager.delete(triggerInstanceUuid);
      } catch (CrudsException ex) {
        LOG.warn(String.format("Error deleting trigger: %s", triggerInstanceUuid), ex);
      }
    }
  }

  @Override
  public List<TriggerReference> listActivatedTriggers() {
    return triggerInstanceManager.listAll().stream()
            .map(e->new TriggerReference(e.getKey(),e.getValue().getTriggerDefinition()))
            .collect(Collectors.toList());
  }

  @Override
  public List<Map.Entry<UUID, TaskDefinition>> selectTaskDefinitions(Predicate<? super Map.Entry<UUID, TaskDefinition>> predicate) throws DataProcessorException {
    try {
      return taskManager.select().stream().filter(predicate != null ? predicate : (Map.Entry<UUID, TaskDefinition> e) -> true).collect(Collectors.toList());
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error selecting task definitions."), ex);
    }
  }

  @Override
  public TaskDefinition readTaskDefinition(UUID taskId) throws DataProcessorException {
    try {
      return taskManager.read(taskId);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error reading task definition: %s", taskId), ex);
    }
  }

  @Override
  public boolean deleteTaskDefinition(UUID taskId) throws DataProcessorException {
    try {
      return taskManager.delete(taskId);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error deleting task definition: %s", taskId), ex);
    }
  }

  @Override
  public UUID addTaskDefinition(TaskDefinition taskDefinition) throws DataProcessorException {
    try {
      return taskManager.create(taskDefinition);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error adding task definition: %s", taskDefinition), ex);
    }
  }

  @Override
  public TaskDefinition updateTaskDefinition(UUID taskId, TaskDefinition taskDefinition) throws DataProcessorException {
    try {
      TaskDefinition oldTaskDef = taskManager.read(taskId);
      if (oldTaskDef != null) {
        if (!taskManager.update(taskId, taskDefinition)) {
          oldTaskDef = null;
        }
      }
      return oldTaskDef;
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error updating task definition: %s <-- %s", taskId, taskDefinition), ex);
    }
  }

  /**
   * Lists types by category.
   *
   * @param category category
   * @return set of types within the category
   */
  private Set<String> listTypesByCategory(Category category) {
    List<UITemplate> templates
            = category == INBOUND ? inboundConnectorRegistry.getTemplates()
                    : category == OUTBOUND ? outboundConnectorRegistry.getTemplates()
                            : null;

    if (templates != null) {
      return templates.stream().map(t -> t.getType()).collect(Collectors.toSet());
    } else {
      return Collections.EMPTY_SET;
    }
  }

  /**
   * Gets broker category by broker type.
   *
   * @param brokerType broker type
   * @return broker category or <code>null</code> if category couldn't be
   * determined
   */
  private Category getBrokerCategoryByType(String brokerType) {
    Set<String> inboundTypes = inboundConnectorRegistry.getTemplates().stream().map(t -> t.getType()).collect(Collectors.toSet());
    Set<String> outboundTypes = outboundConnectorRegistry.getTemplates().stream().map(t -> t.getType()).collect(Collectors.toSet());
    return inboundTypes.contains(brokerType) ? INBOUND : outboundTypes.contains(brokerType) ? OUTBOUND : null;
  }

  /**
   * DefaultEngine-bound trigger context.
   */
  protected class TriggerContext implements TriggerInstance.Context {
    private final UUID taskId;
    
    /**
     * Creates instance of the context.
     */
    public TriggerContext() {
      this.taskId = null;
    }
    
    /**
     * Creates instance of the context.
     * @param taskId task id
     */
    public TriggerContext(UUID taskId) {
      this.taskId = taskId;
    }

    @Override
    public synchronized ProcessInstance submit(TaskDefinition taskDefinition) throws DataProcessorException, InvalidDefinitionException {
      ProcessReference ref = submitTaskDefinition(taskDefinition);
      if (taskId!=null) {
        ref.getProcess().addListener(new HistoryManagerAdaptor(taskId, ref.getProcess(), historyManager));
      }
      ref.getProcess().init();
      return ref.getProcess();
    }
    
    @Override
    public Date lastHarvest() throws DataProcessorException {
      try {
        if (taskId!=null) {
          History history = historyManager.buildHistory(taskId);
          History.Event lastEvent = history.lastEvent();
          return lastEvent!=null? lastEvent.getTimestamp(): null;
        } else {
          return null;
        }
      } catch (CrudsException ex) {
        throw new DataProcessorException(String.format("Error getting last harvest for: %s", taskId), ex);
      }
    }
  }
}
