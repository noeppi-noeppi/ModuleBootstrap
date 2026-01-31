package bootstrap.gradle;

import bootstrap.api.LauncherConstants;
import groovy.lang.GroovyObjectSupport;
import groovy.transform.Internal;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;

@NotNullByDefault
public class BootstrapExtension extends GroovyObjectSupport {

    private final Project project;
    private final ProviderFactory providerFactory;
    
    private final Property<String> applicationName;
    private final Property<String> mainModule;
    private final Property<String> entrypoint;
    private final Property<Configuration> bootModules;
    private final Property<Configuration> bootstrapClasspath;
    private final ListProperty<String> jvmArgs;
    private final List<Action<CopySpec>> resources;
    private final ListProperty<Provider<? extends Task>> dependencies;

    @SuppressWarnings("unchecked")
    public BootstrapExtension(Project project, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.project = project;
        this.providerFactory = providerFactory;
        this.applicationName = objectFactory.property(String.class).convention(project.provider(project::getName));
        this.mainModule = objectFactory.property(String.class).convention(LauncherConstants.MODULE_LAUNCHER);
        this.entrypoint = objectFactory.property(String.class);
        this.bootModules = objectFactory.property(Configuration.class);
        this.bootstrapClasspath = objectFactory.property(Configuration.class);
        this.jvmArgs = objectFactory.listProperty(String.class);
        this.jvmArgs.set(providerFactory.provider(() -> List.of(
                "--add-opens", "java.base/java.lang.invoke=" + LauncherConstants.MODULE_JAR
        )));
        this.resources = new ArrayList<>();
        this.dependencies = objectFactory.listProperty((Class<Provider<? extends Task>>) (Class<?>) Provider.class);
    }

    public Property<String> getApplicationName() {
        return this.applicationName;
    }

    public Property<String> getMainModule() {
        return this.mainModule;
    }

    public Property<String> getEntrypoint() {
        return this.entrypoint;
    }

    public Property<Configuration> getBootModules() {
        return this.bootModules;
    }

    public Property<Configuration> getBootstrapClasspath() {
        return this.bootstrapClasspath;
    }

    public ListProperty<String> getJvmArgs() {
        return this.jvmArgs;
    }
    
    public void copy(Action<CopySpec> action) {
        this.resources.add(action);
    }

    public ListProperty<Provider<? extends Task>> getDependencies() {
        return this.dependencies;
    }

    public void builtBy(Task task) {
        this.dependencies.add(this.providerFactory.provider(() -> task));
    }
    
    public void builtBy(TaskProvider<?> task) {
        this.dependencies.add(task);
    }
    
    @Internal
    public void configureCopySpec(CopySpec copySpec) {
        for (Action<CopySpec> action : this.resources) {
            CopySpec spec = this.project.copySpec();
            action.execute(spec);
            copySpec.with(spec);
        }
    }
}
