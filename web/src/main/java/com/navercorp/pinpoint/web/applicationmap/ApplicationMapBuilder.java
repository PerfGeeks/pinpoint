/*
 * Copyright 2014 NAVER Corp.
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

package com.navercorp.pinpoint.web.applicationmap;

import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.AgentLifeCycleState;
import com.navercorp.pinpoint.web.applicationmap.histogram.*;
import com.navercorp.pinpoint.web.applicationmap.rawdata.*;
import com.navercorp.pinpoint.web.dao.MapResponseDao;
import com.navercorp.pinpoint.web.service.AgentInfoService;
import com.navercorp.pinpoint.web.vo.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author emeroad
 * @author minwoo.jung
 * @author HyunGil Jeong
 */
public class ApplicationMapBuilder {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Range range;

    public ApplicationMapBuilder(Range range) {
        if (range == null) {
            throw new NullPointerException("range must not be null");
        }

        this.range = range;
    }
    
    /**
     * Returns an application map with a single node containing the application's agents that were running.
     */
    public ApplicationMap build(Application application, AgentInfoService agentInfoService) {
        NodeList nodeList = new NodeList();
        LinkList emptyLinkList = new LinkList();
        
        Node node = new Node(application);
        Set<AgentInfo> agentInfos = agentInfoService.getAgentsByApplicationName(application.getName(), range.getTo());
        for (Iterator<AgentInfo> iterator = agentInfos.iterator(); iterator.hasNext();) {
            AgentInfo agentInfo = iterator.next();
            if (!isAgentRunning(agentInfo)) {
                iterator.remove();
            }
        }
        if (agentInfos.isEmpty()) {
            return new ApplicationMap(range, nodeList, emptyLinkList);
        } else {
            ServerBuilder serverBuilder = new ServerBuilder();
            serverBuilder.addAgentInfo(agentInfos);
            ServerInstanceList serverInstanceList = serverBuilder.build();
            node.setServerInstanceList(serverInstanceList);
            node.setNodeHistogram(new NodeHistogram(application, range));
            nodeList.addNode(node);
            return new ApplicationMap(range, nodeList, emptyLinkList);
        }
    }

    public ApplicationMap build(LinkDataDuplexMap linkDataDuplexMap, AgentInfoService agentInfoService,
            NodeHistogramDataSource nodeHistogramDataSource) {
        if (linkDataDuplexMap == null) {
            throw new NullPointerException("linkDataMap must not be null");
        }
        if (agentInfoService == null) {
            throw new NullPointerException("agentInfoService must not be null");
        }

        NodeList nodeList = buildNode(linkDataDuplexMap);
        LinkList linkList = buildLink(nodeList, linkDataDuplexMap);

        appendNodeResponseTime(nodeList, linkList, nodeHistogramDataSource);
        appendAgentInfo(nodeList, linkDataDuplexMap, agentInfoService);

        final ApplicationMap map = new ApplicationMap(range, nodeList, linkList);
        return map;
    }

    public ApplicationMap build(LinkDataDuplexMap linkDataDuplexMap, AgentInfoService agentInfoService,
            final MapResponseDao mapResponseDao) {
        NodeHistogramDataSource responseSource = new NodeHistogramDataSource() {
            @Override
            public NodeHistogram createNodeHistogram(Application application) {
                final List<ResponseTime> responseHistogram = mapResponseDao.selectResponseTime(application, range);
                final NodeHistogram nodeHistogram = new NodeHistogram(application, range, responseHistogram);
                return nodeHistogram;
            }
        };
        return this.build(linkDataDuplexMap, agentInfoService, responseSource);
    }

    public ApplicationMap build(LinkDataDuplexMap linkDataDuplexMap, AgentInfoService agentInfoService,
            final ResponseHistogramBuilder mapHistogramSummary) {
        NodeHistogramDataSource responseSource = new NodeHistogramDataSource() {
            @Override
            public NodeHistogram createNodeHistogram(Application application) {
                List<ResponseTime> responseHistogram = mapHistogramSummary.getResponseTimeList(application);
                final NodeHistogram nodeHistogram = new NodeHistogram(application, range, responseHistogram);
                return nodeHistogram;
            }
        };
        return this.build(linkDataDuplexMap, agentInfoService, responseSource);
    }

    public interface NodeHistogramDataSource {
        NodeHistogram createNodeHistogram(Application application);
    }

