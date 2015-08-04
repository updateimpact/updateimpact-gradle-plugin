/*
 * Copyright 2012 the original author or authors.
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

package com.updateimpact.gradle.graph

import com.updateimpact.report.DependencyChild
import com.updateimpact.report.DependencyId
import groovy.transform.Canonical
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency

class UpdateImpactDependencyGraphRenderer {
    private HashMap<DependencyId, List<DependencyChild>> resolvedDependencies = new HashMap<>()
    private DependencyId currentParent

    private final static String EVICTED_ARROW = " -> "

    UpdateImpactDependencyGraphRenderer(DependencyId parent) {
        this.currentParent = parent
        this.resolvedDependencies.put(parent, new ArrayList<>())
    }

    void render(RenderableDependency root) {
        def visited = new HashSet<ComponentIdentifier>()
        visited.add(root.getId())
        renderChildren(root.getChildren(), visited)
    }

    private void renderChildren(Set<? extends RenderableDependency> children, Set<ComponentIdentifier> visited) {
        for (RenderableDependency child : children) {
            doRender(child, visited)
        }
    }

    private void doRender(final RenderableDependency node, Set<ComponentIdentifier> visited) {
        def children = node.getChildren()
        def alreadyRendered = !visited.add(node.getId())

        DependencyWithEvicted dependencyWithEvicted = getDependencyId(node)
        resolvedDependencies.get(currentParent).add(
                new DependencyChild(dependencyWithEvicted.dependencyId, dependencyWithEvicted.evicted, alreadyRendered))

        if (!alreadyRendered) {
            DependencyId previousParent = currentParent
            currentParent = getDependencyId(node).dependencyId
            renderChildren(children, visited)
            currentParent = previousParent
        }
    }

    private DependencyWithEvicted getDependencyId(RenderableDependency node) {
        String[] dep = node.name.split(":")
        String evictedBy = null
        if (dep[2].contains(EVICTED_ARROW)) {
            evictedBy = dep[2].substring(dep[2].indexOf(EVICTED_ARROW) + EVICTED_ARROW.length())
            dep[2] = dep[2].substring(0, dep[2].indexOf(EVICTED_ARROW))
            // check if that wasn't a version-less dependency
            if (dep[2].empty) {
                dep[2] = evictedBy
                evictedBy = null
            }
        }
        DependencyId id = new DependencyId(dep[0], dep[1], dep[2], "jar", null)
        if (!resolvedDependencies.containsKey(id)) {
            resolvedDependencies.put(id, new ArrayList<DependencyChild>())
        }
        return new DependencyWithEvicted(id, evictedBy)
    }

    Map<DependencyId, List<DependencyChild>> getResolvedDependencies() {
        return resolvedDependencies
    }
}

@Canonical
class DependencyWithEvicted {
    DependencyId dependencyId
    String evicted
}
