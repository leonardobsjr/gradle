/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging;

import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.buildoption.BuildOption;

import java.util.List;

public class LoggingCommandLineConverter extends AbstractCommandLineConverter<LoggingConfiguration> {
    private final List<BuildOption<LoggingConfiguration>> buildOptions = LoggingConfigurationBuildOptions.get();
    public static final String DEBUG = LoggingConfigurationBuildOptions.LogLevelOption.DEBUG_SHORT_OPTION;
    public static final String WARN = LoggingConfigurationBuildOptions.LogLevelOption.WARN_SHORT_OPTION;
    public static final String INFO = LoggingConfigurationBuildOptions.LogLevelOption.INFO_SHORT_OPTION;
    public static final String QUIET = LoggingConfigurationBuildOptions.LogLevelOption.QUIET_SHORT_OPTION;

    @Override
    public LoggingConfiguration convert(ParsedCommandLine commandLine, LoggingConfiguration loggingConfiguration) throws CommandLineArgumentException {
        for (BuildOption<LoggingConfiguration> option : buildOptions) {
            option.applyFromCommandLine(commandLine, loggingConfiguration);
        }

        return loggingConfiguration;
    }

    @Override
    public void configure(CommandLineParser parser) {
        for (BuildOption<LoggingConfiguration> option : buildOptions) {
            option.configure(parser);
        }
    }
}
