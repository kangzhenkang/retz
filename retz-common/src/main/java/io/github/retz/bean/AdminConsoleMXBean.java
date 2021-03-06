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
package io.github.retz.bean;

import java.io.IOException;
import java.util.List;

public interface AdminConsoleMXBean {
    List<String> listUser() throws IOException;
    String createUser(String info) throws IOException;
    String getUser(String keyId) throws IOException;
    boolean enableUser(String id, boolean enabled) throws IOException;
    List<String> getUsage(String start, String end) throws IOException;

    boolean gc();
    boolean gc(int leeway);
}
