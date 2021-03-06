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

package com.flow.platform.plugin.domain;

import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Set;

/**
 * @author yh@firim
 */
public class Plugin implements Comparable {

    public final static Set<PluginStatus> RUNNING_AND_FINISH_STATUS =
        ImmutableSet.of(PluginStatus.INSTALLING, PluginStatus.INSTALLED);

    public final static Set<PluginStatus> FINISH_STATUSES = ImmutableSet.of(PluginStatus.INSTALLED);

    private volatile Boolean isStopped = false;

    //plugin name
    @Expose
    private String name;

    // plugin git url
    @Expose
    private String source;

    // plugin labels
    @Expose
    private Set<String> labels;

    // plugin author
    @Expose
    private String author;

    // plugin support platform
    @Expose
    private Set<String> platform;

    // plugin status
    @Expose
    private PluginStatus status = PluginStatus.PENDING;

    // latest tag
    @Expose
    private String tag;

    // current used Tag
    @Expose
    private String currentTag;

    // if install error, the reason is error trace
    @Expose
    @SerializedName("error")
    private String reason;

    @Expose
    private String description;

    @Expose
    private String latestCommit;

    @Expose
    @SerializedName("detail")
    private PluginDetail pluginDetail;

    public Plugin(String name, String source, Set<String> label, String author, Set<String> platform) {
        this.name = name;
        this.source = source;
        this.labels = label;
        this.author = author;
        this.platform = platform;
    }

    public Plugin(String name, String source, Set<String> labels, String author,
                  Set<String> platform, PluginStatus status, String tag) {
        this.name = name;
        this.source = source;
        this.labels = labels;
        this.author = author;
        this.platform = platform;
        this.status = status;
        this.tag = tag;
    }

    public Plugin() {
        this.isStopped = false;
        this.status = PluginStatus.PENDING;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Set<String> getPlatform() {
        return platform;
    }

    public PluginStatus getStatus() {
        return status;
    }

    public void setStatus(PluginStatus status) {
        this.status = status;
    }

    public void setPlatform(Set<String> platform) {
        this.platform = platform;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Boolean getStopped() {
        return isStopped;
    }

    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }

    public PluginDetail getPluginDetail() {
        return pluginDetail;
    }

    public void setPluginDetail(PluginDetail pluginDetail) {
        this.pluginDetail = pluginDetail;
    }

    public String getCurrentTag() {
        return currentTag;
    }

    public void setCurrentTag(String currentTag) {
        this.currentTag = currentTag;
    }

    public String getLatestCommit() {
        return latestCommit;
    }

    public void setLatestCommit(String latestCommit) {
        this.latestCommit = latestCommit;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Plugin plugin = (Plugin) o;

        return name != null ? name.equals(plugin.name) : plugin.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Plugin{" +
            "name='" + name + '\'' +
            ", source='" + source + '\'' +
            ", labels=" + labels +
            ", author='" + author + '\'' +
            ", platform='" + platform + '\'' +
            '}';
    }
}
