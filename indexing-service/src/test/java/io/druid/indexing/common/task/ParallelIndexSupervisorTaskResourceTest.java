/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.druid.indexing.common.task;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import io.druid.client.indexing.IndexingServiceClient;
import io.druid.data.input.FiniteFirehoseFactory;
import io.druid.data.input.InputSplit;
import io.druid.data.input.impl.StringInputRowParser;
import io.druid.indexer.RunnerTaskState;
import io.druid.indexer.TaskLocation;
import io.druid.indexer.TaskState;
import io.druid.indexer.TaskStatus;
import io.druid.indexer.TaskStatusPlus;
import io.druid.indexing.common.TaskLock;
import io.druid.indexing.common.TaskToolbox;
import io.druid.indexing.common.actions.LockListAction;
import io.druid.indexing.common.actions.SurrogateAction;
import io.druid.indexing.common.actions.TaskActionClient;
import io.druid.indexing.common.task.ParallelIndexSupervisorTask.Status;
import io.druid.indexing.common.task.SinglePhaseParallelIndexTaskRunner.SubTaskStateResponse;
import io.druid.java.util.common.DateTimes;
import io.druid.java.util.common.ISE;
import io.druid.java.util.common.Intervals;
import io.druid.java.util.common.concurrent.Execs;
import io.druid.java.util.common.granularity.Granularities;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.LongSumAggregatorFactory;
import io.druid.segment.indexing.DataSchema;
import io.druid.segment.indexing.granularity.UniformGranularitySpec;
import io.druid.segment.realtime.firehose.ChatHandlerProvider;
import io.druid.segment.realtime.firehose.NoopChatHandlerProvider;
import io.druid.server.security.AuthConfig;
import io.druid.server.security.AuthenticationResult;
import io.druid.server.security.AuthorizerMapper;
import io.druid.timeline.DataSegment;
import io.druid.timeline.partition.NumberedShardSpec;
import org.easymock.EasyMock;
import org.joda.time.Interval;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ParallelIndexSupervisorTaskResourceTest extends AbstractParallelIndexSupervisorTaskTest
{
  private static final int NUM_SUB_TASKS = 10;

  // specId -> spec
  private final ConcurrentMap<String, ParallelIndexSubTaskSpec> subTaskSpecs = new ConcurrentHashMap<>();

  // specId -> taskStatusPlus
  private final ConcurrentMap<String, TaskStatusPlus> runningSpecs = new ConcurrentHashMap<>();

  // specId -> taskStatusPlus list
  private final ConcurrentMap<String, List<TaskStatusPlus>> taskHistories = new ConcurrentHashMap<>();

  // taskId -> subTaskSpec
  private final ConcurrentMap<String, ParallelIndexSubTaskSpec> taskIdToSpec = new ConcurrentHashMap<>();

  // taskId -> task
  private final CopyOnWriteArrayList<TestSubTask> runningTasks = new CopyOnWriteArrayList<>();

  private ExecutorService service;

  private TestSupervisorTask task;
  private SinglePhaseParallelIndexTaskRunner runner;

  @Before
  public void setup() throws IOException
  {
    service = Execs.singleThreaded("parallel-index-supervisor-task-resource-test-%d");
    indexingServiceClient = new LocalIndexingServiceClient();
    localDeepStorage = temporaryFolder.newFolder("localStorage");
  }

  @After
  public void teardown()
  {
    indexingServiceClient.shutdown();
    temporaryFolder.delete();
    service.shutdownNow();
  }

  @Test(timeout = 20000L)
  public void testAPIs() throws Exception
  {
    task = newTask(
        Intervals.of("2017/2018"),
        new ParallelIndexIOConfig(
            new TestFirehose(IntStream.range(0, NUM_SUB_TASKS).boxed().collect(Collectors.toList())),
            false
        )
    );
    actionClient = createActionClient(task);
    toolbox = createTaskToolbox(task);

    prepareTaskForLocking(task);
    Assert.assertTrue(task.isReady(actionClient));
    final Future<TaskStatus> supervisorTaskFuture = service.submit(() -> task.run(toolbox));
    Thread.sleep(1000);

    runner = (SinglePhaseParallelIndexTaskRunner) task.getRunner();
    Assert.assertNotNull("runner is null", runner);

    // test isRunningInParallel
    Response response = runner.isRunningInParallel(newRequest());
    Assert.assertEquals(200, response.getStatus());
    Assert.assertEquals("parallel", response.getEntity());

    // test expectedNumSucceededTasks
    response = runner.getStatus(newRequest());
    Assert.assertEquals(200, response.getStatus());
    Assert.assertEquals(NUM_SUB_TASKS, ((Status) response.getEntity()).getExpectedSucceeded());

    // Since taskMonitor works based on polling, it's hard to use a fancier way to check its state.
    // We use polling to check the state of taskMonitor in this test.
    while (getNumSubTasks(Status::getRunning) < NUM_SUB_TASKS) {
      Thread.sleep(100);
    }

    int succeededTasks = 0;
    int failedTasks = 0;
    checkState(
        succeededTasks,
        failedTasks,
        buildStateMap()
    );

    // numRunningTasks and numSucceededTasks after some successful subTasks
    succeededTasks += 2;
    for (int i = 0; i < succeededTasks; i++) {
      runningTasks.get(0).setState(TaskState.SUCCESS);
    }

    while (getNumSubTasks(Status::getSucceeded) < succeededTasks) {
      Thread.sleep(100);
    }

    checkState(
        succeededTasks,
        failedTasks,
        buildStateMap()
    );

    // numRunningTasks and numSucceededTasks after some failed subTasks
    failedTasks += 3;
    for (int i = 0; i < failedTasks; i++) {
      runningTasks.get(0).setState(TaskState.FAILED);
    }

    // Wait for new tasks to be started
    while (getNumSubTasks(Status::getFailed) < failedTasks || runningTasks.size() < NUM_SUB_TASKS - succeededTasks) {
      Thread.sleep(100);
    }

    checkState(
        succeededTasks,
        failedTasks,
        buildStateMap()
    );

    // Make sure only one subTask is running
    succeededTasks += 7;
    for (int i = 0; i < 7; i++) {
      runningTasks.get(0).setState(TaskState.SUCCESS);
    }

    while (getNumSubTasks(Status::getSucceeded) < succeededTasks) {
      Thread.sleep(100);
    }

    checkState(
        succeededTasks,
        failedTasks,
        buildStateMap()
    );

    Assert.assertEquals(1, runningSpecs.size());
    final String lastRunningSpecId = runningSpecs.keySet().iterator().next();
    final List<TaskStatusPlus> taskHistory = taskHistories.get(lastRunningSpecId);
    // This should be a failed task history because new tasks appear later in runningTasks.
    Assert.assertEquals(1, taskHistory.size());

    // Test one more failure
    runningTasks.get(0).setState(TaskState.FAILED);
    failedTasks++;
    while (getNumSubTasks(Status::getFailed) < failedTasks) {
      Thread.sleep(100);
    }
    while (getNumSubTasks(Status::getRunning) < 1) {
      Thread.sleep(100);
    }

    checkState(
        succeededTasks,
        failedTasks,
        buildStateMap()
    );
    Assert.assertEquals(2, taskHistory.size());

    runningTasks.get(0).setState(TaskState.SUCCESS);
    succeededTasks++;
    while (getNumSubTasks(Status::getSucceeded) < succeededTasks) {
      Thread.sleep(100);
    }

    Assert.assertEquals(TaskState.SUCCESS, supervisorTaskFuture.get(1000, TimeUnit.MILLISECONDS).getStatusCode());
  }

  @SuppressWarnings({"ConstantConditions"})
  private int getNumSubTasks(Function<Status, Integer> func)
  {
    final Response response = runner.getStatus(newRequest());
    Assert.assertEquals(200, response.getStatus());
    return func.apply((Status) response.getEntity());
  }

  private Map<String, SubTaskStateResponse> buildStateMap()
  {
    final Map<String, SubTaskStateResponse> stateMap = new HashMap<>();
    subTaskSpecs.forEach((specId, spec) -> {
      final List<TaskStatusPlus> taskHistory = taskHistories.get(specId);
      final TaskStatusPlus runningTaskStatus = runningSpecs.get(specId);
      stateMap.put(
          specId,
          new SubTaskStateResponse(spec, runningTaskStatus, taskHistory == null ? Collections.emptyList() : taskHistory)
      );
    });
    return stateMap;
  }

  /**
   * Test all endpoints of {@link ParallelIndexSupervisorTask}.
   */
  private void checkState(
      int expectedSucceededTasks,
      int expectedFailedTask,
      Map<String, SubTaskStateResponse> expectedSubTaskStateResponses // subTaskSpecId -> response
  )
  {
    Response response = runner.getStatus(newRequest());
    Assert.assertEquals(200, response.getStatus());
    final Status monitorStatus = (Status) response.getEntity();

    // numRunningTasks
    Assert.assertEquals(runningTasks.size(), monitorStatus.getRunning());

    // numSucceededTasks
    Assert.assertEquals(expectedSucceededTasks, monitorStatus.getSucceeded());

    // numFailedTasks
    Assert.assertEquals(expectedFailedTask, monitorStatus.getFailed());

    // numCompleteTasks
    Assert.assertEquals(expectedSucceededTasks + expectedFailedTask, monitorStatus.getComplete());

    // numTotalTasks
    Assert.assertEquals(runningTasks.size() + expectedSucceededTasks + expectedFailedTask, monitorStatus.getTotal());

    // runningSubTasks
    response = runner.getRunningTasks(newRequest());
    Assert.assertEquals(200, response.getStatus());
    Assert.assertEquals(
        runningTasks.stream().map(AbstractTask::getId).collect(Collectors.toSet()),
        new HashSet<>((Collection<String>) response.getEntity())
    );

    // subTaskSpecs
    response = runner.getSubTaskSpecs(newRequest());
    Assert.assertEquals(200, response.getStatus());
    List<SubTaskSpec<ParallelIndexSubTask>> actualSubTaskSpecMap =
        (List<SubTaskSpec<ParallelIndexSubTask>>) response.getEntity();
    Assert.assertEquals(
        subTaskSpecs.keySet(),
        actualSubTaskSpecMap.stream().map(SubTaskSpec::getId).collect(Collectors.toSet())
    );

    // runningSubTaskSpecs
    response = runner.getRunningSubTaskSpecs(newRequest());
    Assert.assertEquals(200, response.getStatus());
    actualSubTaskSpecMap =
        (List<SubTaskSpec<ParallelIndexSubTask>>) response.getEntity();
    Assert.assertEquals(
        runningSpecs.keySet(),
        actualSubTaskSpecMap.stream().map(SubTaskSpec::getId).collect(Collectors.toSet())
    );

    // completeSubTaskSpecs
    final List<SubTaskSpec<ParallelIndexSubTask>> completeSubTaskSpecs = expectedSubTaskStateResponses
        .entrySet()
        .stream()
        .filter(entry -> !runningSpecs.containsKey(entry.getKey()))
        .map(entry -> entry.getValue().getSpec())
        .collect(Collectors.toList());

    response = runner.getCompleteSubTaskSpecs(newRequest());
    Assert.assertEquals(200, response.getStatus());
    Assert.assertEquals(completeSubTaskSpecs, response.getEntity());

    // subTaskSpec
    final String subTaskId = runningSpecs.keySet().iterator().next();
    response = runner.getSubTaskSpec(subTaskId, newRequest());
    Assert.assertEquals(200, response.getStatus());
    final SubTaskSpec<ParallelIndexSubTask> subTaskSpec =
        (SubTaskSpec<ParallelIndexSubTask>) response.getEntity();
    Assert.assertEquals(subTaskId, subTaskSpec.getId());

    // subTaskState
    response = runner.getSubTaskState(subTaskId, newRequest());
    Assert.assertEquals(200, response.getStatus());
    final SubTaskStateResponse expectedResponse = Preconditions.checkNotNull(
        expectedSubTaskStateResponses.get(subTaskId),
        "response for task[%s]",
        subTaskId
    );
    final SubTaskStateResponse actualResponse = (SubTaskStateResponse) response.getEntity();
    Assert.assertEquals(expectedResponse.getSpec().getId(), actualResponse.getSpec().getId());
    Assert.assertEquals(expectedResponse.getCurrentStatus(), actualResponse.getCurrentStatus());
    Assert.assertEquals(expectedResponse.getTaskHistory(), actualResponse.getTaskHistory());

    // completeSubTaskSpecAttemptHistory
    final String completeSubTaskSpecId = expectedSubTaskStateResponses
        .entrySet()
        .stream()
        .filter(entry -> {
          final TaskStatusPlus currentStatus = entry.getValue().getCurrentStatus();
          return currentStatus != null &&
                 (currentStatus.getState() == TaskState.SUCCESS || currentStatus.getState() == TaskState.FAILED);
        })
        .map(Entry::getKey)
        .findFirst()
        .orElse(null);
    if (completeSubTaskSpecId != null) {
      response = runner.getCompleteSubTaskSpecAttemptHistory(completeSubTaskSpecId, newRequest());
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals(
          expectedSubTaskStateResponses.get(completeSubTaskSpecId).getTaskHistory(),
          response.getEntity()
      );
    }
  }

  private static HttpServletRequest newRequest()
  {
    final HttpServletRequest request = EasyMock.niceMock(HttpServletRequest.class);
    EasyMock.expect(request.getAttribute(AuthConfig.DRUID_AUTHORIZATION_CHECKED)).andReturn(null);
    EasyMock.expect(request.getAttribute(AuthConfig.DRUID_AUTHENTICATION_RESULT))
            .andReturn(new AuthenticationResult("test", "test", "test", Collections.emptyMap()));
    EasyMock.replay(request);
    return request;
  }

  private TestSupervisorTask newTask(
      Interval interval,
      ParallelIndexIOConfig ioConfig
  )
  {
    // set up ingestion spec
    final ParallelIndexIngestionSpec ingestionSpec = new ParallelIndexIngestionSpec(
        new DataSchema(
            "dataSource",
            getObjectMapper().convertValue(
                new StringInputRowParser(
                    DEFAULT_PARSE_SPEC,
                    null
                ),
                Map.class
            ),
            new AggregatorFactory[]{
                new LongSumAggregatorFactory("val", "val")
            },
            new UniformGranularitySpec(
                Granularities.DAY,
                Granularities.MINUTE,
                interval == null ? null : Collections.singletonList(interval)
            ),
            null,
            getObjectMapper()
        ),
        ioConfig,
        new ParallelIndexTuningConfig(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            NUM_SUB_TASKS,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
    );

    // set up test tools
    return new TestSupervisorTask(
        null,
        null,
        ingestionSpec,
        Collections.emptyMap(),
        indexingServiceClient
    );
  }

  private static class TestFirehose implements FiniteFirehoseFactory<StringInputRowParser, Integer>
  {
    private final List<Integer> ids;

    TestFirehose(List<Integer> ids)
    {
      this.ids = ids;
    }

    @Override
    public Stream<InputSplit<Integer>> getSplits()
    {
      return ids.stream().map(InputSplit::new);
    }

    @Override
    public int getNumSplits()
    {
      return ids.size();
    }

    @Override
    public FiniteFirehoseFactory<StringInputRowParser, Integer> withSplit(InputSplit<Integer> split)
    {
      return new TestFirehose(Collections.singletonList(split.get()));
    }
  }

  private class TestSupervisorTask extends TestParallelIndexSupervisorTask
  {
    private TestRunner runner;

    TestSupervisorTask(
        String id,
        TaskResource taskResource,
        ParallelIndexIngestionSpec ingestionSchema,
        Map<String, Object> context,
        IndexingServiceClient indexingServiceClient
    )
    {
      super(
          id,
          taskResource,
          ingestionSchema,
          context,
          indexingServiceClient
      );
    }

    @Override
    public TaskStatus run(TaskToolbox toolbox) throws Exception
    {
      this.runner = new TestRunner(
          this,
          indexingServiceClient,
          new NoopChatHandlerProvider(),
          getAuthorizerMapper()
      );
      return TaskStatus.fromCode(
          getId(),
          runner.run(toolbox)
      );
    }

    @Override
    public ParallelIndexTaskRunner getRunner()
    {
      return runner;
    }
  }

  private class TestRunner extends TestParallelIndexTaskRunner
  {
    private final ParallelIndexSupervisorTask supervisorTask;

    TestRunner(
        ParallelIndexSupervisorTask supervisorTask,
        @Nullable IndexingServiceClient indexingServiceClient,
        @Nullable ChatHandlerProvider chatHandlerProvider,
        AuthorizerMapper authorizerMapper
    )
    {
      super(
          supervisorTask.getId(),
          supervisorTask.getGroupId(),
          supervisorTask.getIngestionSchema(),
          supervisorTask.getContext(),
          indexingServiceClient,
          chatHandlerProvider,
          authorizerMapper
      );
      this.supervisorTask = supervisorTask;
    }

    @Override
    ParallelIndexSubTaskSpec newTaskSpec(InputSplit split)
    {
      final FiniteFirehoseFactory baseFirehoseFactory = (FiniteFirehoseFactory) getIngestionSchema()
          .getIOConfig()
          .getFirehoseFactory();
      final TestSubTaskSpec spec = new TestSubTaskSpec(
          supervisorTask.getId() + "_" + getAndIncrementNextSpecId(),
          supervisorTask.getGroupId(),
          supervisorTask,
          this,
          new ParallelIndexIngestionSpec(
              getIngestionSchema().getDataSchema(),
              new ParallelIndexIOConfig(
                  baseFirehoseFactory.withSplit(split),
                  getIngestionSchema().getIOConfig().isAppendToExisting()
              ),
              getIngestionSchema().getTuningConfig()
          ),
          supervisorTask.getContext(),
          split
      );
      subTaskSpecs.put(spec.getId(), spec);
      return spec;
    }
  }

  private class TestSubTaskSpec extends ParallelIndexSubTaskSpec
  {
    private final SinglePhaseParallelIndexTaskRunner runner;

    TestSubTaskSpec(
        String id,
        String groupId,
        ParallelIndexSupervisorTask supervisorTask,
        SinglePhaseParallelIndexTaskRunner runner,
        ParallelIndexIngestionSpec ingestionSpec,
        Map<String, Object> context,
        InputSplit inputSplit
    )
    {
      super(id, groupId, supervisorTask.getId(), ingestionSpec, context, inputSplit);
      this.runner = runner;
    }

    @Override
    public ParallelIndexSubTask newSubTask(int numAttempts)
    {
      try {
        // taskId is suffixed by the current time and this sleep is to make sure that every sub task has different id
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      final TestSubTask subTask = new TestSubTask(
          getGroupId(),
          getSupervisorTaskId(),
          numAttempts,
          getIngestionSpec(),
          getContext(),
          new LocalParallelIndexTaskClientFactory(runner)
      );
      final TestFirehose firehose = (TestFirehose) getIngestionSpec().getIOConfig().getFirehoseFactory();
      final InputSplit<Integer> split = firehose.getSplits().findFirst().orElse(null);
      if (split == null) {
        throw new ISE("Split is null");
      }
      runningTasks.add(subTask);
      taskIdToSpec.put(subTask.getId(), this);
      runningSpecs.put(
          getId(),
          new TaskStatusPlus(
              subTask.getId(),
              subTask.getType(),
              DateTimes.EPOCH,
              DateTimes.EPOCH,
              TaskState.RUNNING,
              RunnerTaskState.RUNNING,
              -1L,
              TaskLocation.unknown(),
              null,
              null
          )
      );
      return subTask;
    }
  }

  private class TestSubTask extends ParallelIndexSubTask
  {
    private volatile TaskState state = TaskState.RUNNING;

    TestSubTask(
        String groupId,
        String supervisorTaskId,
        int numAttempts,
        ParallelIndexIngestionSpec ingestionSchema,
        Map<String, Object> context,
        IndexTaskClientFactory<ParallelIndexTaskClient> taskClientFactory
    )
    {
      super(
          null,
          groupId,
          null,
          supervisorTaskId,
          numAttempts,
          ingestionSchema,
          context,
          null,
          taskClientFactory
      );
    }

    @Override
    public boolean isReady(TaskActionClient taskActionClient)
    {
      return true;
    }

    @Override
    public TaskStatus run(final TaskToolbox toolbox) throws Exception
    {
      while (state == TaskState.RUNNING) {
        Thread.sleep(100);
      }

      final TestFirehose firehose = (TestFirehose) getIngestionSchema().getIOConfig().getFirehoseFactory();

      final List<TaskLock> locks = toolbox.getTaskActionClient()
                                          .submit(new SurrogateAction<>(getSupervisorTaskId(), new LockListAction()));
      Preconditions.checkState(locks.size() == 1, "There should be a single lock");

      runner.collectReport(
          new PushedSegmentsReport(
              getId(),
              Collections.singletonList(
                  new DataSegment(
                      getDataSource(),
                      Intervals.of("2017/2018"),
                      locks.get(0).getVersion(),
                      null,
                      null,
                      null,
                      new NumberedShardSpec(firehose.ids.get(0), NUM_SUB_TASKS),
                      0,
                      1L
                  )
              )
          )
      );
      return TaskStatus.fromCode(getId(), state);
    }

    void setState(TaskState state)
    {
      Preconditions.checkArgument(
          state == TaskState.SUCCESS || state == TaskState.FAILED,
          "state[%s] should be SUCCESS of FAILED",
          state
      );
      this.state = state;
      final int taskIndex = IntStream.range(0, runningTasks.size())
                                     .filter(i -> runningTasks.get(i).getId().equals(getId())).findAny()
                                     .orElse(-1);
      if (taskIndex == -1) {
        throw new ISE("Can't find an index for task[%s]", getId());
      }
      runningTasks.remove(taskIndex);
      final String specId = Preconditions.checkNotNull(taskIdToSpec.get(getId()), "spec for task[%s]", getId()).getId();
      runningSpecs.remove(specId);
      taskHistories.computeIfAbsent(specId, k -> new ArrayList<>()).add(
          new TaskStatusPlus(
              getId(),
              getType(),
              DateTimes.EPOCH,
              DateTimes.EPOCH,
              state,
              RunnerTaskState.NONE,
              -1L,
              TaskLocation.unknown(),
              null,
              null
          )
      );
    }
  }
}