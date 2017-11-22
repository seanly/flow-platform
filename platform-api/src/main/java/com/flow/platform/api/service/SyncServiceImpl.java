/*
 * Copyright 2017 flow.ci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flow.platform.api.service;

import com.flow.platform.api.domain.sync.SyncEvent;
import com.flow.platform.api.domain.sync.SyncType;
import com.flow.platform.api.service.job.CmdService;
import com.flow.platform.core.queue.PriorityMessage;
import com.flow.platform.domain.AgentPath;
import com.flow.platform.domain.Cmd;
import com.flow.platform.domain.CmdInfo;
import com.flow.platform.domain.CmdResult;
import com.flow.platform.domain.CmdStatus;
import com.flow.platform.domain.CmdType;
import com.flow.platform.queue.PlatformQueue;
import com.flow.platform.util.Logger;
import com.flow.platform.util.git.GitException;
import com.flow.platform.util.git.JGitUtil;
import com.flow.platform.util.http.HttpURL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.PostConstruct;
import org.eclipse.jgit.lib.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author yang
 */

@Service
public class SyncServiceImpl implements SyncService {

    private final static Logger LOGGER = new Logger(SyncService.class);

    private final Map<AgentPath, PlatformQueue<PriorityMessage>> syncQueue = new ConcurrentHashMap<>();

    private final Map<AgentPath, Queue<SyncEvent>> syncTask = new ConcurrentHashMap<>();

    @Autowired
    private QueueCreator syncQueueCreator;

    @Autowired
    private GitService gitService;

    @Autowired
    private CmdService cmdService;

    @Value("${domain.api}")
    private String apiDomain;

    private String callbackUrl;

    @PostConstruct
    private void init() {
        callbackUrl = HttpURL.build(apiDomain).append("/hooks/sync").toString();
    }

    @Override
    public void put(SyncEvent event) {
        for (PlatformQueue<PriorityMessage> agentQueue : syncQueue.values()) {
            agentQueue.enqueue(PriorityMessage.create(event.toBytes(), DEFAULT_SYNC_QUEUE_PRIORITY));
        }
    }

    @Override
    public PlatformQueue<PriorityMessage> getSyncQueue(AgentPath agent) {
        return syncQueue.get(agent);
    }

    @Override
    public Queue<SyncEvent> getSyncTask(AgentPath agent) {
        return syncTask.get(agent);
    }

    @Override
    public void register(AgentPath agent) {
        if (syncQueue.containsKey(agent)) {
            return;
        }

        PlatformQueue<PriorityMessage> queue = syncQueueCreator.create(agent.toString() + "-sync");
        syncQueue.put(agent, queue);

        // init sync event from git
        List<SyncEvent> syncEvents = initSyncEventFromGitWorkspace();
        for (SyncEvent event : syncEvents) {
            queue.enqueue(PriorityMessage.create(event.toBytes(), DEFAULT_SYNC_QUEUE_PRIORITY));
        }
    }

    @Override
    public void remove(AgentPath agent) {
        syncQueue.remove(agent);
    }

    @Override
    public void clean() {
        syncQueue.clear();
    }

    @Override
    public void onCallback(Cmd cmd) {
        Queue<SyncEvent> syncEventQueue = syncTask.get(cmd.getAgentPath());
        SyncEvent next = null;

        if (cmd.getType() == CmdType.CREATE_SESSION) {
            if (cmd.getStatus() == CmdStatus.SENT) {
                // get next sync event but not remove
                next = syncEventQueue.peek();
            }
        }

        else if (cmd.getType() == CmdType.RUN_SHELL) {
            if (Cmd.FINISH_STATUS.contains(cmd.getStatus())) {
                CmdResult result = cmd.getCmdResult();
                if (result != null && result.getExitValue() != null) {
                    SyncEvent current = syncEventQueue.peek();

                    // the task not successfully executed so put to sync queue again
                    if (result.getExitValue() != 0) {
                        syncQueue.get(cmd.getAgentPath())
                            .enqueue(PriorityMessage.create(current.toBytes(), DEFAULT_SYNC_QUEUE_PRIORITY));
                    }

                    // remove current sync event
                    syncEventQueue.remove();

                    // set next node whatever the result
                    next = syncEventQueue.peek();
                }
            }
        }

        else if (cmd.getType() == CmdType.DELETE_SESSION) {
            LOGGER.trace("Sync task finished for agent " + cmd.getAgentPath());
        }

        // run next sync event
        if (next != null) {
            CmdInfo runShell = new CmdInfo(cmd.getAgentPath(), CmdType.RUN_SHELL, next.toScript());
            runShell.setWebhook(callbackUrl);
            runShell.setSessionId(cmd.getSessionId());
            cmdService.sendCmd(runShell, false);
        }

        // delete session when queue is empty
        if (next == null && syncEventQueue.isEmpty()) {
            CmdInfo deleteSession = new CmdInfo(cmd.getAgentPath(), CmdType.DELETE_SESSION, null);
            deleteSession.setWebhook(callbackUrl);
            deleteSession.setSessionId(cmd.getSessionId());
            cmdService.sendCmd(deleteSession, false);
        }
    }

    @Override
    @Scheduled(fixedDelay = 60 * 1000 * 30, initialDelay = 60 * 1000)
    public void syncTask() {
        for (AgentPath agentPath : syncQueue.keySet()) {
            startSync(agentPath);
        }
    }

    private void startSync(AgentPath agentPath) {
        PlatformQueue<PriorityMessage> queue = syncQueue.get(agentPath);

        if (agentPath == null || queue.size() == 0) {
            return;
        }

        // create queue for agent task
        Queue<SyncEvent> syncEventQueue = buildSyncEventQueueForTask(queue);
        syncTask.put(agentPath, syncEventQueue);

        // create cmd to create sync session, the extra field record node path
        try {
            CmdInfo cmdInfo = new CmdInfo(agentPath, CmdType.CREATE_SESSION, null);
            cmdInfo.setWebhook(callbackUrl);
            cmdService.sendCmd(cmdInfo, true);
            LOGGER.trace("Start sync '%s' git repo to agent '%s'", syncEventQueue.size(), agentPath);
        } catch (Throwable e) {
            LOGGER.warn(e.getMessage());
        }
    }

    private Queue<SyncEvent> buildSyncEventQueueForTask(PlatformQueue<PriorityMessage> agentSyncQueue) {
        Queue<SyncEvent> syncEventQueue = new ConcurrentLinkedQueue<>();

        PriorityMessage message;
        while ((message = agentSyncQueue.dequeue()) != null) {
            SyncEvent event = SyncEvent.parse(message.getBody(), SyncEvent.class);
            syncEventQueue.add(event);
        }

        return syncEventQueue;
    }

    private List<SyncEvent> initSyncEventFromGitWorkspace() {
        List<Repository> repos = gitService.repos();
        List<SyncEvent> syncEvents = new ArrayList<>(repos.size());

        for (Repository repo : repos) {
            try {
                List<String> tags = JGitUtil.tags(repo);
                String gitRepoName = repo.getDirectory().getName();

                // git repo needs tags
                if (tags.isEmpty()) {
                    LOGGER.warn("Git repo '%s' cannot be synced since missing tag", gitRepoName);
                    continue;
                }

                HttpURL gitURL = HttpURL.build(apiDomain).append("git").append(gitRepoName);
                syncEvents.add(new SyncEvent(gitURL.toString(), tags.get(0), SyncType.CREATE));
            } catch (GitException e) {
                LOGGER.warn(e.getMessage());
            } finally {
                repo.close();
            }
        }

        return syncEvents;
    }
}