    private NodeList buildNode(LinkDataDuplexMap linkDataDuplexMap) {
        NodeList nodeList = new NodeList();
        createNode(nodeList, linkDataDuplexMap.getSourceLinkDataMap());
        logger.debug("node size:{}", nodeList.size());
        createNode(nodeList, linkDataDuplexMap.getTargetLinkDataMap());
        logger.debug("node size:{}", nodeList.size());

        logger.debug("allNode:{}", nodeList.getNodeList());
        return nodeList;
    }

    private void createNode(NodeList nodeList, LinkDataMap linkDataMap) {

        for (LinkData linkData : linkDataMap.getLinkDataList()) {
            final Application fromApplication = linkData.getFromApplication();
            // FROM is either a CLIENT or a node
            // cannot be RPC. Already converted to unknown.
            if (!fromApplication.getServiceType().isRpcClient()) {
                final boolean success = addNode(nodeList, fromApplication);
                if (success) {
                    logger.debug("createSourceNode:{}", fromApplication);
                }
            } else {
                logger.warn("found rpc fromNode linkData:{}", linkData);
            }

            final Application toApplication = linkData.getToApplication();
            // FROM -> TO : TO is either a CLIENT or a node
            if (!toApplication.getServiceType().isRpcClient()) {
                final boolean success = addNode(nodeList, toApplication);
                if (success) {
                    logger.debug("createTargetNode:{}", toApplication);
                }
            } else {
                logger.warn("found rpc toNode:{}", linkData);
            }
        }

    }

    private boolean addNode(NodeList nodeList, Application application) {
        if (nodeList.containsNode(application)) {
            return false;
        }

        Node fromNode = new Node(application);
        return nodeList.addNode(fromNode);
    }

    private LinkList buildLink(NodeList nodeList, LinkDataDuplexMap linkDataDuplexMap) {
        // don't change
        LinkList linkList = new LinkList();
        createSourceLink(nodeList, linkList, linkDataDuplexMap.getSourceLinkDataMap());
        logger.debug("link size:{}", linkList.size());
        createTargetLink(nodeList, linkList, linkDataDuplexMap.getTargetLinkDataMap());
        logger.debug("link size:{}", linkList.size());

        for (Link link : linkList.getLinkList()) {
            appendLinkHistogram(link, linkDataDuplexMap);
        }
        return linkList;
    }

    private void appendLinkHistogram(Link link, LinkDataDuplexMap linkDataDuplexMap) {
        logger.debug("appendLinkHistogram link:{}", link);

        LinkKey key = link.getLinkKey();
        LinkData sourceLinkData = linkDataDuplexMap.getSourceLinkData(key);
        if (sourceLinkData != null) {
            link.addSource(sourceLinkData.getLinkCallDataMap());
        }
        LinkData targetLinkData = linkDataDuplexMap.getTargetLinkData(key);
        if (targetLinkData != null) {
            link.addTarget(targetLinkData.getLinkCallDataMap());
        }
    }

    private void createSourceLink(NodeList nodeList, LinkList linkList, LinkDataMap linkDataMap) {

        for (LinkData linkData : linkDataMap.getLinkDataList()) {
            final Application fromApplicationId = linkData.getFromApplication();
            Node fromNode = nodeList.findNode(fromApplicationId);

            final Application toApplicationId = linkData.getToApplication();
            Node toNode = nodeList.findNode(toApplicationId);

            // rpc client missing
            if (toNode == null) {
                logger.warn("toNode rcp client not found:{}", toApplicationId);
                continue;
            }

            // for RPC clients: skip if there is a dest application, convert to "unknown cloud" if not
            // shouldn't really be necessary as rpc client toNodes are converted to unknown nodes beforehand.
            if (toNode.getServiceType().isRpcClient()) {
                if (!nodeList.containsNode(toNode.getApplication())) {
                    final Link link = addLink(linkList, fromNode, toNode, CreateType.Source);
                    if (link != null) {
                        logger.debug("createRpcSourceLink:{}", link);
                    }
                }
            } else {
                final Link link = addLink(linkList, fromNode, toNode, CreateType.Source);
                if (link != null) {
                    logger.debug("createSourceLink:{}", link);
                }
            }
        }
    }

    private Link addLink(LinkList linkList, Node fromNode, Node toNode, CreateType createType) {
        final Link link = new Link(createType, fromNode, toNode, range);
        if (linkList.addLink(link)) {
            return link;
        } else {
            return null;
        }
    }

