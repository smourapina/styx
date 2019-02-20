/*-
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2016 Spotify AB
 * --
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
 * -/-/-
 */

package com.spotify.styx.state.handlers;

import static com.spotify.styx.state.handlers.HandlerUtil.argsReplace;
import static java.util.Objects.requireNonNull;

import com.spotify.styx.docker.DockerRunner;
import com.spotify.styx.docker.DockerRunner.RunSpec;
import com.spotify.styx.docker.InvalidExecutionException;
import com.spotify.styx.model.Event;
import com.spotify.styx.model.ExecutionDescription;
import com.spotify.styx.state.OutputHandler;
import com.spotify.styx.state.RunState;
import com.spotify.styx.util.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OutputHandler} that starts docker runs on {@link RunState.State#SUBMITTED} transitions
 */
public class DockerRunnerHandler implements OutputHandler {

  private static final Logger LOG = LoggerFactory.getLogger(DockerRunnerHandler.class);

  private final DockerRunner dockerRunner;

  public DockerRunnerHandler(DockerRunner dockerRunner) {
    this.dockerRunner = requireNonNull(dockerRunner);
  }

  @Override
  public Optional<Event> transitionInto(RunState state) {
    switch (state.state()) {
      case SUBMITTING:
        final RunSpec runSpec;
        try {
          runSpec = createRunSpec(state);
        } catch (ResourceNotFoundException e) {
          LOG.error("Unable to start docker procedure.", e);
          return Optional.of(Event.halt(state.workflowInstance()));
        }

        try {
          LOG.info("running:{} image:{} args:{} termination_logging:{}", state.workflowInstance(),
              runSpec.imageName(), runSpec.args(), runSpec.terminationLogging());
          dockerRunner.start(state.workflowInstance(), runSpec);
        } catch (Throwable e) {
          final String msg = "Failed the docker starting procedure for " + state.workflowInstance().toKey();
          if (isUserError(e)) {
            LOG.info("{}: {}", msg, e.getMessage());
          } else {
            LOG.error(msg, e);
          }
          return Optional.of(Event.runError(state.workflowInstance(), e.getMessage()));
        }

        return Optional.of(Event.submitted(state.workflowInstance(), runSpec.executionId()));

      case SUBMITTED: {
        final DockerRunner.JobStatus jobStatus = dockerRunner.status(state.data().executionId().get());
        if (jobStatus == null) {
          // Gone...
          return Optional.of(Event.runError(state.workflowInstance(), "Job gone"));
        }
        if (jobStatus.error().isPresent()) {
          return Optional.of(Event.runError(state.workflowInstance(), "Job error: " + jobStatus.error().get()));
        }

        switch (jobStatus.phase()) {
          case PENDING:
            // Let's wait some more
            return Optional.empty();
          case RUNNING:
          case SUCCEEDED:
          case FAILED:
            return Optional.of(Event.started(state.workflowInstance()));
          default:
            throw new AssertionError();
        }
      }

      case RUNNING:
        final DockerRunner.JobStatus jobStatus = dockerRunner.status(state.data().executionId().get());
        if (jobStatus == null) {
          // Gone...
          return Optional.of(Event.runError(state.workflowInstance(), "Job gone"));
        }
        if (jobStatus.error().isPresent()) {
          return Optional.of(Event.runError(state.workflowInstance(), "Job error: " + jobStatus.error().get()));
        }

        switch (jobStatus.phase()) {
          case PENDING:
            return Optional.of(Event.runError(state.workflowInstance(), "Unexpected job phase: " + jobStatus.phase()));
          case RUNNING:
            // Let's wait some more
            return Optional.empty();
          case SUCCEEDED:
          case FAILED:
            return Optional.of(Event.terminate(state.workflowInstance(), jobStatus.exitCode()));
          default:
            throw new AssertionError();
        }

      case TERMINATED:
      case FAILED:
      case ERROR:
        // TODO: remove this effectively unused cleanup?
        if (state.data().executionId().isPresent()) {
          final String executionId = state.data().executionId().get();
          dockerRunner.cleanup(state.workflowInstance(), executionId);
        }
        return Optional.empty();

      default:
        return Optional.empty();
    }
  }

  private boolean isUserError(Throwable e) {
    return e instanceof InvalidExecutionException;
  }

  private RunSpec createRunSpec(RunState state) throws ResourceNotFoundException {
    final Optional<ExecutionDescription> executionDescriptionOpt = state.data().executionDescription();

    final ExecutionDescription executionDescription = executionDescriptionOpt.orElseThrow(
        () -> new ResourceNotFoundException("Missing execution description for " + state.workflowInstance()));

    final String executionId = state.data().executionId().orElseThrow(
        () -> new ResourceNotFoundException("Missing execution id for " + state.workflowInstance()));

    final String dockerImage = executionDescription.dockerImage();
    final List<String> dockerArgs = executionDescription.dockerArgs();
    final String parameter = state.workflowInstance().parameter();
    final List<String> command = argsReplace(dockerArgs, parameter);
    return RunSpec.builder()
        .executionId(executionId)
        .imageName(dockerImage)
        .args(command)
        .terminationLogging(executionDescription.dockerTerminationLogging())
        .secret(executionDescription.secret())
        .serviceAccount(executionDescription.serviceAccount())
        .trigger(state.data().trigger())
        .commitSha(state.data().executionDescription().flatMap(ExecutionDescription::commitSha))
        .env(executionDescription.env())
        .build();
  }
}
