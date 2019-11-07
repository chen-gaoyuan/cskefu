/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.handler.api;

import com.chatopera.cc.acd.AutomaticServiceDist;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.cache.Cache;
import com.chatopera.cc.handler.Handler;
import com.chatopera.cc.model.AgentStatus;
import com.chatopera.cc.model.SessionConfig;
import com.chatopera.cc.model.User;
import com.chatopera.cc.persistence.repository.AgentStatusRepository;
import com.chatopera.cc.persistence.repository.AgentUserRepository;
import com.chatopera.cc.persistence.repository.OrganRepository;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.RestResult;
import com.chatopera.cc.util.RestResultType;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.nio.charset.CharacterCodingException;
import java.util.Date;
import java.util.List;

/**
 * ACD服务
 * 获取队列统计信息
 */
@RestController
@RequestMapping("/api/servicequene")
public class ApiServiceQueneController extends Handler {

    @Autowired
    private AgentStatusRepository agentStatusRepository;


    @Autowired
    private AgentUserRepository agentUserRepository;

    @Autowired
    private OrganRepository organRes;


    @Autowired
    private Cache cache;

    /**
     * 获取队列统计信息，包含当前队列服务中的访客数，排队人数，坐席数
     *
     * @param request
     * @return
     */
    @RequestMapping(method = RequestMethod.GET)
    @Menu(type = "apps", subtype = "user", access = true)
    public ResponseEntity<RestResult> list(HttpServletRequest request) {
        return new ResponseEntity<>(
                new RestResult(RestResultType.OK, AutomaticServiceDist.getAgentReport(super.getOrgi(request))),
                HttpStatus.OK);
    }