    private void createTargetLink(NodeList nodeList, LinkList linkList, LinkDataMap linkDataMap) {

        for (LinkData linkData : linkDataMap.getLinkDataList()) {
            final Application fromApplicationId = linkData.getFromApplication();
            Node fromNode = nodeList.findNode(fromApplicationId);

            final Application toApplicationId = linkData.getToApplication();
            Node toNode = nodeList.findNode(toApplicationId);

            // rpc client missing
            if (fromNode == null) {
                logger.warn("fromNode rcp client not found:{}", toApplicationId);
                continue;
            }

            // for RPC clients: skip if there is a dest application, convert to "unknown cloud" if not
            if (toNode.getServiceType().isRpcClient()) {
                // check if "to" node exists
                if (!nodeList.containsNode(toNode.getApplication())) {
                    final Link link = addLink(linkList, fromNode, toNode, CreateType.Target);
                    if (link != null) {
                        logger.debug("createRpcTargetLink:{}", link);
                    }
                }
            } else {
                final Link link = addLink(linkList, fromNode, toNode, CreateType.Target);
                if (link != null) {
                    logger.debug("createTargetLink:{}", link);
                }
            }
        }
    }

    public void appendNodeResponseTime(NodeList nodeList, LinkList linkList,
            NodeHistogramDataSource nodeHistogramDataSource) {
        if (nodeHistogramDataSource == null) {
            throw new NullPointerException("nodeHistogramDataSource must not be null");
        }

        final Collection<Node> nodes = nodeList.getNodeList();
        for (Node node : nodes) {
            final ServiceType nodeType = node.getServiceType();
            if (nodeType.isWas()) {
                // for WAS nodes, set their own response time histogram
                final Application wasNode = node.getApplication();
                final NodeHistogram nodeHistogram = nodeHistogramDataSource.createNodeHistogram(wasNode);
                node.setNodeHistogram(nodeHistogram);

            } else if (nodeType.isTerminal() || nodeType.isUnknown()) {
                final NodeHistogram nodeHistogram = createTerminalNodeHistogram(node, linkList);
                node.setNodeHistogram(nodeHistogram);
            } else if (nodeType.isUser()) {
                // for User nodes, find its source link and create the histogram
                Application userNode = node.getApplication();

                final NodeHistogram nodeHistogram = new NodeHistogram(userNode, range);
                final List<Link> fromLink = linkList.findFromLink(userNode);
                if (fromLink.size() > 1) {
                    logger.warn("Invalid from UserNode:{}", linkList);
                    throw new IllegalArgumentException("Invalid from UserNode.size() :" + fromLink.size());
                } else if (fromLink.size() == 0) {
                    logger.warn("from UserNode not found:{}", userNode);
                    continue;
                }
                final Link sourceLink = fromLink.get(0);
                nodeHistogram.setApplicationHistogram(sourceLink.getHistogram());

                ApplicationTimeHistogram histogramData = sourceLink.getTargetApplicationTimeSeriesHistogramData();
                nodeHistogram.setApplicationTimeHistogram(histogramData);

                node.setNodeHistogram(nodeHistogram);
            } else {
                // dummy data
                NodeHistogram dummy = new NodeHistogram(node.getApplication(), range);
                node.setNodeHistogram(dummy);
            }

        }

    }

    private NodeHistogram createTerminalNodeHistogram(Node node, LinkList linkList) {
        // for Terminal nodes, add all links pointing to iself and create the histogram
        final Application nodeApplication = node.getApplication();
        final NodeHistogram nodeHistogram = new NodeHistogram(nodeApplication, range);

        // create applicationHistogram
        final List<Link> toLinkList = linkList.findToLink(nodeApplication);
        final Histogram applicationHistogram = new Histogram(node.getServiceType());
        for (Link link : toLinkList) {
            applicationHistogram.add(link.getHistogram());
        }
        nodeHistogram.setApplicationHistogram(applicationHistogram);

        // create applicationTimeHistogram
        LinkCallDataMap linkCallDataMap = new LinkCallDataMap();
        for (Link link : toLinkList) {
            LinkCallDataMap sourceLinkCallDataMap = link.getSourceLinkCallDataMap();
            linkCallDataMap.addLinkDataMap(sourceLinkCallDataMap);
        }
        ApplicationTimeHistogramBuilder builder = new ApplicationTimeHistogramBuilder(nodeApplication, range);
        ApplicationTimeHistogram applicationTimeHistogram = builder.build(linkCallDataMap.getLinkDataList());
        nodeHistogram.setApplicationTimeHistogram(applicationTimeHistogram);

        // for Terminal nodes, create AgentLevel histogram
        if (nodeApplication.getServiceType().isTerminal()) {
            final Map<String, Histogram> agentHistogramMap = new HashMap<String, Histogram>();

            for (Link link : toLinkList) {
                LinkCallDataMap sourceLinkCallDataMap = link.getSourceLinkCallDataMap();
                AgentHistogramList targetList = sourceLinkCallDataMap.getTargetList();
                for (AgentHistogram histogram : targetList.getAgentHistogramList()) {
                    Histogram find = agentHistogramMap.get(histogram.getId());
                    if (find == null) {
                        find = new Histogram(histogram.getServiceType());
                        agentHistogramMap.put(histogram.getId(), find);
                    }
                    find.add(histogram.getHistogram());
                }
                nodeHistogram.setAgentHistogramMap(agentHistogramMap);
            }
        }

        LinkCallDataMap mergeSource = new LinkCallDataMap();
        for (Link link : toLinkList) {
            LinkCallDataMap sourceLinkCallDataMap = link.getSourceLinkCallDataMap();
            mergeSource.addLinkDataMap(sourceLinkCallDataMap);
        }

        AgentTimeHistogramBuilder agentTimeBuilder = new AgentTimeHistogramBuilder(nodeApplication, range);
        AgentTimeHistogram agentTimeHistogram = agentTimeBuilder.buildTarget(mergeSource);
        nodeHistogram.setAgentTimeHistogram(agentTimeHistogram);

        return nodeHistogram;
    }

