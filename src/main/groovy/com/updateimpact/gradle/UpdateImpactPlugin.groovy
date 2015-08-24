package com.updateimpact.gradle

import com.google.gson.Gson
import com.updateimpact.gradle.graph.DependencyWithEvicted
import com.updateimpact.gradle.graph.UpdateImpactDependencyGraphRenderer

import com.updateimpact.report.*
import groovy.util.logging.Slf4j
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult

import java.awt.*
import java.util.List

@Slf4j
class UpdateImpactPlugin implements Plugin<Project>{

    public static final String SUBMIT_TASK_NAME = 'updateImpactSubmit'

    public static final String FILE_TASK_NAME = 'updateImpactToFile'

    @Override
    void apply(Project project) {
        project.extensions.create(SUBMIT_TASK_NAME, UpdateimpactPluginExtension)
        UpdateimpactPluginExtension updateimpactPluginExtension = project.extensions.updateImpactSubmit
        log.info(System.getProperties().collect{it.key + " = " + it.value}.join("\n"))

        Task submitTask = project.task(SUBMIT_TASK_NAME) << { Task task ->
            DependencyReport report = createDependencyReport(project, updateimpactPluginExtension)

            SubmitLogger submitLogger = new SubmitLogger() {
                public void info(String message) { log.info(message); }
                public void error(String message) { log.error(message); }
            };

            String link = new ReportSubmitter(updateimpactPluginExtension.url, submitLogger).trySubmitReport(report);
            if (link != null) {
                if (updateimpactPluginExtension.openBrowser) {
                    log.info("Trying to open the report in the default browser ... " +
                            "(you can disable this by setting the updateimpact.openbrowser property to false)");
                    openWebpage(link);
                }
            }
        }
        submitTask.group = 'Dependencies'
        submitTask.description = 'Analyze your dependencies at http://updateimpact.com'

        Task createFileTask = project.task(FILE_TASK_NAME) << { Task task ->
            DependencyReport report = createDependencyReport(project, updateimpactPluginExtension)

            new File('updateimpact.json').withPrintWriter { it.append(new Gson().toJson(report)) }
        }
        createFileTask.group = 'Dependencies'
        createFileTask.description = 'Create updateimpact.json report for http://updateimpact.com [for debugging]'
    }

    private DependencyReport createDependencyReport(Project project, UpdateimpactPluginExtension updateimpactPluginExtension) {
        List<ModuleDependencies> deps = project.configurations.collect { Configuration c ->
            UpdateImpactDependencyGraphRenderer renderer = new UpdateImpactDependencyGraphRenderer(getDependencyId(project))

            ResolutionResult result = c.getIncoming().getResolutionResult()
            RenderableDependency root = new RenderableModuleResult(result.getRoot())
            renderer.render(root)

            toModuleDependencies(getDependencyId(project), c.name, renderer.getResolvedDependencies())
        }.findAll {it.dependencies.size() > 1}

        return createDependencyReport(project, updateimpactPluginExtension.apiKey, deps)
    }

    private ModuleDependencies toModuleDependencies(DependencyId parent, String config, Map<DependencyWithEvicted, List<DependencyId>> deps) {
        return new ModuleDependencies(parent, config, deps.collect {
            Map.Entry<DependencyWithEvicted, List<DependencyId>> e -> new Dependency(e.key.dependencyId, e.key.evicted, false, e.value)
        })
    }

    private DependencyReport createDependencyReport(Project project, String apiKey, List<ModuleDependencies> moduleDependencies) {
        return new DependencyReport(
                project.name,
                apiKey,
                buildId(),
                moduleDependencies,
                Collections.emptyList(),
                "1.0",
                "gradle-plugin-1.1.4"
        )
    }

    private DependencyId getDependencyId(Project project) {
        return new DependencyId(project.group.toString(), project.name, project.version.toString(), "jar", null)
    }

    private String buildId() {
        return UUID.randomUUID().toString()
    }

    private void openWebpage(String url) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null
        if (desktop && desktop.isSupported(Desktop.Action.BROWSE))
            desktop.browse(url.toURI())
    }
}
