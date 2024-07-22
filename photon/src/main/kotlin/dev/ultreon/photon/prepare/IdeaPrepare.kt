package dev.ultreon.photon.prepare

import org.gradle.api.Project
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.intellij.lang.annotations.Language
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

fun Project.prepareIdea() {
    fun Application.extracted(dependency: Project) {
        if (dependency.path == ":") {
            this.moduleName = rootProject.name + ".main"
            return
        }
        this.moduleName = rootProject.name + ".${dependency.name}.main"
    }

    (this.extensions.findByName("idea") as IdeaModel).apply {
        project { proj ->
            proj.settings.apply {
                fun ProjectSettings.setupIdea(dependency: Project) {
                    var name: String? = "(${this@prepareIdea.path})"
                    if (name == "(:)") name = null
                    dependency.afterEvaluate {
                        mkdir("${dependency.projectDir}/build/photon")
                        mkdir("${dependency.projectDir}/run")
                        mkdir("${dependency.projectDir}/run/client")
                        mkdir("${dependency.projectDir}/run/client/alt")
                        mkdir("${dependency.projectDir}/run/client/main")
                        mkdir("${dependency.projectDir}/run/server")

                        val ps = File.pathSeparator!!
                        val files = dependency.configurations.getByName("runtimeClasspath").files

                        val classPath = files.asSequence()
                            .filter { it != null }
                            .map { path }
                            .joinToString(ps)

                        @Language("XML")
                        val log4jConfig = """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <Configuration>
                                <Appenders>
                                    <!--=========================-->
                                    <!--     Console logging     -->
                                    <!--=========================-->
                                    <Console name="SysOut" target="SYSTEM_OUT">
                                        <PatternLayout
                                                pattern="%style{[%d{HH:mm:ss}]}{blue} %highlight{[%t/%level]}{FATAL=red, ERROR=red, WARN=yellow, INFO=green, DEBUG=darkGray, TRACE=blue} %style{(%logger{1}/%marker{1})}{cyan} %highlight{%msg%n}{FATAL=red, ERROR=red, WARN=normal, INFO=normal, DEBUG=normal, TRACE=normal}"
                                                disableAnsi="false"/>
                                    </Console>

                                    <JTextAreaAppender name="jtextarea-log" maxLines="100">
                                        <PatternLayout>
                                            <pattern>[%d{HH:mm:ss}] [%t/%level] (%logger) %msg%n</pattern>
                                        </PatternLayout>
                                    </JTextAreaAppender>
                                    <!--=======================-->
                                    <!--    Normal log file    -->
                                    <!--=======================-->
                                    <RollingRandomAccessFile name="LatestFile" fileName="logs/latest.log"
                                                             filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz">
                                        <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] (%logger) %msg%n"/>
                                        <Policies>
                                            <TimeBasedTriggeringPolicy/>
                                            <OnStartupTriggeringPolicy/>
                                        </Policies>
                                    </RollingRandomAccessFile>

                                    <!--======================-->
                                    <!--    Debug log file    -->
                                    <!--======================-->
                                    <RollingRandomAccessFile name="DebugFile" fileName="logs/debug.log" filePattern="logs/debug-%i.log.gz">
                                        <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] (%logger) %msg%n"/>

                                        <!--========================-->
                                        <!--    Keep 7 files max    -->
                                        <!--========================-->
                                        <DefaultRolloverStrategy max="7" fileIndex="min"/>
                                        <Policies>
                                            <SizeBasedTriggeringPolicy size="200MB"/>
                                            <OnStartupTriggeringPolicy/>
                                        </Policies>
                                    </RollingRandomAccessFile>
                                </Appenders>
                                <Loggers>
                                    <!--======================-->
                                    <!--    Logging levels    -->
                                    <!--======================-->
                                    <Logger name="io.netty" level="WARN"/>
                                    <Root level="all">
                                        <AppenderRef ref="SysOut" level="TRACE"/>
                                        <AppenderRef ref="LatestFile" level="INFO"/>
                                        <AppenderRef ref="DebugFile" level="TRACE"/>
                                    </Root>
                                </Loggers>
                            </Configuration>
                        """.trimIndent()

                        val log4jFile = file("${dependency.projectDir}/build/photon/log4j.xml")
                        Files.writeString(
                            log4jFile.toPath(),
                            log4jConfig,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                        )

//language=TEXT
                        val conf = """
commonProperties
  fabric.development=true
  log4j2.formatMsgNoLookups=true
  fabric.log.disableAnsi=false
  fabric.skipMcProvider=true
  fabric.zipfs.use_temp_file=false
  log4j.configurationFile=${dependency.projectDir}/build/photon/log4j.xml
    """.trimIndent()
                        val launchFile = file("${dependency.projectDir}/build/photon/launch.cfg")
                        Files.writeString(
                            launchFile.toPath(),
                            conf,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                        )

                        val cpFile = file("${dependency.projectDir}/build/photon/classpath.txt")
                        Files.writeString(
                            cpFile.toPath(),
                            classPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                        )

                        var defaultJvmArgs = "-Xmx4g -Dfabric.dli.config=${launchFile.path}"

                        if (System.getProperty("os.name").lowercase().startsWith("mac")) {
                            defaultJvmArgs += " -XstartOnFirstThread"
                        }

                        runConfigurations {
                            create(
                                if (name != null) "Quantum Voxel Client $name" else "Quantum Voxel Client",
                                Application::class.java
                            ).apply {
                                jvmArgs ="$defaultJvmArgs -Dfabric.dli.env=CLIENT -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient"
                                mainClass = "net.fabricmc.devlaunchinjector.Main"
                                extracted(dependency)
                                workingDirectory = "$projectDir/run/client/main/"
                                programParameters = "--gameDir=."
                            }
                            create(
                                if (name != null) "Quantum Voxel Data $name" else "Quantum Voxel Data",
                                Application::class.java
                            ).apply {
                                jvmArgs ="$defaultJvmArgs -Dfabric.dli.env=CLIENT -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient"
                                mainClass = "dev.ultreon.quantum.data.gen.DataGenerator"
                                extracted(dependency)
                                workingDirectory = "$projectDir/run/data/main/"
                                programParameters = "--gameDir=."
                            }
                            create(
                                if (name != null) "Quantum Voxel Client Alt $name" else "Quantum Voxel Client Alt",
                                Application::class.java
                            ).apply {
                                jvmArgs ="$defaultJvmArgs -Dfabric.dli.env=CLIENT -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient"
                                mainClass = "net.fabricmc.devlaunchinjector.Main"
                                extracted(dependency)
                                workingDirectory = "$projectDir/run/client/alt/"
                                programParameters = "--gameDir=."
                            }
                            create(
                                if (name != null) "Quantum Voxel Server $name" else "Quantum Voxel Server",
                                Application::class.java
                            ).apply {
                                jvmArgs ="$defaultJvmArgs -Dfabric.dli.env=SERVER -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer"
                                mainClass = "net.fabricmc.devlaunchinjector.Main"
                                extracted(dependency)
                                workingDirectory = "$projectDir/run/server/"
                                programParameters = "--gameDir=."
                            }
                        }
                    }

                }

                this.setupIdea(this@prepareIdea)
            }
        }
    }
}
