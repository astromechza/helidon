/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webserver.tyrus;

import java.net.URI;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class EchoServiceTest.
 */
public class EchoServiceTest extends TyrusSupportBaseTest {

    @BeforeAll
    public static void startServer() throws Exception {
        webServer(true, EchoEndpoint.class);
    }

    @Test
    public void testEchoSingle() {
        try {
            URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/echo");
            new EchoClient(uri).echo("One");
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        }
    }

    @Test
    public void testEchoMultiple() {
        try {
            URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/echo");
            new EchoClient(uri).echo("One", "Two", "Three");
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        }
    }
}
