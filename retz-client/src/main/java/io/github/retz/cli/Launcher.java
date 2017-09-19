/**
 *    Retz
 *    Copyright (C) 2016-2017 Nautilus Technologies, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.github.retz.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import io.github.retz.protocol.Protocol;
import io.github.retz.web.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Launcher {
    static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

    private static final List<SubCommand> SUB_COMMANDS;

    static {
        SUB_COMMANDS = new ArrayList<>();

        SUB_COMMANDS.add(new CommandHelp());
        SUB_COMMANDS.add(new CommandPing());
        SUB_COMMANDS.add(new CommandConfig());
        SUB_COMMANDS.add(new CommandList());
        SUB_COMMANDS.add(new CommandSchedule());
        SUB_COMMANDS.add(new CommandGetJob());
        SUB_COMMANDS.add(new CommandGetFile());
        SUB_COMMANDS.add(new CommandListFiles());
        SUB_COMMANDS.add(new CommandKill());
        SUB_COMMANDS.add(new CommandKillall());
        SUB_COMMANDS.add(new CommandRun());
        SUB_COMMANDS.add(new CommandGetApp());
        SUB_COMMANDS.add(new CommandListApp());
        SUB_COMMANDS.add(new CommandLoadApp());
    }

    public static void main(String... argv) {
        int status = execute(argv);
        System.exit(status);
    }

    public static int execute(String... argv) {
        LOG.debug(Client.VERSION_STRING);
        try {
            Configuration conf = parseConfiguration(argv);
            JCommander commander = conf.commander;

            if (commander.getParsedCommand() == null) {
                LOG.error("Invalid subcommand");
                help(SUB_COMMANDS);
            } else {
                if (conf.commands.verbose) {
                    LOG.info("Command={}, Config={}, Client version={} Protocol version={}",
                            commander.getParsedCommand(),
                            conf.commands.getConfigFile(),
                            Client.VERSION_STRING,
                            Protocol.PROTOCOL_VERSION);
                    LOG.info("Configuration: {}", conf.configuration.toString());
                }
                return conf.getParsedSubCommand().handle(conf.configuration, conf.commands.verbose);
            }

        } catch (MissingCommandException e) {
            LOG.error(e.toString(), e);
            help(SUB_COMMANDS);
        } catch (ParameterException e) {
            LOG.error(e.toString(), e);
            help(SUB_COMMANDS);
        } catch (Throwable t) {
            LOG.error(t.toString(), t);
        }
        return -1;
    }

    private static boolean oneOf(String key, String... list) {
        for (String s : list) {
            if (key.equals(s)) return true;
        }
        return false;
    }

    public static void help() {
        help(SUB_COMMANDS);
    }
    private static void help(List<SubCommand> subCommands) {
        LOG.info("Subcommands:");
        for (SubCommand subCommand : subCommands) {
            LOG.info("\t{}\t{} ({})", subCommand.getName(),
                    subCommand.description(), subCommand.getClass().getName());
        }
    }

    static Configuration parseConfiguration(String... argv) throws IOException, URISyntaxException {
        Configuration conf = new Configuration();

        conf.commands = new MainCommand();
        conf.commander = new JCommander(conf.commands);

        for (SubCommand subCommand : SUB_COMMANDS) {
            subCommand.add(conf.commander);
        }

        conf.commander.parse(argv);

        conf.configuration = new ClientCLIConfig(conf.commands.getConfigFile());
        return conf;
    }

    static class Configuration {
        ClientCLIConfig configuration;
        JCommander commander;
        MainCommand commands;

        SubCommand getParsedSubCommand() {
            for (SubCommand subCommand : SUB_COMMANDS) {
                if (subCommand.getName().equals(commander.getParsedCommand())) {
                    return subCommand;
                }
            }
            throw new ParameterException("unknown command: " + commander.getParsedCommand());
        }
    }
}

