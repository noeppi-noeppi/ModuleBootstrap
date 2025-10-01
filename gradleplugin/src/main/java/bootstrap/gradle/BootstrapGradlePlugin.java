package bootstrap.gradle;

import bootstrap.api.LauncherConstants;
import bootstrap.gradle.script.BootstrapScriptGenerator;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.*;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.stream.Stream;

@NotNullByDefault
public class BootstrapGradlePlugin implements Plugin<Project> {

    public static final String BOOTSTRAP_EXTENSION_NAME = "bootstrap";
    public static final String BOOT_MODULES_CONFIGURATION_NAME = "bootModules";
    public static final String TASK_APP_HOME_NAME = "setupAppHome";
    
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        NamedDomainObjectProvider<Configuration> bootModules = project.getConfigurations().register(BOOT_MODULES_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
        });
        NamedDomainObjectProvider<Configuration> compileClasspath = project.getConfigurations().named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        NamedDomainObjectProvider<Configuration> runtimeClasspath = project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        compileClasspath.configure(configuration -> configuration.extendsFrom(bootModules.get()));

        BootstrapExtension bootstrapExt = project.getExtensions().create(BOOTSTRAP_EXTENSION_NAME, BootstrapExtension.class, project);
        bootstrapExt.getBootModules().convention(bootModules);
        bootstrapExt.getBootstrapClasspath().convention(runtimeClasspath);

        TaskProvider<Jar> jarTask = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        Provider<FileCollection> bootstrapClasspath = project.provider(() -> project
                .files(bootstrapExt.getBootstrapClasspath().get(), jarTask.get().getArchiveFile())
                .builtBy(jarTask, bootstrapExt.getDependencies())
        );
        
        TaskProvider<Copy> appHomeTask = project.getTasks().register(TASK_APP_HOME_NAME, Copy.class, task -> {
            task.setGroup("application");
            task.setDestinationDir(project.getLayout().getBuildDirectory().dir(task.getName()).get().getAsFile());
        });
        project.getGradle().projectsEvaluated(g -> appHomeTask.configure(task -> bootstrapExt.configureCopySpec(task.getRootSpec())));
        
        TaskProvider<JavaExec> runTask = project.getTasks().register(ApplicationPlugin.TASK_RUN_NAME, JavaExec.class, task -> {
            task.setGroup("application");
            task.dependsOn(jarTask);
            task.dependsOn(appHomeTask);
            task.dependsOn(bootstrapExt.getDependencies());
            task.setClasspath(project.files().from(bootstrapExt.getBootModules()));
            task.getMainModule().set(bootstrapExt.getMainModule());
            task.getMainClass().unset();
            task.getJvmArguments().convention(bootstrapExt.getJvmArgs().map(args -> Stream.concat(Stream.of("--add-modules", "ALL-DEFAULT", "--add-modules", "ALL-MODULE-PATH"), args.stream()).toList()));
            task.getModularity().getInferModulePath().set(true);

            JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
            JavaToolchainService toolchainExt = project.getExtensions().getByType(JavaToolchainService.class);
            task.getJavaLauncher().convention(toolchainExt.launcherFor(javaExt.getToolchain()));
        });

        project.getGradle().projectsEvaluated(g -> runTask.configure(task -> {
            task.systemProperty(LauncherConstants.PROP_CLASSPATH, bootstrapClasspath.map(FileCollection::getAsPath).get());
            task.systemProperty(LauncherConstants.PROP_HOME, appHomeTask.get().getDestinationDir().toPath().toAbsolutePath().toString());
            if (bootstrapExt.getEntrypoint().isPresent()) {
                task.systemProperty(LauncherConstants.PROP_ENTRYPOINT, bootstrapExt.getEntrypoint().get());
            }
        }));

        TaskProvider<CreateStartScripts> startScriptsTask = project.getTasks().register(ApplicationPlugin.TASK_START_SCRIPTS_NAME, CreateStartScripts.class, task -> {
            task.setGroup("distribution");
            task.dependsOn(jarTask);
            task.dependsOn(bootstrapExt.getDependencies());
            task.setOutputDir(project.getLayout().getBuildDirectory().dir(task.getName()).get().getAsFile());
            task.setClasspath(project.files().from(bootstrapExt.getBootModules()));
            task.getMainModule().set(bootstrapExt.getMainModule());
            task.getMainClass().unset();
            task.getModularity().getInferModulePath().set(true);
            task.setExecutableDir("bin");
            task.setUnixStartScriptGenerator(new BootstrapScriptGenerator(false, bootstrapExt, bootstrapClasspath));
            task.setWindowsStartScriptGenerator(new BootstrapScriptGenerator(true, bootstrapExt, bootstrapClasspath));
        });

        project.getGradle().projectsEvaluated(g -> startScriptsTask.configure(task -> {
            task.setApplicationName(bootstrapExt.getApplicationName().get());
            task.setDefaultJvmOpts(bootstrapExt.getJvmArgs().get());
        }));

        DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
        Distribution distribution = distributions.getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME);
        this.configureDistribution(project, distribution, bootstrapExt, bootModules, bootstrapClasspath, startScriptsTask);
    }
    
    private void configureDistribution(Project project, Distribution distribution, BootstrapExtension bootstrapExt, Provider<Configuration> bootModules, Provider<FileCollection> bootstrapClasspath, TaskProvider<CreateStartScripts> startScriptsTask) {
        distribution.getDistributionBaseName().convention(bootstrapExt.getApplicationName());

        CopySpec binarySpec = project.copySpec();
        binarySpec.into("bin");
        binarySpec.from(startScriptsTask);
        binarySpec.filePermissions((permissions) -> permissions.unix(Integer.parseInt("755", 8)));

        CopySpec bootModulesSpec = project.copySpec();
        bootModulesSpec.into("lib/boot");
        bootModulesSpec.from(bootModules);
        
        CopySpec bootstrapClasspathSpec = project.copySpec();
        bootstrapClasspathSpec.into("lib/classpath");
        bootstrapClasspathSpec.from(bootstrapClasspath);

        distribution.getContents().with(binarySpec, bootModulesSpec, bootstrapClasspathSpec);

        project.getGradle().projectsEvaluated(g -> bootstrapExt.configureCopySpec(distribution.getContents()));
    }
}
