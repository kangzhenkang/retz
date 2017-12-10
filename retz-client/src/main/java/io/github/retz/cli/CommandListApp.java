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

import io.github.retz.protocol.ErrorResponse;
import io.github.retz.protocol.ListAppResponse;
import io.github.retz.protocol.Response;
import io.github.retz.protocol.data.Application;
import io.github.retz.web.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CommandListApp implements SubCommand {
    static final Logger LOG = LoggerFactory.getLogger(CommandListApp.class);

    @Override
    public String description() {
        return "List all loaded applications";
    }

    @Override
    public String getName() {
        return "list-app";
    }

    @Override
    public int handle(ClientCLIConfig fileConfig, boolean verbose)throws Throwable {
        LOG.debug("Configuration: {}", fileConfig.toString());
        try (io.github.retz.grpc.Client client = new io.github.retz.grpc.Client(fileConfig.getUri(), fileConfig.getAuthenticator())) {
            for (Application application : client.listApps()) {
                LOG.info(application.toString());
            }
        }
        return 0;
    }
    private int handle1(ClientCLIConfig fileConfig, boolean verbose) throws Throwable {
        try (Client webClient = Client.newBuilder(fileConfig.getUri())
                .setAuthenticator(fileConfig.getAuthenticator())
                .checkCert(!fileConfig.insecure())
                .setVerboseLog(verbose)
                .build()) {

            Response res = webClient.listApp();

            if (res instanceof ErrorResponse) {
                LOG.error(res.status());
                return -1;
            }
            ListAppResponse r = (ListAppResponse) res;

            for (Application a : r.applicationList()) {
                LOG.info(a.toString());
            }
            return 0;

        }
    }
}