    public void appendAgentInfo(NodeList nodeList, LinkDataDuplexMap linkDataDuplexMap,
            AgentInfoService agentInfoService) {
        for (Node node : nodeList.getNodeList()) {
            appendServerInfo(node, linkDataDuplexMap, agentInfoService);
        }

    }

    private void appendServerInfo(Node node, LinkDataDuplexMap linkDataDuplexMap, AgentInfoService agentInfoService) {
        final ServiceType nodeServiceType = node.getServiceType();
        if (nodeServiceType.isUnknown()) {
            // we do not know the server info for unknown nodes
            return;
        }

        if (nodeServiceType.isTerminal()) {
            // extract information about the terminal node
            ServerBuilder builder = new ServerBuilder();
            for (LinkData linkData : linkDataDuplexMap.getSourceLinkDataList()) {
                Application toApplication = linkData.getToApplication();
                if (node.getApplication().equals(toApplication)) {
                    builder.addCallHistogramList(linkData.getTargetList());
                }
            }
            ServerInstanceList serverInstanceList = builder.build();
            node.setServerInstanceList(serverInstanceList);
        } else if (nodeServiceType.isWas()) {
            Set<AgentInfo> agentList = agentInfoService.getAgentsByApplicationName(node.getApplication().getName(),
                    range.getTo());
            if (agentList.isEmpty()) {
                logger.warn("agentInfo not found. applicationName:{}", node.getApplication());
                // avoid NPE
                node.setServerInstanceList(new ServerInstanceList());
                return;
            }
            logger.debug("add agentInfo. {}, {}", node.getApplication(), agentList);
            ServerBuilder builder = new ServerBuilder();
            agentList = filterAgentInfoByResponseData(agentList, node);
            builder.addAgentInfo(agentList);
            ServerInstanceList serverInstanceList = builder.build();

            // agentSet exists if the destination is a WAS, and has agent installed
            node.setServerInstanceList(serverInstanceList);
        } else {
            // add empty information
            node.setServerInstanceList(new ServerInstanceList());
        }

    }
    
    /**
     * Filters AgentInfo by whether they actually have response data.
     * For agents that do not have response data, check their status and include those that were alive.
     */
    private Set<AgentInfo> filterAgentInfoByResponseData(Set<AgentInfo> agentList, Node node) {
        Set<AgentInfo> filteredAgentInfo = new HashSet<AgentInfo>();

        NodeHistogram nodeHistogram = node.getNodeHistogram();
        Map<String, Histogram> agentHistogramMap = nodeHistogram.getAgentHistogramMap();
        for (AgentInfo agentInfo : agentList) {
            String agentId = agentInfo.getAgentId();
            if (agentHistogramMap.containsKey(agentId)) {
                filteredAgentInfo.add(agentInfo);
            } else {
                if (isAgentRunning(agentInfo)) {
                    filteredAgentInfo.add(agentInfo);
                }
            }
        }

        return filteredAgentInfo;
    }
    
    private boolean isAgentRunning(AgentInfo agentInfo) {
        return agentInfo.getStatus().getState() == AgentLifeCycleState.RUNNING;
    }
    
    

}