    /**
     * 坐席状态操作，就绪、未就绪、忙
     *
     * @param request
     * @return
     */
    @RequestMapping(method = RequestMethod.PUT)
    @Menu(type = "apps", subtype = "user", access = true)
    public ResponseEntity<RestResult> agentStatus(
            HttpServletRequest request,
            @Valid String status) throws CharacterCodingException {
        User logined = super.getUser(request);
        AgentStatus agentStatus = null;
        if (StringUtils.isNotBlank(status) && status.equals(MainContext.AgentStatusEnum.READY.toString())) {
            List<AgentStatus> agentStatusList = agentStatusRepository.findByAgentnoAndOrgi(
                    logined.getId(), super.getOrgi(request));
            if (agentStatusList.size() > 0) {
                agentStatus = agentStatusList.get(0);
                agentStatus.setSkills(logined.getSkills());
            } else {
                agentStatus = new AgentStatus();
                agentStatus.setUserid(logined.getId());
                agentStatus.setUsername(logined.getUname());
                agentStatus.setAgentno(logined.getId());
                agentStatus.setLogindate(new Date());
                agentStatus.setSkills(logined.getSkills());

                SessionConfig sessionConfig = AutomaticServiceDist.initSessionConfig(super.getOrgi(request));

                agentStatus.setUsers(agentUserRepository.countByAgentnoAndStatusAndOrgi(
                        logined.getId(),
                        MainContext.AgentUserStatusEnum.INSERVICE.toString(),
                        super.getOrgi(request)));

                agentStatus.setUpdatetime(new Date());

                agentStatus.setOrgi(super.getOrgi(request));
                agentStatus.setMaxusers(sessionConfig.getMaxuser());
                agentStatusRepository.save(agentStatus);

            }
            if (agentStatus != null) {
                /**
                 * 更新当前用户状态
                 */
                agentStatus.setUsers(cache.getInservAgentUsersSizeByAgentnoAndOrgi(
                        agentStatus.getAgentno(),
                        super.getOrgi(request)));
                agentStatus.setStatus(MainContext.AgentStatusEnum.READY.toString());
                cache.putAgentStatusByOrgi(agentStatus, super.getOrgi(request));

                AutomaticServiceDist.recordAgentStatus(
                        agentStatus.getAgentno(), agentStatus.getUsername(), agentStatus.getAgentno(),
                        logined.isAdmin(), agentStatus.getAgentno(),
                        MainContext.AgentStatusEnum.OFFLINE.toString(), MainContext.AgentStatusEnum.READY.toString(),
                        MainContext.AgentWorkType.MEIDIACHAT.toString(), agentStatus.getOrgi(), null);
                AutomaticServiceDist.allotAgent(agentStatus.getAgentno(), super.getOrgi(request));
            }
        } else if (StringUtils.isNotBlank(status)) {
            if (status.equals(MainContext.AgentStatusEnum.NOTREADY.toString())) {
                List<AgentStatus> agentStatusList = agentStatusRepository.findByAgentnoAndOrgi(
                        logined.getId(), super.getOrgi(request));
                for (AgentStatus temp : agentStatusList) {
                    AutomaticServiceDist.recordAgentStatus(
                            temp.getAgentno(), temp.getUsername(), temp.getAgentno(),
                            logined.isAdmin(),
                            temp.getAgentno(),
                            temp.isBusy() ? MainContext.AgentStatusEnum.BUSY.toString() : MainContext.AgentStatusEnum.READY.toString(),
                            MainContext.AgentStatusEnum.NOTREADY.toString(),
                            MainContext.AgentWorkType.MEIDIACHAT.toString(), temp.getOrgi(), temp.getUpdatetime());
                    agentStatusRepository.delete(temp);
                }
                cache.deleteAgentStatusByAgentnoAndOrgi(super.getUser(request).getId(), super.getOrgi(request));
            } else if (StringUtils.isNotBlank(status) && status.equals(MainContext.AgentStatusEnum.BUSY.toString())) {
                List<AgentStatus> agentStatusList = agentStatusRepository.findByAgentnoAndOrgi(
                        logined.getId(), super.getOrgi(request));
                if (agentStatusList.size() > 0) {
                    agentStatus = agentStatusList.get(0);
                    agentStatus.setBusy(true);
                    AutomaticServiceDist.recordAgentStatus(
                            agentStatus.getAgentno(), agentStatus.getUsername(), agentStatus.getAgentno(),
                            logined.isAdmin(), agentStatus.getAgentno(),
                            MainContext.AgentStatusEnum.READY.toString(), MainContext.AgentStatusEnum.BUSY.toString(),
                            MainContext.AgentWorkType.MEIDIACHAT.toString(), agentStatus.getOrgi(),
                            agentStatus.getUpdatetime());
                    agentStatus.setUpdatetime(new Date());

                    agentStatusRepository.save(agentStatus);
                    cache.putAgentStatusByOrgi(agentStatus, super.getOrgi(request));
                }
            } else if (StringUtils.isNotBlank(status) && status.equals(
                    MainContext.AgentStatusEnum.NOTBUSY.toString())) {
                List<AgentStatus> agentStatusList = agentStatusRepository.findByAgentnoAndOrgi(
                        logined.getId(), super.getOrgi(request));
                if (agentStatusList.size() > 0) {
                    agentStatus = agentStatusList.get(0);
                    agentStatus.setBusy(false);
                    AutomaticServiceDist.recordAgentStatus(
                            agentStatus.getAgentno(), agentStatus.getUsername(), agentStatus.getAgentno(),
                            logined.isAdmin(), agentStatus.getAgentno(),
                            MainContext.AgentStatusEnum.BUSY.toString(), MainContext.AgentStatusEnum.READY.toString(),
                            MainContext.AgentWorkType.MEIDIACHAT.toString(), agentStatus.getOrgi(),
                            agentStatus.getUpdatetime());

                    agentStatus.setUpdatetime(new Date());
                    agentStatusRepository.save(agentStatus);
                    cache.putAgentStatusByOrgi(agentStatus, super.getOrgi(request));
                }
                AutomaticServiceDist.allotAgent(agentStatus.getAgentno(), super.getOrgi(request));
            }
            AutomaticServiceDist.broadcastAgentsStatus(
                    super.getOrgi(request), "agent", "api", super.getUser(request).getId());
        }
        return new ResponseEntity<>(new RestResult(RestResultType.OK, agentStatus), HttpStatus.OK);
    }
}